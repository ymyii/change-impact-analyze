---
title: "Wiki Index"
type: project
relations: []
code_refs: []
---

# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: 两个独立 Maven reactor；Java 17 Analyzer + Java 8 内置 Plugin；`impact` 分析显式 JDK 8 target，`tree` 生成 repository 级 report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: Root CLI、共享 Maven runtime/preflight 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: stderr-only 五段 DiagnosticLog、retained/transient 边界、Preflight、verbosity，以及 `-vv` command-scoped Runtime Metrics。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台内嵌 Maven 3.6.3、两个独立 repository ZIP，以及 Stable/Snapshot 分离 cache 与 command-scoped settings。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、统一 Tree progress Diagnostic、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、stale recovery 与 detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: GraphML 决定 mediation；Schema v2 manifest 只作为 command-scoped immutable `IJarRepository` 的 Resolver/systemPath ingestion 输入。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Coordinate JAR Repository](features/jar-locator.md)
- Summary: `ArtifactCoord` 是 dependency JAR logical identity；repository deterministic 选择 Resolver binding，并以 tracked `JarLease` 隔离 physical handle。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: logical coordinate pair 经 repository lease 并行去重 diff；除stable method hash外，默认检测class/method/constructor/field Java 8 JVM access narrowing。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 每Module由独立strategy构建selected WALA Call Graph；默认Basic RTA与bounded ReflectionOptions，两种ZeroX算法保持可选。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 构图后read-only解析call/structural/access reference，使用typed JVM access decision与deterministic reverse BFS生成representative path/disposition。

### [Report Generator](features/report-generator.md)
- Summary: `impact`原子生成英文Overall与Module三页；展示实际algorithm/ReflectionOptions、typed coverage/access evidence、path/disposition与Unified diff。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer/Artifact Path Plugin 独立 SemVer、Snapshot 周期复用、Maven release profile 与 Git annotated tag。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 双 reactor bootstrap、完整 JDK 8 gate、unit/integration tests、两个 repository ZIP、uber JAR 和 CLI smoke。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer 驱动的 Snapshot iteration、Stable release、commit 与双 Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: 显式选择algorithm/ReflectionOptions运行42-dependency fixture，校验versioned path count与四页Report，并汇总三次以上同环境Wall/RSS中位数。

## Glossary
