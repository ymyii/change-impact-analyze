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
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包和本地运行操作"
code_refs:
  - path: "pom.xml"
    desc: "Maven coordinates、依赖、测试和 uber JAR 配置"
  - path: "src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI 和 global options 入口"
  - path: "src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Spring backend per-Module impact pipeline 编排"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "tree pipeline 编排"
---

# Project: Dependency Analyzer

## Summary

Dependency Analyzer 是 Java 17 analyzer + Maven + picocli CLI。`impact` 使用显式 JDK 8 比较 dependency 升级前后的 bytecode 与业务调用影响；`tree` 扫描 Git repository 内的 Maven reactor，生成 repository 级静态 HTML dependency tree report。

## Design Decisions

- Public CLI 固定为 `dependency-analyzer [global-options] <subcommand>`。
- Maven coordinates 为 `io.github.dependencyanalysis:dependency-analyzer:0.1.0-SNAPSHOT`，Java base package 为 `io.github.dependencyanalysis`，uber JAR 为 `dependency-analyzer.jar`。
- `impact` 使用 GraphML dependency diff；`tree` 使用 verbose text 采集完整 dependency occurrence。
- `impact` 只构建 target per-Module Vanilla 0-1-CFA；baseline 不 compile、不构建 Call Graph。
- `impact` 默认并发分析两个 Module；Module 内 WALA build/query 单线程，SSA equivalence 全局串行。
- 两个 subcommand 共享 Maven runtime 和 preflight Schema，但分别组装检查 DAG；pipeline 只消费 preflight decision。

## Module Map

- `src/main/java/io/github/dependencyanalysis/cli/` - Root CLI、global option 和 Maven argument 安全校验。
- `src/main/java/io/github/dependencyanalysis/runtime/` - 内嵌或用户指定 Maven runtime。
- `src/main/java/io/github/dependencyanalysis/preflight/` - DAG preflight framework 和结果 Schema。
- `src/main/java/io/github/dependencyanalysis/impact/` - `impact` command、pipeline 和影响追踪 domain。
- `src/main/java/io/github/dependencyanalysis/tree/` - Git snapshot、reactor inventory、text parser、version analysis 和 HTML report。
- `src/main/java/io/github/dependencyanalysis/{build,dependency,bytecode,callgraph,jar,report,workspace}/` - `impact` pipeline 的稳定阶段实现。
- `src/test/java/` - unit tests；`src/integration-test/java/` - Failsafe integration tests。

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
- wiki 描述当前稳定工程事实；用户操作写入 `docs/user-manual.md`。
