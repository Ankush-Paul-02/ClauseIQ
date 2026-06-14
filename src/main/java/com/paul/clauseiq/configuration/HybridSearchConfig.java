package com.paul.clauseiq.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hybrid.search")
public class HybridSearchConfig {
    private int rrfK = 60;
    private int vectorTopK = 5;
    private int keywordTopK = 10;
    private int maxTopK = 50;
    private double vectorSimilarityThreshold = 0.6;
    private double lowScoreThreshold = 0.02;
    private double lowScoreMultiplier = 0.9;
    private double normalScoreMultiplier = 0.5;
    private double minThreshold = 0.01;
    private int keywordExtractionTimeout = 8;

    private String keywordSearchQuery = """
            SELECT id, content, metadata,
                   ts_rank(content_tsv, websearch_to_tsquery(?)) AS rank
            FROM vector_store
            WHERE content_tsv @@ websearch_to_tsquery(?)
            ORDER BY rank DESC
            LIMIT 20
            """;

    private String keywordExtractionPrompt = """
            Extract the main technical skills, technologies, 
            and key terms from this query.
            Return ONLY the keywords separated by spaces.
            Example: "Who knows Java and Spring?" -> "Java Spring"
            No explanations. No punctuation.
            """;
}