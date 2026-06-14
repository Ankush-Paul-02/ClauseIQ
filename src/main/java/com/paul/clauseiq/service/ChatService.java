package com.paul.clauseiq.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public String ask(String question) {

        return chatClient
                .prompt()
                .user(question)
                .advisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                .call()
                .content();
    }
}