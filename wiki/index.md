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
- Summary: `impact` rolling Module/SSA/WALA detach与`tree` Reader/external merge pipeline；两者共用UUID task cache和Writer流式Report。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: stderr-only 五段 DiagnosticLog、Preflight、verbosity，以及`-vv`下100 ms heap observation、10 s snapshot与final peak summary。

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
- Summary: logical coordinate pair经repository lease并行去重Diff；检测bytecode、JVM access narrowing与ServiceLoader registration removal。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: CHA changed-paths裁剪无关external target并传递保留external祖先type；固定JDK leaf、Object Diff-directed policy、五种algorithm capability与Schema v8 diagnostics。

### [JDK Method Models](features/jdk-method-models.md)
- Summary: CHA固定`none`；四种非CHA algorithm默认接入独立`models/jdk8`精确catalog，并允许显式`none`。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 构图后统一collector绑定公共`ReferenceEvidence`；冻结session上的query只按anchor执行deterministic reverse BFS与access decision。

### [Report Generator](features/report-generator.md)
- Summary: `impact`通过Writer原子生成英文Overall与Module三页；使用WALA-detached snapshot并展示artifact policy、ancestor exception与pruned target metrics。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer、Dependency Evidence Plugin、公共JDK engine与JDK 8 model独立SemVer、release profile与Git annotated tag。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

### [Benchmark Scenario Coverage](rules/benchmark-scenario-coverage.md)
- Summary: Analyzer能力新增必须同步维护semantic benchmark场景；canonical matrix仅在用户明确授权后执行。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 公共model、JDK 8 model、Plugin与Analyzer四reactor顺序bootstrap；完整JDK 8 gate、uber JAR和CLI smoke。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 公共JDK engine与JDK 8 model的顺序构建、exact catalog/fixed-point/Packaging验收、metrics与failure entrypoint。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer驱动四个独立artifact的Snapshot iteration、Stable release、commit与component Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: changed-paths/full各执行5次warm-up、25次formal与4次非CHA`none`control；68个JVM和18组semantic baseline通过后原子发布。

## Glossary
