package com.gymlet.web;

/** Thrown when a request has no valid authenticated user. Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
