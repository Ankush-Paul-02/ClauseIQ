package com.paul.clauseiq.controller;

import com.paul.clauseiq.data.entity.DocumentMetadata;
import com.paul.clauseiq.dto.DocumentStatusResponse;
import com.paul.clauseiq.dto.UploadResponse;
import com.paul.clauseiq.repository.DocumentRepository;
import com.paul.clauseiq.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentRepository documentRepository;


    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam MultipartFile file
    ) throws IOException {

        UUID documentId = documentIngestionService.upload(file);

        return ResponseEntity.accepted().body(new UploadResponse(documentId));
    }

    @GetMapping("/{id}/status")
    public DocumentStatusResponse status(
            @PathVariable UUID id
    ) {

        DocumentMetadata document = documentRepository.findById(id).orElseThrow();

        return new DocumentStatusResponse(document.getStatus());
    }
}