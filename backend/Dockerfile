# backend（Spring Boot）の実行イメージ
# ビルド: docker build -t knowledge-navigator/backend backend

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
# 依存解決のレイヤーをソース変更から分離してキャッシュを効かせる
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home knavi
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER knavi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
