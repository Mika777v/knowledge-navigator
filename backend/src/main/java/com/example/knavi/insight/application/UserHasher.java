package com.example.knavi.insight.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 検索ログに残すユーザーの仮名（HMAC-SHA256(user_id)）。
 *
 * <p>鍵がなければ仮名から本人を割り出せないため、ログだけが漏れても個人の検索履歴は分からない。
 * 同じ人の検索は同じ値になるので、自分の検索へのクリック・フィードバックかどうかの確認と、利用者数の集計に使える。
 */
@Component
class UserHasher {

    static final String LOCAL_DEFAULT_SECRET = "local-dev-only-change-me";
    private static final Logger log = LoggerFactory.getLogger(UserHasher.class);

    private final SecretKeySpec key;

    UserHasher(@Value("${knavi.insight.user-hash-secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("knavi.insight.user-hash-secret（KNAVI_USER_HASH_SECRET）を設定してください。");
        }
        if (LOCAL_DEFAULT_SECRET.equals(secret)) {
            log.warn("検索ログの仮名化にローカル用の既定の鍵を使っています。本番では KNAVI_USER_HASH_SECRET を設定してください。");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    String hash(UUID userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 を利用できません。", e);
        }
    }
}
