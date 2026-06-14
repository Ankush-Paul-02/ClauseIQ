package com.paul.clauseiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paul.clauseiq.configuration.HybridSearchConfig;
import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.exceptions.SearchException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final HybridSearchConfig config;
    private final MeterRegistry meterRegistry;

    @Timed(value = "hybrid.search.time", description = "Time taken for hybrid search")
    @CircuitBreaker(name = "hybridSearch", fallbackMethod = "searchFallback")
    @Cacheable(value = "searchResults", key = "#query + '_' + #topK", unless = "#result.isEmpty()")
    public List<Document> search(String query, int topK) {
        validateInput(query, topK);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            List<Document> vectorResults = performVectorSearch(query);
            meterRegistry.counter("hybrid.search.vector.count").increment(vectorResults.size());

            String keywordQuery = extractKeywords(query);
            List<Document> keywordResults = performKeywordSearch(keywordQuery);
            meterRegistry.counter("hybrid.search.keyword.count").increment(keywordResults.size());

            if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
                meterRegistry.counter("hybrid.search.empty").increment();
                return Collections.emptyList();
            }

            List<Document> results = mergeAndRankResults(vectorResults, keywordResults, topK);

            stopWatch.stop();
            meterRegistry.timer("hybrid.search.success.time").record(stopWatch.getTotalTimeMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);

            return results;

        } catch (Exception e) {
            meterRegistry.counter("hybrid.search.error").increment();
            log.error("Error during hybrid search for query: {}", query, e);
            throw new SearchException("Failed to perform search", e);
        }
    }

    private List<Document> searchFallback(String query, int topK, Throwable t) {
        log.warn("Falling back to vector-only search for query: {}", query, t);
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .build()
            );
        } catch (Exception e) {
            log.error("Fallback search failed", e);
            return Collections.emptyList();
        }
    }

    private List<Document> performVectorSearch(String query) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(config.getVectorTopK())
                            .similarityThreshold(config.getVectorSimilarityThreshold())
                            .build()
            );
        } catch (Exception e) {
            log.error("Vector search failed", e);
            meterRegistry.counter("hybrid.search.vector.error").increment();
            return Collections.emptyList();
        }
    }

    private List<Document> performKeywordSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return keywordSearch(query);
        } catch (Exception e) {
            log.error("Keyword search failed", e);
            meterRegistry.counter("hybrid.search.keyword.error").increment();
            return Collections.emptyList();
        }
    }

    private List<Document> mergeAndRankResults(
            List<Document> vectorResults,
            List<Document> keywordResults,
            int topK) {

        Map<String, Double> chunkScores = new ConcurrentHashMap<>();

        applyRrf(chunkScores, vectorResults);
        applyRrf(chunkScores, keywordResults);

        // Document-level aggregation
        Map<String, Double> documentScores = new HashMap<>();
        Map<String, Document> bestChunkPerDocument = new LinkedHashMap<>();
        Map<String, Double> bestChunkScorePerDocument = new HashMap<>();

        // Sort chunks by RRF score
        List<Map.Entry<String, Double>> sortedScores = new ArrayList<>(chunkScores.entrySet());
        sortedScores.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        for (Map.Entry<String, Double> entry : sortedScores) {
            Document document = findDocument(entry.getKey(), vectorResults, keywordResults);
            if (document == null) continue;

            String documentId = getDocumentId(document);
            if (documentId == null) continue;

            // Keep highest-scoring chunk per document
            if (!bestChunkScorePerDocument.containsKey(documentId) ||
                    entry.getValue() > bestChunkScorePerDocument.get(documentId)) {
                bestChunkPerDocument.put(documentId, document);
                bestChunkScorePerDocument.put(documentId, entry.getValue());
            }

            documentScores.merge(documentId, entry.getValue(), Math::max);
        }

        // Calculate adaptive threshold
        double maxScore = documentScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);

        double threshold = calculateAdaptiveThreshold(maxScore);

        log.debug("Max score: {}, Threshold: {}", maxScore, threshold);

        // Filter and rank documents
        List<Document> filteredDocs = filterAndRankDocuments(
                bestChunkPerDocument, documentScores, threshold, topK);

        meterRegistry.gauge("hybrid.search.results.count", filteredDocs.size());

        return filteredDocs;
    }

    private List<Document> filterAndRankDocuments(
            Map<String, Document> bestChunkPerDocument,
            Map<String, Double> documentScores,
            double threshold,
            int topK) {

        List<Document> result = new ArrayList<>();

        for (Map.Entry<String, Document> entry : bestChunkPerDocument.entrySet()) {
            String documentId = entry.getKey();
            Document doc = entry.getValue();
            Double score = documentScores.get(documentId);

            if (score != null && score >= threshold) {
                result.add(doc);
                log.debug("Document {} passed threshold: score={}, threshold={}",
                        documentId, score, threshold);
            }
        }

        // Sort by score
        result.sort((d1, d2) -> {
            String id1 = getDocumentId(d1);
            String id2 = getDocumentId(d2);
            Double score1 = documentScores.get(id1);
            Double score2 = documentScores.get(id2);
            return Double.compare(score2 != null ? score2 : 0, score1 != null ? score1 : 0);
        });

        // Limit to topK
        if (result.size() > topK) {
            result = result.subList(0, topK);
        }

        // Fallback: if filtered everything, return top documents
        if (result.isEmpty() && !bestChunkPerDocument.isEmpty()) {
            log.info("Threshold filtered all documents. Returning top {} by score.", topK);

            List<Map.Entry<String, Document>> sortedEntries =
                    new ArrayList<>(bestChunkPerDocument.entrySet());
            sortedEntries.sort((e1, e2) -> {
                Double s1 = documentScores.get(e1.getKey());
                Double s2 = documentScores.get(e2.getKey());
                return Double.compare(s2 != null ? s2 : 0, s1 != null ? s1 : 0);
            });

            for (int i = 0; i < Math.min(topK, sortedEntries.size()); i++) {
                result.add(sortedEntries.get(i).getValue());
            }
        }

        return result;
    }

    private double calculateAdaptiveThreshold(double maxScore) {
        double threshold;

        if (maxScore < config.getLowScoreThreshold()) {
            threshold = maxScore * config.getLowScoreMultiplier();
        } else {
            threshold = maxScore * config.getNormalScoreMultiplier();
        }

        threshold = Math.max(threshold, config.getMinThreshold());
        threshold = Math.min(threshold, maxScore);

        return threshold;
    }

    private void applyRrf(Map<String, Double> scores, List<Document> documents) {
        int rrfK = config.getRrfK();

        for (int rank = 0; rank < documents.size(); rank++) {
            String id = documents.get(rank).getId();
            if (id != null) {
                scores.merge(id, 1.0 / (rrfK + rank), Double::sum);
            }
        }
    }

    private Document findDocument(String id, List<Document>... resultLists) {
        return Stream.of(resultLists)
                .flatMap(List::stream)
                .filter(document -> id.equals(document.getId()))
                .findFirst()
                .orElse(null);
    }

    private List<Document> keywordSearch(String query) {
        final String searchQuery = query.trim();

        return jdbcTemplate.query(
                config.getKeywordSearchQuery(),
                (rs, rowNum) -> {
                    try {
                        Map<String, Object> metadata = objectMapper.readValue(
                                rs.getString("metadata"), Map.class);

                        return new Document(
                                rs.getString("id"),
                                rs.getString("content"),
                                metadata
                        );
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse metadata for document", e);
                        throw new SearchException("Failed to parse document metadata", e);
                    }
                },
                searchQuery,
                searchQuery
        );
    }

    @CircuitBreaker(name = "keywordExtraction", fallbackMethod = "extractKeywordsFallback")
    private String extractKeywords(String query) {
        try {
            String keywords = chatClient
                    .prompt()
                    .system(config.getKeywordExtractionPrompt())
                    .user(query)
                    .call()
                    .content();

            return keywords != null ? keywords.trim() : query;
        } catch (Exception e) {
            log.error("Keyword extraction failed", e);
            meterRegistry.counter("hybrid.search.keyword.extraction.error").increment();
            throw e;
        }
    }

    private String extractKeywordsFallback(String query, Throwable t) {
        log.warn("Using original query as keywords fallback", t);
        return query;
    }

    private String getDocumentId(Document document) {
        try {
            return (String) document.getMetadata().get(MetadataConstants.DOCUMENT_ID);
        } catch (Exception e) {
            log.error("Failed to get document ID", e);
            return null;
        }
    }

    private void validateInput(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be null or empty");
        }
        if (topK <= 0 || topK > config.getMaxTopK()) {
            throw new IllegalArgumentException(
                    "topK must be between 1 and " + config.getMaxTopK());
        }
    }
}