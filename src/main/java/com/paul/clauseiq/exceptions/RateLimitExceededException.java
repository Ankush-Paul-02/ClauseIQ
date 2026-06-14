package com.paul.clauseiq.exceptions;

/**
 * Exception thrown when rate limits are exceeded.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    /**
     * Creates a new RateLimitExceededException.
     *
     * @param message           the error message
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Creates a new RateLimitExceededException with a cause.
     *
     * @param message           the error message
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @param cause             the underlying cause
     */
    public RateLimitExceededException(String message, long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}