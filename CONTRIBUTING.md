# コーディング規約と開発ワークフロー

このドキュメントは helpdesk-api の開発ルールを定める。
**「人が気をつける」ルールは書かない。原則としてツールで自動強制できるものだけを規約とし、
強制できないものは理由つきで明文化する。**

---

## 1. コーディング規約

### 1.1 自動強制されるもの

| ルール | 強制するツール | 実行コマンド |
|---|---|---|
| インデント・改行・空白・import 順 | Spotless (palantir-java-format) | `./gradlew spotlessApply` |
| 命名規約・禁止 API・複雑度 | Checkstyle | `./gradlew checkstyleMain` |
| レイヤー間の依存方向 | ArchUnit | `./gradlew test` |
| 文字コード・改行コード | .editorconfig | エディタが適用 |
| 依存ライブラリの脆弱性 | OWASP Dependency-Check | `./gradlew dependencyCheckAnalyze` |

**フォーマットについて議論しない。** Spotless の出力が正であり、
好みの差は `spotlessApply` を実行して終わりにする。

### 1.2 パッケージ構成

機能優先（package by feature）を採用する。レイヤー優先（`controller/` `service/` を
トップに置く方式）は、機能追加のたびに複数ディレクトリを横断することになるため採らない。

```
com.caltdeepl.helpdesk
├── HelpdeskApplication.java
├── common/                  # 機能をまたいで共有するもののみ
│   ├── config/
│   ├── error/
│   └── security/
├── auth/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── dto/
├── user/
├── ticket/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/              # 状態遷移などの純粋なドメインロジック
│   ├── entity/
│   └── dto/
└── analytics/
```

`common` は「2 つ以上の機能から使われることが確定してから」置く。
最初から共通化しない。

### 1.3 命名

| 対象 | 規約 | 例 |
|---|---|---|
| クラス | パスカルケース | `TicketService` |
| メソッド・変数 | キャメルケース | `assignTo` |
| 定数 | 大文字スネークケース | `MAX_PAGE_SIZE` |
| パッケージ | 全小文字 | `com.caltdeepl.helpdesk.ticket` |
| Controller | `Xxx Controller` | `TicketController` |
| Service | `XxxService` | `TicketService` |
| Repository | `XxxRepository` | `TicketRepository` |
| リクエスト DTO | `XxxRequest` | `CreateTicketRequest` |
| レスポンス DTO | `XxxResponse` | `TicketResponse` |
| エンティティ | 単数形の名詞 | `Ticket` |
| 単体テスト | `XxxTest` | `TicketStatusTest` |
| 結合テスト | `XxxIT` | `TicketApiIT` |

### 1.4 設計ルール（Checkstyle で強制できないもの）

#### DTO とエンティティを分ける

- エンティティ（`@Entity`）を Controller 層に露出させない。ArchUnit で検証する
- DTO は `record` を使い、不変にする

```java
// Good
public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank String body,
        @NotNull Priority priority) {}
```

#### null を返さない

- 単一の値が存在しない可能性がある場合は `Optional<T>` を**戻り値にのみ**使う
- 引数・フィールドに `Optional` を使わない（Checkstyle で禁止済み）
- コレクションは `null` ではなく空リストを返す

#### 例外

- `RuntimeException` や `Exception` を直接 throw しない（Checkstyle で禁止済み）
- 業務エラーは `AppException` を使い、`ErrorCode` で分類する
- 例外を握りつぶさない。catch したら必ずログに出すか再送出する
- `printStackTrace()` 禁止。ロガーに例外オブジェクトを渡す

```java
// Good
throw AppException.notFound("チケット");
throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
        "%s から %s へは遷移できません".formatted(from, to));
```

#### 日時

- `java.util.Date` / `Calendar` / `java.sql.Timestamp` を使わない（Checkstyle で禁止済み）
- アプリ内は `Instant`（UTC）で扱い、表示のみ `ZonedDateTime` に変換する
- DB は `timestamptz`

