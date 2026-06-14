package com.paul.clauseiq.validation;

import com.paul.clauseiq.exceptions.InvalidDocumentException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class DocumentValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException(
                    "File cannot be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidDocumentException(
                    "File exceeds maximum size limit"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {

            throw new InvalidDocumentException(
                    "Unsupported file type"
            );
        }
    }
}
