# ---------------------------------------------------------------------------
# マルチステージビルド
#   builder … 依存解決とビルド。依存レイヤーを分けてキャッシュを効かせる
#   runtime … JRE のみ。非 root ユーザーで実行する
# ---------------------------------------------------------------------------

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# 1) 依存解決に必要なファイルだけ先にコピー
#    ソース変更だけではこのレイヤーが無効化されないためビルドが速くなる
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 2) ソースをコピーしてビルド（テストはCIで実行済みのためスキップ）
COPY config ./config
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test -x check

# ---------------------------------------------------------------------------

FROM eclipse-temurin:25-jre AS runtime

# ヘルスチェック用に curl だけ入れる（イメージサイズより運用のしやすさを優先）
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# 非 root ユーザーを作成して実行する
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home /app app

WORKDIR /app
COPY --from=builder --chown=app:app /workspace/build/libs/app.jar ./app.jar

USER app

ENV TZ=Asia/Tokyo \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]