package com.paul.clauseiq.exceptions;

/**
 * Exception thrown when chat operations fail.
 */
public class ChatException extends RuntimeException {

    private final String errorCode;
    private final String question;
    private final transient Object context;

    /**
     * Creates a new ChatException with a message.
     *
     * @param message the error message
     */
    public ChatException(String message) {
        super(message);
        this.errorCode = "CHAT_ERROR";
        this.question = null;
        this.context = null;
    }

    /**
     * Creates a new ChatException with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ChatException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CHAT_ERROR";
        this.question = null;
        this.context = null;
    }

    /**
     * Creates a new ChatException with detailed information about the failed operation.
     *
     * @param message   the error message
     * @param errorCode a specific error code for categorization
     * @param question  the question that was being processed
     * @param cause     the underlying cause
     */
    public ChatException(String message, String errorCode, String question, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.question = question;
        this.context = null;
    }

    /**
     * Creates a new ChatException with full context about the failure.
     *
     * @param message   the error message
     * @param errorCode a specific error code for categorization
     * @param question  the question that was being processed
     * @param context   additional context about what was happening
     * @param cause     the underlying cause
     */
    public ChatException(String message, String errorCode, String question, Object context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.question = question;
        this.context = context;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getQuestion() {
        return question;
    }

    public Object getContext() {
        return context;
    }
}