## 対応タスク / 要件

<!-- 例: Task #1 / FR-2.1 / NFR-4.2。該当しない場合は「なし」。 -->

- Task:
- Requirement:

## 目的

<!-- 何を解決する PR か。実装詳細ではなく、変更理由を書く。 -->

## 変更内容

<!-- 主要変更を箇条書きで。 -->

-

## 設計判断

<!--
なぜこの実装方法を選んだか。
検討したが採用しなかった案と理由も残す。
大きな判断は docs/adr/ に ADR として切り出す。
-->

- 採用:
- 却下した案と理由:
- 関連 ADR:

## 確認方法

<!-- レビュアー（将来の自分）が再現できるコマンドと期待結果を書く。 -->

```bash
./gradlew spotlessCheck
./gradlew check

# Docker / DB / Actuator に影響する変更の場合
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8082/actuator/health | jq .
```

期待結果:

-

## 影響範囲・リスク

<!-- API 互換性、DB migration、認証・認可、性能、運用、環境変数など。 -->

- API:
- DB:
- Security:
- Performance:
- Operations / Environment:

## ドキュメント / 運用変更

<!-- README、task doc、ADR、OpenAPI、.env.example、runbook など。 -->

- [ ] 該当なし
- [ ] `README.md` を更新した
- [ ] `docs/task-NN-*.md` を更新した
- [ ] 必要な ADR を追加・更新した
- [ ] `.env.example` を更新した
- [ ] OpenAPI / API ドキュメントを更新した

## チェックリスト

- [ ] `./gradlew spotlessApply` または `spotlessCheck` を実行した
- [ ] `./gradlew check` が green
- [ ] 新規・変更したロジックに必要なテストを追加した
- [ ] DB 結合が必要な変更では `./gradlew integrationTest` を確認した
- [ ] Docker 構成に影響する変更では `docker compose up -d --build` を確認した
- [ ] Actuator / DB に影響する変更では `/actuator/health` の DB `UP` を確認した
- [ ] API を変更した場合、OpenAPI の記述・生成物を更新した
- [ ] DB を変更した場合、Flyway migration を追加し適用を確認した
- [ ] 秘密情報をコード・ログ・fixture・`.env.example` に含めていない
- [ ] 設計判断をこの PR に記載した（必要なら ADR を追加した）
- [ ] 対応 Task の完了条件を満たしている