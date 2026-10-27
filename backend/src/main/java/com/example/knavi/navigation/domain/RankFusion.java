package com.example.knavi.navigation.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）。点数の尺度が違う複数の順位（キーワード検索・ベクトル検索）を、順位だけで1つにまとめる（ADR-0007）。
 * score = Σ 1 / (k + 順位)。順位は 1 から数える。
 */
public final class RankFusion {

    /** 定数 k（ADR-0007。大きいほど上位と下位の差が小さくなる） */
    public static final int K = 60;

    private RankFusion() {}

    /** それぞれの順位（上位から並べた ID）を合わせた点数。どこかの順位に出た ID だけを、最初に出た順で返す */
    public static <T> Map<T, Double> fuse(List<List<T>> rankings) {
        Map<T, Double> scores = new LinkedHashMap<>();
        for (List<T> ranking : rankings) {
            for (int i = 0; i < ranking.size(); i++) {
                scores.merge(ranking.get(i), 1.0 / (K + i + 1), Double::sum);
            }
        }
        return scores;
    }
}
