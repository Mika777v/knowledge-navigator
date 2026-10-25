package com.example.knavi.seed;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@code knavi.seed.enabled=true} で起動したときにダミーデータを投入する。
 * {@code spring.main.web-application-type=none} と組み合わせると、投入後にプロセスが終了する（make seed）。
 */
@Component
@ConditionalOnProperty(name = "knavi.seed.enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedLoader loader;
    private final SeedSourcePdfs sourcePdfs;
    private final SeedProperties properties;
    private final ConfigurableApplicationContext context;

    SeedRunner(
            SeedLoader loader,
            SeedSourcePdfs sourcePdfs,
            SeedProperties properties,
            ConfigurableApplicationContext context) {
        this.loader = loader;
        this.sourcePdfs = sourcePdfs;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.demoUserPassword() == null
                || properties.demoUserPassword().length() < 8) {
            throw new IllegalStateException("knavi.seed.demo-user-password（DEMO_USER_PASSWORD）に8文字以上を指定してください");
        }
        Path path = Path.of(properties.path() == null ? "../data/seed" : properties.path());
        SeedLoader.Summary summary = loader.load(path, properties.demoUserPassword());
        log.info("seed completed: {}", summary);
        if (properties.pdfPath() != null && !properties.pdfPath().isBlank()) {
            int attached = sourcePdfs.attach(Path.of(properties.pdfPath()));
            log.info("source PDFs attached: {} documents", attached);
        }

        // Web サーバーなしで起動した場合（make seed）は、投入が終わったらプロセスを終了する
        if (!(context instanceof WebServerApplicationContext)) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }
}
