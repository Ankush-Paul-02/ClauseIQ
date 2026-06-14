package com.paul.clauseiq.data.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "documents")
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fileName;

    private String contentType;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private Instant uploadedAt;

    private Instant processedAt;
}