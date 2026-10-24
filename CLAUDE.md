# CLAUDE.md

AI エージェント（Claude Code など）向けの作業ガイド。人向けの詳細は README.md と CONTRIBUTING.md を正とする。

## プロジェクト

問い合わせ（チケット）管理 API。転職用ポートフォリオ。

- Java 25 / Spring Boot 4.1（MVC + 仮想スレッド）/ PostgreSQL 17 / Flyway / Gradle Kotlin DSL
- ベースパッケージ: `com.caltdeepl.helpdesk`（機能優先: 各機能の下に `controller` / `service` / `repository` / `domain`）
- デプロイ予定: AWS（Terraform）。Task #14 で実施

## よく使うコマンド

```bash
./gradlew spotlessApply          # 整形（コミット前に必ず）
./gradlew check                  # spotlessCheck + Checkstyle + 単体テスト + 結合テスト
./gradlew test                   # 単体テスト（src/test、DB 不要）
./gradlew integrationTest        # 結合テスト（src/integrationTest、Testcontainers で実 PostgreSQL）
docker compose up -d --build     # API(8082) + PostgreSQL(5435)
curl -fsS http://localhost:8082/actuator/health
```

ローカルのポート: API `8082`、PostgreSQL `5435`（ホスト）/ `5432`（コンテナ内）。

## 守ること

- 業務エラーは `AppException` + `ErrorCode` で投げる。`RuntimeException` / `Exception` を直接 throw しない（Checkstyle で禁止）
- エラー応答は RFC 9457（`application/problem+json`）。5xx では内部情報を返さない
- 日時は `Instant`（UTC）。`java.util.Date` / `Calendar` / `java.sql.Timestamp` は使わない
- ログは SLF4J。`System.out` と `printStackTrace()` は使わない
- スキーマ変更は Flyway のマイグレーション（`src/main/resources/db/migration/V{n}__{説明}.sql`）だけで行う
- レイヤーの依存は controller → service → repository の一方向（ArchUnit で検査）

## 作業の進め方

- タスクは GitHub Issue（ラベル `task`）で管理する。着手前に対応する Issue を確認する
- ブランチ → PR → CI → squash マージ。`main` へ直接 push しない
- コミットは Conventional Commits（`feat:` / `fix:` / `docs:` / `test:` / `refactor:` / `chore:`）
- PR 本文に `Closes #NN` を書く
- タスク完了時は Issue の「作業メモ」欄を埋め、README の「現在の状態」とロードマップを更新する
- 設計判断は `docs/adr/NNNN-短い説明.md` に ADR として残す（テンプレート: `docs/adr/0000-template.md`）

## やってはいけないこと

- `.env` や秘密情報（パスワード・JWT_SECRET・トークン）をコミット・出力しない。必要なら「どこに置いたか」だけを書く
- `main` の Ruleset を変更しない、force push しない
- 確認なしに Issue・PR の作成や GitHub の設定変更をしない
