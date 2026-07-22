package com.paul.clauseiq.strategy;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service("ollama")
public class OllamaStrategy implements AIStrategy {

    private final ChatClient chatClient;

    public OllamaStrategy(OllamaChatModel model) {
        this.chatClient = ChatClient.builder(model).build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public String extractKeyWords(String query) {
        return chatClient.prompt()
                .system("""
                        Extract only important search keywords.
                        Return comma separated keywords only.
                        """)
                .user(query)
                .call()
                .content();
    }
}
