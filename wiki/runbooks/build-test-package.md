---
title: "Build, Test, Package"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "项目 stack、module map 和 artifact 名称"
  - path: "wiki/features/maven-runtime.md"
    desc: "打入 uber JAR 的 Maven distribution resources"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "使用打包后 JAR 执行持续 impact benchmark"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "正式 versioned distribution、manifest 与 reproducibility gate"
  - path: "wiki/rules/release-versioning.md"
    desc: "Maven build 前的 version/fingerprint contract"
code_refs:
  - path: "pom.xml"
    desc: "Root parent/aggregator"
  - path: "analyzer/pom.xml"
    desc: "Analyzer Surefire、Failsafe、Checkstyle、Plugin resource 与 Shade 配置"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Java 8 Maven Plugin packaging 与 shading"
  - path: "docs/user-manual.md"
    desc: "打包后 CLI 使用手册"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "最终 shaded JAR、真实 command 与 interruption black-box gate"
  - path: "scripts/build-distribution.sh"
    desc: "正式/dev distribution 统一构建入口"
---

# Runbook: Build, Test, Package

## Summary

本 runbook 覆盖 Dependency Analyzer 的编译、unit tests、integration tests、Checkstyle、打包和本地 smoke verification。

<!-- version-contract:start -->
- Analyzer release: `0.1.0`
- Artifact Path Plugin release: `1.0.1`
<!-- version-contract:end -->

本页命令用于日常开发 quality gate。正式交付必须使用 [Version and Distribution](version-and-distribution.md)，不能以单次 `mvn package` 代替 version contract、JDK 8 smoke 和 reproducibility gate。

## Prerequisites

- Java 17 JDK。
- Maven 3.x。
- 真实 `impact` integration/smoke 通过 `TEST_JDK8_HOME` 指向完整 JDK 8；Analyzer 仍由 Java 17 启动。
- integration tests 需要本地 `git` 和可运行 Maven executable；内嵌 runtime 测试不依赖 PATH Maven。

## Commands

所有 command 从 repository root 执行。Reactor 顺序为 root parent、`plugins/`、`plugins/artifact-path-resolver/`、`analyzer/`；最终 CLI path 保持 `target/dependency-analyzer.jar`。

### Checkstyle

```sh
mvn validate
```

### Unit tests

```sh
mvn test
```

### Integration tests 与全量 quality gate

```sh
mvn failsafe:integration-test failsafe:verify
mvn clean verify
```

带真实 JDK 8 `impact` gate：

```sh
TEST_JDK8_HOME=/path/to/jdk8 mvn clean verify
```

### 打包与 Root CLI smoke

```sh
mvn package
java -jar target/dependency-analyzer.jar --help
java -jar target/dependency-analyzer.jar impact --help
java -jar target/dependency-analyzer.jar tree --help
```

正式 distribution：

```sh
TEST_JDK8_HOME=/path/to/jdk8 ./scripts/build-distribution.sh
```

最终 JAR smoke 必须包含真实 `impact`、full-repository `tree`、subdirectory `tree` 与 process interruption recovery；不能只调用 Java command class。
Subdirectory `tree` fixture 必须包含 requested module、同 reactor dependency module 与无关 sibling，并断言 Report 只出现前两者；full-reactor fixture 必须断言全部 active module 使用 `REACTOR_ROOT_SCOPE`。

## Success Criteria

- `mvn validate` 输出 `0 Checkstyle violations`。
- `mvn test` 的 Surefire tests 全部通过。
- `mvn verify` 的 Surefire 和 Failsafe tests 全部通过。
- `target/dependency-analyzer.jar` 存在，manifest `Main-Class` 为 `io.github.dependencyanalysis.cli.DependencyAnalyzerCli`。
- JAR 包含 Maven distribution、`maven-dependency-plugin:3.6.1` 完整 repository archive，以及 Artifact Path Plugin self-contained JAR、consumer POM 和 SHA-512。
- Artifact Path Plugin class major 不超过 `52`；Plugin JAR 不包含 Maven/Resolver implementation class，Jackson Core 已 relocate。
- Maven 3.6.3、3.8.9、3.9.x compatibility suite 均执行 exclusion、mediation、session reuse、artifact type/classifier/SNAPSHOT、Reactor skip 和 JSON contract；Maven 4 不执行。
- JAR 使用 WALA 1.8.0；JDK 8 smoke 覆盖 per-Module scope、CHA、Vanilla 0-1-CFA、`FULL` Reflection 和 MethodHandle extension，并断言 stdout/stderr 不含 `got NEW`。
- `impact` 缺少 JDK 8、传入 JDK 17、缺少 `javac`/`rt.jar` 时 Preflight 返回 exit `1`；`tree` 不受 JDK 8 限制。
- JAR 内 Maven/plugin archive 的实际 SHA-512 与 packaged checksum 一致。
- Packaged JAR 只包含 version contract 指定的 Artifact Path Plugin release；其 `plugin.xml` 声明 `dependencyGraphFileName`，Console 的 preflight expected SHA-512 与 Mojo actual-loaded SHA-512 一致。
- Maven `-X` smoke 显示 `(f) dependencyGraphFileName = ...` 和 `implementation=graphml-v1`，即使 local repository 保留旧 Plugin release 也必须执行 current release。
- 正式 distribution 两次固定 timestamp build 的 CLI JAR SHA-512 一致，并发布 `releaseEligible=true` 的 Schema v1 build manifest。
- Root/两个 subcommand help 列出全部当前 option；无 subcommand或未知 option 返回 usage failure。
- Packaged JAR 中断测试保留已发布 reactor page 与最后一个 `RUNNING x/N` Index。
- Packaged JAR 在空 plugin cache 且无远程 plugin repository 时仍能执行 tree，Console 不出现 `evidence is incomplete`。
- Reactor HTML metadata 使用表格，dependency tree 无交互，依赖冲突表的检索、Module/Scope filter、排序和 10/50/100 分页在 `file://` 下可用。

## Failure Entrypoints

- Compile/test failure：`target/surefire-reports/`。
- Integration failure：`target/failsafe-reports/`。
- Checkstyle failure：Maven console 中的 file/line/check 名称。
- Embedded runtime/plugin failure：校验 `analyzer/src/main/resources/maven/` 下 distribution、Dependency Plugin repository、Artifact Path Plugin consumer POM、attribution 和 SHA-512，以及 `plugins/artifact-path-resolver/target/` 的 Plugin JAR。
- Shade/manifest failure：检查 `analyzer/pom.xml` 的 `maven-shade-plugin` `finalName` 与 `mainClass`。
