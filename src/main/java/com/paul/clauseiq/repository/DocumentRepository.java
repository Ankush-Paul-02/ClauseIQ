package com.paul.clauseiq.repository;

import com.paul.clauseiq.data.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentMetadata, UUID> {
}