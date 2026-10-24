# ADR-0002: Micrometer 導入前に W3C 形式の trace-id を自前発行する

- ステータス: Accepted
- 日付: 2026-10-24

## 背景

Task #1 でエラー応答を RFC 9457 の problem+json に揃え、ログと突き合わせられる追跡 ID を付ける必要がある。
README 4.2 は W3C Trace Context の 32 桁 hex `trace-id` を想定している。
一方、構造化ログと Micrometer Tracing は Task #12 の範囲である。

## 検討した選択肢

### 案 A: 今回 Micrometer Tracing + Brave を入れる

- 利点: 本番と同じ伝播形式を最初から使える
- 欠点: Task #12 と範囲が重なる。依存と設定が増え、エラー変換の検証が主役ではなくなる

### 案 B: リクエストごとに UUID を発行する

- 利点: 実装が短い
- 欠点: README 4.2 の W3C 形式と合わない。後で Micrometer に載せ替えるとき応答の形が変わる

### 案 C: W3C の 32 桁 hex を自前発行し、`traceparent` があればその trace-id を使う

- 利点: 応答の `traceId` の形が Task #12 以降も変わらない。依存を増やさない
- 欠点: span-id やサンプリングは持たない。完全な分散トレースではない

## 決定

案 C を採用する。MDC キーは `traceId`。problem+json のすべてのエラー応答に同じ値を載せる。
5xx では内部情報を返さない。4xx では原因が分かる detail を返しつつ `traceId` も付ける。

## 結果

- `TraceId` / `TraceIdFilter` が追跡 ID の発行と MDC への載せる処理を担う
- Task #12 で Micrometer Tracing に切り替える。そのとき応答フィールド名と 32 桁 hex の形は維持する
- コンソールログは `%X{traceId:-}` で突き合わせる。JSON 構造化は Task #12

## 参照

- README 4.2 エラーレスポンス
- NFR-3.1（リクエスト単位の追跡 ID）
- Issue #28（Task #1）
