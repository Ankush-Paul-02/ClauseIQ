# ClauseIQ

ClauseIQ is a multi-document RAG (Retrieval-Augmented Generation) platform that enables users to upload PDF documents and ask natural language questions across all uploaded content.

## Features

- Multi-document PDF ingestion
- Semantic search using PGVector
- Hybrid Search (Vector Search + PostgreSQL Full-Text Search)
- Reciprocal Rank Fusion (RRF) ranking
- Source-cited responses
- Asynchronous document processing
- Metadata-aware retrieval
- HNSW vector indexing
- Local LLM support via Ollama
- Caching, rate limiting, and circuit breakers
- Health checks and monitoring

## Tech Stack

- Java 21
- Spring Boot
- Spring AI
- PostgreSQL
- PGVector
- Ollama (Gemma 4)
- Apache Tika
- Caffeine Cache
- Resilience4j
- Micrometer

## Architecture

```text
PDF Upload
    ↓
Apache Tika
    ↓
Chunking
    ↓
Embeddings
    ↓
PGVector
    ↓
Hybrid Search
(Vector + FTS + RRF)
    ↓
Ollama
    ↓
Answer + Sources
```

## Running Locally

### Start PostgreSQL

```bash
docker run --name clauseiq-postgres \
-e POSTGRES_USER=root \
-e POSTGRES_PASSWORD=root \
-e POSTGRES_DB=clauseiq \
-p 5432:5432 \
-d pgvector/pgvector:pg17
```

### Start Ollama

```bash
ollama serve
```

Pull required models:

```bash
ollama pull gemma4:e4b
ollama pull mxbai-embed-large
```

### Run Application

```bash
./mvnw spring-boot:run
```

## API Endpoints

### Upload Document

```http
POST /api/v1/documents
```

### Ask Questions

```http
POST /api/v1/chat
```

Request:

```json
{
  "question": "Who knows Kafka?"
}
```

Response:

```json
{
  "answer": "Ankush Paul mentions Kafka...",
  "sources": [
    {
      "fileName": "Ankush_Paul_Resume.pdf",
      "chunkIndex": 1
    }
  ]
}
```

## Future Planning

- Cross-encoder reranking
- Azure AI Search integration
- S3/Azure Blob Storage
- Streaming responses
- Multi-tenant support

## Author

Ankush Paul