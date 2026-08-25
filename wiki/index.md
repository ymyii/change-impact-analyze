---
title: "Wiki Index"
type: project
relations: []
code_refs: []
---

# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: 四个独立Maven reactor；Java 17 Analyzer内嵌Java 8 Plugin与JDK 8 Method Model；`impact`与`tree`共享单入口reactor scope resolver。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: 中立reactor层统一`tree`/`impact`入口scope；Impact保持per-Module Call Graph编排，tree以单次Maven session生成dependency与classpath evidence。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: 入口POM与reactor scope fail-fast、Console-only五段DiagnosticLog、`-vv`审计、Runtime Metrics与显式Schema 13 topology JSON边界。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户或内嵌Maven、双repository ZIP、command-scoped settings overlay，以及与实际Maven JVM/settings一致的profile activation。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: 单入口Maven evidence生成Tree Schema v1；全量dependency occurrence、带展开标志的响应式筛选与唯一活动Module card通过本地shard按需渲染。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree`保持Git-relative入口POM路径，使用UUID workspace/tmp、owner lock、stale recovery与detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译target；入口aggregator完整compile、owned leaf使用`-pl/-am`、standalone直接执行，baseline与target分别规划scope。

### [Structured Dependency Evidence Collection](features/dependency-evidence-collection.md)
- Summary: Plugin 3.1提供impact Schema v3与tree Classpath Evidence Schema v1；两者复用owner、路径边界与原子发布且只写command cache。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

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
- Summary: Impact Schema 5与Tree Schema v1共享安全shard writer和可配置loader；离线交互由Java门禁与双viewport `file://` Playwright共同约束。

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
- Summary: 同一含义复用统一概念，同一职责与不变量复用canonical implementation；新增抽象必须具有明确语义边界和所有者。

### [User Manual Maintenance](rules/user-manual-maintenance.md)
- Summary: 用户手册必须是可与Analyzer JAR独立交付的用户任务闭包，并随CLI、runtime、Report、状态和限制同步维护。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 四reactor顺序bootstrap、artifact-first Maven gate、uber JAR/CLI smoke，以及独立必跑的Chromium双viewport Report gate。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 公共JDK engine与JDK 8 model的顺序构建、exact catalog/fixed-point/Packaging验收、metrics与failure entrypoint。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Maven Versions/Enforcer驱动四个独立artifact的Snapshot iteration、Stable release、commit与component Git tag。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: changed-paths/full各执行1次warm-up与5次formal；固定Java-first方法体短路过滤、Schema 13和CHA pruning合同通过后原子发布。

## Glossary
