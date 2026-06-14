package com.paul.clauseiq.service;

import com.paul.clauseiq.constants.MetadataConstants;
import com.paul.clauseiq.data.entity.DocumentMetadata;
import com.paul.clauseiq.data.entity.DocumentStatus;
import com.paul.clauseiq.event.DocumentUploadedEvent;
import com.paul.clauseiq.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Handles asynchronous document ingestion for ClauseIQ.
 * <p>
 * This component is triggered via Spring Application Events after a document
 * upload is accepted by the system. Processing is intentionally decoupled
 * from the HTTP request lifecycle to improve responsiveness and support
 * future migration to distributed event brokers such as Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncDocumentProcessor {

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    /**
     * Consumes document upload events and executes the ingestion pipeline.
     * <p>
     * Lifecycle:
     * UPLOADED -> PROCESSING -> COMPLETED
     * or
     * FAILED
     * <p>
     * Any ingestion failure is captured and reflected in document status
     * so the client can query processing progress independently.
     */
    @Async("documentExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void process(@NonNull DocumentUploadedEvent event) {

        DocumentMetadata metadata = documentRepository.findById(
                        event.documentId()
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Document not found: " + event.documentId()
                        )
                );

        try {
            metadata.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(metadata);

            processDocument(event.filePath(), metadata);

            metadata.setStatus(DocumentStatus.COMPLETED);

            metadata.setProcessedAt(Instant.now());

        } catch (Exception ex) {
            log.error("Failed ingestion for documentId={}", metadata.getId(), ex);
            metadata.setStatus(DocumentStatus.FAILED);
            metadata.setProcessedAt(Instant.now());
        }

        documentRepository.save(metadata);
    }

    /**
     * Converts a document into vector-searchable chunks.
     * <p>
     * Processing stages:
     * 1. Extract raw text using Apache Tika.
     * 2. Enrich chunks with retrieval metadata.
     * 3. Split content into embedding-friendly segments.
     * 4. Persist chunk embeddings into PGVector.
     * <p>
     * InputStream is used instead of temporary files to keep the ingestion
     * pipeline storage-agnostic and compatible with future S3/Azure Blob
     * integrations.
     */
    private void processDocument(
            Path filePath,
            DocumentMetadata metadata
    ) throws IOException {

        try (InputStream inputStream = Files.newInputStream(filePath)) {

            List<Document> documents = getDocuments(inputStream);

            TokenTextSplitter splitter = TokenTextSplitter
                    .builder()
                    .withChunkSize(500)
                    .withMinChunkSizeChars(200)
                    .withMinChunkLengthToEmbed(100)
                    .build();

            List<Document> chunks = splitter.apply(documents);

            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);

                chunk.getMetadata().put(
                        MetadataConstants.CHUNK_INDEX,
                        i + 1
                );

                chunk.getMetadata().put(
                        MetadataConstants.DOCUMENT_ID,
                        metadata.getId().toString()
                );

                chunk.getMetadata().put(
                        MetadataConstants.FILE_NAME,
                        metadata.getFileName()
                );

                chunk.getMetadata().put(
                        MetadataConstants.CONTENT_TYPE,
                        metadata.getContentType()
                );

                chunk.getMetadata().put(
                        MetadataConstants.UPLOADED_AT,
                        metadata.getUploadedAt().toString()
                );
            }

            vectorStore.add(chunks);

            log.info("Stored {} chunks for document {}", chunks.size(), metadata.getFileName());
        }
    }

    /**
     * Extracts textual content from the uploaded document
     */
    private static @NonNull List<Document> getDocuments(InputStream inputStream) {
        TikaDocumentReader reader = new TikaDocumentReader(
                new InputStreamResource(inputStream)
        );

        return reader.read();
    }
}