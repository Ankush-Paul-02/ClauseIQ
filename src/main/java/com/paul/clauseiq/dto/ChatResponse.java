package com.paul.clauseiq.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        List<String> sources
) {
}