# Product Recommendation Service

A backend service that aggregates data from external sources to deliver personalized product recommendations. Built with Java 21 and Spring WebFlux.

## Architecture Overview

### Why Spring WebFlux

The service integrates with two external APIs that have significant response latencies — up to 1800ms for the User Profile Service and 800ms for the Product Catalog Service. Under high concurrency (1000+ simultaneous requests), a traditional thread-per-request model would exhaust the thread pool. Spring WebFlux with Netty handles this efficiently using non-blocking I/O on a small number of event-loop threads.

### Caching Strategy

The external services have very different data freshness requirements, which drove the caching decisions:

| Service | Data changes | Cache TTL |
|---|---|---|
| User Profile | ~once per week | 30 minutes |
| Product Catalog | Prices: every few minutes / Inventory: real-time | 2 minutes |

User profiles are cached aggressively since they are expensive to fetch (1200–1800ms) and rarely change. Product catalog responses use a short TTL to keep prices and inventory reasonably fresh while still reducing load. Both caches use Caffeine, which is non-blocking and safe for reactive pipelines.

### Parallel Category Fetching

A user typically has 3 preferred categories. Rather than fetching them sequentially (which would take up to 3 × 800ms = 2400ms), the service uses `Flux.flatMap` to fetch all categories in parallel. The total product fetch time equals the slowest individual request, not the sum.

### Resilience Patterns

Each external service client has three layers of protection:

- **Timeout** — WebClient `.timeout()` caps individual requests (5s for user profiles, 2s for products)
- **Retry with backoff** — failed requests are retried with exponential backoff (2 retries for user profiles, 3 for products)
- **Circuit Breaker** — Resilience4j opens the circuit after 50% failure rate across a 10-request sliding window, preventing cascading failures

When a product category fails even after retries, the service returns partial results from the remaining categories instead of failing the entire request.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose

## Running Locally (without Docker)

Start WireMock first (requires Docker):

```bash
docker run --rm -p 8089:8080 \
  -v ./wiremock:/home/wiremock \
  wiremock/wiremock:latest \
  --global-response-templating --verbose
```

Then run the application:

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`.

## Running with Docker Compose

```bash
docker compose up --build
```

This starts three containers:
- `app` on port 8080 — the recommendation service
- `db` on port 5432 — PostgreSQL for feedback storage
- `wiremock` on port 8089 — simulated external services

## Running Tests

```bash
mvn test
```

## API Reference

All endpoints require HTTP Basic authentication.

Default credentials: `user` / `secret`

---

### GET /recommendations

Returns personalized product recommendations for a user.

**Query parameters**

| Parameter | Required | Description |
|---|---|---|
| userId | Yes | The user identifier |
| category | No | Filter recommendations to a specific category |

**Example request**

```bash
curl -u user:secret \
  "http://localhost:8080/recommendations?userId=123"
```

**Example response**

```json
{
  "recommendations": [
    {
      "userId": "123",
      "productId": "PROD-4821",
      "productName": "Premium electronics Item",
      "category": "electronics",
      "currentPrice": 149.99,
      "originalPrice": 249.99,
      "discount": 40,
      "averageRating": 4.5,
      "totalReviews": 342,
      "availability": "IN_STOCK",
      "recommendationReason": "Great deal with 40% discount",
      "generatedAt": "2025-11-26T10:30:00Z"
    }
  ],
  "totalRecommendations": 1
}
```

**Recommendation reasons** — the most specific applicable rule wins:
1. Discount available → `"Great deal with {N}% discount"`
2. High rating (≥ 4.0) → `"Highly rated product in {category}"`
3. Default → `"Matches your preferred category: {category}"`

**Error responses**

| Status | Cause |
|---|---|
| 401 | Missing or invalid credentials |
| 404 | User not found (userId: `notfound`, `unknown`, `invalid`) |
| 503 | External service unavailable and circuit breaker open |

---

### POST /recommendations/feedback

Records user feedback on a recommendation.

**Request body**

```json
{
  "userId": "123",
  "productId": "PROD-4821",
  "feedback": "liked",
  "comment": "Optional comment"
}
```

`feedback` accepts: `liked`, `disliked`, `purchased`, `not_interested`

**Example request**

```bash
curl -u user:secret -X POST \
  http://localhost:8080/recommendations/feedback \
  -H "Content-Type: application/json" \
  -d '{"userId":"123","productId":"PROD-4821","feedback":"liked"}'
```

**Response**

```json
{
  "status": "success",
  "message": "Feedback recorded successfully"
}
```

---

## Monitoring

Spring Boot Actuator exposes the following endpoints (no authentication required):

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health and component status |
| `GET /actuator/metrics` | JVM and application metrics |
| `GET /actuator/prometheus` | Prometheus-compatible metrics scrape endpoint |

Circuit breaker state is visible via `/actuator/metrics/resilience4j.circuitbreaker.state`.

## Performance Analysis

**Identified bottlenecks**

1. **User Profile Service latency (1200–1800ms)** — the dominant factor on cold requests. Addressed with Caffeine caching (30-minute TTL). Subsequent requests for the same user return immediately from cache.

2. **Sequential category fetching** — a user with 3 categories would take up to 2400ms if fetched one by one. Addressed with parallel fetching using `Flux.flatMap`, reducing this to the slowest single request (~800ms).

3. **Service failures under load** — cascading failures from a degraded external service could bring the entire service down. Addressed with circuit breakers that stop forwarding requests to failing services and return immediately with an error.

**Observed performance profile**

- Cold request (no cache): ~1200–1800ms (dominated by user profile fetch)
- Warm request (user profile cached): ~400–800ms (parallel product fetches)
- Degraded mode (circuit open): <50ms (immediate fallback)

## Project Structure

```
src/main/java/com/skyportugal/recommendation/
├── api/dto/          Response and request models for the public API
├── client/           WebClient-based HTTP clients for external services
│   └── dto/          Response models for external service contracts
├── config/           Spring configuration (security, caching, web clients)
├── controller/       REST controllers
├── domain/           JPA entities
├── exception/        Custom exceptions and global error handler
├── repository/       Spring Data JPA repositories
└── service/          Business logic
```
