package com.skyportugal.recommendation.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}
