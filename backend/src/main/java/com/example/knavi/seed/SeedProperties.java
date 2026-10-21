package com.example.knavi.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled true のとき起動時に投入する（make seed から指定）
 * @param path data/seed ディレクトリ
 * @param demoUserPassword デモユーザー全員に設定するパスワード
 */
@ConfigurationProperties("knavi.seed")
record SeedProperties(boolean enabled, String path, String demoUserPassword) {}
