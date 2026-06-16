# Wiki Index

## Project

### [Project Overview](project/project-overview.md)
- Summary: Maven Java 17 变更影响分析命令行工具，分析 Java 8 Maven 项目依赖升级后的静态影响范围，输出 HTML/Markdown 报告。
- Code: `pom.xml`, `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`
- Related: [Build, Test, Package](runbooks/build-test-package.md), [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

## Architecture

### [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)
- Summary: 线性阶段分析架构，模块边界、数据流和关键设计决策。
- Code: `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java`
- Related: [Project Overview](project/project-overview.md), [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md), [Git Workspace Management](features/git-workspace-management.md), [Maven Build Runner](features/maven-build-runner.md), [Dependency Tree Extraction](features/dependency-tree-extraction.md), [Dependency Diff Engine](features/dependency-diff-engine.md)

## Features

### [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md)
- Summary: picocli CLI 参数解析、校验、退出码控制和诊断事件收集框架。
- Code: `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: 使用 git worktree 隔离 baseline/target workspace，支持 current workspace 模式，自动清理临时 worktree。
- Code: `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandRunner.java`, `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandResult.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceResult.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSideInfo.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSide.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspacePrepareException.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 调用用户环境 `mvn compile` 编译 workspace，收集 main classes 目录。
- Code: `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildResult.java`, `src/main/java/io/github/changeimpact/analyze/build/ModuleBuildOutput.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildException.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: 调用 Maven dependency plugin 输出 GraphML，解析为结构化 resolved dependency tree。
- Code: `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`, `src/main/java/io/github/changeimpact/analyze/dependency/GraphMLParser.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ArtifactCoord.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyNode.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyScope.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ModuleDependencyTree.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalysisException.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Diff Engine](features/dependency-diff-engine.md)

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比两侧 resolved dependency tree，按模块维度 union diff，生成依赖变动清单。
- Code: `src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Tree Extraction](features/dependency-tree-extraction.md)

## Rules

### [Coding Principles](rules/coding-principles.md)
- Summary: 项目级编码基本原则：KISS、YAGNI、系统性修复、避免 hack、SOLID 原则、高内聚低耦合。
- Code: 无特定文件
- Related: 无

## Runbooks

### [Build, Test, Package](runbooks/build-test-package.md)
- Summary: Maven 编译、测试、checkstyle、打包和运行操作手册。
- Code: `pom.xml`
- Related: [Project Overview](project/project-overview.md)

## Glossary
