package com.example.knavi.content.domain;

/**
 * 本文を分割した1セクション（保存前）。
 *
 * @param ordinal 文書内の順番（0 から）
 * @param path 章・条の位置（例: {@code 2/5} = 第2章 第5条。章がなければ {@code 5}）
 * @param articleNo 条番号（例: 第5条）。条でない見出しは null
 * @param heading 見出し（例: 結婚祝金）
 * @param content 見出し行を除いた本文（Markdown）
 */
public record SectionDraft(int ordinal, String path, String articleNo, String heading, String content) {}
