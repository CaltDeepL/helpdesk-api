import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.check)
    checkstyle
    jacoco
}

group = "com.caltdeepl"
version = "0.1.0-SNAPSHOT"
description = "問い合わせ管理 API"

java {
    toolchain {
        // Java 25 (LTS)。ローカル・CI・Docker はすべてこのバージョンに揃える。
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

// ---------------------------------------------------------------------------
// 結合テスト用の SourceSet
//   src/test            … 単体テスト（DB 不要・高速）
//   src/integrationTest … 結合テスト（Testcontainers で実 PostgreSQL を起動）
// ---------------------------------------------------------------------------
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- DB / マイグレーション ---
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- API 仕様 ---
    implementation(libs.springdoc.openapi)

    // --- 認証 ---
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // --- 構造化ログ ---
    implementation(libs.logstash.encoder)

    // --- テスト ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- 結合テスト ---
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:postgresql")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

// ---------------------------------------------------------------------------
// Spotless: フォーマットの自動整形
//   ./gradlew spotlessApply  で整形、spotlessCheck は CI で実行
//   google-java-format は新しい JDK への追従が遅れることがあるため
//   palantir-java-format（Google Java Style 派生）を採用
// ---------------------------------------------------------------------------
configure<SpotlessExtension> {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "jakarta", "org", "com", "")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", "*.yml", "*.yaml", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------------
// Checkstyle: 命名・構造のルール
// ---------------------------------------------------------------------------
checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

// ---------------------------------------------------------------------------
// テスト
// ---------------------------------------------------------------------------
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        description = "Testcontainers を用いた結合テストを実行する"
        group = "verification"
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        shouldRunAfter(tasks.test)
    }

tasks.check {
    dependsOn(integrationTestTask)
}

// ---------------------------------------------------------------------------
// JaCoCo: カバレッジ
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // NFR-5.2: 主要ロジックのカバレッジ 70% 以上
                minimum = "0.70".toBigDecimal()
            }
        }
    }
    // 土台段階ではコードが少なく閾値を満たせないため、タスク #2 以降で有効化する
    enabled = false
}

// ---------------------------------------------------------------------------
// OWASP Dependency-Check: 依存ライブラリの脆弱性検査
//
//   通常時   : NVD API キー（環境変数 NVD_API_KEY）を使って差分更新する
//   NVD障害時: -PnvdAutoUpdate=false を付けて起動し、NVD に一切アクセスせず
//              キャッシュ済みのローカル DB だけで解析する
//
//   注意: キーを外しても障害の回避にはならない。キーの有無はレート制限が
//        変わるだけで、接続先は同じ NVD の API。障害時に必要なのは
//        「NVD を叩かないこと」であり、それが autoUpdate=false にあたる。
// ---------------------------------------------------------------------------
val nvdApiKey: String = System.getenv("NVD_API_KEY").orEmpty()

// NVD 障害時のフォールバック用。既定は true（通常どおり更新する）
val nvdAutoUpdate: Boolean =
    (findProperty("nvdAutoUpdate") as String?)?.toBooleanStrictOrNull() ?: true

// 自前・社内ミラーの NVD データフィードがあれば指定する（任意）
val nvdDatafeedUrl: String = System.getenv("NVD_DATAFEED_URL").orEmpty()

dependencyCheck {
    failBuildOnCVSS = 7.0f

    // NVD に到達できないことを「脆弱性あり」と混同しない。
    // 更新を試みる通常時のみ、解析エラーでビルドを落とす。
    failOnError = nvdAutoUpdate
    autoUpdate = nvdAutoUpdate

    nvd {
        apiKey = nvdApiKey
        // キーなしは 5req/30秒の制限。間隔を空けないと 429 で弾かれる
        delay = if (nvdApiKey.isBlank()) 6000 else 2000
        maxRetryCount = 20
        if (nvdDatafeedUrl.isNotBlank()) {
            datafeedUrl = nvdDatafeedUrl
        }
    }

    analyzers {
        assemblyEnabled = false
        nodeAuditEnabled = false
        nodeEnabled = false
        // OSS Index は NVD とは独立した脆弱性ソース。
        // NVD が落ちている間もこちらは生きていることが多い。
        ossIndexEnabled = true
    }

    suppressionFile = "config/dependency-check-suppressions.xml"
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "app.jar"
}
