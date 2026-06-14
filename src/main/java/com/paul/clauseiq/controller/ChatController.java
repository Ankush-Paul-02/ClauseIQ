package com.paul.clauseiq.controller;

import com.paul.clauseiq.dto.ChatResponse;
import com.paul.clauseiq.service.ChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> askQuestion(
            @RequestParam
            @NotBlank(message = "Question cannot be empty")
            @Size(max = 500, message = "Question too long")
            String question
    ) {
        try {
            ChatResponse response = chatService.ask(question);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw e; // Will be handled by GlobalExceptionHandler
        } catch (Exception e) {
            log.error("Error processing question", e);
            throw new RuntimeException("Failed to process question", e);
        }
    }
}