#### ログ

- SLF4J を使う。`System.out` 禁止（Checkstyle で強制済み）
- パラメータ化ログを使い、文字列連結しない

```java
// Good
LOG.info("チケットを作成しました ticketId={} priority={}", ticket.id(), ticket.priority());

// Bad
LOG.info("チケットを作成しました ticketId=" + ticket.id());
```

- **パスワード・トークン・パスワードハッシュをログに出さない**
- エンティティ全体を `toString()` でログに出さない（意図せず秘匿情報が混ざる）

#### トランザクション

- `@Transactional` は Service 層にのみ付ける。Controller / Repository には付けない
- 読み取り専用のメソッドには `@Transactional(readOnly = true)` を付ける
- `spring.jpa.open-in-view=false` を前提とする。Controller 層で遅延ロードしない

#### データアクセス

- 単純な CRUD は Spring Data JPA
- 集計・複雑な検索は `JdbcClient` で生 SQL を書く（JPQL で無理をしない）
- N+1 を避ける。一覧取得で関連を辿る場合は `@EntityGraph` か JOIN FETCH を使う

### 1.5 テスト

| 種類 | 置き場所 | 実行 | 方針 |
|---|---|---|---|
| 単体テスト | `src/test` | `./gradlew test` | DB 不要。ドメインロジックを網羅する |
| アーキテクチャテスト | `src/test` | `./gradlew test` | ArchUnit |
| 結合テスト | `src/integrationTest` | `./gradlew integrationTest` | Testcontainers で実 PostgreSQL |

- テストメソッド名は `@DisplayName` に日本語で「何を検証しているか」を書く
- アサーションは AssertJ に統一する（`assertEquals` より意図が読める）
- **H2 などの互換 DB は使わない。** PostgreSQL 固有の機能を使っているため、
  互換 DB で通ったテストが本番で落ちる事故を避ける
- 1 テスト 1 検証。複数の関心事を 1 メソッドに詰め込まない

### 1.6 コメント

- 「何をしているか」は書かない（コードを読めば分かる）
- 「なぜそうしたか」を書く
- 大きな設計判断は ADR に書き、コードからは ADR 番号を参照する

```java
// Good: なぜ
// 保留中の経過時間は SLA から除外する（FR-6.3）。
// 期限そのものを更新せず、判定時に差し引くことで履歴の再計算を可能にしている。

// Bad: 何を
// status が PENDING かどうかを判定する
```

- `TODO` は許可するが、`FIXME` は Checkstyle でビルドエラーにする
  （「あとで直す」をコミットしないため）

---

## 2. 開発ワークフロー

### 2.1 ブランチ戦略

GitHub Flow を採用する。個人開発でも **必ず PR を経由**する。

```
main（保護ブランチ・常にデプロイ可能）
 ├── feat/ticket-status-transition
 ├── fix/jwt-expiration-handling
 ├── docs/adr-0003-sla-calculation
 └── chore/bump-spring-boot
```

| プレフィックス | 用途 |
|---|---|
| `feat/` | 機能追加 |
| `fix/` | バグ修正 |
| `refactor/` | 挙動を変えない改善 |
| `docs/` | ドキュメント |
| `chore/` | ビルド・依存・CI |
| `test/` | テストのみの変更 |

### 2.2 コミットメッセージ

Conventional Commits に従う。

```
<type>(<scope>): <日本語の要約>

<本文: なぜこの変更が必要かを書く。任意>
```

| type | 用途 |
|---|---|
| `feat` | 機能追加 |
| `fix` | バグ修正 |
| `refactor` | リファクタリング |
| `perf` | 性能改善 |
| `test` | テスト |
| `docs` | ドキュメント |
| `build` | ビルド設定 |
| `ci` | CI 設定 |
| `chore` | その他 |

例:

