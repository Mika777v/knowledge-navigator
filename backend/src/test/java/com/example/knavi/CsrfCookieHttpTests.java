package com.example.knavi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * CSRF Cookie の発行を、実際の HTTP サーバー経由で確認する。
 *
 * <p>MockMvc の csrf() は CSRF トークンの保存先をテスト用に差し替えるため、同じコンテキストで先に使われると
 * Cookie が書かれなくなる。テストの実行順に左右されないよう、このテストは本物のフィルターを通す。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CsrfCookieHttpTests {

    @LocalServerPort
    int port;

    @Test
    void csrfEndpointIssuesReadableXsrfCookie() {
        var response = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/auth/csrf")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("X-XSRF-TOKEN");
        var cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .anySatisfy(c -> assertThat(c)
                        .startsWith("XSRF-TOKEN=")
                        // SPA が JavaScript で読めるよう HttpOnly ではない
                        .doesNotContainIgnoringCase("HttpOnly"));
    }
}
