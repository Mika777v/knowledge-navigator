package com.example.knavi.insight;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 検索ログの記録（ナビゲーション検索から呼ぶ公開 API）。ユーザー ID は保存せず、HMAC で仮名化した値だけを残す */
public interface SearchLogRecorder {

    /**
     * 検索を記録する。記録に失敗しても検索自体は続けられるよう、例外は投げずに空を返す。
     *
     * @return 検索ログの ID（クリック・フィードバックの記録に使う）
     */
    Optional<UUID> record(SearchLogEntry entry);

    /**
     * @param userId 検索したユーザー（仮名化して保存する）
     * @param departmentId 所属部署（部署ごとの集計用。未所属なら null）
     * @param query 入力そのまま
     * @param normalizedQuery 口語の言い回しなどを除いたもの（集計用）
     * @param resultCounts ブロックごとの件数（例: programs=2）
     * @param topHits 上位の結果（種類・コード・スコア）
     * @param latencyMs 処理時間
     */
    record SearchLogEntry(
            UUID userId,
            UUID departmentId,
            String query,
            String normalizedQuery,
            Map<String, Integer> resultCounts,
            List<TopHit> topHits,
            long latencyMs) {}

    record TopHit(String type, String code, double score) {}
}
