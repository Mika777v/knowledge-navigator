package com.example.knavi.intelligence.infrastructure;

import com.example.knavi.intelligence.application.AiClient;
import com.example.knavi.intelligence.application.AiProperties;
import com.example.knavi.intelligence.application.AiServiceInfo;
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

    HttpAiClient(AiProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
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
