package com.paul.clauseiq.service;

import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatResponse ask(String question) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build()
        );

        String context = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);

        List<String> sources = documents.stream()
                .map(d -> (String) d.getMetadata().get(MetadataConstants.FILE_NAME))
                .distinct()
                .toList();

        String answer = chatClient
                .prompt()
                .system("""
                        You are a document assistant.
                        
                        Answer ONLY using the supplied context.
                        
                        If answer is not found say:
                        I could not find that information.
                        """)
                .user("""
                        Context:
                        
                        %s
                        
                        Question:
                        
                        %s
                        """.formatted(
                        context,
                        question
                ))
                .call()
                .content();

        return new ChatResponse(answer, sources);
    }
}