package com.example.knavi.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    /** テストの中で進められる時計 */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock, 3, Duration.ofMinutes(15), Duration.ofMinutes(10));

    @Test
    void locksAfterConsecutiveFailuresAndUnlocksLater() {
        throttle.recordFailure("sato@example.com");
        throttle.recordFailure("sato@example.com");
        assertThat(throttle.lockedFor("sato@example.com")).isEmpty();

        throttle.recordFailure("SATO@example.com ");
        assertThat(throttle.lockedFor("sato@example.com")).contains(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(4));
        assertThat(throttle.lockedFor("sato@example.com")).contains(Duration.ofMinutes(6));
        clock.advance(Duration.ofMinutes(6));
        assertThat(throttle.lockedFor("sato@example.com")).isEmpty();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        throttle.recordFailure("sato@example.com");
        throttle.recordFailure("sato@example.com");
        clock.advance(Duration.ofMinutes(16));
        throttle.recordFailure("sato@example.com");
        assertThat(throttle.lockedFor("sato@example.com")).isEmpty();
    }

    @Test
    void successResetsTheCountAndOtherAccountsAreIndependent() {
        throttle.recordFailure("sato@example.com");
        throttle.recordFailure("sato@example.com");
        throttle.recordSuccess("sato@example.com");
        throttle.recordFailure("sato@example.com");
        assertThat(throttle.lockedFor("sato@example.com")).isEmpty();

        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("tanaka@example.com");
        }
        assertThat(throttle.lockedFor("tanaka@example.com")).isPresent();
        assertThat(throttle.lockedFor("sato@example.com")).isEmpty();
    }
}
