package com.example.knavi.intelligence.domain;

import com.example.knavi.intelligence.domain.EmbeddingSource.SourceType;
import java.util.UUID;

/** embeddings の1行を指す（元の種類・元の ID・区切りの番号。モデルは別に持つ） */
public record ChunkKey(SourceType type, UUID id, int chunkNo) {}
