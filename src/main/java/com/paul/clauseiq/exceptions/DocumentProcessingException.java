package com.paul.clauseiq.exceptions;

/**
 * Exception thrown when document processing operations fail.
 */
public class DocumentProcessingException extends RuntimeException {

    private final String documentId;
    private final String fileName;

    /**
     * Creates a new DocumentProcessingException.
     *
     * @param message    the error message
     * @param documentId the ID of the document being processed
     * @param fileName   the name of the file being processed
     */
    public DocumentProcessingException(String message, String documentId, String fileName) {
        super(message);
        this.documentId = documentId;
        this.fileName = fileName;
    }

    /**
     * Creates a new DocumentProcessingException with a cause.
     *
     * @param message    the error message
     * @param documentId the ID of the document being processed
     * @param fileName   the name of the file being processed
     * @param cause      the underlying cause
     */
    public DocumentProcessingException(String message, String documentId, String fileName, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
        this.fileName = fileName;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFileName() {
        return fileName;
    }
}