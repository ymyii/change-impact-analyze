# Wiki Index

## Project

### [Change Impact Analyze](project/change-impact-analyze.md)
- Summary: Maven Java 17 变更影响分析 CLI，分析 Java 8 Maven 项目依赖升级后的静态影响范围，输出 HTML/Markdown 报告。

## Architecture

### [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)
- Summary: 线性阶段分析架构，覆盖 workspace、build、dependency、bytecode、call graph、impact 和 report 的边界与数据流。

## Features

### [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md)
- Summary: picocli CLI 参数解析、校验、退出码控制、ChangePointKind 过滤和跨阶段诊断事件收集。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: 使用 git worktree 隔离 baseline/target workspace，支持子目录 project 路径对齐和 current workspace mode。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 调用用户环境 `mvn compile -B` 编译 workspace，收集 main classes，并支持 `--build-java-home` 隔离 Maven 子进程 JDK。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: 调用 Maven dependency plugin 输出 GraphML，解析 resolved dependency tree，并排除 target reactor module 依赖。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比 baseline/target resolved dependency tree，按模块 union diff 生成稳定排序的依赖变动清单。

### [Jar Locator](features/jar-locator.md)
- Summary: 为 VERSION_CHANGED 依赖定位 Maven local repository 中的 old/new jar 文件。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 对 VERSION_CHANGED 依赖的 old/new jar 执行 ASM bytecode diff，生成可过滤的 ChangePoint 清单。

### [Call Graph Engine](features/call-graph-engine.md)
- Summary: 基于 target main classes 使用 WALA RTA 构建应用 Call Graph，并补充 ServiceLoader 与 Reflection 间接调用边。

### [Impact Tracing](features/impact-tracing.md)
- Summary: 将 ChangePoint 映射到 target bytecode seed methods，并沿 Call Graph 反向追踪受影响业务入口。

### [Report Generator](features/report-generator.md)
- Summary: 生成多文件 HTML 或 Markdown 报告，展示依赖变动、内部变化、影响路径和诊断事件。

## Rules

### [Process Command Resolution](rules/process-command-resolution.md)
- Summary: 通过 `ProcessBuilder` 执行外部工具命令前必须经过 `CommandResolver.resolve()`，保证 Windows 与 Unix-like 行为一致。

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Maven 编译、测试、checkstyle、集成测试、打包和运行操作手册。

## Glossary
