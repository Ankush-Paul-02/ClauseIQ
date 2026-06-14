package com.paul.clauseiq.controller;

import com.paul.clauseiq.dto.ChatRequest;
import com.paul.clauseiq.dto.ChatResponse;
import com.paul.clauseiq.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse ask(
            @RequestBody ChatRequest request
    ) {
        return chatService.ask(request.question());
    }
}