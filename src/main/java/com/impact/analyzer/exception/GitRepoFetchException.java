package com.impact.analyzer.exception;

/**
 * Custom exception for clear error boundary when fetching GitHub repositories.
 */
public class GitRepoFetchException extends Exception {

    private final int statusCode;

    public GitRepoFetchException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0; // Default for IO/Network errors
    }

    public GitRepoFetchException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}