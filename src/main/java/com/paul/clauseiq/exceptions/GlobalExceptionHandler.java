package com.paul.clauseiq.exceptions;

import com.healthmarketscience.jackcess.ConstraintViolationException;
import com.paul.clauseiq.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<String> handleInvalidDocument(
            InvalidDocumentException ex
    ) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<ErrorResponse> handleSearchException(
            SearchException ex, WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Search error [{}]: {}", errorId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Search Error")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorResponse> handleChatException(
            ChatException ex, WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Chat error [{}]: {}", errorId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Chat Error")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ErrorResponse> handleDocumentProcessingException(
            DocumentProcessingException ex, WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Document processing error [{}]: {} (Document: {}, File: {})",
                errorId, ex.getMessage(), ex.getDocumentId(), ex.getFileName(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Document Processing Error")
                .message(ex.getMessage())
                .errorCode("DOCUMENT_PROCESSING_ERROR")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(
            RateLimitExceededException ex, WebRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Rate Limit Exceeded")
                .message(ex.getMessage())
                .errorCode("RATE_LIMIT_EXCEEDED")
                .path(request.getDescription(false))
                .retryAfterSeconds(ex.getRetryAfterSeconds())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .errorCode("INVALID_ARGUMENT")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {

        log.warn("Constraint violation: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Input validation failed: " + ex.getMessage())
                .errorCode("VALIDATION_ERROR")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String errorId = UUID.randomUUID().toString();
        log.error("Unexpected error [{}]: {}", errorId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .errorCode("INTERNAL_ERROR")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}