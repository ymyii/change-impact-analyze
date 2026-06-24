# Wiki Index

## Project

### [Change Impact Analyze](project/change-impact-analyze.md)
- Summary: Maven Java 17 变更影响分析命令行工具，分析 Java 8 Maven 项目依赖升级后的静态影响范围，输出 HTML/Markdown 报告。

## Architecture

### [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)
- Summary: 线性阶段分析架构，模块边界、数据流和 Architecture Decision Records。

## Features

### [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md)
- Summary: picocli CLI 参数解析、校验、退出码控制和诊断事件收集框架。`--project` 可选，默认当前目录。`--build-java-home` 可选，覆盖 Maven 子进程的 JAVA_HOME。`--include-change-kinds` 可选，逗号分隔 ChangePointKind，大小写不敏感，默认 6 种非 ADDED 类型。

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: 使用 git worktree 隔离 baseline/target workspace，支持子目录 project 路径对齐和 current workspace mode，自动清理临时 worktree。

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 调用用户环境 `mvn compile` 编译 workspace，收集 main classes 目录。支持 `--build-java-home` 覆盖 Maven 子进程 JAVA_HOME 实现 JDK 隔离。

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: 调用 Maven dependency plugin 输出 GraphML，解析为结构化 resolved dependency tree。支持 `--build-java-home` 覆盖 Maven 子进程 JAVA_HOME 实现 JDK 隔离。

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比两侧 resolved dependency tree，按模块维度 union diff，生成依赖变动清单。

### [Jar Locator](features/jar-locator.md)
- Summary: 为 VERSION_CHANGED 依赖定位 Maven local repository 中的 old/new jar 文件。

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 对 VERSION_CHANGED 依赖的 old/new jar 执行 bytecode diff，使用 ASM 9.7 + SHA-256 body hash，生成 ChangePoint 清单。支持通过构造函数按 `ChangePointKind` 过滤，默认排除 ADDED 类型。

### [Report Generator](features/report-generator.md)
- Summary: 生成多文件 HTML 或 Markdown 变更影响分析报告（index + 3 个子文件），按模块分组展示依赖变动、变化点、影响路径和诊断信息。

## Rules

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Maven 编译、测试、checkstyle、打包和运行操作手册。

## Glossary
