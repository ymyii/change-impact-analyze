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
- Summary: `ImpactExecutionEngine`以唯一command-wide `common` pool统一限制front preparation、JAR diff、Impact Query与code comparison，Module仍严格串行。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: Console-only五段DiagnosticLog、全局common pool并发上限、QueryNode进度、JAR diff汇总、`-vv` SSA method审计与Runtime Metrics、显式topology JSON边界。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台内嵌 Maven 3.6.3、两个独立 repository ZIP，以及 Stable/Snapshot 分离 cache 与 command-scoped settings。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: `TreeCommand`与`TreeExecutionEngine`分离；Git snapshot、bounded/full reactor execution、Reader逐行解析、cache-backed conflict grouping、incremental checkpoint与流式offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、command-owned report-cache、stale recovery与detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Structured Dependency Evidence Collection](features/dependency-evidence-collection.md)
- Summary: Maven resolved/raw graph 结构化采集，Schema v3 统一 selected tree、occurrence topology、reactor keys 与 physical bindings；evidence 只写 command cache。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Coordinate JAR Repository](features/jar-locator.md)
- Summary: `ArtifactCoord` 是 dependency JAR logical identity；repository deterministic 选择 Resolver binding，并以 tracked `JarLease` 隔离 physical handle。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: logical coordinate pair经repository lease并行去重Diff；跨class major的method body执行normalized SSA filtering，`-vv`为retained结果输出old/new bytecode与IR审计。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 正式CHA与experimental `k-obj`按strategy隔离；canonical SCC topology utility由cycle识别和Impact root selection复用。

### [JDK Method Models](features/jdk-method-models.md)
- Summary: CHA固定`none`；experimental `k-obj`默认接入独立`models/jdk8`精确catalog，并允许显式`none`。

### [Impact Tracing](features/impact-tracing.md)
- Summary: ChangePoint收集期SSA与query期CHA local receiver分阶段执行；reverse slice按root SCC生成确定性最短Impact Path，不保留candidate/final双模型。

### [Report Generator](features/report-generator.md)
- Summary: Impact/Structural Affected Paths采用Schema 3 unique entities、4 MiB离线分片与按需查询；报告展示SSA证据，code comparison通过`common` pool并发生成。

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

### [Code and Concept Reuse](rules/code-concept-reuse.md)
- Summary: 同一含义复用统一概念，同一职责与不变量复用canonical implementation；新增抽象必须具有明确语义边界和所有者。

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
