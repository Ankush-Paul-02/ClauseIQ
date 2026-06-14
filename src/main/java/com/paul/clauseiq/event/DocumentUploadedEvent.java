package com.paul.clauseiq.event;

import java.nio.file.Path;
import java.util.UUID;

public record DocumentUploadedEvent(
        UUID documentId,
        Path filePath
) {
}