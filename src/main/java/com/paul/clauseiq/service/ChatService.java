package com.paul.clauseiq.service;

import com.paul.clauseiq.configuration.ChatServiceConfig;
import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.dto.ChatResponse;
import com.paul.clauseiq.dto.SourceDto;
import com.paul.clauseiq.exceptions.ChatException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final ChatServiceConfig config;
    private final MeterRegistry meterRegistry;

    @Timed(value = "chat.ask.time", description = "Time taken to answer question")
    @CircuitBreaker(name = "chatService", fallbackMethod = "askFallback")
    @RateLimiter(name = "chatService")
    @Cacheable(value = "chatResponses", key = "#question", unless = "#result == null || #result.answer.isEmpty()")
    public ChatResponse ask(String question) {
        validateInput(question);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            log.info("Processing question: {}", question);
            meterRegistry.counter("chat.questions.total").increment();

            List<Document> documents = hybridSearchService.search(question, config.getMaxDocuments());

            if (documents.isEmpty()) {
                meterRegistry.counter("chat.no.results").increment();
                log.warn("No documents found for question: {}", question);
                return createEmptyResponse();
            }

            log.info("Found {} documents from hybrid search", documents.size());

            // Log document details for debugging
            for (Document doc : documents) {
                String fileName = getMetadataValue(doc, MetadataConstants.FILE_NAME);
                String content = doc.getText();
                log.info("Document: {}, Content length: {}, Content preview: {}",
                        fileName,
                        content != null ? content.length() : 0,
                        content != null ? content.substring(0, Math.min(100, content.length())) : "NULL");
            }

            // Verify relevance for multiple documents
            List<Document> verifiedDocuments = documents.size() == 1 ?
                    documents :
                    verifyDocumentRelevance(documents, question);

            if (verifiedDocuments.isEmpty()) {
                log.info("No documents passed verification, using top document");
                verifiedDocuments = Collections.singletonList(documents.get(0));
            }

            log.info("Using {} verified documents for answer generation", verifiedDocuments.size());

            ChatResponse response = buildResponse(verifiedDocuments, question);

            // Validate response
            if (response.answer() == null || response.answer().trim().isEmpty()) {
                log.error("Generated empty answer for question: {}", question);
                meterRegistry.counter("chat.empty.responses").increment();

                // Try fallback answer generation
                String fallbackAnswer = generateSimpleAnswer(verifiedDocuments, question);
                response = new ChatResponse(fallbackAnswer, response.sources());
            }

            stopWatch.stop();
            meterRegistry.timer("chat.ask.success.time")
                    .record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);

            return response;

        } catch (Exception e) {
            meterRegistry.counter("chat.errors").increment();
            log.error("Error processing question: {}", question, e);
            throw new ChatException("Failed to process question", e);
        }
    }

    private ChatResponse askFallback(String question, Throwable t) {
        log.warn("Fallback response for question: {}", question, t);
        return new ChatResponse(
                "I'm experiencing technical difficulties. Please try again later.",
                Collections.emptyList()
        );
    }

    private ChatResponse buildResponse(List<Document> documents, String question) {
        String context = buildContext(documents);
        List<SourceDto> sources = extractSources(documents);

        log.info("Preparing answer with {} sources. Context length: {}", sources.size(), context.length());
        log.debug("Context: {}", context);

        try {
            String answer = generateAnswer(context, question);

            log.info("Generated answer length: {}", answer != null ? answer.length() : 0);
            log.debug("Generated answer: {}", answer);

            if (answer == null || answer.trim().isEmpty()) {
                log.error("LLM returned empty/null answer");

                // Try with simplified prompt
                answer = generateSimpleAnswer(documents, question);
            }

            meterRegistry.counter("chat.responses.generated").increment();
            return new ChatResponse(answer, sources);

        } catch (Exception e) {
            log.error("Failed to generate answer", e);

            // Generate a simple answer from context
            String simpleAnswer = generateSimpleAnswer(documents, question);
            return new ChatResponse(simpleAnswer, sources);
        }
    }

    private String buildContext(List<Document> documents) {
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (i > 0) {
                contextBuilder.append("\n\n").append("=".repeat(50)).append("\n\n");
            }

            String content = doc.getText();
            if (content == null || content.trim().isEmpty()) {
                log.warn("Document has null/empty content: {}",
                        getMetadataValue(doc, MetadataConstants.FILE_NAME));
                continue;
            }

            contextBuilder.append(String.format("""
                            FILE: %s
                            DOCUMENT_ID: %s
                            CHUNK_INDEX: %s
                            
                            CONTENT:
                            %s
                            """,
                    getMetadataValue(doc, MetadataConstants.FILE_NAME),
                    getMetadataValue(doc, MetadataConstants.DOCUMENT_ID),
                    getMetadataValue(doc, MetadataConstants.CHUNK_INDEX),
                    content
            ));
        }

        String context = contextBuilder.toString();

        // If context is too long, truncate it
        if (context.length() > 4000) {
            log.warn("Context too long ({} chars), truncating to 4000 chars", context.length());
            context = context.substring(0, 4000) + "\n... (truncated)";
        }

        return context;
    }

    private String generateAnswer(String context, String question) {
        try {
            // Simplified prompt to get more reliable responses
            String systemPrompt = """
                    You are a resume search assistant. Answer the question based on the provided context.
                    
                    Rules:
                    1. Only mention information found in the context
                    2. Be concise and direct
                    3. If the context doesn't contain the answer, say so clearly
                    4. Quote relevant parts when possible
                    """;

            String userPrompt = String.format("""
                    Context:
                    %s
                    
                    Question: %s
                    
                    Answer:
                    """, context, question);

            log.debug("System prompt: {}", systemPrompt);
            log.debug("User prompt length: {}", userPrompt.length());

            String answer = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (answer != null) {
                answer = answer.trim();
                log.info("LLM returned answer of length: {}", answer.length());
            } else {
                log.error("LLM returned null response");
            }

            return answer;

        } catch (Exception e) {
            log.error("Error calling LLM", e);
            return null;
        }
    }

    /**
     * Generate a simple answer without LLM as fallback
     */
    private String generateSimpleAnswer(List<Document> documents, String question) {
        log.info("Generating simple answer without LLM");

        StringBuilder answer = new StringBuilder();
        answer.append("Based on the search results, here's what I found:\n\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String fileName = getMetadataValue(doc, MetadataConstants.FILE_NAME);
            String content = doc.getText();

            answer.append(String.format("From %s:\n", fileName));

            if (content != null && !content.isEmpty()) {
                // Extract relevant sentences
                String[] sentences = content.split("[.!?]+");
                List<String> relevantSentences = new ArrayList<>();

                String queryLower = question.toLowerCase();
                for (String sentence : sentences) {
                    if (sentence.toLowerCase().contains(queryLower.split("\\s+")[0])) {
                        relevantSentences.add(sentence.trim());
                    }
                }

                if (!relevantSentences.isEmpty()) {
                    for (String sentence : relevantSentences.subList(0, Math.min(3, relevantSentences.size()))) {
                        answer.append("  - ").append(sentence).append(".\n");
                    }
                } else {
                    // Just show first 200 chars
                    String preview = content.length() > 200 ?
                            content.substring(0, 200) + "..." : content;
                    answer.append("  ").append(preview).append("\n");
                }
            }
            answer.append("\n");
        }

        return answer.toString();
    }

    private List<Document> verifyDocumentRelevance(List<Document> documents, String question) {
        if (documents.size() <= 1) {
            return documents;
        }

        log.debug("Verifying relevance of {} documents", documents.size());

        String queryLower = question.toLowerCase();
        List<String> keyTerms = extractKeyTerms(queryLower);

        log.debug("Extracted key terms: {}", keyTerms);

        List<Document> relevantDocs = new ArrayList<>();

        for (Document doc : documents) {
            String fileName = getMetadataValue(doc, MetadataConstants.FILE_NAME);
            String content = doc.getText();

            if (content == null) {
                log.warn("Document '{}' has null content", fileName);
                continue;
            }

            content = content.toLowerCase();
            boolean isRelevant = false;

            for (String term : keyTerms) {
                if (content.contains(term)) {
                    isRelevant = true;
                    log.debug("  - Term '{}' found in '{}'", term, fileName);
                    break;
                }
            }

            log.debug("Document '{}': {}", fileName, isRelevant ? "RELEVANT" : "NOT RELEVANT");

            if (isRelevant) {
                relevantDocs.add(doc);
            }
        }

        // If no documents are relevant, return original list
        if (relevantDocs.isEmpty()) {
            log.warn("No documents passed relevance check, returning all documents");
            return documents;
        }

        meterRegistry.gauge("chat.verified.documents", relevantDocs.size());

        return relevantDocs;
    }

    private List<SourceDto> extractSources(List<Document> documents) {
        Set<String> seenDocumentIds = new HashSet<>();
        List<SourceDto> sources = new ArrayList<>();

        for (Document doc : documents) {
            String documentId = getMetadataValue(doc, MetadataConstants.DOCUMENT_ID);
            if (documentId != null && !seenDocumentIds.contains(documentId)) {
                seenDocumentIds.add(documentId);
                sources.add(new SourceDto(
                        documentId,
                        getMetadataValue(doc, MetadataConstants.FILE_NAME),
                        getIntegerMetadata(doc, MetadataConstants.CHUNK_INDEX)
                ));
            }
        }

        return sources;
    }

    private List<String> extractKeyTerms(String query) {
        String[] words = query.replaceAll("[?.,!]", " ").split("\\s+");

        Set<String> stopWords = config.getStopWords();

        return Arrays.stream(words)
                .map(String::toLowerCase)
                .filter(word -> word.length() >= 2)
                .filter(word -> !stopWords.contains(word))
                .distinct()
                .toList();
    }

    private ChatResponse createEmptyResponse() {
        return new ChatResponse(
                config.getNoResultsMessage(),
                Collections.emptyList()
        );
    }

    private String getMetadataValue(Document doc, String key) {
        try {
            Object value = doc.getMetadata().get(key);
            return value != null ? value.toString() : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    private Integer getIntegerMetadata(Document doc, String key) {
        try {
            Object value = doc.getMetadata().get(key);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateInput(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be null or empty");
        }
        if (question.length() > config.getMaxQuestionLength()) {
            throw new IllegalArgumentException(
                    "Question exceeds maximum length of " + config.getMaxQuestionLength());
        }
    }
}