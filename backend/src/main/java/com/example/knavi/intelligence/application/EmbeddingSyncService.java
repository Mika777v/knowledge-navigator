package com.example.knavi.intelligence.application;

import com.example.knavi.intelligence.domain.ChunkKey;
import com.example.knavi.intelligence.domain.EmbeddingSource;
import com.example.knavi.intelligence.domain.TextChunker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「今あるべきベクトル」と embeddings 表を見比べ、足りない・変わったものだけ作り、要らない行は消す（ADR-0012）。
 *
 * <p>あるべきもの: 公開中の最新の版の条、公開中の FAQ・制度のうち、CONFIDENTIAL でなく、ai-service のプロバイダに送ってよい公開範囲に入るもの（ADR-0008）。
 * 1回の合わせは1つのトランザクションで行い、途中で失敗したら何も変えずに次の回でやり直す。
 */
@Service
public class EmbeddingSyncService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSyncService.class);

    /** DB の embeddings.embedding の次元（ADR-0007） */
    static final int DIMS = 1024;
    /** 社外に送らないプロバイダに送ってよい公開範囲（CONFIDENTIAL はどこにも送らない） */
    static final Set<String> LOCAL_VISIBILITIES = Set.of("INTERNAL", "MANAGERS", "RESTRICTED");

    private final AiClient ai;
    private final EmbeddingStore store;
    private final AiProperties aiProperties;
    private final EmbeddingProperties properties;

    EmbeddingSyncService(AiClient ai, EmbeddingStore store, AiProperties aiProperties, EmbeddingProperties properties) {
        this.ai = ai;
        this.store = store;
        this.aiProperties = aiProperties;
        this.properties = properties;
    }

    /** 合わせた結果（作った・消した数）。ほかの台が合わせている間は skipped */
    public record SyncResult(int embedded, int deleted, boolean skipped) {}

    @Transactional
    public SyncResult syncOnce() {
        if (!store.tryLock()) {
            return new SyncResult(0, 0, true);
        }
        AiServiceInfo info = ai.info();
        if (info.dims() != DIMS) {
            // 次元の違うモデルは保存できない（マイグレーションで列を足す必要がある。ADR-0007）
            log.warn("ai-service の次元 {} が {} と違うため、ベクトルを作りません（モデル {}）", info.dims(), DIMS, info.model());
            return new SyncResult(0, 0, true);
        }
        String model = info.model();
        int deleted = store.deleteOtherModels(model);

        Map<ChunkKey, String> wanted = new LinkedHashMap<>();
        Map<ChunkKey, String> texts = new LinkedHashMap<>();
        for (EmbeddingSource source : store.sources(visibilities(info))) {
            List<String> chunks = TextChunker.chunks(source.header(), source.body());
            for (int i = 0; i < chunks.size(); i++) {
                ChunkKey key = new ChunkKey(source.type(), source.id(), i);
                wanted.put(key, sha256(chunks.get(i)));
                texts.put(key, chunks.get(i));
            }
        }

        Map<ChunkKey, String> existing = store.hashes(model);
        for (ChunkKey key : existing.keySet()) {
            if (!wanted.containsKey(key)) {
                // 元がない・公開をやめた・送ってよい範囲から外れた
                store.delete(key, model);
                deleted++;
            }
        }

        List<ChunkKey> changed = new ArrayList<>();
        wanted.forEach((key, hash) -> {
            if (!hash.equals(existing.get(key))) {
                changed.add(key);
            }
        });
        int batchSize = Math.max(1, properties.batchSize());
        for (int from = 0; from < changed.size(); from += batchSize) {
            List<ChunkKey> batch = changed.subList(from, Math.min(from + batchSize, changed.size()));
            List<float[]> vectors = ai.embed(batch.stream().map(texts::get).toList());
            for (int i = 0; i < batch.size(); i++) {
                store.upsert(batch.get(i), model, wanted.get(batch.get(i)), vectors.get(i));
            }
        }
        if (!changed.isEmpty() || deleted > 0) {
            log.info("ベクトルを合わせました: 作成・更新 {} 件、削除 {} 件（モデル {}）", changed.size(), deleted, model);
        }
        return new SyncResult(changed.size(), deleted, false);
    }

    /** プロバイダに送ってよい公開範囲（ADR-0008） */
    Set<String> visibilities(AiServiceInfo info) {
        if (!info.external()) {
            return LOCAL_VISIBILITIES;
        }
        List<String> configured =
                aiProperties.externalVisibilities() == null ? List.of() : aiProperties.externalVisibilities();
        return Set.copyOf(
                configured.stream().filter(LOCAL_VISIBILITIES::contains).toList());
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
