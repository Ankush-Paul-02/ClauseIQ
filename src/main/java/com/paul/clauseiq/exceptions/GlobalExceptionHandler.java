package com.paul.clauseiq.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<String> handleInvalidDocument(
            InvalidDocumentException ex
    ) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}