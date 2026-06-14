package com.paul.clauseiq.dto;

import com.paul.clauseiq.data.entity.DocumentStatus;

public record DocumentStatusResponse(
        DocumentStatus status
) {
}