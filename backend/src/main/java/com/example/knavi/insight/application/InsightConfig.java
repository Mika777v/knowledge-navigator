package com.example.knavi.insight.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定期実行（検索ログの削除）を有効にする。テストやダミーデータの投入では
 * {@code knavi.scheduling.enabled=false} にして止められる。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "knavi.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class InsightConfig {}
