# VS Code extensions

`extensions.json` は helpdesk-api の基本構成に必要性が高いものだけを推奨する。

- Extension Pack for Java
  - Java language support
  - debugger
  - JUnit
  - Gradle

- Spring Boot Extension Pack
  - Spring Boot Tools
  - Spring Boot Dashboard
  - Spring Initializr

- YAML
  - `application.yaml`
  - `compose.yaml`
  - GitHub Actions YAML
  の補完・検証に使用する。

- Container Tools
  - Dockerfile
  - Docker Compose
  - コンテナ・イメージ管理
  に使用する。

- Checkstyle for Java
  - `config/checkstyle/` と IDE の診断を合わせる。

## 任意追加候補

### SonarQube for IDE

Extension ID:

```text
sonarsource.sonarlint-vscode
```

IDE 上で追加の静的解析を行いたい場合に追加する。

CI の Spotless / Checkstyle / OWASP Dependency-Check の代替ではなく、
ローカル開発時の補助として利用する。

### Docker DX

Extension ID:

```text
docker.docker
```

Dockerfile / Compose の編集支援をさらに強化したい場合に追加する。

Container Tools と機能が重なる部分があるため、必須にはしない。