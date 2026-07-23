package com.paul.clauseiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paul.clauseiq.configuration.AiProperties;
import com.paul.clauseiq.configuration.HybridSearchConfig;
import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.exceptions.SearchException;
import com.paul.clauseiq.factory.AiStrategyFactory;
import com.paul.clauseiq.strategy.AIStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AiStrategyFactory aiStrategyFactory;
    private final AiProperties aiProperties;
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
            log.info("Vector search returned {} results for query: '{}'", vectorResults.size(), query);
            for (int i = 0; i < Math.min(3, vectorResults.size()); i++) {
                Document doc = vectorResults.get(i);
                log.info("Vector result[{}]: id={}, doc_id={}, score={}, content_preview={}",
                        i, doc.getId(),
                        getDocumentId(doc),
                        doc.getMetadata().getOrDefault("similarity_score", "N/A"),
                        doc.getText() != null ? doc.getText().substring(0, Math.min(50, doc.getText().length())) : "NULL");
            }

            String keywordQuery = extractKeywords(query);
            List<Document> keywordResults = performKeywordSearch(keywordQuery);
            meterRegistry.counter("hybrid.search.keyword.count").increment(keywordResults.size());
            log.info("Keyword search returned {} results for query: '{}'", keywordResults.size(), keywordQuery);
            for (int i = 0; i < Math.min(3, keywordResults.size()); i++) {
                Document doc = keywordResults.get(i);
                log.info("Keyword result[{}]: id={}, doc_id={}, content_preview={}",
                        i, doc.getId(),
                        getDocumentId(doc),
                        doc.getText() != null ? doc.getText().substring(0, Math.min(50, doc.getText().length())) : "NULL");
            }

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
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(config.getVectorTopK())
                            .similarityThreshold(config.getVectorSimilarityThreshold())
                            .build()
            );

            for (Document doc : results) {
                if (doc.getMetadata().get(MetadataConstants.DOCUMENT_ID) == null) {
                    log.error("Vector search result missing DOCUMENT_ID! Chunk ID: {}, Available metadata: {}",
                            doc.getId(), doc.getMetadata().keySet());
                }
            }

            return results;
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
            int topK
    ) {

        Map<String, Double> chunkScores = new ConcurrentHashMap<>();
        applyRrf(chunkScores, vectorResults);
        applyRrf(chunkScores, keywordResults);

        // Sort chunks by RRF score, descending
        List<Map.Entry<String, Double>> sortedScores = new ArrayList<>(chunkScores.entrySet());
        sortedScores.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        Map<String, Double> documentScores = new HashMap<>();
        Map<String, List<Document>> chunksPerDocument = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : sortedScores) {
            Document document = findDocument(entry.getKey(), vectorResults, keywordResults);
            if (document == null) {
                log.warn("Document not found for chunk key: {}", entry.getKey());
                continue;
            }

            String documentId = getDocumentId(document);
            if (documentId == null) {
                log.warn("Skipping chunk id={} - missing DOCUMENT_ID metadata. Available metadata: {}",
                        entry.getKey(), document.getMetadata());
                continue;
            }

            log.debug("Processing chunk: doc_id={}, score={}, content_length={}",
                    documentId, entry.getValue(),
                    document.getText() != null ? document.getText().length() : 0);

            // chunks arrive already sorted by score, so appending preserves best-first order
            chunksPerDocument.computeIfAbsent(documentId, id -> new ArrayList<>()).add(document);
            documentScores.merge(documentId, entry.getValue(), Math::max);
        }

        log.info("After RRF merge: {} unique documents, {} total chunks",
                chunksPerDocument.size(),
                chunksPerDocument.values().stream().mapToInt(List::size).sum());

        // Cap chunks kept per document so one document can't dominate the whole context
        int maxChunksPerDocument = config.getMaxChunksPerDocument(); // e.g. 3
        chunksPerDocument.replaceAll((docId, chunks) ->
                chunks.size() > maxChunksPerDocument ? chunks.subList(0, maxChunksPerDocument) : chunks);

        double maxScore = documentScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);

        double threshold = calculateAdaptiveThreshold(maxScore);
        log.debug("Max score: {}, Threshold: {}", maxScore, threshold);

        List<Document> filteredDocs = filterAndRankDocuments(chunksPerDocument, documentScores, threshold, topK);

        meterRegistry.gauge("hybrid.search.results.count", filteredDocs.size());
        return filteredDocs;
    }

    private List<Document> filterAndRankDocuments(
            Map<String, List<Document>> chunksPerDocument,
            Map<String, Double> documentScores,
            double threshold,
            int topK) {

        List<String> passingDocIds = chunksPerDocument.keySet().stream()
                .filter(id -> documentScores.getOrDefault(id, 0.0) >= threshold)
                .sorted(Comparator.comparingDouble((String id) -> documentScores.getOrDefault(id, 0.0)).reversed())
                .toList();

        if (passingDocIds.isEmpty() && !chunksPerDocument.isEmpty()) {
            log.info("Threshold filtered all documents. Returning top {} documents by score instead.", topK);
            passingDocIds = chunksPerDocument.keySet().stream()
                    .sorted(Comparator.comparingDouble((String id) -> documentScores.getOrDefault(id, 0.0)).reversed())
                    .toList();
        }

        List<Document> result = new ArrayList<>();
        for (String docId : passingDocIds.stream().limit(topK).toList()) {
            result.addAll(chunksPerDocument.get(docId));
            log.debug("Document {} included: score={}, chunks={}",
                    docId, documentScores.get(docId), chunksPerDocument.get(docId).size());
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
            scores.merge(id, 1.0 / (rrfK + rank), Double::sum);
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

        log.info("Executing keyword search with SQL query and parameter: '{}'", searchQuery);

        try {
            return jdbcTemplate.query(
                    config.getKeywordSearchQuery(),
                    (rs, rowNum) -> {
                        try {
                            Map<String, Object> metadata = objectMapper.readValue(rs.getString("metadata"), Map.class);

                            Document doc = new Document(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    metadata
                            );

                            // Validate metadata
                            if (doc.getMetadata().get(MetadataConstants.DOCUMENT_ID) == null) {
                                log.warn("Keyword result missing DOCUMENT_ID: id={}", doc.getId());
                            }

                            return doc;
                        } catch (JsonProcessingException e) {
                            log.error("Failed to parse metadata for row {}", rowNum, e);
                            throw new SearchException("Failed to parse document metadata", e);
                        }
                    },
                    searchQuery,
                    searchQuery,
                    config.getKeywordTopK()
            );
        } catch (Exception e) {
            log.error("Keyword search SQL failed for query: '{}'. Error: {}", searchQuery, e.getMessage());

            // Try with simpler query as fallback
            try {
                String fallbackQuery = searchQuery.replaceAll("[^\\w\\s]", " ").trim();
                log.info("Retrying keyword search with cleaned query: '{}'", fallbackQuery);
                return jdbcTemplate.query(
                        config.getKeywordSearchQuery(),
                        (rs, rowNum) -> {
                            Map<String, Object> metadata = null;
                            try {
                                metadata = objectMapper.readValue(
                                        rs.getString("metadata"), Map.class);
                            } catch (JsonProcessingException ex) {
                                throw new RuntimeException(ex);
                            }
                            return new Document(rs.getString("id"), rs.getString("content"), metadata);
                        },
                        fallbackQuery,
                        fallbackQuery,
                        config.getKeywordTopK()
                );
            } catch (Exception ex) {
                log.error("Fallback keyword search also failed", ex);
                return Collections.emptyList();
            }
        }
    }

    @CircuitBreaker(name = "keywordExtraction", fallbackMethod = "extractKeywordsFallback")
    private String extractKeywords(String query) {
        try {
/*
            String keywords = chatClient
                    .prompt()
                    .system(config.getKeywordExtractionPrompt())
                    .user(query)
                    .call()
                    .content();
*/
            AIStrategy aiStrategy = aiStrategyFactory.get(aiProperties.getKeywordProvider());
            String keywords = aiStrategy.extractKeyWords(query);

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
            Object docId = document.getMetadata().get(MetadataConstants.DOCUMENT_ID);
            if (docId == null) {
                log.warn("Document {} has no DOCUMENT_ID metadata. Available metadata keys: {}",
                        document.getId(), document.getMetadata().keySet());

                // Fallback: try to extract from content or use document ID itself
                String fallbackId = document.getId();
                log.warn("Using fallback document ID: {}", fallbackId);
                return fallbackId;
            }
            log.debug("Found DOCUMENT_ID: {} for chunk: {}", docId, document.getId());
            return docId.toString();
        } catch (Exception e) {
            log.error("Failed to get document ID for chunk: {}", document.getId(), e);
            // Fallback to chunk ID as document identifier
            return document.getId();
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