# Wiki Index

## Project

### [Project Overview](project/project-overview.md)
- Summary: Maven Java 17 变更影响分析命令行工具，分析 Java 8 Maven 项目依赖升级后的静态影响范围，输出 HTML/Markdown 报告。
- Code: `pom.xml`, `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`
- Related: [Build, Test, Package](runbooks/build-test-package.md), [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

## Architecture

### [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)
- Summary: 线性阶段分析架构，模块边界、数据流和关键设计决策。
- Code: `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java`, `src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java`, `src/main/java/io/github/changeimpact/analyze/jar/JarLocationResult.java`, `src/main/java/io/github/changeimpact/analyze/jar/JarLocatorException.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/StableHashMethodVisitor.java`, `src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphEngine.java`, `src/main/java/io/github/changeimpact/analyze/callgraph/CallGraph.java`, `src/main/java/io/github/changeimpact/analyze/callgraph/CallEdge.java`, `src/main/java/io/github/changeimpact/analyze/callgraph/MethodId.java`, `src/main/java/io/github/changeimpact/analyze/impact/ImpactTracer.java`, `src/main/java/io/github/changeimpact/analyze/impact/ImpactResult.java`, `src/main/java/io/github/changeimpact/analyze/impact/ImpactPath.java`, `src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java`, `src/main/java/io/github/changeimpact/analyze/report/ReportException.java`
- Related: [Project Overview](project/project-overview.md), [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md), [Git Workspace Management](features/git-workspace-management.md), [Maven Build Runner](features/maven-build-runner.md), [Dependency Tree Extraction](features/dependency-tree-extraction.md), [Dependency Diff Engine](features/dependency-diff-engine.md), [Jar Locator](features/jar-locator.md), [Bytecode Diff Engine](features/bytecode-diff-engine.md), [Report Generator](features/report-generator.md)

## Features

### [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md)
- Summary: picocli CLI 参数解析、校验、退出码控制和诊断事件收集框架。`--project` 可选，默认当前目录。`--build-java-home` 可选，覆盖 Maven 子进程的 JAVA_HOME。`--include-change-kinds` 可选，逗号分隔 ChangePointKind，大小写不敏感，默认 6 种非 ADDED 类型。
- Code: `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java`, `src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java`, `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Git Workspace Management](features/git-workspace-management.md)
- Summary: 使用 git worktree 隔离 baseline/target workspace，支持子目录 project 路径对齐和 current workspace 模式，自动清理临时 worktree。
- Code: `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java`, `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandRunner.java`, `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandResult.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceResult.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSideInfo.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSide.java`, `src/main/java/io/github/changeimpact/analyze/workspace/WorkspacePrepareException.java`, `src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Maven Build Runner](features/maven-build-runner.md)
- Summary: 调用用户环境 `mvn compile` 编译 workspace，收集 main classes 目录。支持 `--build-java-home` 覆盖 Maven 子进程 JAVA_HOME 实现 JDK 隔离。
- Code: `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildResult.java`, `src/main/java/io/github/changeimpact/analyze/build/ModuleBuildOutput.java`, `src/main/java/io/github/changeimpact/analyze/build/BuildException.java`, `src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md)

### [Dependency Tree Extraction](features/dependency-tree-extraction.md)
- Summary: 调用 Maven dependency plugin 输出 GraphML，解析为结构化 resolved dependency tree。支持 `--build-java-home` 覆盖 Maven 子进程 JAVA_HOME 实现 JDK 隔离。
- Code: `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java`, `src/main/java/io/github/changeimpact/analyze/dependency/GraphMLParser.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ArtifactCoord.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyNode.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyScope.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ModuleDependencyTree.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalysisException.java`, `src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Diff Engine](features/dependency-diff-engine.md)

### [Dependency Diff Engine](features/dependency-diff-engine.md)
- Summary: 对比两侧 resolved dependency tree，按模块维度 union diff，生成依赖变动清单。
- Code: `src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java`, `src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Tree Extraction](features/dependency-tree-extraction.md), [Jar Locator](features/jar-locator.md)

### [Jar Locator](features/jar-locator.md)
- Summary: 为 VERSION_CHANGED 依赖定位 Maven local repository 中的 old/new jar 文件。
- Code: `src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java`, `src/main/java/io/github/changeimpact/analyze/jar/JarLocationResult.java`, `src/main/java/io/github/changeimpact/analyze/jar/JarLocatorException.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Diff Engine](features/dependency-diff-engine.md)

### [Bytecode Diff Engine](features/bytecode-diff-engine.md)
- Summary: 对 VERSION_CHANGED 依赖的 old/new jar 执行 bytecode diff，使用 ASM 9.7 + SHA-256 body hash，生成 ChangePoint 清单。支持通过构造函数按 `ChangePointKind` 过滤，默认排除 ADDED 类型。
- Code: `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java`, `src/main/java/io/github/changeimpact/analyze/bytecode/StableHashMethodVisitor.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [Dependency Diff Engine](features/dependency-diff-engine.md), [Jar Locator](features/jar-locator.md)

### [Report Generator](features/report-generator.md)
- Summary: 生成多文件 HTML 或 Markdown 变更影响分析报告（index + 3 个子文件），按模块分组展示依赖变动、变化点、影响路径和诊断信息。
- Code: `src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java`, `src/main/java/io/github/changeimpact/analyze/report/ReportException.java`
- Related: [Analysis Pipeline Architecture](architecture/analysis-pipeline.md), [CLI Validation and Diagnostics](features/cli-validation-diagnostics.md)

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
