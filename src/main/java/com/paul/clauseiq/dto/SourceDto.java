package com.paul.clauseiq.dto;

public record SourceDto(
        String documentId,
        String fileName,
        Integer chunkIndex
) {
}