```
feat(ticket): ステータス遷移 API を追加

遷移表をドメイン層に閉じ込め、不正な遷移は 422 を返すようにした。
DB の CHECK 制約では遷移理由を扱えないため、アプリ側で制御する。
```

- 1 コミット 1 目的。整形だけの変更は分離する
- WIP コミットはローカルで潰してから push する

### 2.3 ブランチ保護（GitHub Ruleset）

`main` に対して以下を設定する。

| 設定 | 値 |
|---|---|
| 直接 push | 禁止 |
| PR 必須 | はい |
| 必須ステータスチェック | `Format` / `Lint` / `Test` / `Docker Build` |
| ブランチを最新にしてからマージ | 必須 |
| マージ方式 | Squash merge のみ |
| マージ後のブランチ削除 | 自動 |
| force push | 禁止 |

> `Audit`（脆弱性検査）は NVD の取得失敗で不安定になりうるため、必須チェックには含めず
> 失敗時に確認する運用とする。

### 2.4 CI パイプライン

`.github/workflows/ci.yml` が以下を実行する。

| ジョブ | 内容 | 落ちる条件 |
|---|---|---|
| `Format` | `spotlessCheck` | 整形されていない |
| `Lint` | `checkstyleMain` / `checkstyleTest` | 警告が 1 件でもある |
| `Test` | `test` → `integrationTest` → `jacocoTestReport` | テスト失敗・カバレッジ不足 |
| `Audit` | `dependencyCheckAnalyze` | CVSS 7.0 以上の脆弱性 |
| `Docker Build` | イメージビルド | ビルド失敗 |

### 2.5 依存の更新

Dependabot が毎週月曜にまとめて PR を作る。

- Spring 系・テスト系はグループ化して 1 PR にまとめる
- マイナー・パッチは CI が green なら基本マージ
- メジャー更新は移行手順を確認し、ADR を書いてから対応する

### 2.6 リリース

```bash
git tag -a v0.2.0 -m "ステータス遷移 API を追加"
git push origin v0.2.0
```

タグを打つと CHANGELOG を生成する（Conventional Commits を守っていることが前提）。

### 2.7 設計判断の記録（ADR）

技術選定・データモデル・API 契約に関わる判断は `docs/adr/` に残す。

- ファイル名: `NNNN-短い説明.md`（例: `0003-sla-calculation-strategy.md`）
- テンプレートは `docs/adr/0000-template.md`
- 一度 `Accepted` にした ADR は書き換えず、新しい ADR で `Superseded` にする

### 2.8 タスクごとの作業メモ

ロードマップの各タスク完了時に `docs/task-NN-*.md` を作成する。含める内容:

1. ゴールと完了条件
2. 設計判断の根拠
3. つまずいた点と教訓
4. 次タスクへの引き継ぎ
5. 再現コマンド

これは学習記録であると同時に、**職務経歴書・面接で語る材料の一次ソース**になる。
記憶が新しいうちに書く。

---

## 3. 開発環境

### IntelliJ IDEA の推奨設定

| 設定 | 値 |
|---|---|
| Project SDK | 25 (Temurin) |
| Gradle JVM | Project SDK |
| ビルド・実行 | Gradle に委譲 |
| EditorConfig | 有効（`.editorconfig` を自動適用） |
| Actions on Save | Reformat code / Optimize imports を **オフ** |

> 保存時の自動整形はオフにする。IDE の整形結果と Spotless の結果が食い違うと
> 差分が発散するため、整形は `./gradlew spotlessApply` に一本化する。

### 推奨プラグイン

| プラグイン | 用途 |
|---|---|
| Checkstyle-IDEA | `config/checkstyle/checkstyle.xml` を IDE 上で適用 |
| Conventional Commit | コミットメッセージの補助 |
| .env files support | `.env` の補完 |

### コミット前に必ず実行

```bash
./gradlew spotlessApply check
```
