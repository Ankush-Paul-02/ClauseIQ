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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentRepository documentRepository;


    @PostMapping("/upload")
    public ResponseEntity<List<UploadResponse>> upload(
            @RequestParam MultipartFile[] files
    ) throws IOException {

        List<UUID> documentIds = documentIngestionService.upload(files);

        List<UploadResponse> response = documentIds.stream()
                .map(UploadResponse::new)
                .toList();

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}/status")
    public DocumentStatusResponse status(
            @PathVariable UUID id
    ) {

        DocumentMetadata document = documentRepository.findById(id).orElseThrow();

        return new DocumentStatusResponse(document.getStatus());
    }
}