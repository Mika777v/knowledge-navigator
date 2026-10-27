package com.example.knavi.navigation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 意味検索（ベクトル検索）の設定（ADR-0007）。
 *
 * @param enabled false ならキーワード検索だけ（今までと同じ結果）
 * @param limit ブロック（制度・条・FAQ）ごとに取り出すベクトル検索の上位の数
 * @param minSimilarity これより似ていない（コサイン類似度が低い）ものは出さない。どんな質問にも何か出てしまい、
 *     「見つからない → 総合窓口」の案内が出なくなるのを防ぐ。data/eval の評価で決める
 */
@ConfigurationProperties("knavi.search.vector")
public record VectorSearchProperties(boolean enabled, int limit, double minSimilarity) {}
