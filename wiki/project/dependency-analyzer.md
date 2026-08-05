---
title: "Dependency Analyzer"
type: project
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Root CLI、共享基础设施和两条分析 pipeline 的架构边界"
  - path: "wiki/features/maven-runtime.md"
    desc: "内嵌 Maven 与两个 Plugin repository 的 runtime 行为"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "外部命令执行的跨平台约束"
  - path: "wiki/rules/release-versioning.md"
    desc: "两个 Maven reactor 的独立 SemVer 与 Git tag 约束"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包和本地验证操作"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Maven-native version iteration 与 release 操作"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "打包后 impact 的持续性能与场景完整性验证"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer parent/aggregator、SemVer 和 release profile"
  - path: "analyzer/pom.xml"
    desc: "Java 17 Analyzer、测试、repository ZIP copy 与 uber JAR 配置"
  - path: "plugins/pom.xml"
    desc: "独立 Plugin parent/aggregator、Java 8 与 release profile"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Artifact Path Maven Plugin 与 repository ZIP packaging"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI 和 global options 入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Spring backend per-Module impact pipeline 编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "tree pipeline 编排"
  - path: "benchmarks/impact-medium/run-benchmark.sh"
    desc: "Git 管理的 impact benchmark 总入口"
---

# Project: Dependency Analyzer

## Summary

Dependency Analyzer 是 Java 17 + Maven + picocli CLI。`impact` 使用显式完整 JDK 8 比较 dependency 升级前后的 bytecode 与业务调用影响；`tree` 扫描 Git repository 内的 Maven reactor，生成 repository 级 offline HTML dependency tree report。

Source repository 包含两个独立 Maven reactor：root reactor 只构建 Analyzer，`plugins/pom.xml` reactor 只构建内置 Maven Plugin。两者通过 Maven local repository 交付，不建立同一 reactor 内的 module dependency。

## Design Decisions

- Analyzer `2.0.0` 与 Artifact Path Plugin `2.1.0` 是新的稳定 release 起点，之后继续独立使用 SemVer。
- 日常开发在下一次 release 前复用同一个 `X.Y.Z-SNAPSHOT`；只有 release 决策才切换 stable version、commit 并创建 Git tag。
- Artifact Path Plugin 以 Java 8 bytecode 发布；执行 Plugin reactor 的 Maven JVM 可以使用 Java 8 以上版本。Analyzer 使用 Java 17 构建和运行，完整 `TEST_JDK8_HOME` 是 Analyzer quality gate 的强制输入。
- Analyzer JAR 将 Maven Dependency Plugin 和 Artifact Path Plugin 统一内嵌为两个独立 Maven repository ZIP；runtime 不安装 loose JAR/POM，也不维护项目自有 checksum/fingerprint。
- `impact` 以 GraphML 作为唯一 mediation authority；Artifact Path Plugin Schema v2 JSON 只向 command-scoped `IJarRepository` ingestion selected dependency physical binding，后续 domain 只保存 coordinate；`tree` 使用 verbose text 采集完整 dependency occurrence。
- `impact` 只构建 target per-Module Vanilla 0-1-CFA；baseline 不 compile、不构建 Call Graph。
- 两个 subcommand 共享 Maven runtime 和 preflight Schema，但分别组装检查 DAG；pipeline 只消费 preflight decision。

## Module Map

- `pom.xml` - Analyzer reactor parent/aggregator，只包含 `analyzer/`。
- `analyzer/` - GAV `io.github.dependencyanalysis:dependency-analyzer:2.0.0`；Java 17 CLI/application，兼容输出为 `target/dependency-analyzer.jar`。
- `analyzer/src/main/java/io/github/dependencyanalysis/runtime/` - 内嵌或用户指定 Maven runtime、两个 Plugin repository cache 与 settings overlay。
- `analyzer/src/main/java/io/github/dependencyanalysis/preflight/` - DAG preflight framework 和结果 Schema。
- `analyzer/src/main/java/io/github/dependencyanalysis/impact/` - `impact` command、pipeline 和影响追踪 domain。
- `analyzer/src/main/java/io/github/dependencyanalysis/tree/` - Git snapshot、reactor inventory、dependency collection、version analysis 和 HTML report。
- `analyzer/src/main/java/io/github/dependencyanalysis/{build,dependency,bytecode,callgraph,jar,report,workspace}/` - `impact` pipeline 的稳定阶段实现。
- `analyzer/src/test/java/` - unit tests；`analyzer/src/integration-test/java/` - Failsafe integration tests。
- `plugins/pom.xml` - 独立 Plugin reactor parent/aggregator。
- `plugins/artifact-path-resolver/` - GAV `io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:2.1.0`；Java 8 `resolve-artifact-paths` goal 与 attached `repository` ZIP。
- `benchmarks/impact-medium/` - 可复现的中型 impact fixture、资源采样与报告 contract verification。

## Technical Stack

- Analyzer runtime/build 为 Java 17；`impact` target runtime 与 Analyzer tests 使用完整 JDK 8。
- Maven 3.x；应用默认内嵌 Apache Maven 3.6.3 runtime。
- picocli 4.7.6、ASM/ASM Tree 9.7、WALA 1.8.0、Vineflower 1.12.0 slim、JUnit 5、AssertJ。
- maven-shade-plugin 生成 uber JAR；maven-assembly-plugin 生成 Artifact Path Plugin repository ZIP。

## Main Entrypoints

- `DependencyAnalyzerCli.main()` - JVM 和 Root CLI 入口。
- `ImpactCommand.call()` - dependency upgrade impact 分析入口。
- `TreeCommand.call()` - repository dependency tree report 入口。
- `plugins/pom.xml` - Plugin bootstrap/install 入口。
- `pom.xml` - Analyzer build/test/package 入口。

## Repository Conventions

- Plugin source 变化后，先执行 Plugin reactor `clean install`，再执行 Analyzer reactor；不得依赖旧 local Snapshot。
- 所有外部 process token 都先经过 `CommandResolver.resolve()`，并使用 `ProcessBuilder`，不经过 shell 拼接。
- 用户 Maven argument 必须逐 token 传入，禁止覆盖工具控制的 POM、module selection、output、verbose 和 token 参数。
- `tmp-files/` 仅用于临时产物，不提交 Git。
- wiki 描述当前稳定工程事实；用户操作写入 `docs/user-manual.md`。
