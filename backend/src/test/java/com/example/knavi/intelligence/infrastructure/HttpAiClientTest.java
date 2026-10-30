package com.example.knavi.intelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.knavi.intelligence.application.AiChatInfo;
import com.example.knavi.intelligence.application.AiClient;
import com.example.knavi.intelligence.application.AiClient.AiServiceException;
import com.example.knavi.intelligence.application.AiClient.RagAnswer;
import com.example.knavi.intelligence.application.AiClient.RagPassage;
import com.example.knavi.intelligence.application.AiProperties;
import com.example.knavi.intelligence.application.AiServiceInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ai-service の代わりの小さな HTTP サーバーで、要求の形と応答の読み取りを確かめる */
class HttpAiClientTest {

    HttpServer server;
    final Map<String, String> seen = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/info", exchange -> {
            seen.put("auth", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, 200, """
                    {"service":"knavi-ai-service","version":"0.1.0",
                     "embedding":{"provider":"ollama","model":"bge-m3","dims":1024,"external":false},
                     "chat":{"provider":"ollama","model":"qwen3.5:4b","external":false}}
                    """);
        });
        server.createContext("/old/v1/info", exchange -> respond(exchange, 200, """
                {"service":"knavi-ai-service","version":"0.1.0",
                 "embedding":{"provider":"ollama","model":"bge-m3","dims":1024,"external":false}}
                """));
        server.createContext("/v1/rag/answer", exchange -> {
            seen.put("answer", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"answerable":true,"reason":"ANSWERED","answer":"30,000円です。[1]","citations":[1],
                     "model":"qwen3.5:4b"}
                    """);
        });
        server.createContext("/v1/assistant/extract", exchange -> {
            seen.put("extract", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"answers":{"EMPLOYMENT_TYPE":"CONTRACT"},"model":"qwen3.5:4b"}
                    """);
        });
        server.createContext("/v1/embeddings", exchange -> {
            seen.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"model":"bge-m3","dims":2,"embeddings":[[0.1,0.2],[0.3,0.4]]}
                    """);
        });
        server.createContext("/fail/v1/info", exchange -> respond(exchange, 401, "{\"detail\":\"invalid token\"}"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    HttpAiClient client(String path) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + path;
        Duration timeout = Duration.ofSeconds(5);
        return new HttpAiClient(
                new AiProperties(url, "secret-token", timeout, timeout, timeout, timeout, List.of("INTERNAL")));
    }

    @Test
    void readsTheChatProvider() {
        assertThat(client("").chatInfo()).isEqualTo(new AiChatInfo("ollama", "qwen3.5:4b", false));
    }

    @Test
    void anOldServiceWithoutChatIsAnError() {
        assertThatThrownBy(() -> client("/old").chatInfo()).isInstanceOf(AiServiceException.class);
    }

    @Test
    void postsTheQuestionAndPassagesAndReadsTheAnswer() {
        RagAnswer answer = client("").answer("結婚祝金はいくら？", List.of(new RagPassage(1, "規程 第5条", "30,000円を支給する。")));

        assertThat(answer).isEqualTo(new RagAnswer(true, "ANSWERED", "30,000円です。[1]", List.of(1), "qwen3.5:4b"));
        assertThat(seen.get("answer"))
                .contains("\"question\":\"結婚祝金はいくら？\"")
                .contains("\"ref\":1")
                .contains("\"title\":\"規程 第5条\"");
    }

    @Test
    void postsTheTextAndQuestionsAndReadsTheExtractedAnswers() {
        var answers = client("")
                .extract(
                        "契約社員です",
                        List.of(new AiClient.ExtractQuestion(
                                "EMPLOYMENT_TYPE",
                                "雇用形態を教えてください。",
                                List.of(
                                        new AiClient.ExtractChoice("REGULAR", "正社員"),
                                        new AiClient.ExtractChoice("CONTRACT", "契約社員")))));

        assertThat(answers).containsExactly(Map.entry("EMPLOYMENT_TYPE", "CONTRACT"));
        assertThat(seen.get("extract"))
                .contains("\"text\":\"契約社員です\"")
                .contains("\"code\":\"EMPLOYMENT_TYPE\"")
                .contains("\"label\":\"契約社員\"");
    }

    @Test
    void sendsTheSharedTokenAndReadsInfo() {
        assertThat(client("").info()).isEqualTo(new AiServiceInfo("ollama", "bge-m3", 1024, false));
        assertThat(seen.get("auth")).isEqualTo("Bearer secret-token");
    }

    @Test
    void sendsTextsAndReadsVectorsInOrder() {
        var vectors = client("").embed(List.of("育児休業", "結婚祝金"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);
        assertThat(seen.get("body")).contains("\"texts\"").contains("育児休業");
    }

    @Test
    void embedQueryReturnsTheVectorOfTheQuestion() {
        server.removeContext("/v1/embeddings");
        server.createContext("/v1/embeddings", exchange -> respond(exchange, 200, """
                {"model":"bge-m3","dims":2,"embeddings":[[0.5,0.6]]}
                """));

        assertThat(client("").embedQuery("結婚したら")).containsExactly(0.5f, 0.6f);
    }

    @Test
    void wrongCountIsAnError() {
        assertThatThrownBy(() -> client("").embed(List.of("1つだけ"))).isInstanceOf(AiServiceException.class);
    }

    @Test
    void httpErrorsBecomeAiServiceException() {
        assertThatThrownBy(() -> client("/fail").info()).isInstanceOf(AiServiceException.class);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
