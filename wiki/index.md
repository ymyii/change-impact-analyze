# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: Java 17 analyzer + Maven + picocli CLI；`impact` 分析显式 JDK 8 target，`tree` 生成 repository 级 report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: Root CLI、共享 Maven runtime/preflight 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: `--java-home`、impact JDK 8 boundary、Command Preflight、Tree Analysis issue 与 exit code 契约。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台 JAR 内嵌 Maven 3.6.3、Windows `mvn.cmd` contract 与 Dependency Plugin repository 的离线准备。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、纯 aggregator result boundary、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、stale recovery 与 detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 使用 preflight 选定 Maven runtime 编译 `impact` baseline/target 并收集 main classes。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: `impact` 使用 Maven dependency plugin GraphML 提取兼容的 resolved dependency tree。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Jar Locator](features/jar-locator.md)
- Summary: 为 VERSION_CHANGED dependency 定位 Maven local repository 中的 old/new JAR。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 对 VERSION_CHANGED JAR 执行 ASM bytecode diff，并用内嵌 Vineflower 为实际受影响的 method body 生成 old/new Java-like evidence。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 使用目标 JDK 8 Primordial、all-application WALA 1.8.0 RTA、heartbeat/timeout 构建 Call Graph。

### [Impact Tracing](features/impact-tracing.md)
- Summary: Call Graph 前精确扫描 seed、复用唯一 seed reverse BFS，并选择进入调用链的 method body evidence。

### [Report Generator](features/report-generator.md)
- Summary: 生成带稳定 `MB-xxx` old/new method evidence 的 `impact` HTML/Markdown，以及完整 `tree` offline report。

## Rules

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Checkstyle、unit/integration tests、全量 quality gate、uber JAR 和 CLI smoke commands。

## Glossary
