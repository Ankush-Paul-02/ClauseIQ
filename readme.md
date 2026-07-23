Here's your corrected and improved README:

```markdown
# ClauseIQ

ClauseIQ is a multi-document RAG (Retrieval-Augmented Generation) platform that enables users to upload documents and ask natural language questions across all uploaded content. It combines semantic vector search with keyword-based full-text search using Reciprocal Rank Fusion (RRF) to deliver accurate, source-cited answers.

## Features

- 📄 Multi-format document ingestion (PDF, DOCX, images via Apache Tika)
- 🔍 Hybrid Search: Vector similarity (PGVector) + PostgreSQL Full-Text Search
- 🎯 Reciprocal Rank Fusion (RRF) for result ranking
- 🤖 Dual LLM support: Google Gemini & Ollama (local models)
- ⚡ Asynchronous document processing with Spring Events
- 📊 Metadata-aware chunk retrieval with source attribution
- 🛡️ Resilience patterns: Circuit breakers, rate limiting, and fallbacks
- 💾 Caffeine caching for repeated queries
- 📈 Micrometer + Prometheus monitoring and health checks
- 🔄 HNSW vector indexing for fast similarity search

## Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.1
- **AI Framework:** Spring AI 2.0
- **Database:** PostgreSQL 17 with PGVector extension
- **LLM Providers:** Google Gemini 2.5 Flash, Ollama (Gemma 4)
- **Embeddings:** Ollama (mxbai-embed-large), Google (text-embedding-004)
- **Document Processing:** Apache Tika 3.3
- **Caching:** Caffeine Cache
- **Resilience:** Resilience4j (Circuit Breakers, Rate Limiters, Time Limiters)
- **Monitoring:** Micrometer, Prometheus, Spring Boot Actuator
- **Build Tool:** Maven

## Architecture

```text
                          ┌─────────────────┐
                          │   PDF / DOCX    │
                          │     Upload      │
                          └────────┬────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  Apache Tika    │
                          │  Text Extraction│
                          └────────┬────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  Token Text     │
                          │  Splitter       │
                          │  (800 char)     │
                          └────────┬────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  Embedding      │
                          │  Generation     │
                          │  (1024 dims)    │
                          └────────┬────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  PGVector       │
                          │  Storage        │
                          └────────┬────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              │
          ┌──────────────┐ ┌──────────────┐      │
          │   Vector     │ │   Keyword    │      │
          │   Search     │ │   Search     │      │
          │  (Semantic)  │ │ (PostgreSQL  │      │
          │              │ │  Full-Text)  │      │
          └──────┬───────┘ └──────┬───────┘      │
                 │                │              │
                 └────────┬───────┘              │
                          │                      │
                          ▼                      │
                 ┌─────────────────┐             │
                 │  RRF Fusion     │◄────────────┘
                 │  & Ranking      │
                 └────────┬────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │  Relevance      │
                 │  Verification   │
                 └────────┬────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │  LLM Answer     │
                 │  Generation     │
                 │ (Gemini/Ollama) │
                 └────────┬────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │  Answer +       │
                 │  Sources        │
                 └─────────────────┘
```

## Prerequisites

- Java 21
- Docker & Docker Compose
- Maven 3.9+
- PostgreSQL 17 with PGVector
- Ollama (for local LLM) or Google AI API key (for Gemini)

## Running Locally

### 1. Clone the Repository

```bash
git clone https://github.com/Ankush-Paul-02/ClauseIQ.git
cd clauseiq
```

### 2. Start PostgreSQL with PGVector

```bash
docker run --name clauseiq-postgres \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=root \
  -e POSTGRES_DB=clauseiq \
  -p 5432:5432 \
  -d pgvector/pgvector:pg17
```

### 3. Start Ollama (Optional - for local LLM)

```bash
ollama serve
```

Pull required models:

```bash
ollama pull gemma4:e4b
ollama pull mxbai-embed-large
```

### 4. Configure Environment Variables

Create a `.env` file or set environment variables:

```bash
export GEMINI_API_KEY=your_gemini_api_key_here
```

### 5. Configure Application

Edit `src/main/resources/application.yml` to switch between LLM providers:

```yaml
app:
  ai:
    chat-provider: gemini    # or 'ollama' for local LLM
    keyword-provider: gemini # or 'ollama' for local LLM
