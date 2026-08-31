---
title: "Wiki Index"
type: project
---

# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: 解释三个 CLI 用户目标、四个独立 Maven reactor、主要交付物与技术边界。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: 解释 CLI、Git workspace、Maven evidence、三条分析 pipeline 与离线 publication 的依赖方向。

## Features

### [Dependency Impact Analysis](features/dependency-impact-analysis.md)
- Summary: 用户通过 `impact` 比较 baseline 与 target，并获得静态 dependency upgrade 影响路径离线报告。

### [Repository Dependency Tree Diff](features/repository-dependency-tree-diff.md)
- Summary: 用户通过 `tree diff` 比较双侧 dependency occurrence、chain 与 Reactor 结构。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: 用户通过 `tree analyze` 查看单侧 bounded Maven scope 的依赖树、版本来源与 class conflict。

## Implementation

### [Bytecode Diff Engine](implementation/bytecode-diff-engine.md)
- Summary: 解释 logical JAR pair 的 bytecode / resource diff、Java-first 方法体过滤与 stable ChangePoint identity。

### [Call Graph Engine](implementation/call-graph-engine.md)
- Summary: 解释 per-Module CHA / `k-obj` 构图、entrypoint、classpath ownership、boundary 与 topology 冻结机制。

### [CLI Preflight and Diagnostics](implementation/cli-preflight-diagnostics.md)
- Summary: 解释 argument / Preflight boundary、五段 Console Diagnostic、verbosity 成本门禁与 Runtime Metrics。

### [Impact Dependency Diff Engine](implementation/dependency-diff-engine.md)
- Summary: 解释 impact 专用 artifact flatten diff、ChangeType 不变量与稳定排序边界。

### [Structured Dependency Evidence Collection](implementation/dependency-evidence-collection.md)
- Summary: 解释 Maven session 内的 selected graph、occurrence、artifact binding 与原子证据 publication。

### [Git Workspace Management](implementation/git-workspace-management.md)
- Summary: 解释 local commit、current workspace、detached worktree、owner identity 与清理生命周期。

### [Impact Tracing](implementation/impact-tracing.md)
- Summary: 解释 ChangePoint evidence binding、reverse query、CHA path pruning 与代表路径选择。

### [Coordinate JAR Repository](implementation/jar-locator.md)
- Summary: 解释 logical coordinate 到 canonical physical artifact 的绑定与 tracked lease ownership。

### [JDK Method Models](implementation/jdk-method-models.md)
- Summary: 解释公共 model engine、JDK 8 catalog、Synthetic IR 与 algorithm-specific installation contract。

### [Maven Build Runner](implementation/maven-build-runner.md)
- Summary: 解释 aggregator、owned leaf 与 standalone 的 bounded compile planning。

### [Maven Runtime](implementation/maven-runtime.md)
- Summary: 解释 Maven executable、settings overlay、repository ZIP 与 command runtime descriptor。

### [Report Generator](implementation/report-generator.md)
- Summary: 解释三套 versioned schema、callback shard、原子 / 增量 publication 与 `file://` browser gate。

## Rules

### [Benchmark Scenario Coverage](rules/benchmark-scenario-coverage.md)
- Summary: 约束 observable capability 与 benchmark contract 同步，并要求用户明确授权后才能执行 benchmark。

### [Code and Concept Reuse](rules/code-concept-reuse.md)
- Summary: 约束领域概念、事实来源和 canonical implementation 复用，禁止 speculative abstraction 与重复模型。

### [Operational Evidence Design](rules/operational-evidence-design.md)
- Summary: 约束功能设计同时覆盖指标、进度和审计，并在生成高成本证据前执行 verbosity 门禁。

### [Package Boundaries](rules/package-boundaries.md)
- Summary: 约束 impact、Call Graph、strategy、protocol、classpath 与 Report 的职责和单向依赖。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 约束 production external process token 在 `ProcessBuilder` 前统一解析跨平台 executable。

### [Release Versioning](rules/release-versioning.md)
- Summary: 约束四个独立 artifact 的 Semantic Versioning、release profile 与 Git annotated tag。

### [User Manual Maintenance](rules/user-manual-maintenance.md)
- Summary: 约束用户手册形成可与 Analyzer JAR 独立交付的完整用户任务闭包。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 按四 reactor 依赖顺序 bootstrap，先打包可交付 artifact，再执行 Maven 与 browser quality gate。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: 在用户明确授权后执行双 scope CHA matrix、semantic verification 与原子 snapshot publication。

### [JDK Models Build and Test](runbooks/jdk-models-build-test.md)
- Summary: 构建并验收公共 JDK model engine、JDK 8 catalog、fixed-point behavior 与 packaging。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: 执行四个 artifact 的 Snapshot iteration、Stable release、验证、commit 与 tag。
