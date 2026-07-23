package com.paul.clauseiq.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hybrid.search")
public class HybridSearchConfig {
    private int rrfK = 60;
    private int vectorTopK = 15;
    private int keywordTopK = 15;
    private int maxTopK = 50;
    private int maxChunksPerDocument = 3;
    private double vectorSimilarityThreshold = 0.3;
    private double lowScoreThreshold = 0.01;
    private double lowScoreMultiplier = 0.9;
    private double normalScoreMultiplier = 0.5;
    private double minThreshold = 0.01;
    private int keywordExtractionTimeout = 8;

    private String keywordSearchQuery = """
            SELECT id,
                   content,
                   metadata,
                   ts_rank(
                       content_tsv,
                       websearch_to_tsquery('english', ?)
                   ) AS rank
            FROM vector_store
            WHERE content_tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT ?;
            """;

    private String keywordExtractionPrompt = """
            You are an intelligent keyword extraction assistant.
            
            Given a user's question, extract the most important search terms that would help retrieve relevant information from any type of document.
            
            The documents may include resumes, offer letters, contracts, research papers, invoices, policies, manuals, emails, reports, or any other text.
            
            Rules:
            - Return only the keywords.
            - Separate keywords with spaces.
            - Preserve names, organizations, technologies, products, dates, and important nouns.
            - Remove filler words such as "what", "tell", "about", "do", "is", "the", etc.
            - Do not explain your answer.
            - Do not add punctuation.
            
            Examples:
            Question: "Who is Ankush Paul?"
            Output: Ankush Paul
            
            Question: "What is the salary mentioned in the offer letter?"
            Output: salary offer letter
            
            Question: "Explain the leave policy."
            Output: leave policy
            
            Question: "What technologies are used in the project?"
            Output: technologies project
            
            Question: "Summarize this document."
            Output: summary document
            """;
}