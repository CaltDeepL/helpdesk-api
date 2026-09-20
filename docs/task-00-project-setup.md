以下の簡潔版で十分です。詳細な経緯は削り、Task #0 の完了状態・主要判断・GitHub 設定・NVD 縮退・次タスクだけ残しています。

`docs/task-00-project-setup.md`

````markdown
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
| Lint | Checkstyle |
| Security | OWASP Dependency-Check / Dependabot |
| Health | Spring Boot Actuator |

### ローカルポート

| サービス | ホスト | コンテナ |
| --- | ---: | ---: |
| API | `8082` | `8082` |
| PostgreSQL | `5435` | `5432` |

Compose 内の DB 接続:

```text
db:5432
```

ホストからの DB 接続:

```text
localhost:5435
```

## 設計判断

| 判断 | 理由 |
| --- | --- |
| Spring Boot 4.1 / Java 25 | 新規プロジェクトとして現行世代を採用 |
| RFC 9457 | API エラー形式を統一 |
| 機能優先パッケージ | 機能追加時の横断を減らす |
| Unit / Integration Test 分離 | 日常テストを高速に保つ |
| Testcontainers | 本番同等の PostgreSQL で検証 |
| Version Catalog | dependency / plugin version を集中管理 |
| Audit を Required CI にしない | NVD 障害で merge が停止するのを防ぐ |
| GitHub Ruleset | `main` への直接変更を禁止 |

## GitHub 設定

### Merge

```text
Squash merge       : ON
Merge commit       : OFF
Rebase merge       : OFF
Auto-delete branch : ON
```

### main Ruleset

```text
PR required                 : ON
Required approvals          : 0
Linear history              : ON
Force push                  : blocked
Deletion                    : blocked
Conversation resolution     : required
Branch up-to-date           : required
Bypass                      : none
```

### Required status checks

```text
Format
Lint
Test
Docker Build
```

`Audit` は Required に含めない。

### Actions

```text
GITHUB_TOKEN      : read-only
Create/approve PR : disabled
Artifact retention: 14 days
```

workflow 側にも明示する。

```yaml
permissions:
  contents: read
```

### Code security

```text
Dependency graph            : ON
Dependabot alerts           : ON
Dependabot security updates : ON
Secret scanning             : ON（利用可能な場合）
Push protection             : ON（利用可能な場合）
```

## NVD 障害時の運用

`NVD_API_KEY` は Task #0 の必須条件にしない。

通常 CI:

```text
Format
Lint
Test
Docker Build
```

は NVD に依存させない。

NVD 障害時:

```text
NVD_DEGRADED=true
```

とし、Dependency-Check は NVD 更新を停止する。

```bash
./gradlew dependencyCheckAnalyze -PnvdAutoUpdate=false
```

キャッシュがない場合は Audit をスキップし、通常 CI は継続する。

NVD 復旧後:

1. `NVD_API_KEY` を GitHub Secret に登録
2. `NVD_DEGRADED` を削除または `false`
3. Security Audit を手動実行
4. 脆弱性情報を再取得・確認

## つまずいた点と教訓

- `gradlew` / `gradlew.bat` はプロジェクトルートに置く
- `gradle-wrapper.jar` はバイナリなので編集しない
- JDK 25 が認識されているか `java --version` で確認する
- Version Catalog は `gradle/libs.versions.toml`
- Spring Boot の main class は `src/main/java/...` に置く
- Dockerfile の `COPY` と実際のディレクトリ構成を一致させる
- Compose 起動前に `.env` を作成する
- Spring Boot major update 時は `application.yaml` の互換性も確認する
- 外部サービス依存の Audit と必須 CI を分離する

## 再現コマンド

```bash
cp .env.example .env

java --version
./gradlew --version

./gradlew spotlessApply
./gradlew check
./gradlew integrationTest

docker compose up -d --build
docker compose ps

curl -fsS http://localhost:8082/actuator/health | jq .
```

## 次タスクへの引き継ぎ

Task #1: エラーハンドリング基盤の検証。

`GlobalExceptionHandler` が RFC 9457 の
`application/problem+json` を返すことを結合テストで確認する。

主な対象:

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

アプリケーション例外と DB 接続障害は別テストとして扱い、
5xx では SQL・stack trace・DB 情報などをレスポンスへ漏らさない。

---

Task #0 完了。
````
