---
title: "Dependency Analyzer"
type: project
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Root CLI、共享基础设施和两条分析 pipeline 的架构边界"
  - path: "wiki/features/maven-runtime.md"
    desc: "Maven runtime 选择和 config dir 行为"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "Repository dependency tree 功能契约"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "外部命令执行的跨平台约束"
  - path: "wiki/rules/release-versioning.md"
    desc: "Analyzer/Plugin 独立 SemVer 与 release fingerprint 约束"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包和本地运行操作"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Version bump、正式 distribution 与 build manifest 操作"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "打包后 impact 的持续性能与场景完整性验证"
code_refs:
  - path: "pom.xml"
    desc: "Root parent/aggregator 与 shared version management"
  - path: "analyzer/pom.xml"
    desc: "Java 17 Analyzer、测试、内置 Plugin resources 与 uber JAR 配置"
  - path: "plugins/pom.xml"
    desc: "所有内置 Maven Plugin 的 Java 8 parent/aggregator"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Artifact Path Maven Plugin module"
  - path: "build-support/version-contract.properties"
    desc: "Analyzer/Plugin release version 与 source fingerprint ledger"
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

Dependency Analyzer 是 Java 17 analyzer + Maven + picocli CLI。`impact` 使用显式 JDK 8 比较 dependency 升级前后的 bytecode 与业务调用影响；`tree` 扫描 Git repository 内的 Maven reactor，生成 repository 级静态 HTML dependency tree report。

## Design Decisions

<!-- version-contract:start -->
- Analyzer release: `0.1.0`
- Artifact Path Plugin release: `1.0.1`
<!-- version-contract:end -->

- Public CLI 固定为 `dependency-analyzer [global-options] <subcommand>`。
- Maven coordinates 为 `io.github.dependencyanalysis:dependency-analyzer:0.1.0`，Java base package 为 `io.github.dependencyanalysis`，uber JAR 兼容路径为 `target/dependency-analyzer.jar`。
- Analyzer 与 Artifact Path Plugin 使用独立 SemVer；正式 distribution 同时发布 versioned CLI JAR、SHA-512 与 build manifest。
- `impact` 以 GraphML 作为唯一 mediation authority，并由内置 Artifact Path Plugin JSON 绑定 selected dependency physical path；Plugin 不执行第二次 collection。`tree` 使用 verbose text 采集完整 dependency occurrence。
- `impact` 只构建 target per-Module Vanilla 0-1-CFA；baseline 不 compile、不构建 Call Graph。
- `impact` 默认并发分析两个 Module；Module 内 WALA build/query 单线程，SSA equivalence 全局串行。
- 两个 subcommand 共享 Maven runtime 和 preflight Schema，但分别组装检查 DAG；pipeline 只消费 preflight decision。

## Module Map

- `pom.xml` - packaging `pom` 的 root parent/aggregator。
- `analyzer/` - GAV `io.github.dependencyanalysis:dependency-analyzer:0.1.0`；Java 17 CLI/application，最终仍发布兼容路径 `target/dependency-analyzer.jar`。
- `analyzer/src/main/java/io/github/dependencyanalysis/cli/` - Root CLI、global option 和 Maven argument 安全校验。
- `analyzer/src/main/java/io/github/dependencyanalysis/runtime/` - 内嵌或用户指定 Maven runtime。
- `analyzer/src/main/java/io/github/dependencyanalysis/preflight/` - DAG preflight framework 和结果 Schema。
- `analyzer/src/main/java/io/github/dependencyanalysis/impact/` - `impact` command、pipeline 和影响追踪 domain。
- `analyzer/src/main/java/io/github/dependencyanalysis/tree/` - Git snapshot、reactor inventory、text parser、version analysis 和 HTML report。
- `analyzer/src/main/java/io/github/dependencyanalysis/{build,dependency,bytecode,callgraph,jar,report,workspace}/` - `impact` pipeline 的稳定阶段实现。
- `analyzer/src/test/java/` - unit tests；`analyzer/src/integration-test/java/` - Failsafe integration tests。
- `plugins/` - 内置 Maven Plugin parent/aggregator；后续 Plugin 作为 sibling module 加入。
- `plugins/artifact-path-resolver/` - GAV `io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:1.0.1`；Java 8 `resolve-artifact-paths` goal。
- `build-support/` - Version contract 与无版本 consumer POM template。
- `scripts/` - Version contract 和正式/dev distribution 的 POSIX shell 入口及 Java 17 source-file helpers。
- `benchmarks/impact-medium/` - 可复现的中型 impact fixture、资源采样脚本与报告 contract verification。

## Technical Stack

- Analyzer runtime 为 Java 17；`impact` target runtime 为完整 JDK 8，`tree` 接受 Maven-compatible JDK。
- Maven 3.x 构建；应用默认内嵌 Apache Maven 3.6.3 runtime。
- picocli 4.7.6、ASM/ASM Tree 9.7、WALA 1.8.0、Vineflower 1.12.0 slim、JUnit 5、AssertJ。
- maven-shade-plugin 生成包含 runtime distribution 的 uber JAR。

## Main Entrypoints

- `DependencyAnalyzerCli.main()` - JVM 和 Root CLI 入口。
- `ImpactCommand.call()` - dependency upgrade impact 分析入口。
- `TreeCommand.call()` - repository dependency tree report 入口。

## Repository Conventions

- 所有外部 process token 都先经过 `CommandResolver.resolve()`，并使用 `ProcessBuilder`，不经过 shell 拼接。
- 用户 Maven argument 必须逐 token 传入，禁止覆盖工具控制的 POM、module selection、output、verbose 和 token 参数。
- `tmp-files/` 仅用于临时产物，不提交 Git。
- 可复用 benchmark source 和脚本进入 `benchmarks/`；运行生成的 nested Git repository、JAR、HTML 和日志进入 `tmp-files/`。
- wiki 描述当前稳定工程事实；用户操作写入 `docs/user-manual.md`。
