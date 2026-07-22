package com.paul.clauseiq.service;

import com.paul.clauseiq.data.entity.DocumentMetadata;
import com.paul.clauseiq.data.entity.DocumentStatus;
import com.paul.clauseiq.event.DocumentUploadedEvent;
import com.paul.clauseiq.repository.DocumentRepository;
import com.paul.clauseiq.validation.DocumentValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final DocumentValidator validator;

    public List<UUID> upload(MultipartFile[] files) throws IOException {

        List<UUID> uuids = new ArrayList<>();

        for (MultipartFile file : files) {

            validator.validate(file);

            String originalFilename = file.getOriginalFilename();

            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".tmp";

            Path uploadedFile = Files.createTempFile(
                    "clauseiq-",
                    extension
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

            log.info(
                    "Uploaded document id={} file={} size={} bytes",
                    metadata.getId(),
                    metadata.getFileName(),
                    metadata.getFileSize()
            );

            publisher.publishEvent(
                    new DocumentUploadedEvent(
                            metadata.getId(),
                            uploadedFile
                    )
            );

            uuids.add(metadata.getId());
        }

        return uuids;
    }
}