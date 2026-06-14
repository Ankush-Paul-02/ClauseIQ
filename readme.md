Current architecture:

Question
↓
Embedding
↓
PGVector similarity search
↓
Top K chunks
↓
LLM

New architecture:

Question
┌──────────────┐
│              │
▼              ▼
Keyword Search   Vector Search
│              │
└──────┬───────┘
        ▼
        Rank Fusion
        ▼
        Top Chunks
        ▼
        LLM