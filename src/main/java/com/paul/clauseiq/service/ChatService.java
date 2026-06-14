package com.paul.clauseiq.service;

import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.dto.ChatResponse;
import com.paul.clauseiq.dto.SourceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                        .similarityThreshold(0.75)
                        .build()
        );

        if (documents.isEmpty()) {
            return new ChatResponse(
                    "I could not find that information.",
                    Collections.emptyList()
            );
        }

        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        List<SourceDto> sources = documents.stream()
                .map(doc -> new SourceDto(
                        (String) doc.getMetadata().get(MetadataConstants.FILE_NAME),
                        (Integer) doc.getMetadata().get(MetadataConstants.CHUNK_INDEX)
                ))
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