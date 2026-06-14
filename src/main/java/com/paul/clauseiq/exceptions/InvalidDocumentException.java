package com.paul.clauseiq.exceptions;

public class InvalidDocumentException
        extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}