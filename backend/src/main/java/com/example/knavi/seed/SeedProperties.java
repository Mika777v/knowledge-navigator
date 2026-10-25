package com.example.knavi.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled true のとき起動時に投入する（make seed から指定）
 * @param path data/seed ディレクトリ
 * @param demoUserPassword デモユーザー全員に設定するパスワード
 * @param pdfPath data/pdf ディレクトリ。指定すると、規程の公開中の版に同じ内容の原本（PDF）とページを結び付ける
 *     （原本の置き場所が要るため make seed だけで指定する）
 */
@ConfigurationProperties("knavi.seed")
record SeedProperties(boolean enabled, String path, String demoUserPassword, String pdfPath) {}
