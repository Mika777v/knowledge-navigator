package com.example.knavi.navigation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.navigation.infrastructure.NavigationQueries.KeywordMatch;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NavigationServiceTest {

    @Test
    void keepsOnlyTheLongestOverlappingKeywords() {
        UUID id = UUID.randomUUID();
        var marriage = new KeywordMatch("結婚", "LIFE_EVENT", id, 1.5);
        var ceremony = new KeywordMatch("結婚式", "LIFE_EVENT", id, 1.5);
        var leave = new KeywordMatch("休み", "PROGRAM", id, 1.0);

        assertThat(NavigationService.longestMatches(List.of(marriage, ceremony, leave)))
                .containsExactly(ceremony, leave);
    }

    @Test
    void snippetIsCutAroundTheFirstMatchedTerm() {
        String content = "あ".repeat(50) + "結婚\nしたとき" + "い".repeat(200);

        String snippet = NavigationService.snippet(content, List.of("休暇", "結婚"));

        assertThat(snippet).startsWith("…").endsWith("…").contains("結婚 したとき");
        assertThat(snippet.indexOf("結婚")).isEqualTo(31);
    }

    @Test
    void snippetStartsAtTheBeginningWhenNoTermIsFound() {
        assertThat(NavigationService.snippet("短い本文", List.of("結婚"))).isEqualTo("短い本文");
    }
}
