package com.example.knavi.organization.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ログイン試行の制限（パスワードの総当たり対策）。同じメールアドレスで一定時間内に続けて失敗したら、しばらくログインさせない。
 *
 * <ul>
 *   <li>アカウントが存在しないメールアドレスも同じように数える（応答の違いからアカウントの有無が分からないように）
 *   <li>送信元 IP では数えない（利用者のリクエストはすべて BFF を経由するため、IP では区別できない）
 *   <li>状態はこのプロセスのメモリに持つ（MVP は backend 1台。複数台にするときは DB などで共有する）
 * </ul>
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);
    /** 覚えておくアカウント数の目安。超えたら期限切れの記録を捨てる（大量のメールアドレスでメモリを使い切られないように） */
    private static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockDuration;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginThrottle(
            Clock clock,
            @Value("${knavi.auth.login.max-failures:5}") int maxFailures,
            @Value("${knavi.auth.login.window:15m}") Duration window,
            @Value("${knavi.auth.login.lock-duration:15m}") Duration lockDuration) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockDuration = lockDuration;
    }

    /** ロック中なら、解除までの残り時間を返す */
    public Optional<Duration> lockedFor(String email) {
        Attempts a = attempts.get(key(email));
        if (a == null) {
            return Optional.empty();
        }
        synchronized (a) {
            Instant now = clock.instant();
            return a.lockedUntil != null && now.isBefore(a.lockedUntil)
                    ? Optional.of(Duration.between(now, a.lockedUntil))
                    : Optional.empty();
        }
    }

    public void recordFailure(String email) {
        if (attempts.size() > PURGE_THRESHOLD) {
            purgeExpired();
        }
        Attempts a = attempts.computeIfAbsent(key(email), k -> new Attempts());
        synchronized (a) {
            Instant now = clock.instant();
            a.failures.addLast(now);
            while (!a.failures.isEmpty() && a.failures.peekFirst().isBefore(now.minus(window))) {
                a.failures.removeFirst();
            }
            if (a.failures.size() >= maxFailures) {
                a.lockedUntil = now.plus(lockDuration);
                a.failures.clear();
                // メールアドレスはログに残さない（ログからアカウントを推測されないように）
                log.warn("ログインに{}回続けて失敗したため、{}分間ロックしました", maxFailures, lockDuration.toMinutes());
            }
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Attempts a = e.getValue();
                boolean locked = a.lockedUntil != null && now.isBefore(a.lockedUntil);
                boolean recent = !a.failures.isEmpty() && a.failures.peekLast().isAfter(now.minus(window));
                return !locked && !recent;
            }
        });
    }

    private static String key(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        final Deque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;
    }
}
