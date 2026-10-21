package com.example.knavi.shared.security;

import java.util.Map;

/**
 * WHERE 句に埋め込む条件と、そのバインド変数。
 *
 * @param sql 条件式（括弧で囲んである）
 * @param params 名前付きパラメーター（JdbcClient の {@code params(Map)} に渡す）
 */
public record SqlPredicate(String sql, Map<String, Object> params) {}
