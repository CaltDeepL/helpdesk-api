# helpdesk-api

問い合わせ（チケット）管理 API。カスタマーサポート業務のドメインを対象とした、バックエンドエンジニア転職用のポートフォリオプロジェクト。

- 言語: Java 25 (LTS)
- フレームワーク: Spring Boot 4.1
- DB: PostgreSQL 17

## 現在の状態

Task #0（プロジェクト土台）は完了。次は Task #1（エラーハンドリング基盤の検証）に進む。

| 項目 | 状態 |
| --- | --- |
| Docker Compose で API / PostgreSQL を起動 | ✅ |
| `/actuator/health` で DB 接続を含むヘルスチェック | ✅ |
| `./gradlew check` | ✅ |
| CI（Format / Lint / Test / Docker Build） | ✅ |
| `main` ブランチ保護 | ✅ |

ローカルのポート割り当て:

| 用途 | ホスト | コンテナ |
| --- | ---: | ---: |
| helpdesk-api | `8082` | `8082` |
| PostgreSQL | `5435` | `5432` |

---

## 目次

1. [背景と目的](#1-背景と目的)
2. [要件定義](#2-要件定義)
3. [ドメインモデル](#3-ドメインモデル)
4. [API 設計方針](#4-api-設計方針)
5. [技術選定と根拠](#5-技術選定と根拠)
6. [セットアップ](#6-セットアップ)
7. [開発ワークフロー](#7-開発ワークフロー)
8. [実装ロードマップ](#8-実装ロードマップ)

---

## 1. 背景と目的

### 背景

カスタマーサポートの現場では、問い合わせがメール・電話・チャットなど複数チャネルから流入し、
担当者ごとに対応状況が属人化しやすい。特に以下が典型的な課題となる。

- 誰がどの問い合わせを持っているか分からず、対応漏れ・二重対応が起きる
- 「保留」の実態が人によって違い、放置と区別がつかない
- 初回応答までの時間が計測されておらず、改善のしようがない
- 対応履歴が個人のメールボックスに閉じており、引き継ぎができない

### 目的

問い合わせのライフサイクルを**サーバー側で一元管理し、不正な状態遷移をシステムが拒否する**ことで、
運用ルールを人の記憶ではなくデータモデルで保証する。

### このプロジェクトで示したいこと

| 観点 | 示す内容 |
| --- | --- |
| ドメイン設計 | 状態遷移を型とテストで表現し、不正遷移を弾く |
| API 設計 | RFC 9457 準拠のエラー、一貫した命名、OpenAPI による契約 |
| DB 設計 | 正規化、インデックス設計、履歴の持ち方 |
| 品質 | 静的解析・アーキテクチャテスト・結合テストの自動化 |
| 運用 | 構造化ログ、メトリクス、ヘルスチェック、CI/CD |

---

## 2. 要件定義

### 2.1 スコープ

#### 対象に含む

- 問い合わせ（チケット）の登録・更新・検索
- ステータス遷移の制御
- 担当者のアサイン
- 対応履歴（コメント）の記録
- SLA（初回応答期限・解決期限）の管理と超過検知
- 対応実績の集計

#### 対象に含まない（明示的に除外）

| 除外項目 | 理由 |
| --- | --- |
| メール受信によるチケット自動起票 | 外部連携が主役になり、ドメイン設計の見せ場がぼやけるため |
| リアルタイムチャット | WebSocket の題材は別プロジェクトで扱うため |
| 多言語対応 | 日本語固定とし、設計の複雑度を上げないため |
| マルチテナント | 単一組織を前提とする |

### 2.2 登場人物（ロール）

| ロール | 説明 | 主な権限 |
| --- | --- | --- |
| `AGENT` | サポート担当者 | 自分に割り当てられたチケットの操作、全チケットの閲覧 |
| `MANAGER` | 管理者 | 全チケットの操作、担当者アサイン、集計の閲覧 |

> 認証されたユーザーのみが API を利用できる。顧客（問い合わせ元）は本 API のユーザーではなく、
> チケットの属性として記録される。

### 2.3 用語定義

| 用語 | 定義 |
| --- | --- |
| チケット (Ticket) | 1 件の問い合わせ。ライフサイクルを持つ |
| ステータス (Status) | チケットの状態。あらかじめ定義された遷移のみ許可される |
| 優先度 (Priority) | `LOW` / `NORMAL` / `HIGH` / `URGENT` |
| コメント (Comment) | チケットに紐づく対応記録。公開／内部メモの 2 種 |
| SLA | 初回応答期限・解決期限。優先度から自動計算される |
| 初回応答 | そのチケットに対する最初の「公開コメント」 |

### 2.4 機能要件

#### FR-1 認証・ユーザー管理

| ID | 要件 |
| --- | --- |
| FR-1.1 | メールアドレスとパスワードでユーザー登録ができる |
| FR-1.2 | ログインすると JWT アクセストークンが発行される |
| FR-1.3 | 認証が必要なエンドポイントは、トークン欠如・不正・期限切れで 401 を返す |
| FR-1.4 | パスワードは復元不可能な形式で保存する |

#### FR-2 チケット管理

| ID | 要件 |
| --- | --- |
| FR-2.1 | 件名・本文・優先度・カテゴリ・問い合わせ元を指定してチケットを作成できる |
| FR-2.2 | チケットには一意な参照番号を採番する（例: `TKT-2026-000123`） |
| FR-2.3 | チケットの一覧を取得できる。ページングは必須 |
| FR-2.4 | ステータス・優先度・担当者・カテゴリ・期間・キーワードで絞り込める |
| FR-2.5 | チケットの件名・本文・優先度・カテゴリを更新できる |
| FR-2.6 | チケットは物理削除しない（監査証跡を残すため） |

#### FR-3 ステータス遷移

| ID | 要件 |
| --- | --- |
| FR-3.1 | ステータスは定義された遷移のみ許可し、それ以外は 422 を返す |
| FR-3.2 | 遷移の履歴（誰が・いつ・何から何へ）をすべて記録する |
| FR-3.3 | `PENDING`（保留）への遷移には理由の入力を必須とする |
| FR-3.4 | `RESOLVED` への遷移には担当者が割り当てられている必要がある |

#### FR-4 アサイン

| ID | 要件 |
| --- | --- |
| FR-4.1 | `MANAGER` は任意のチケットに担当者を割り当てられる |
| FR-4.2 | `AGENT` は未アサインのチケットを自分に割り当てられる |
| FR-4.3 | 担当者の変更履歴を記録する |

#### FR-5 コメント

| ID | 要件 |
| --- | --- |
| FR-5.1 | チケットにコメントを追加できる |
| FR-5.2 | コメントは「公開」「内部メモ」の可視性を持つ |
| FR-5.3 | 最初の公開コメント投稿時に、初回応答日時を記録する |
| FR-5.4 | `CLOSED` のチケットにはコメントを追加できない |

#### FR-6 SLA

| ID | 要件 |
| --- | --- |
| FR-6.1 | チケット作成時、優先度に応じて初回応答期限・解決期限を自動計算する |
| FR-6.2 | 期限超過は「期限 < 現在時刻 かつ 未達成」で判定する（バッチ更新はしない） |
| FR-6.3 | 保留中の時間は経過時間から除外する |
| FR-6.4 | 期限超過チケットの一覧を取得できる |

SLA 既定値（設定で変更可能）:

| 優先度 | 初回応答期限 | 解決期限 |
| --- | --- | --- |
| `URGENT` | 1 時間 | 4 時間 |
| `HIGH` | 4 時間 | 1 営業日 |
| `NORMAL` | 1 営業日 | 3 営業日 |
| `LOW` | 2 営業日 | 5 営業日 |

#### FR-7 集計

| ID | 要件 |
| --- | --- |
| FR-7.1 | 期間を指定して、新規・解決・クローズ件数を取得できる |
| FR-7.2 | 平均初回応答時間・平均解決時間を取得できる |
| FR-7.3 | 担当者別・カテゴリ別に集計できる |
| FR-7.4 | SLA 遵守率を取得できる |

### 2.5 非機能要件

| 分類 | ID | 要件 |
| --- | --- | --- |
| 性能 | NFR-1.1 | 一覧 API はチケット 10 万件の状態で p95 300ms 以内 |
| 性能 | NFR-1.2 | 検索条件に対応するインデックスを設計時に定義する |
| 可用性 | NFR-2.1 | `/actuator/health` で DB 接続を含む死活監視ができる |
| 可観測性 | NFR-3.1 | ログは JSON 構造化ログとし、リクエスト単位の追跡 ID を持つ |
| 可観測性 | NFR-3.2 | メトリクスを Micrometer 経由で公開する |
| セキュリティ | NFR-4.1 | パスワード・トークンをログに出力しない |
| セキュリティ | NFR-4.2 | 依存ライブラリの脆弱性を CI で検査する |
| 保守性 | NFR-5.1 | レイヤー間の依存方向をアーキテクチャテストで強制する |
| 保守性 | NFR-5.2 | 主要ロジックのテストカバレッジ 70% 以上 |
| 移植性 | NFR-6.1 | `docker compose up` のみでローカル環境が起動する |

### 2.6 制約・前提

- 単一リージョン・単一インスタンス構成を前提とする
- タイムゾーンは `Asia/Tokyo`。DB には `timestamptz` で保存し、UTC で持つ
- 通貨・金額は扱わない
- 営業日計算は「土日祝を除く」とし、祝日マスタはアプリ内に持つ

---

## 3. ドメインモデル

### 3.1 ステータス遷移図

```text
                    ┌──────────────────────────────┐
                    │                              │
                    ▼                              │
  (作成) ──▶ OPEN ──────▶ IN_PROGRESS ──────▶ RESOLVED ──────▶ CLOSED
              │               │   ▲               │              ▲
              │               │   │               │              │
              │               ▼   │               └──────────────┘
              │           PENDING ─┘                   (再オープン不可)
              │               │
              └───────────────┴──────────────▶ CLOSED
                                             (対応不要として終了)
```

許可される遷移:

| From | To | 追加条件 |
| --- | --- | --- |
| `OPEN` | `IN_PROGRESS` | 担当者が割り当て済み |
| `OPEN` | `PENDING` | 理由必須 |
| `OPEN` | `CLOSED` | 理由必須 |
| `IN_PROGRESS` | `PENDING` | 理由必須 |
| `IN_PROGRESS` | `RESOLVED` | 担当者が割り当て済み |
| `PENDING` | `IN_PROGRESS` | — |
| `PENDING` | `CLOSED` | 理由必須 |
| `RESOLVED` | `IN_PROGRESS` | 再対応（理由必須） |
| `RESOLVED` | `CLOSED` | — |
| `CLOSED` | （なし） | 終端状態 |

上記以外はすべて 422 `invalid-status-transition` を返す。

### 3.2 ER 図

```text
┌─────────────────┐       ┌──────────────────────┐       ┌────────────────────┐
│ users           │       │ tickets              │       │ ticket_comments    │
├─────────────────┤       ├──────────────────────┤       ├────────────────────┤
│ id (PK)         │◀──┬───│ id (PK)              │◀──────│ id (PK)            │
│ email (UQ)      │   │   │ reference_no (UQ)    │       │ ticket_id (FK)     │
│ password_hash   │   │   │ subject              │       │ author_id (FK)     │
│ display_name    │   ├───│ assignee_id (FK,NULL)│       │ body               │
│ role            │   │   │ requester_name       │       │ visibility         │
│ created_at      │   ├───│ created_by (FK)      │       │ created_at         │
│ updated_at      │   │   │ status               │       └────────────────────┘
└─────────────────┘   │   │ priority             │
                      │   │ category_id (FK)     │       ┌────────────────────┐
┌─────────────────┐   │   │ first_response_due_at│       │ ticket_status_logs │
│ categories      │   │   │ resolution_due_at    │       ├────────────────────┤
├─────────────────┤◀──┼───│ first_responded_at   │◀──────│ id (PK)            │
│ id (PK)         │   │   │ resolved_at          │       │ ticket_id (FK)     │
│ name (UQ)       │   │   │ closed_at            │       │ changed_by (FK)    │
│ created_at      │   └───│ created_at           │       │ from_status (NULL) │
└─────────────────┘       │ updated_at           │       │ to_status          │
                          └──────────────────────┘       │ reason (NULL)      │
                                                         │ changed_at         │
                                                         └────────────────────┘
```

### 3.3 主要な設計判断

| 判断 | 採用した方式 | 却下した案と理由 |
| --- | --- | --- |
| 状態遷移の表現 | `enum` + 遷移表を持つドメインサービス | DB の CHECK 制約のみ → 遷移理由や履歴を扱えない |
| 履歴の持ち方 | 別テーブル `ticket_status_logs` に追記 | `tickets` に直接上書き → 監査証跡が残らない |
| SLA 超過判定 | 参照時に計算 | バッチで `is_overdue` 列を更新 → 常に遅延が生じる |
| チケット削除 | 論理削除もせず `CLOSED` で終端 | 物理削除 → 監査要件を満たせない |
| コメント編集 | 不可（訂正は新規コメントで） | 編集可 → 対応履歴の信頼性が下がる |

これらの根拠は `docs/adr/` に ADR として個別に記録する。

---

## 4. API 設計方針

### 4.1 エンドポイント一覧（予定）

| メソッド | パス | 説明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | ユーザー登録 |
| `POST` | `/api/auth/login` | ログイン（JWT 発行） |
| `GET` | `/api/me` | 自分の情報 |
| `POST` | `/api/tickets` | チケット作成 |
| `GET` | `/api/tickets` | チケット一覧（検索・ページング） |
| `GET` | `/api/tickets/{id}` | チケット詳細 |
| `PATCH` | `/api/tickets/{id}` | チケット更新 |
| `POST` | `/api/tickets/{id}/status` | ステータス遷移 |
| `POST` | `/api/tickets/{id}/assignee` | 担当者アサイン |
| `GET` | `/api/tickets/{id}/comments` | コメント一覧 |
| `POST` | `/api/tickets/{id}/comments` | コメント追加 |
| `GET` | `/api/categories` | カテゴリ一覧 |
| `GET` | `/api/analytics/summary` | 件数サマリ |
| `GET` | `/api/analytics/response-time` | 応答時間の集計 |
| `GET` | `/api/analytics/sla` | SLA 遵守率 |

### 4.2 エラーレスポンス

RFC 9457 (`application/problem+json`) に準拠する。

```json
{
  "type": "https://helpdesk.example.com/problems/invalid-status-transition",
  "title": "許可されていないステータス遷移です",
  "status": 422,
  "detail": "CLOSED から IN_PROGRESS へは遷移できません",
  "instance": "/api/tickets/42/status",
  "traceId": "00-4bf92f...-01"
}
```

| HTTP | 用途 |
| --- | --- |
| 400 | リクエスト形式の誤り |
| 401 | 未認証・トークン不正 |
| 403 | 権限不足 |
| 404 | リソースが存在しない（他人のリソースも 404 とし、存在を漏らさない） |
| 409 | 一意制約違反 |
| 422 | ドメインルール違反（不正な状態遷移など） |

5xx の場合、内部情報は返さず `traceId` のみを返す。

### 4.3 API バージョニング

Spring Boot 4 の API バージョニング機能を用いて `/api/v1` 相当を宣言的に管理する。
初期リリースは v1 のみだが、破壊的変更を伴う際の移行手順を ADR に残す。

---

## 5. 技術選定と根拠

| 層 | 採用 | 根拠 |
| --- | --- | --- |
| 言語 | Java 25 (LTS) | record / sealed / パターンマッチ等を活用 |
| フレームワーク | Spring Boot 4.1 | 新規プロジェクトとして 4 系を採用 |
| 並行処理 | 仮想スレッド | `spring.threads.virtual.enabled=true`。ブロッキング記述のまま高並行性を得る |
| データアクセス | Spring Data JPA + JdbcClient | 単純な CRUD は JPA、集計クエリは JdbcClient で生 SQL |
| DB | PostgreSQL 17 | 部分インデックス等の PostgreSQL 機能を利用 |
| マイグレーション | Flyway | SQL をそのまま管理でき、レビューしやすい |
| 認証 | Spring Security + JWT | ステートレス。将来の OIDC 追加も見据える |
| API 仕様 | springdoc-openapi | コードから OpenAPI を生成し、仕様と実装の乖離を防ぐ |
| テスト | JUnit + Testcontainers | 実 PostgreSQL に対して結合テストを行う |
| 監視 | Actuator + Micrometer | ヘルスチェックとメトリクスの標準的な公開 |

### なぜ WebFlux を選ばなかったか

このシステムは I/O バウンドだが、リアクティブ特有の学習コスト・デバッグの難しさに見合う
スループット要件がない。Java 21 で正式化された仮想スレッドにより、
ブロッキング記述のまま高い並行性を得られるため、
可読性を優先して MVC + 仮想スレッドを採用した。

---

## 6. セットアップ

### 前提

| ツール | バージョン / 補足 |
| --- | --- |
| JDK | 25（Eclipse Temurin 推奨） |
| Docker / Docker Compose | Docker Compose v2 |
| Git | 任意の現行バージョン |
| `jq` | ヘルスチェック JSON の確認用（任意） |

Gradle Wrapper はリポジトリにコミット済みのものを使用する。グローバル Gradle のインストールは不要。

```bash
./gradlew --version
```

Wrapper の構成:

```text
gradlew
gradlew.bat
gradle/
├── libs.versions.toml
└── wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
```

`gradle-wrapper.jar` はバイナリファイルのため、テキストエディターで表示できなくても正常。
`.gitattributes` で JAR を binary、`gradlew` を LF、`gradlew.bat` を CRLF として扱う。

### 初回セットアップ

```bash
# 1. 環境変数
cp .env.example .env

# 2. DB と API をビルド・起動
docker compose up -d --build

# 3. コンテナ状態
docker compose ps

# 4. DB を含むヘルスチェック
curl -fsS http://localhost:8082/actuator/health | jq .
```

Compose 内では API から PostgreSQL へ `db:5432` で接続する。
ホストから PostgreSQL へ接続する場合は `localhost:5435` を使う。

### ローカル開発（アプリはホスト、DB のみ Docker）

```bash
docker compose up -d db
./gradlew bootRun --args='--spring.profiles.active=local'
```

### よく使うコマンド

| コマンド | 内容 |
| --- | --- |
| `./gradlew spotlessApply` | Java / Gradle ファイルを整形 |
| `./gradlew spotlessCheck` | フォーマット違反を検出 |
| `./gradlew check` | Lint・単体テストなど通常の品質ゲートを実行 |
| `./gradlew test` | 単体テスト |
| `./gradlew integrationTest` | Testcontainers を用いた結合テスト |
| `./gradlew jacocoTestCoverageVerification` | カバレッジ閾値チェック（Task #0 時点では無効） |
| `./gradlew dependencyCheckAnalyze` | OWASP Dependency-Check |

### 主要 URL

| URL | 内容 |
| --- | --- |
| `http://localhost:8082/actuator/health` | ヘルスチェック |
| `http://localhost:8082/swagger-ui.html` | Swagger UI |
| `http://localhost:8082/v3/api-docs` | OpenAPI JSON |

### Task #0 で整備した開発基盤

- Spring Boot 4.1 / Java 25 / Gradle Kotlin DSL
- Gradle Wrapper と Version Catalog (`gradle/libs.versions.toml`)
- Spotless + palantir-java-format / Checkstyle
- 単体テストと `integrationTest` SourceSet の分離
- Testcontainers + PostgreSQL 17
- Docker multi-stage build / Docker Compose healthcheck
- Actuator による DB 接続を含むヘルスチェック
- `.env.example` による環境変数のひな形管理
- CI の Format / Lint / Test / Docker Build
- `main` ブランチ保護

---

## 7. 開発ワークフロー

詳細は [CONTRIBUTING.md](CONTRIBUTING.md) を参照。要点のみ:

- `main` は保護ブランチ。直接 push しない
- 作業は `feature/xxx` などのブランチを切り、PR 経由でマージ（Squash merge）
- コミットメッセージは Conventional Commits
- 必須 CI（Format / Lint / Test / Docker Build）が green でなければマージ不可
- 設計判断は `docs/adr/` に ADR として記録する

---

## 8. 実装ロードマップ

| # | タスク | 完了条件 |
| --- | --- | --- |
| 0 | ✅ **プロジェクト土台** | Compose healthy / health 200 + DB UP / Gradle check / CI / branch protection |
| 1 | エラーハンドリング基盤 | 独自例外が problem+json に変換されることをテストで確認 |
| 2 | 認証（register / login / JWT） | 登録 201・ログイン 200・不正トークン 401 |
| 3 | ユーザー・ロール | `MANAGER` / `AGENT` の権限差がテストで確認できる |
| 4 | カテゴリ CRUD | 結合テスト green |
| 5 | チケット CRUD | 参照番号の採番を含め結合テスト green |
| 6 | ステータス遷移 | 遷移表の全組み合わせをユニットテストで網羅 |
| 7 | アサイン | 権限別の可否がテストで確認できる |
| 8 | コメント | 可視性の出し分け・初回応答日時の記録 |
| 9 | SLA | 保留時間の除外を含む期限計算のユニットテスト |
| 10 | 検索・ページング | 10 万件投入時のクエリプランを検証 |
| 11 | 集計 API | 応答時間・SLA 遵守率の算出 |
| 12 | 可観測性 | 構造化ログ・追跡 ID・メトリクス |
| 13 | OpenAPI | Swagger UI 公開、operationId の一意性をテスト |
| 14 | デプロイ | CI green → 自動デプロイ、公開 URL で疎通 |
| 15 | ネイティブイメージ（任意） | 起動時間の比較を README に記載 |

各タスク完了時に `docs/task-NN-*.md` として作業メモ
（ゴールと完了条件・設計判断の根拠・つまずいた点と教訓・次タスクへの引き継ぎ・再現コマンド）
を残す。

---

## ライセンス

MIT