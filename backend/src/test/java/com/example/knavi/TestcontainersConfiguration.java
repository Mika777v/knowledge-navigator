package com.example.knavi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト用の PostgreSQL。本番と同じ拡張（pg_bigm / pgvector）を持つ infra/docker/postgres のイメージを使う。
 * イメージ名はシステムプロパティ {@code knavi.test.postgres-image}（build.gradle.kts で設定）で変更できる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String IMAGE_PROPERTY = "knavi.test.postgres-image";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        DockerImageName image = DockerImageName.parse(
                        System.getProperty(IMAGE_PROPERTY, "knowledge-navigator/postgres:local"))
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(image);
    }
}
