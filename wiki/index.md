# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: Java 17 + Maven + picocli dependency analysis CLI，提供 `impact` 与 repository 级 `tree` report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: Root CLI、共享 Maven runtime/preflight 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: Public CLI、Command Preflight、Tree Analysis issue、三阶段 Console 与 exit code 契约。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、JAR 内嵌 Maven 3.6.3 与 Dependency Plugin repository 的离线准备和 config dir 管理。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、纯 aggregator result boundary、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact` baseline/target worktree 与 `tree` current/local-ref repository snapshot 的隔离和清理。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 使用 preflight 选定 Maven runtime 编译 `impact` baseline/target 并收集 main classes。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: `impact` 使用 Maven dependency plugin GraphML 提取兼容的 resolved dependency tree。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Jar Locator](features/jar-locator.md)
- Summary: 为 VERSION_CHANGED dependency 定位 Maven local repository 中的 old/new JAR。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 对 VERSION_CHANGED JAR 执行 ASM bytecode diff，生成可过滤的 ChangePoint。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 基于 target main classes 使用 WALA RTA 构建 Call Graph，并补充动态调用边。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 将 ChangePoint 映射到 target bytecode seed，并沿 Call Graph 反向追踪业务入口。

### [Report Generator](features/report-generator.md)
- Summary: 生成 `impact` HTML/Markdown，以及 Index 表格、Reactor cross-module section、Module tabs 与 Maven-style verbose tree 的 `tree` offline report。

## Rules

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Checkstyle、unit/integration tests、全量 quality gate、uber JAR 和 CLI smoke commands。

## Glossary
