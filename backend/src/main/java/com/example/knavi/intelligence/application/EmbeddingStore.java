package com.example.knavi.intelligence.application;

import com.example.knavi.intelligence.domain.ChunkKey;
import com.example.knavi.intelligence.domain.EmbeddingSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** ベクトルの元の文章と、embeddings 表の読み書き（ADR-0007・0012） */
public interface EmbeddingStore {

    /** 同時に1台だけが合わせるためのロック（トランザクションの終わりで外れる）。取れなければ false */
    boolean tryLock();

    /** 公開中の条（最新の版）・FAQ・制度のうち、公開範囲が visibilities に入るもの */
    List<EmbeddingSource> sources(Collection<String> visibilities);

    /** そのモデルで保存済みの行と、その content_hash */
    Map<ChunkKey, String> hashes(String model);

    void upsert(ChunkKey key, String model, String contentHash, float[] embedding);

    void delete(ChunkKey key, String model);

    /** 今のモデル以外の行を消し、消した数を返す */
    int deleteOtherModels(String model);
}
