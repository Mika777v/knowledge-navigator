package com.example.knavi.content.domain;

/**
 * 本文を分割した1セクション（保存前）。
 *
 * @param ordinal 文書内の順番（0 から）
 * @param path 章・条の位置（例: {@code 2/5} = 第2章 第5条。章がなければ {@code 5}）
 * @param articleNo 条番号（例: 第5条）。条でない見出しは null
 * @param heading 見出し（例: 結婚祝金）
 * @param content 見出し行を除いた本文（Markdown）
 * @param pageFrom 原本（PDF）でのページの始まり。原本が無い版は null
 * @param pageTo 原本（PDF）でのページの終わり。原本が無い版は null
 */
public record SectionDraft(
        int ordinal, String path, String articleNo, String heading, String content, Integer pageFrom, Integer pageTo) {

    /** 原本の無い（Markdown で書いた）版のセクション */
    public SectionDraft(int ordinal, String path, String articleNo, String heading, String content) {
        this(ordinal, path, articleNo, heading, content, null, null);
    }

    public SectionDraft withPages(int from, int to) {
        return new SectionDraft(ordinal, path, articleNo, heading, content, from, to);
    }
}
