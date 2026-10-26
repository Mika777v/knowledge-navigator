import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot の BOM のバージョンを上書きする（理由は libs.versions.toml）
extra["tomcat.version"] = libs.versions.tomcat.get()
extra["jackson-bom.version"] = libs.versions.jackson.get()
extra["jackson-2-bom.version"] = libs.versions.jackson2.get()

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
        mavenBom(libs.aws.sdk.bom.get().toString())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation(libs.springdoc.webmvc.ui)
    // S3 クライアント。HTTP は JDK の URLConnection を使い、Netty・Apache HttpClient は入れない
    implementation(libs.aws.sdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
    }
    implementation(libs.aws.sdk.url.connection.client)
    implementation(libs.pdfbox)
    implementation(libs.sudachi)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// ---------------------------------------------------------------- 形態素解析の辞書（ADR-0011）
// SudachiDict core（Apache License 2.0）は約 200MB あるため、リポジトリに入れずに PyPI の配布物から取り寄せる。
// SHA-256 が合わなければ失敗にする。取り寄せたものは Gradle のキャッシュの下に置き、次からは使い回す
val sudachiDictVersion = "20260723.1"
val sudachiDictSha256 = "2b711055dca03423869e491eca0ddbe3e17c4d7418ed738fd7c75d4e0eb9e4b1"
val sudachiDictUrl = "https://files.pythonhosted.org/packages/85/af/ba8419f684865b8cca587e01cedb41ba83fbdc985d75ab9e6ff38fdedf1a/" +
    "sudachidict_core-$sudachiDictVersion-py3-none-any.whl"
val sudachiDictionaryFile = gradle.gradleUserHomeDir.resolve("caches/knavi-sudachi/system_core-$sudachiDictVersion.dic")

val sudachiDictionary = tasks.register("sudachiDictionary") {
    description = "形態素解析の辞書（SudachiDict core $sudachiDictVersion）を取り寄せ、SHA-256 を確かめて展開する"
    inputs.property("version", sudachiDictVersion)
    inputs.property("sha256", sudachiDictSha256)
    outputs.file(sudachiDictionaryFile)
    doLast {
        if (sudachiDictionaryFile.exists()) {
            return@doLast
        }
        sudachiDictionaryFile.parentFile.mkdirs()
        val wheel = File(sudachiDictionaryFile.parentFile, "sudachidict_core-$sudachiDictVersion.whl.part")
        URI(sudachiDictUrl).toURL().openStream().use { input -> wheel.outputStream().use { output -> input.copyTo(output) } }
        val digest = MessageDigest.getInstance("SHA-256")
        wheel.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { b -> "%02x".format(b) }
        if (actual != sudachiDictSha256) {
            wheel.delete()
            throw GradleException("SudachiDict の SHA-256 が一致しません: $actual")
        }
        ZipFile(wheel).use { zip ->
            val entry = zip.getEntry("sudachidict_core/resources/system.dic")
                ?: throw GradleException("SudachiDict の配布物に system.dic がありません")
            val part = File(sudachiDictionaryFile.path + ".part")
            zip.getInputStream(entry).use { input -> part.outputStream().use { output -> input.copyTo(output) } }
            part.renameTo(sudachiDictionaryFile)
        }
        wheel.delete()
    }
}

/** backend のイメージに入れるため、辞書を決まった場所に写す（Dockerfile） */
tasks.register<Copy>("copySudachiDictionary") {
    dependsOn(sudachiDictionary)
    from(sudachiDictionaryFile)
    // ファイル名に辞書の版を残す（起動時に、検索列を作り直すかの判定に使う）
    into(layout.buildDirectory.dir("sudachi"))
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(sudachiDictionary)
    systemProperty("knavi.text.sudachi.dictionary", sudachiDictionaryFile.absolutePath)
}

tasks.test {
    useJUnitPlatform()
    // テスト用 DB イメージ（infra/docker/postgres）。`make up` か CI でビルドしたものを使う
    systemProperty("knavi.test.postgres-image",
        System.getenv("KNAVI_TEST_POSTGRES_IMAGE") ?: "knowledge-navigator/postgres:local")
    // 取り込みジョブの定期実行を止める（テストは IngestionWorker.runOnce() を直接呼んで、処理の順番を決める）
    systemProperty("knavi.ingestion.worker.enabled", "false")
    // 形態素解析の辞書（ADR-0011）。辞書なしで動くこと（劣化運転）は、テストの中でこの設定を外して確かめる
    dependsOn(sudachiDictionary)
    systemProperty("knavi.text.sudachi.dictionary", sudachiDictionaryFile.absolutePath)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 実行可能 jar（bootJar）だけを作る
tasks.jar {
    enabled = false
}
