package com.example.knavi.navigation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class RankFusionTest {

    @Test
    void itemsInBothRankingsComeFirst() {
        var fused = RankFusion.fuse(List.of(List.of("a", "b", "c"), List.of("c", "d")));

        assertThat(fused.get("c")).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fused.get("a")).isCloseTo(1.0 / 61, within(1e-12));
        assertThat(fused.get("c")).isGreaterThan(fused.get("a"));
        assertThat(fused.get("d")).isCloseTo(1.0 / 62, within(1e-12));
    }

    @Test
    void keepsTheOrderOfFirstAppearance() {
        assertThat(RankFusion.fuse(List.of(List.of("a", "b"), List.of("c"))).keySet())
                .containsExactly("a", "b", "c");
    }

    @Test
    void emptyRankingsGiveNothing() {
        assertThat(RankFusion.fuse(List.of(List.of(), List.of()))).isEmpty();
    }
}
