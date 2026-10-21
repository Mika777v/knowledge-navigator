package com.example.knavi.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.TestcontainersConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

/** data/seed の実データを投入して、件数・参照・再実行の安全性を確認する */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SeedLoaderTests {

    static final Path SEED = Path.of("..", "data", "seed");
    static final String PASSWORD = "demo-password-for-test";

    @Autowired
    SeedLoader loader;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AuthenticationManager authenticationManager;

    @Test
    void loadsAllSeedDataAndCanBeRunTwice() {
        var first = loader.load(SEED, PASSWORD);
        var second = loader.load(SEED, PASSWORD);

        assertThat(second).isEqualTo(first);
        assertThat(first.departments()).isEqualTo(11);
        assertThat(first.contacts()).isEqualTo(15);
        assertThat(first.users()).isEqualTo(9);
        assertThat(first.documents()).isEqualTo(10);
        assertThat(first.programs()).isGreaterThanOrEqualTo(30);
        assertThat(first.lifeEvents()).isEqualTo(8);
        assertThat(first.faqs()).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM documents WHERE status = 'PUBLISHED'"))
                .isEqualTo(10);
        assertThat(count("SELECT count(*) FROM document_versions")).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM programs")).isEqualTo(first.programs());
        assertThat(count("SELECT count(*) FROM faqs")).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM navigation_keywords")).isEqualTo(first.keywords());
    }

    @Test
    void linksProgramsToArticlesAndContacts() {
        loader.load(SEED, PASSWORD);

        var basis = jdbc.sql("""
                        SELECT d.code || ' ' || s.article_no || '（' || s.heading || '）'
                        FROM programs p
                        JOIN program_basis b ON b.program_id = p.id
                        JOIN documents d ON d.id = b.document_id
                        JOIN sections s ON s.id = b.section_id
                        WHERE p.code = 'PRG-MARRIAGE-GIFT'
                        ORDER BY b.sort_order
                        """).query(String.class).list();
        assertThat(basis).containsExactly("REG-GA-001 第5条（結婚祝金）", "REG-GA-001 第3条（申請方法）");

        assertThat(count("""
                        SELECT count(*) FROM programs p
                        WHERE NOT EXISTS (SELECT 1 FROM program_contacts c WHERE c.program_id = p.id)
                        """)).as("窓口のない制度").isZero();
        assertThat(count("SELECT count(*) FROM program_basis WHERE article_no IS NOT NULL AND section_id IS NULL"))
                .as("条が見つからない根拠")
                .isZero();
    }

    @Test
    void mapsLifeEventsAndKeywords() {
        loader.load(SEED, PASSWORD);

        var marriage = jdbc.sql("""
                        SELECT p.code FROM life_events e
                        JOIN life_event_programs lp ON lp.life_event_id = e.id
                        JOIN programs p ON p.id = lp.program_id
                        WHERE e.code = 'MARRIAGE' AND lp.relation = 'PRIMARY'
                        ORDER BY lp.sort_order
                        """).query(String.class).list();
        assertThat(marriage).containsExactly("PRG-MARRIAGE-GIFT", "PRG-MARRIAGE-LEAVE");

        // 「入籍」は きっかけ「結婚」 と 結婚祝金・結婚休暇 に結びつく
        var targets = jdbc.sql(
                        "SELECT DISTINCT target_type FROM navigation_keywords WHERE normalized_term = '入籍' ORDER BY 1")
                .query(String.class)
                .list();
        assertThat(targets).containsExactly("LIFE_EVENT", "PROGRAM");
        // 全角の「ＰＣ」でも引けるように正規化して保存されている
        assertThat(count("SELECT count(*) FROM navigation_keywords WHERE normalized_term = 'pc'"))
                .isPositive();
    }

    @Test
    void keepsManagerOnlyContentRestricted() {
        loader.load(SEED, PASSWORD);

        assertThat(jdbc.sql("SELECT visibility FROM programs WHERE code = 'PRG-MANAGER-CHILDCARE-SUPPORT'")
                        .query(String.class)
                        .single())
                .isEqualTo("MANAGERS");
        assertThat(count("SELECT count(*) FROM faqs WHERE visibility = 'MANAGERS'"))
                .isEqualTo(2);
    }

    @Test
    void demoUsersCanLogInWithTheConfiguredPassword() {
        loader.load(SEED, PASSWORD);

        var auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("sato@example.com", PASSWORD));
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void setsDefaultContact() {
        loader.load(SEED, PASSWORD);

        assertThat(jdbc.sql("SELECT value FROM app_settings WHERE key = 'navigation.default_contact_code'")
                        .query(String.class)
                        .single())
                .isEqualTo("HR-REP");
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
