package com.example.knavi.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class DemoModeTest {

    @Test
    void デモ環境でなければ何もしない() {
        assertThatCode(() -> new DemoMode(false).rejectIfEnabled("パスワードを変更")).doesNotThrowAnyException();
    }

    @Test
    void デモ環境なら403で断る() {
        assertThatThrownBy(() -> new DemoMode(true).rejectIfEnabled("パスワードを変更"))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getBody().getDetail()).isEqualTo("デモ環境ではパスワードを変更できません（データは毎日初期化されます）。");
                });
    }
}
