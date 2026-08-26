---
title: "Wiki Index"
type: project
relations: []
code_refs: []
---

# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: 四个独立Maven reactor；Java 17 Analyzer内嵌Java 8 Plugin与JDK 8 Method Model；`impact`、`tree analyze`与`tree diff`共享bounded reactor scope resolver。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: 中立reactor层统一三个分析入口；Impact保持per-Module Call Graph编排，Tree Analyze执行单侧enrichment，Tree Diff按Reactor流式采集双侧dependency并增量发布。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: 入口POM与reactor scope fail-fast、Console-only五段DiagnosticLog、`-vv`审计、Runtime Metrics与显式Schema 13 topology JSON边界。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户或内嵌Maven、双repository ZIP、command-scoped settings overlay，以及与实际Maven JVM/settings一致的profile activation。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: `tree analyze`以单侧Maven evidence生成Tree Schema v2；Reactor/Module全量dependency occurrence、双版本徽标与Resolution source通过本地shard按需渲染。

### [Repository Dependency Tree Diff](features/repository-dependency-tree-diff.md)
- Summary: `tree diff`比较commit-ish baseline与ref或当前工作区target；按DependencyKey分类、PathKey配对chain，并以独立Schema v1按Reactor增量发布离线报告。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`与`tree diff`复用双侧worktree/current workspace生命周期，`tree analyze`使用单侧snapshot；三者保持Git-relative入口POM路径和owned UUID清理边界。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译target；入口aggregator完整compile、owned leaf使用`-pl/-am`、standalone直接执行，baseline与target分别规划scope。

### [Structured Dependency Evidence Collection](features/dependency-evidence-collection.md)
- Summary: Plugin 3.1提供impact Schema v3与tree Classpath Evidence Schema v1；两者复用owner、路径边界与原子发布且只写command cache。

### [Impact Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 为Impact对比baseline/target resolved artifact并生成稳定`DependencyChange`；不承载Tree occurrence、scope、directness或PathKey语义。

### [Coordinate JAR Repository](features/jar-locator.md)
- Summary: `ArtifactCoord` 是 dependency JAR logical identity；repository deterministic 选择 Resolver binding，并以 tracked `JarLease` 隔离 physical handle。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 全部method body候选先执行Vineflower文本比较；Java命中短路，未命中再执行normalized SSA；源码证据只写当前command cache。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 正式CHA与experimental `k-obj`按strategy隔离；CHA固定限制JDK声明分派，canonical SCC utility由topology与Impact QueryNode slice复用。

### [JDK Method Models](features/jdk-method-models.md)
- Summary: CHA固定`none`；experimental `k-obj`默认接入独立`models/jdk8`精确catalog，并允许显式`none`。

### [Impact Tracing](features/impact-tracing.md)
- Summary: ChangePoint收集期固定执行decompiled Java first、normalized SSA on miss的短路过滤；下游仅消费effective ChangePoint，CHA query按caller-local事实生成确定性代表路径。

### [Report Generator](features/report-generator.md)
- Summary: Impact Schema 5、Tree Analyze Schema v2与Tree Diff Schema v1共享安全callback shard边界；离线交互由Java门禁与双viewport `file://` Playwright共同约束。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer、Dependency Evidence Plugin、公共JDK engine与JDK 8 model独立SemVer、release profile与Git annotated tag。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

### [Operational Evidence Design](rules/operational-evidence-design.md)
- Summary: 所有功能设计必须覆盖指标监控、进度跟踪与审计日志；运行证据可复用，但高成本生成只能在对应详细级别启用后执行。

### [Benchmark Scenario Coverage](rules/benchmark-scenario-coverage.md)
- Summary: CHA-only canonical matrix为双scope共12个JVM与2个baseline；仅在用户明确授权后执行。

### [Package Boundaries](rules/package-boundaries.md)
- Summary: 按职责分包、单向依赖、algorithm隔离、无兼容壳与测试镜像production package，由ArchUnit持续强制。

### [Code and Concept Reuse](rules/code-concept-reuse.md)
- Summary: 遵循KISS、YAGNI与DRY；复用canonical概念和实现，只编码当前可到达状态，不为明确领域不变量排除的理论情况增加防御分支。

### [User Manual Maintenance](rules/user-manual-maintenance.md)
- Summary: 用户手册必须是可与Analyzer JAR独立交付的用户任务闭包，并随CLI、runtime、Report、状态和限制同步维护。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 四reactor顺序bootstrap、artifact-first Maven gate、uber JAR与三个分析入口smoke，以及包含Tree Diff的Chromium双viewport Report gate。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 公共JDK engine与JDK 8 model的顺序构建、exact catalog/fixed-point/Packaging验收、metrics与failure entrypoint。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer驱动四个独立artifact的Snapshot iteration、Stable release、commit与component Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: changed-paths/full各执行1次warm-up与5次formal；固定Java-first方法体短路过滤、Schema 13和CHA pruning合同通过后原子发布。

## Glossary
