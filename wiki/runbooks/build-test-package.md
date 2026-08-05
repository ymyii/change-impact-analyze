---
title: "Build, Test, Package"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "项目 stack、双 reactor module map 和 artifact 名称"
  - path: "wiki/features/maven-runtime.md"
    desc: "打入 uber JAR 的 Maven distribution 与两个 repository ZIP"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "使用打包后 JAR 执行持续 impact benchmark"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Stable version、release profile、commit 与 tag 操作"
  - path: "wiki/rules/release-versioning.md"
    desc: "独立 SemVer、Snapshot 复用与 release gate"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer parent/aggregator、Enforcer 与 shared build management"
  - path: "analyzer/pom.xml"
    desc: "Analyzer Surefire、Failsafe、repository ZIP copy 与 Shade 配置"
  - path: "plugins/pom.xml"
    desc: "独立 Plugin parent/aggregator、Java 8 与 Enforcer"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Maven Plugin、shading、flatten 与 repository ZIP packaging"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "最终 shaded JAR 与真实 command black-box gate"
---

# Runbook: Build, Test, Package

## Summary

本 runbook 覆盖两个独立 Maven reactor 的本地开发、Plugin bootstrap、Checkstyle、unit/integration tests、打包和 CLI smoke。日常验证不修改 version、不要求 Git commit。

## Prerequisites

- Maven JVM 使用 Java 17 JDK。
- Maven 3.x。
- `TEST_JDK8_HOME` 指向完整 JDK 8 root；必须存在 `bin/java`、`bin/javac`、`jre/lib/rt.jar`，且 probe 结果为 Java 8。
- Integration tests 需要本地 `git` 和可运行 Maven executable。
- 所有 command 从 repository root 执行。

## Commands

### Plugin reactor

Plugin 第一次 bootstrap 或 Plugin source/package 变化后执行：

```sh
mvn -f plugins/pom.xml clean install
```

该 reactor 使用 `maven.compiler.release=8`，但 Maven command 本身不要求在 JDK 8 上运行。输出包括：

```text
plugins/artifact-path-resolver/target/
├── dependency-analyzer-artifact-path-maven-plugin-<version>.jar
└── dependency-analyzer-artifact-path-maven-plugin-<version>-repository.zip
```

Attached `repository` ZIP 同时安装到 Maven local repository。

### Analyzer reactor

完整 quality gate：

```sh
TEST_JDK8_HOME=/absolute/path/to/jdk8 mvn clean verify
```

只运行 unit tests：

```sh
TEST_JDK8_HOME=/absolute/path/to/jdk8 mvn test
```

只打包仍会编译 tests，因此同样必须提供 JDK 8：

```sh
TEST_JDK8_HOME=/absolute/path/to/jdk8 mvn package
```

Analyzer reactor 不构建 `plugins/`。若 local repository 没有 `artifact-path-plugin.version` 对应的 `repository` ZIP，dependency resolution 必须失败；先执行 Plugin `clean install`。

### CLI smoke

```sh
java -jar target/dependency-analyzer.jar --version
java -jar target/dependency-analyzer.jar --help
java -jar target/dependency-analyzer.jar impact --help
java -jar target/dependency-analyzer.jar tree --help
```

## Success Criteria

- Plugin reactor 输出 `0 Checkstyle violations`，tests 全部通过，Plugin class major 不超过 `52`。
- Plugin repository ZIP 只有 Maven layout 下当前 version 的 JAR 与 consumer POM，不包含项目生成的 checksum sidecar。
- Analyzer `mvn clean verify` 的 Surefire 与 Failsafe tests 全部通过且 `Skipped: 0`。
- 真实 JDK 8 test 完成 JDK probe、WALA scope、CHA 与 Vanilla 0-1-CFA，不因缺少环境变量跳过。
- `target/dependency-analyzer.jar` 存在，manifest `Main-Class` 为 `io.github.dependencyanalysis.cli.DependencyAnalyzerCli`。
- Analyzer JAR 在 `maven/plugin-repositories/` 下恰有 Maven Dependency Plugin 与 Artifact Path Plugin 两个 `repository` ZIP；不存在旧 loose Artifact Path Plugin JAR/POM/checksum resource。
- Empty Maven local repository + blocked wildcard mirror 下，packaged runtime 可执行 `dependency:tree + resolve-artifact-paths`。
- 最新 duplicate class precedence tests、report tests 与 `PackagedJarCliIT` 全部通过。
- Root/两个 subcommand help 列出当前 option；CLI `--version` 与 Maven build metadata 一致。

## Failure Entrypoints

- `TEST_JDK8_HOME` missing/file failure：确认变量是 absolute JDK 8 root，不是 JRE 或 Java 17 home。
- Artifact Path Plugin repository ZIP resolution failure：重新执行 `mvn -f plugins/pom.xml clean install`，确认 Analyzer property 与 installed version 一致。
- Plugin test failure：`plugins/artifact-path-resolver/target/surefire-reports/`。
- Analyzer unit/integration failure：`target/surefire-reports/`、`target/failsafe-reports/`。
- Checkstyle failure：Maven Console 中的 file/line/check 名称。
- Shade/manifest failure：检查 `analyzer/pom.xml` 的 `maven-shade-plugin` `finalName` 与 `mainClass`。
- Embedded repository failure：检查 Analyzer JAR 中 `maven/plugin-repositories/` 的两个 ZIP 和 runtime manager 的必要文件路径。

## Configuration

- Analyzer development version：root `revision`。
- Artifact Path Plugin development version：`plugins/pom.xml` 的 `revision`。
- Analyzer 使用的 Plugin version：root `artifact-path-plugin.version`。
- 详细 release 切换见 [Version and Distribution](version-and-distribution.md)。
