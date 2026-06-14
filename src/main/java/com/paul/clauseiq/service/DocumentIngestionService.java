package com.paul.clauseiq.service;

import com.paul.clauseiq.data.entity.DocumentMetadata;
import com.paul.clauseiq.data.entity.DocumentStatus;
import com.paul.clauseiq.event.DocumentUploadedEvent;
import com.paul.clauseiq.repository.DocumentRepository;
import com.paul.clauseiq.validation.DocumentValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final DocumentValidator validator;

    public UUID upload(MultipartFile file) throws IOException {

        validator.validate(file);

        Path uploadedFile = Files.createTempFile(
                "clauseiq-",
                ".pdf"
        );

        file.transferTo(uploadedFile);

        DocumentMetadata metadata = repository.save(
                DocumentMetadata.builder()
                        .fileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .status(DocumentStatus.UPLOADED)
                        .uploadedAt(Instant.now())
                        .build()
        );

        publisher.publishEvent(
                new DocumentUploadedEvent(
                        metadata.getId(),
                        uploadedFile
                )
        );

        return metadata.getId();
    }
}