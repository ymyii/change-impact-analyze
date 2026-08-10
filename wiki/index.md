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
- Summary: Root CLI、dependency occurrence path planning、method-body boundary 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: stderr-only 五段 DiagnosticLog、Preflight、verbosity，以及`-vv`下100 ms heap observation、10 s snapshot与final peak summary。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台内嵌 Maven 3.6.3、两个独立 repository ZIP，以及 Stable/Snapshot 分离 cache 与 command-scoped settings。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、统一 Tree progress Diagnostic、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、stale recovery 与 detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: 普通 GraphML 决定 mediation/binding，verbose GraphML 保留 occurrence/multi-parent topology；Schema v2 manifest 初始化 command-scoped repository。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Coordinate JAR Repository](features/jar-locator.md)
- Summary: `ArtifactCoord` 是 dependency JAR logical identity；repository deterministic 选择 Resolver binding，并以 tracked `JarLease` 隔离 physical handle。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: logical coordinate pair 经 repository lease 并行去重 diff；除stable method hash外，默认检测class/method/constructor/field Java 8 JVM access narrowing。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 每 Module 构建一张 WALA Call Graph；默认 changed-paths 仅让到达变更 dependency 的路径使用真实 IR，其他 external method 使用 no-op/factory boundary。

### [JDK Method Models](features/jdk-method-models.md)
- Summary: 独立`models/jdk`公共engine与`models/jdk8`精确catalog生成conservative WALA Synthetic IR；尚未接入`impact`。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 构图后 read-only 解析 call/structural/access reference，并结合 dangerous transfer、factory evidence 与 deterministic reverse BFS 生成结果。

### [Report Generator](features/report-generator.md)
- Summary: `impact` 原子生成英文 Overall 与 Module 三页；展示 requested/actual dependency scope、全部到达路径、body policy 与 boundary evidence。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer、Artifact Path Plugin、公共JDK engine与JDK 8 model独立SemVer、release profile与Git annotated tag。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

### [Benchmark Scenario Coverage](rules/benchmark-scenario-coverage.md)
- Summary: Analyzer 能力新增必须同步提交 semantic benchmark 场景，并通过 changed-paths/full 48-JVM canonical matrix。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 双 reactor bootstrap、完整 JDK 8 gate、unit/integration tests、两个 repository ZIP、uber JAR 和 CLI smoke。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 公共JDK engine与JDK 8 model的顺序构建、exact catalog/fixed-point/Packaging验收、metrics与failure entrypoint。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer 驱动的 Snapshot iteration、Stable release、commit 与双 Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: changed-paths/full 各执行4次warm-up与20个正式样本，输出两份HTML及两组snapshot；48个JVM和8组semantic baseline全部通过后原子发布。

## Glossary
