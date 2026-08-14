---
title: "Wiki Index"
type: project
relations: []
code_refs: []
---

# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: 四个独立Maven reactor；Java 17 Analyzer内嵌Java 8 Plugin与JDK 8 Method Model；`impact`分析显式JDK 8 target，`tree`生成repository级report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: `impact`严格串行Module、按QueryNode并发Impact Query、可选试验性SSA与WALA detach；`tree`使用Reader/external merge；两者共用UUID task cache和Writer流式Report。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: stderr-only五段DiagnosticLog、半核analysis parallelism默认值、QueryNode进度、JAR diff汇总，以及`-vv` Runtime Metrics。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台内嵌 Maven 3.6.3、两个独立 repository ZIP，以及 Stable/Snapshot 分离 cache 与 command-scoped settings。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、Reader逐行解析、cache-backed conflict grouping、incremental checkpoint与流式offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、task-scoped report-cache、stale recovery与detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Structured Dependency Evidence Collection](features/dependency-evidence-collection.md)
- Summary: Maven resolved/raw graph 结构化采集，Schema v3 统一 selected tree、occurrence topology、reactor keys 与 physical bindings；evidence 只写 command cache。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Coordinate JAR Repository](features/jar-locator.md)
- Summary: `ArtifactCoord` 是 dependency JAR logical identity；repository deterministic 选择 Resolver binding，并以 tracked `JarLease` 隔离 physical handle。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: logical coordinate pair经repository lease并行去重Diff；检测bytecode、JVM access narrowing与ServiceLoader registration removal，并按唯一pair汇总changes/failure。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 正式CHA与experimental `k-obj`按strategy隔离；engine只接收Call Graph input contract并冻结metadata，Impact层负责Evidence和coverage reason映射。

### [JDK Method Models](features/jdk-method-models.md)
- Summary: CHA固定`none`；experimental `k-obj`默认接入独立`models/jdk8`精确catalog，并允许显式`none`。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 构图后统一collector绑定公共`ReferenceEvidence`；冻结session按exact `QueryNode`分组并发reverse BFS，共享单节点局部`ReverseTrace`，并在`-vv`下提供QueryNode独立10秒心跳。

### [Report Generator](features/report-generator.md)
- Summary: `impact`通过Writer原子生成Overall与每Module两页；Affected Paths以内存单表分页/搜索、规范化member/diff和按需着色详情控制DOM规模。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer、Dependency Evidence Plugin、公共JDK engine与JDK 8 model独立SemVer、release profile与Git annotated tag。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

### [Operational Evidence Design](rules/operational-evidence-design.md)
- Summary: 所有功能设计必须覆盖指标监控、进度跟踪与审计日志；运行证据可复用，但高成本生成只能在对应详细级别启用后执行。

### [Benchmark Scenario Coverage](rules/benchmark-scenario-coverage.md)
- Summary: CHA-only canonical matrix为双scope共14个JVM与4个baseline；仅在用户明确授权后执行。

### [Package Boundaries](rules/package-boundaries.md)
- Summary: 按职责分包、单向依赖、algorithm隔离、无兼容壳与测试镜像production package，由ArchUnit持续强制。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 公共model、JDK 8 model、Plugin与Analyzer四reactor顺序bootstrap；完整JDK 8 gate、uber JAR和CLI smoke。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 公共JDK engine与JDK 8 model的顺序构建、exact catalog/fixed-point/Packaging验收、metrics与failure entrypoint。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer驱动四个独立artifact的Snapshot iteration、Stable release、commit与component Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: changed-paths/full各执行1次SSA warm-up、5次SSA formal与1次CHA local receiver control；14个JVM和4组baseline通过后原子发布。

## Glossary
