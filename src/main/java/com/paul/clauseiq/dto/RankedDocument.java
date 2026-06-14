package com.paul.clauseiq.dto;

import org.springframework.ai.document.Document;

public record RankedDocument(
        Document document,
        double score
) {
}