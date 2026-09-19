# タスク #0: プロジェクト土台

## ゴールと完了条件

| 完了条件 | 状態 |
|---|---|
| `docker compose up -d --build` でコンテナが healthy になる | 未 |
| `/actuator/health` が 200 を返し、DB 接続が UP | 未 |
| `./gradlew check` が green | 未 |
| CI（Format / Lint / Test / Docker Build）が green | 未 |
| ブランチ保護が有効 | 未 |

## 設計判断の根拠

| 判断 | 理由 | 参照 |
|---|---|---|
| Spring Boot 4.1 / Java 25 | 3.5 系はサポート終了済み | ADR-0002 |
| RFC 9457 でエラーを統一 | 標準仕様 + Spring 組み込み対応 | ADR-0003 |
| 機能優先のパッケージ構成 | 機能追加時のディレクトリ横断を避ける | CONTRIBUTING 1.2 |
| 単体テストと結合テストの SourceSet 分離 | 結合テストの遅さを日常の `test` に持ち込まない | build.gradle.kts |
| 結合テストは Testcontainers（H2 不使用） | 互換 DB で通ったテストが本番で落ちる事故を避ける | CONTRIBUTING 1.5 |
| Spotless は palantir-java-format | google-java-format は新 JDK への追従が遅れることがある | build.gradle.kts |
| JaCoCo の閾値チェックは当面 `enabled = false` | 土台段階ではコードが少なく 70% を満たせない | build.gradle.kts |

## つまずいた点と教訓

<!-- 作業しながら追記する -->

## 次タスクへの引き継ぎ

次はタスク #1（エラーハンドリング基盤の検証）。
`GlobalExceptionHandler` が実際に problem+json を返すことを、
ダミーの Controller を使った結合テストで確認する。

## 再現コマンド

```bash
cp .env.example .env
docker compose up -d --build
curl -fsS localhost:8080/actuator/health | jq .

./gradlew spotlessApply
./gradlew check
```