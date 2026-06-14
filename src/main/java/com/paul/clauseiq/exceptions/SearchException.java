package com.paul.clauseiq.exceptions;

/**
 * Exception thrown when search operations fail.
 */
public class SearchException extends RuntimeException {

    private final String errorCode;
    private final transient Object additionalInfo;

    /**
     * Creates a new SearchException with a message.
     *
     * @param message the error message
     */
    public SearchException(String message) {
        super(message);
        this.errorCode = "SEARCH_ERROR";
        this.additionalInfo = null;
    }

    /**
     * Creates a new SearchException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public SearchException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SEARCH_ERROR";
        this.additionalInfo = null;
    }

    /**
     * Creates a new SearchException with a message, error code, and cause.
     *
     * @param message the error message
     * @param errorCode a specific error code for categorization
     * @param cause the underlying cause
     */
    public SearchException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.additionalInfo = null;
    }

    /**
     * Creates a new SearchException with additional diagnostic information.
     *
     * @param message the error message
     * @param errorCode a specific error code for categorization
     * @param additionalInfo additional diagnostic information
     * @param cause the underlying cause
     */
    public SearchException(String message, String errorCode, Object additionalInfo, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.additionalInfo = additionalInfo;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getAdditionalInfo() {
        return additionalInfo;
    }
}