package com.paul.clauseiq.dto;

public record SourceDto(
        String fileName,
        Integer chunkIndex
) {
}