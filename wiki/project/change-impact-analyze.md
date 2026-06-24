---
title: "Change Impact Analyze"
type: project
relations:
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包操作手册"
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "分析流水线架构和模块边界"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "外部命令执行的跨平台约束"
code_refs:
  - path: "pom.xml"
    desc: "Maven 构建配置，依赖和插件定义"
  - path: "src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java"
    desc: "CLI 主入口"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java"
    desc: "诊断事件收集器"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java"
    desc: "Git workspace 管理核心"
  - path: "src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java"
    desc: "Maven 编译执行器"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java"
    desc: "依赖树提取与 GraphML 解析"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java"
    desc: "Maven local repository jar 定位"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java"
    desc: "Bytecode diff 核心引擎"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphEngine.java"
    desc: "业务代码 Call Graph 构建"
  - path: "src/main/java/io/github/changeimpact/analyze/impact/ImpactTracer.java"
    desc: "变化点到业务方法的影响追踪"
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java"
    desc: "HTML/Markdown 报告生成"
---

# Project: Change Impact Analyze

## Summary

Change Impact Analyze 是一个命令行变更影响分析工具，用于分析 Java 8 Maven 项目在依赖升级后的静态影响范围，输出可审计的 HTML 或 Markdown 报告。项目采用 Maven 构建，Java 17 运行时，picocli 作为 CLI 框架。核心能力包括 CLI 参数校验与诊断框架、Git workspace 管理、Maven 编译执行、依赖树提取、依赖 diff、jar 定位、bytecode diff、Call Graph 构建、影响追踪和报告生成。

## Design Decisions

- 工具自身使用 Java 17 运行和构建，目标分析对象保持为 Java 8 Maven 项目，编译目标项目时通过用户环境 Maven 与可选 `--build-java-home` 保持兼容边界。
- 分析结果以文件化 HTML/Markdown 报告为交付形式，保留依赖变动、bytecode 变化点、影响路径和诊断事件，便于代码评审和审计。

## Module Map

- `src/main/java/io/github/changeimpact/analyze/cli/` - CLI 入口和命令行参数解析。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/` - 诊断框架，收集和结构化阶段诊断事件。
- `src/main/java/io/github/changeimpact/analyze/workspace/` - Git workspace 管理，使用 git worktree 隔离 baseline/target 工作目录，支持 current workspace mode，自动清理临时 worktree。
- `src/main/java/io/github/changeimpact/analyze/build/` - Maven 编译执行，收集 main classes 目录。
- `src/main/java/io/github/changeimpact/analyze/dependency/` - 依赖树提取，调用 Maven dependency plugin 生成 GraphML 并解析为结构化依赖树。
- `src/main/java/io/github/changeimpact/analyze/jar/` - 根据 `VERSION_CHANGED` 依赖定位 Maven local repository 中的 old/new jar。
- `src/main/java/io/github/changeimpact/analyze/bytecode/` - 对 old/new jar 执行 bytecode diff，产出 `ChangePoint`。
- `src/main/java/io/github/changeimpact/analyze/callgraph/` - 基于 target main classes 构建全局 Call Graph。
- `src/main/java/io/github/changeimpact/analyze/impact/` - 从变化点反向追踪受影响业务方法，生成影响路径。
- `src/main/java/io/github/changeimpact/analyze/report/` - 生成 HTML/Markdown 报告。
- `src/main/java/io/github/changeimpact/analyze/util/` - 跨平台命令解析等通用工具。
- `src/test/java/` - 单元测试。
- `src/integration-test/java/` - 集成测试（通过 build-helper-maven-plugin 注册为测试源码目录）。

## Technical Stack

- **Runtime**: Java 17（`maven.compiler.release=17`）。
- **Build**: Maven（`pom.xml`），uber-jar 通过 maven-shade-plugin 打包。
- **CLI**: picocli 4.7.6，命令行参数解析和 `--help` 生成。
- **Logging**: slf4j-simple 2.0.13（SLF4J 2.0 API + simple 实现）。
- **Testing**: JUnit Jupiter 5.10.2 + AssertJ 3.25.3。
- **Code Style**: maven-checkstyle-plugin 3.3.1，使用 `sun_checks.xml`，在 `validate` 阶段执行，`failsOnError=true`。
- **Static Analysis**: ASM 9.7 支撑 bytecode diff 和应用 bytecode 扫描；WALA 1.6.13 构建 Call Graph。

## Main Entrypoints

- `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java` - CLI 主类，picocli `@Command` 注解定义命令名、版本和帮助信息。`main()` 方法为 JVM 入口。

## Repository Conventions

- 基础包路径：`io.github.changeimpact.analyze`。
- 单元测试放在 `src/test/java/`，与主代码包结构一致。
- 集成测试放在 `src/integration-test/java/`，与主代码包结构一致。
- 构建产物输出到 `target/`，uber-jar 名为 `change-impact-analyze.jar`。
- 临时文件和设计文档放在 `tmp-files/`，不提交 git。
- 通过 `ProcessBuilder` 启动外部命令时遵守 `wiki/rules/process-command-resolution.md`。
