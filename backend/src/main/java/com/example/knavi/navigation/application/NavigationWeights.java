package com.example.knavi.navigation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 検索スコアの重み（docs/ARCHITECTURE.md §4.3）。評価用質問セット（data/eval）で比べて調整する。
 *
 * @param dictionary 辞書の一致（制度・きっかけへの直接の案内）。キーワードごとの weight を掛ける
 * @param programName 制度名に語が含まれる
 * @param faqQuestion FAQ の質問文に語が含まれる
 * @param heading 条の見出し・文書名に語が含まれる
 * @param body 本文（説明・回答・条文）に語が含まれる
 * @param basis 上位の制度の根拠になっている文書・条（制度のスコアに掛ける）
 * @param linkedFaq 上位の制度に紐づく FAQ（制度のスコアに掛ける）
 * @param minRelativeScore 1位のスコアに対してこの割合より低い制度は出さない（関係の薄いものを並べない）
 * @param keywordStem 辞書の語が語幹（活用語尾・助詞以下を除いたもの）でだけ一致したときに掛ける重み
 */
@ConfigurationProperties("knavi.navigation.weights")
public record NavigationWeights(
        @DefaultValue("3.0") double dictionary,
        @DefaultValue("2.5") double programName,
        @DefaultValue("2.0") double faqQuestion,
        @DefaultValue("1.5") double heading,
        @DefaultValue("1.0") double body,
        @DefaultValue("0.5") double basis,
        @DefaultValue("0.3") double linkedFaq,
        @DefaultValue("0.2") double minRelativeScore,
        @DefaultValue("0.7") double keywordStem) {}
