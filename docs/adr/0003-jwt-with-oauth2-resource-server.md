# ADR-0003: JWT の発行・検証に Spring Security OAuth2 Resource Server（Nimbus）を使う

- ステータス: Accepted
- 日付: 2026-10-25

## 背景

Task #2 で、登録・ログインと JWT アクセストークンによる認証を入れる（FR-1.1〜1.4）。
完了条件は「登録 201・ログイン 200・不正トークン 401」。
土台の段階で jjwt 0.13.0 を依存に入れてあり、`app.jwt.secret` / `app.jwt.expiration-minutes` の設定もある。
ただし、トークンの検証をどこが担うかはまだ決めていない。
後続の Task #3（ロールによる権限差）と Task #14（AWS へのデプロイ）でも、この認証の仕組みをそのまま使う。

どちらの案でも、次は自前で実装する。

- `/api/auth/register` と `/api/auth/login`
- パスワードの照合
- 401 を problem+json で返す AuthenticationEntryPoint

## 検討した選択肢

### 案 A: jjwt で署名・検証し、自前の認証フィルタを置く

- 利点: 依存の追加がいらない。API が素直で、HS256 なら短く書ける。処理の中身をすべて自分のコードで説明できる
- 欠点:
  - 認証フィルタを自作する。ヘッダの取り出し、`Bearer` 形式の判定、例外の 401 への振り分け、SecurityContext への設定までが自前の責任になり、漏れがそのまま脆弱性になる
  - 標準の部品を使わない理由の説明が要る
  - 外部の IdP に移るときはフィルタを書き直す

### 案 B: `spring-boot-starter-security-oauth2-resource-server`（Nimbus）を使う

- 利点:
  - 検証は Spring Security 標準の `BearerTokenAuthenticationFilter` と `NimbusJwtDecoder` が担い、署名・期限・形式を実績あるコードで確かめられる
  - 発行も同じ系列の `NimbusJwtEncoder` で書けるので、jjwt は不要になる
  - Task #3 で使う部品（`JwtAuthenticationConverter` による権限への変換、`@AuthenticationPrincipal Jwt`、テスト用の `jwt()`）がそろっている
  - Task #14 で外部の IdP（Cognito など）に移るときも、主に設定（`issuer-uri` など）の変更で済む
- 欠点:
  - 依存を入れ替える（starter を追加し、jjwt を削除）
  - Nimbus の API は jjwt より回りくどい（`JwtEncoderParameters`、`JwsHeader` など）
  - 名前に反して OAuth2 のフローは使わず、自前で発行した HS256 トークンを検証するだけの構成になる

## 決定

案 B を採用する。

決め手は、認証のような安全性に直結する部分で、自作のコードを最小にできること。
もう一つは、Task #3 と Task #14 の拡張を、作り直しではなく設定と部品の追加で受けられること。
依存の入れ替えは `build.gradle.kts` と `gradle/libs.versions.toml` の数行で済み、今の段階なら費用は小さい。

## 結果

- jjwt（api / impl / jackson）を依存から外し、`spring-boot-starter-security-oauth2-resource-server` を追加する
- 署名は HS256。鍵は環境変数 `JWT_SECRET`（`app.jwt.secret`）から読み、256 bit 以上を必須にする。足りなければ起動時に失敗させる
- 有効期限は `app.jwt.expiration-minutes`。`sub` にユーザー ID、`role` にロールを入れる
- 401 は自前の AuthenticationEntryPoint で problem+json + `traceId` にそろえる（ADR-0002 の残課題）
- CSRF 保護は無効にする。認証は `Authorization: Bearer` ヘッダだけで、セッションも Cookie も発行しないため、ブラウザが認証情報を自動で送る CSRF の前提が成り立たない。Cookie を発行しないことは結合テスト（`AuthIntegrationTest.noCookieIsIssued`）で検査する。CodeQL の `java/spring-disabled-csrf-protection` は、この理由で誤検知として扱う
- 受け入れた欠点: 鍵は単一の共有秘密で、鍵のローテーションは扱わない
- 見直す条件: 外部の IdP を使うことになったとき、または複数のサービスでトークンを検証することになったときは、RS256 と JWKS 公開に切り替える。その際は新しい ADR を書く。Cookie で認証する機能（ブラウザ向けの画面など）を入れるときは、CSRF 保護を有効にし直す

## 参照

- README 2.4 FR-1、4.2 エラーレスポンス
- ADR-0002（フィルター層のエラーを problem+json にそろえる残課題）
- Issue #29（Task #2）
- Spring Security Reference: OAuth 2.0 Resource Server — JWT
