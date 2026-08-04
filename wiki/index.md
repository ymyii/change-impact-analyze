# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: Maven multi-module；Java 17 Analyzer + Java 8 内置 Plugin；`impact` 分析显式 JDK 8 target，`tree` 生成 repository 级 report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: Root CLI、共享 Maven runtime/preflight 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: 全局 `INFO`/`DEBUG`/`TRACE` verbosity、stable concurrent task context、impact JDK 8/内嵌 Plugin Preflight 与 exit code 契约。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台内嵌 Maven 3.6.3，以及 versioned Plugin repository 的离线准备、fingerprint 与实际加载 JAR SHA-512 evidence。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、纯 aggregator result boundary、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、stale recovery 与 detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: `impact` 以 GraphML 作为唯一 mediation authority；Artifact Path goal 不再 collection，为 selected `compile/runtime/provided` 解析 Resolver path，并为 `system` 绑定 effective `systemPath`。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Jar Locator](features/jar-locator.md)
- Summary: Legacy JAR path 计算说明；`impact` production path 已改用 Maven resolved absolute artifact path。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: physical JAR pair 并行去重 diff；MethodNode canonical hash覆盖 CFG/exception/bootstrap topology，SSA filtering 延迟到 candidate path 后。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 每 Module 独立使用 target JDK 8、可配置 PROJECT package/class entrypoints、WALA Vanilla 0-1-CFA `FULL` Reflection。

### [Impact Tracing](features/impact-tracing.md)
- Summary: Call Graph 后从 live WALA graph 直接 reverse query，保留 Context、Structural Reference Path、serial SSA filtering 与 path-related code evidence。

### [Report Generator](features/report-generator.md)
- Summary: `impact` 原子生成英文 Overall Index + 每个非-skip Module 三页；只突出有路径证据的 dependency changes，提供折叠 Unified diff、technical evidence 与 responsive navigation。

## Rules

### [Release Versioning](rules/release-versioning.md)
- Summary: Analyzer/内置 Plugin 独立 SemVer、immutable release coordinate、source fingerprint 与 version bump gate。

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: 日常 Checkstyle、unit/integration tests、全量 Maven quality gate、uber JAR 和 CLI smoke commands。

### [Version and Distribution](runbooks/version-and-distribution.md)
- Summary: Version contract verify/bump、正式/dev distribution、packaged smoke、reproducibility 与 build manifest。

### [Impact Benchmark](runbooks/impact-benchmark.md)
- Summary: 从 Git 管理的 source fixture 生成 42 个 dependencies 与 9 类 ChangePoint，执行 impact、采集资源并校验四页 HTML report。

## Glossary
