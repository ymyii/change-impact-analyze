# Wiki Index

## Project

### [Dependency Analyzer](project/dependency-analyzer.md)
- Summary: Java 17 analyzer + Maven + picocli CLI；`impact` 分析显式 JDK 8 target，`tree` 生成 repository 级 report。

## Architecture

### [Dependency Analysis Pipelines](architecture/dependency-analysis-pipelines.md)
- Summary: Root CLI、共享 Maven runtime/preflight 与独立 `impact`/`tree` pipeline 的结构和数据流。

## Features

### [CLI Preflight and Diagnostics](features/cli-preflight-diagnostics.md)
- Summary: 全局 `INFO`/`DEBUG`/`TRACE` verbosity、`--java-home`、impact JDK 8 boundary、Command Preflight、Tree Analysis issue 与 exit code 契约。

### [Maven Runtime](features/maven-runtime.md)
- Summary: 用户 executable、跨平台 JAR 内嵌 Maven 3.6.3、Windows `mvn.cmd` contract 与 Dependency Plugin repository 的离线准备。

### [Repository Dependency Tree Report](features/repository-dependency-tree-report.md)
- Summary: Git snapshot、bounded/full reactor execution、纯 aggregator result boundary、incremental checkpoint 与 Module-tab offline report。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: `impact`/`tree` config UUID workspace/tmp、owner lock、stale recovery 与 detached worktree cleanup。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 只编译 target；reactor root compile 一次，leaf 使用 `-pl/-am`；baseline dependency 与 target build 并行。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: GraphML 保留 mediated tree，pinned `dependency:list` 提供 custom repository/SNAPSHOT 可用的 absolute artifact path。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，生成稳定排序的 dependency changes。

### [Jar Locator](features/jar-locator.md)
- Summary: Legacy JAR path 计算说明；`impact` production path 已改用 Maven resolved absolute artifact path。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: physical JAR pair 并行去重 diff；MethodNode canonical hash覆盖 CFG/exception/bootstrap topology，SSA filtering 延迟到 candidate path 后。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 每 Module 独立使用 target JDK 8、PROJECT-only all-method entrypoints、WALA Vanilla 0-1-CFA `FULL` Reflection。

### [Impact Tracing](features/impact-tracing.md)
- Summary: Call Graph 后从 live WALA graph 解析 seed并直接 reverse query，保留 Context、Structural Impact 与 serial SSA filtering。

### [Report Generator](features/report-generator.md)
- Summary: `impact` 原子生成 HTML Index + per-Module pages；展示 model boundaries、partial status、paths、dispositions、SSA/metrics；Markdown 已移除。

## Rules

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 所有 production external process token 在 `ProcessBuilder` 前必须经过 `CommandResolver.resolve()`。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Checkstyle、unit/integration tests、全量 quality gate、uber JAR 和 CLI smoke commands。

## Glossary
