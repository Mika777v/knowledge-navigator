package com.example.knavi.intelligence.infrastructure;

import com.example.knavi.intelligence.application.AiClient;
import com.example.knavi.intelligence.application.AiProperties;
import com.example.knavi.intelligence.application.AiServiceInfo;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** ai-service を HTTP + JSON で呼ぶ。共有のトークンを Authorization ヘッダーで送る（ADR-0006） */
@Component
class HttpAiClient implements AiClient {

    private final RestClient client;
    /** 検索の質問用（待つ時間を短くする） */
    private final RestClient queryClient;

    HttpAiClient(AiProperties properties) {
        this.client = client(properties, properties.readTimeout());
        this.queryClient = client(properties, properties.queryTimeout());
    }

    private static RestClient client(AiProperties properties, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    @Override
    public AiServiceInfo info() {
        try {
            InfoResponse response = client.get().uri("/v1/info").retrieve().body(InfoResponse.class);
            if (response == null || response.embedding() == null) {
                throw new AiServiceException("ai-service の /v1/info の応答が空です", null);
            }
            return response.embedding();
        } catch (RestClientException e) {
            throw new AiServiceException("ai-service の /v1/info に失敗しました: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return embed(client, texts);
    }

    @Override
    public float[] embedQuery(String question) {
        return embed(queryClient, List.of(question)).getFirst();
    }

    private static List<float[]> embed(RestClient client, List<String> texts) {
        try {
            EmbeddingResponse response = client.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(texts))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (response == null
                    || response.embeddings() == null
                    || response.embeddings().size() != texts.size()) {
                throw new AiServiceException("ai-service の /v1/embeddings の応答の数が違います", null);
            }
            return response.embeddings();
        } catch (RestClientException e) {
            // 文章の中身はメッセージに入れない（社内文書のため）
            throw new AiServiceException("ai-service の /v1/embeddings に失敗しました: " + e.getMessage(), e);
        }
    }

    record InfoResponse(String service, String version, AiServiceInfo embedding) {}

    record EmbeddingRequest(List<String> texts) {}

    record EmbeddingResponse(String model, int dims, List<float[]> embeddings) {}
}
