# Task #0: プロジェクト土台

## ゴールと完了条件

| 完了条件 | 状態 |
| --- | --- |
| `docker compose up -d --build` でコンテナが healthy | ✅ |
| `/actuator/health` が 200、DB が `UP` | ✅ |
| `./gradlew check` が green | ✅ |
| CI（Format / Lint / Test / Docker Build）が green | ✅ |
| `main` のブランチ保護が有効 | ✅ |
| GitHub Actions / Code security 設定完了 | ✅ |

## 構成

| 項目 | 採用 |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1 |
| DB | PostgreSQL 17 |
| Build | Gradle Kotlin DSL |
| Migration | Flyway |
| Integration Test | Testcontainers |
| Format | Spotless + palantir-java-format |
| Lint | Checkstyle 14.1.0 |
| Security | OWASP Dependency-Check / Dependabot / Dependency Review |
| Health | Spring Boot Actuator |

### ローカルポート

| サービス | ホスト | コンテナ |
| --- | ---: | ---: |
| API | `8082` | `8082` |
| PostgreSQL | `5435` | `5432` |

Compose 内の DB 接続は `db:5432`、ホストからは `localhost:5435` を使用する。

## 設計判断

| 判断 | 理由 |
| --- | --- |
| Spring Boot 4.1 / Java 25 | 新規プロジェクトとして現行世代を採用 |
| RFC 9457 | API エラー形式を統一 |
| 機能優先パッケージ | 機能追加時のディレクトリ横断を減らす |
| Unit / Integration Test 分離 | 日常の `test` を軽量に保つ |
| Testcontainers | 本番と同じ PostgreSQL で結合テストする |
| Version Catalog | dependency / plugin version を集中管理 |
| Audit を Required CI にしない | NVD 障害で merge を停止させない |
| Dependency Review を併設 | NVD 非依存の脆弱性検査経路を持つ |
| GitHub Ruleset | `main` への直接変更・force push を防ぐ |
| Required approvals = 0 | 一人開発でも PR + CI による保護を成立させる |

## Task #0 で整備したもの

- Gradle Wrapper / Version Catalog
- Spotless / Checkstyle / JaCoCo
- `test` / `integrationTest` SourceSet
- Testcontainers + PostgreSQL
- Docker multi-stage build / Docker Compose
- Actuator health check
- `.env.example`
- GitHub Actions CI
- Dependabot
- Dependency Review
- OWASP Dependency-Check
- `main` Ruleset
- VS Code 推奨設定
- README / ADR / Task document

## CI

GitHub Actions workflow:

```text
.github/workflows/ci.yml
```

Required checks:

```text
Format
Lint
Test
Docker Build
```

Task #0 の PR で以下を確認済み。

```text
Format             success
Lint               success
Test               success
Docker Build       success
Dependency Review  success
Audit              success
```

`Audit` は NVD 外部障害の影響を受けるため Required check には含めない。

## NVD 障害時の運用

`NVD_API_KEY` は Task #0 の必須条件にしない。

API Key 未設定または NVD 障害時は縮退モードを使用する。

```text
NVD_DEGRADED=true
```

ローカル:

```bash
./gradlew dependencyCheckAnalyze -PnvdAutoUpdate=false
```

縮退モードでは NVD にアクセスせず、キャッシュ済み DB がある場合のみ解析する。

キャッシュが存在しない場合は Audit をスキップし、通常 CI は継続する。

通常 CI:

```text
Format
Lint
Test
Docker Build
```

は NVD の状態に依存させない。

## GitHub Ruleset

Ruleset:

```text
main protection
```

設定:

```text
Enforcement                     Active
Target                          default branch
Deletion                        blocked
Force push                      blocked
Linear history                  required
Pull Request                    required
Required approvals              0
Conversation resolution         required
Branch up-to-date               required
Bypass                          none
```

Required status checks:

```text
Format
Lint
Test
Docker Build
```

## つまずいた点と教訓

- `gradlew` / `gradlew.bat` はプロジェクトルートに配置する
- `gradle-wrapper.jar` はバイナリなので編集しない
- JDK 25 が認識されているか `java --version` で確認する
- Version Catalog は `gradle/libs.versions.toml` に置く
- Spring Boot main class は `src/main/java/...` に置く
- Dockerfile の `COPY` と実際のディレクトリ構成を一致させる
- Compose 起動前に `.env` を作成する
- Spring Boot major update 時は `application.yaml` の互換性も確認する
- Checkstyle `11.2.0` は存在しないため正式版 `14.1.0` に修正した
- Checkstyle 設定は `config/checkstyle/checkstyle.xml` に統一した
- Spring Boot 起動クラスの誤検知は `ignoreAnnotatedBy` で除外した
- Java / Kotlin Gradle DSL / Markdown / YAML は `spotlessApply` で整形した
- Task #0 時点の unit / integration test は `NO-SOURCE`
- Gradle 10 向け deprecated warning は別タスクとして扱う
- GitHub Actions workflow は `.github/workflows/*.yml` 配下でなければ認識されない
- 外部サービス依存の Audit と Required CI は分離する

## 再現コマンド

```bash
cp .env.example .env

java --version
./gradlew --version

./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew clean check
./gradlew integrationTest

docker compose up -d --build
docker compose ps

curl -fsS http://localhost:8082/actuator/health | jq .

docker compose exec db pg_isready
```

## 次タスクへの引き継ぎ

Task #1: エラーハンドリング基盤の検証。

`GlobalExceptionHandler` が RFC 9457 の
`application/problem+json` を返すことを結合テストで確認する。

対象:

```text
400
404
409
422
500
```

確認項目:

```text
type
title
status
detail
instance
traceId
```

アプリケーション例外と DB 接続障害は別テストとして扱う。

5xx では以下をレスポンスに漏らさない。

```text
stack trace
SQL
JDBC URL
DB credentials
internal exception class
```

---

Task #0 完了。