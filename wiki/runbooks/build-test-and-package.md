---
name: "Build Test and Package"
type: runbook
---

## Purpose and Scope

本 Runbook 从 repository root 构建 [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md)、[JDK Method Models](../c4/components/dependency-analyzer-cli-jdk-method-models.md) 与 [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md)，并先产出可交付 artifact，再完成 Maven、CLI 与 Offline Report quality gates。日常验证不修改 version，也不执行 benchmark。

## Prerequisites

- Maven command 使用 Java 17 JDK 与 Maven 3.x。
- 按下方本机配置步骤设置完整 JDK 8；必须包含 `bin/java`、`bin/javac` 和 `jre/lib/rt.jar`。
- Integration tests 可调用 local `git` 与 Maven executable。
- Browser gate 使用 Node.js 20 或更高版本、npm 与 Playwright Chromium。
- 所有 command 从 repository root 执行。

本机首次配置：复制 `.env.example` 为 `.env`，将 `TEST_JDK8_HOME` 改为完整 JDK 8 的绝对路径。`.env` 已被 Git 忽略，真实路径不得写入受跟踪文件。

```sh
cp .env.example .env
```

`./mvn-local` 在仓库根目录执行 Maven，并将参数原样传递。它把 `.env` 作为 Bash 配置加载并导出变量，因此文件应只包含可信的本机配置；路径含空格时使用引号。配置优先级为命令行 `-Dtest.jdk8.home`、`.env` 中的 `TEST_JDK8_HOME`、调用环境中的 `TEST_JDK8_HOME`。没有 `.env` 时仍可使用环境变量或命令行参数。

直接运行 `mvn` 不加载 `.env`；需提前导出 `TEST_JDK8_HOME` 或传入 `-Dtest.jdk8.home`。脚本需要 Bash 与 PATH 中的 Maven；Windows 可在 Bash 环境使用，或直接为 Maven 提供配置。

## Procedure

1. 安装公共 JDK model engine。

   ```sh
   ./mvn-local -f models/jdk/pom.xml -DskipTests clean install
   ```

2. 安装 JDK 8 model artifact。

   ```sh
   ./mvn-local -f models/jdk8/pom.xml -DskipTests clean install
   ```

3. 安装 Dependency Evidence Plugin 与 attached repository ZIP。

   ```sh
   ./mvn-local -f plugins/pom.xml -DskipTests clean install
   ```

4. 在执行完整 tests 前，先产出可交付 Analyzer artifact。

   ```sh
   ./mvn-local -DskipTests package
   ```

5. 确认 executable JAR 的基本入口可用。

   ```sh
   java -jar target/dependency-analyzer.jar --version
   java -jar target/dependency-analyzer.jar --help
   ```

6. 交付 JAR 后验证公共 JDK model engine，补齐前置安装跳过的测试。

   ```sh
   ./mvn-local -f models/jdk/pom.xml verify
   ```

7. 验证 JDK 8 model。

   ```sh
   ./mvn-local -f models/jdk8/pom.xml verify
   ```

8. 验证 Dependency Evidence Plugin。

   ```sh
   ./mvn-local -f plugins/pom.xml verify
   ```

9. 执行 Analyzer Maven quality gate。

   ```sh
   ./mvn-local verify
   ```

10. 首次执行或 lockfile 变化后安装 Node.js dependency 与 Chromium。

    ```sh
    npm ci
    npx playwright install chromium
    ```

11. 在 Maven fixture 已生成后执行 Offline Report browser gate。

    ```sh
    npm run test:report
    ```

12. 验证全部 public command help。

    ```sh
    java -jar target/dependency-analyzer.jar impact --help
    java -jar target/dependency-analyzer.jar tree --help
    java -jar target/dependency-analyzer.jar tree analyze --help
    java -jar target/dependency-analyzer.jar tree diff --help
    ```

## Success Criteria

- Plugin tests 与 Checkstyle 通过；Plugin class major 不超过 `52`，attached repository ZIP 包含当前 Maven layout artifacts。
- `target/dependency-analyzer.jar` 存在，manifest `Main-Class` 为 `io.github.dependencyanalysis.cli.DependencyAnalyzerCli`。
- Analyzer Surefire、Failsafe、Checkstyle 与 ArchUnit 均通过且无 skip。
- JAR 包含公共 model engine、JDK 8 façade/catalog，以及 Maven Dependency Plugin 与 Dependency Evidence Plugin 两个 repository ZIP。
- `target/playwright-report-fixture/` 包含 Impact、Tree Analyze 与 Tree Diff fixture；Playwright 两个 viewport project 全部通过，且无 HTTP/HTTPS request、page error 或异常 Console error。
- Packaged JAR 能生成三个真实 Report，所有本地 `href`/`src` 留在 report root，Tree Index 至少到达一个 Reactor page。
- Root 与全部 subcommand help 展示当前 option；直接执行 `tree` 输出帮助并返回 `1`。

## Failure Entry Points

- `test.jdk8.home must point to a complete JDK 8`：修正 `.env` 中的 `TEST_JDK8_HOME`，使用 `./mvn-local`，或传入 absolute `-Dtest.jdk8.home`；不得使用 JRE 或 Java 17 home。
- Plugin repository ZIP resolution failure：重新执行 `./mvn-local -f plugins/pom.xml -DskipTests clean install`，确认 root `artifact-path-plugin.version` 一致。
- JDK model resolution failure：按公共 engine、JDK 8 model 顺序执行 `clean install`，确认 root `jdk8-models.version` 一致。
- Analyzer unit/integration failure：检查 `target/surefire-reports/` 与 `target/failsafe-reports/`，停止后续 browser gate。
- Playwright fixture missing：先完成 `./mvn-local verify`；浏览器缺失时执行 `npx playwright install chromium`。
- Playwright failure：检查 `target/playwright/html-report/` 与 `target/playwright/test-results/`。
- Shade/manifest failure：检查 `analyzer/pom.xml` 的 `finalName` 与 `mainClass`。