```

### 6. Run the Application

```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8081`

### 7. Using Docker Compose (All-in-One)

```bash
docker-compose up -d
```

## API Endpoints

### Health Check

```http
GET /actuator/health
```

### Upload Documents

```http
POST /api/v1/documents/upload
Content-Type: multipart/form-data

files: [select multiple PDF/DOCX files]
```

**Response:** `202 Accepted`
```json
[
  {
    "documentId": "f593f31c-ba3c-4147-b9ab-cceffd3d6fdf"
  }
]
```

### Check Document Processing Status

```http
GET /api/v1/documents/{documentId}/status
```

**Response:**
```json
{
  "status": "COMPLETED"
}
```

**Status values:** `UPLOADED` → `PROCESSING` → `COMPLETED` | `FAILED`

### Ask Questions

```http
POST /api/v1/chat
Content-Type: application/x-www-form-urlencoded

question=What skills does Ankush have?
```

**Response:**
```json
{
  "answer": "Based on the documents, Ankush Paul is a Specialist in the Risk Consulting SBU...",
  "sources": [
    {
      "documentId": "f593f31c-ba3c-4147-b9ab-cceffd3d6fdf",
      "fileName": "PwC Offer Letter.pdf",
      "chunkIndex": 1
    },
    {
      "documentId": "786ed844-b8ce-4ec2-a612-e1f90873fff4",
      "fileName": "101700326_JUN_2026.pdf",
      "chunkIndex": 1
    }
  ]
}
```

### Debug Search (Development Only)

```http
GET /api/v1/debug/vector-search?query=Ankush+skills&threshold=0.3
GET /api/v1/debug/hybrid-search?query=Ankush+skills&topK=5
```

## Configuration

### Key Configuration Properties

```yaml
# Search Configuration
hybrid:
  search:
    vector-top-k: 5           # Number of vector search results
    keyword-top-k: 10         # Number of keyword search results
    vector-similarity-threshold: 0.3  # Minimum similarity score
    max-chunks-per-document: 3 # Chunks to include per document
    rrf-k: 60                 # RRF ranking constant

# LLM Configuration
app:
  ai:
    chat-provider: gemini     # LLM provider for answers
    keyword-provider: gemini  # LLM provider for keyword extraction

# Chat Service
chat:
  service:
    max-documents: 5          # Max documents to search
    max-question-length: 200  # Max question length
```

## Monitoring

### Available Metrics

- `chat.questions.total` - Total questions asked
- `chat.no.results` - Questions with no results
- `chat.errors` - Error count
- `hybrid.search.vector.count` - Vector search results count
- `hybrid.search.keyword.count` - Keyword search results count
- `hybrid.search.results.count` - Final results after RRF
- `hybrid.search.time` - Search duration

### Prometheus Endpoint

```http
GET /actuator/prometheus
```

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=HybridSearchServiceTest

# Run with coverage
./mvnw verify
```

## Future Enhancements

- [ ] Cross-encoder reranking for improved result ordering
- [ ] Azure AI Search integration as alternative vector store
- [ ] S3/Azure Blob Storage for document persistence
- [ ] Streaming (SSE) responses for real-time answer generation
- [ ] Multi-tenant support with user authentication
- [ ] Support for more document formats (images with OCR, audio transcripts)
- [ ] Chat history and conversation context
- [ ] Document comparison and summarization features
- [ ] Web UI with drag-and-drop upload
- [ ] Kubernetes deployment configuration

## Project Structure

```
src/main/java/com/paul/clauseiq/
├── configuration/     # App configuration classes
├── controller/        # REST controllers
├── service/           # Business logic services
├── data/entity/       # JPA entities
├── dto/               # Data Transfer Objects
├── event/             # Spring Application Events
├── repository/        # Data repositories
├── strategy/          # AI provider strategies
├── factory/           # Strategy factories
├── constants/         # Constants
├── exceptions/        # Custom exceptions
└── validation/        # Input validators
```

## Author

**Ankush Paul**

---