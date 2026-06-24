---
title: "Analysis Pipeline Architecture"
type: architecture
relations:
  - path: "wiki/project/change-impact-analyze.md"
    desc: "项目概览和技术栈"
  - path: "wiki/features/cli-validation-diagnostics.md"
    desc: "CLI 参数校验与诊断框架"
  - path: "wiki/features/git-workspace-management.md"
    desc: "Git workspace 管理"
  - path: "wiki/features/maven-build-runner.md"
    desc: "Maven 编译执行"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "依赖树提取与解析"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "依赖变动对比"
  - path: "wiki/features/jar-locator.md"
    desc: "Jar 文件定位"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "Bytecode diff 引擎"
  - path: "wiki/features/report-generator.md"
    desc: "HTML/Markdown 报告生成"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java"
    desc: "CLI 主入口，总流程编排"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java"
    desc: "诊断事件收集器，贯穿所有阶段"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java"
    desc: "Workspace 管理模块"
  - path: "src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java"
    desc: "Maven 编译执行模块"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java"
    desc: "依赖树提取模块"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java"
    desc: "依赖变动对比模块"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java"
    desc: "依赖变动数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java"
    desc: "变动类型枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java"
    desc: "Jar 文件定位模块"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocationResult.java"
    desc: "Jar 定位结果数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocatorException.java"
    desc: "Jar 定位异常"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java"
    desc: "Bytecode diff 核心引擎"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java"
    desc: "Bytecode 变化点数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java"
    desc: "9 种变化类型枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java"
    desc: "Bytecode diff 异常"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java"
    desc: "Jar → class index 索引器"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/StableHashMethodVisitor.java"
    desc: "Method body SHA-256 hash visitor"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphEngine.java"
    desc: "WALA RTA Call Graph 构建引擎"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraph.java"
    desc: "不可变 Call Graph 数据模型"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallEdge.java"
    desc: "Call Graph 边数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/MethodId.java"
    desc: "方法唯一标识"
  - path: "src/main/java/io/github/changeimpact/analyze/impact/ImpactTracer.java"
    desc: "从变化点反向追踪受影响业务方法"
  - path: "src/main/java/io/github/changeimpact/analyze/impact/ImpactResult.java"
    desc: "影响追踪结果数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/impact/ImpactPath.java"
    desc: "单条影响路径数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java"
    desc: "HTML/Markdown 报告生成器"
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportException.java"
    desc: "报告生成异常"
---

# Architecture: Analysis Pipeline Architecture

## Summary

分析流水线采用线性阶段架构，每个阶段独立可验证，后续阶段依赖前序阶段产物。所有阶段共享统一的诊断框架，通过 `DiagnosticCollector` 收集阶段日志、耗时和失败原因。

## Structure

- **CLI Layer**: 参数解析、校验、退出码、总流程编排。
- **Diagnostics**: 贯穿所有阶段的诊断事件收集，支持 stage/level/message/side/module/artifact/path/elapsedMillis。
- **WorkspaceManager**: 准备 baseline、target、current workspace 三类分析输入，使用 git worktree 隔离。
- **BuildRunner**: 调用用户环境默认 `mvn` 编译，收集 main classes。
- **DependencyAnalyzer**: 调用 Maven dependency plugin 输出 GraphML 并解析为结构化依赖树。
- **DependencyDiffEngine**: 对比两侧 resolved dependency tree，按模块维度 union diff，生成 `DependencyChange` 清单。
- **JarLocator**: 从 Maven local repository 定位 version changed 依赖的 old/new jar 文件。
- **BytecodeDiffEngine**: 对 old/new jar 做 bytecode diff，使用 ASM 9.7 读取 class 文件，通过 SHA-256 body hash 检测 method body 变化，生成 `ChangePoint` 清单。
- **CallGraphEngine**: 基于业务代码 main classes 使用 WALA RTA 构建全局 Call Graph，并通过 ServiceLoader 和 Reflection enricher 补充间接调用边。
- **ImpactTracer**: 从变化点反向追踪受影响业务方法，四阶段流程：resolve seeds → reverse BFS → build paths → sort。
- **ReportGenerator**: 生成多文件 HTML 或 Markdown 报告，按模块分组展示依赖变动、变化点、影响路径和诊断信息。

## Architecture Diagram

```mermaid
flowchart TD
    CLI["CLI Layer\n参数解析/校验"] --> Diag["DiagnosticCollector\n诊断框架"]
    CLI --> WS["WorkspaceManager\nbaseline/target/current"]
    WS --> Build["BuildRunner\nmvn compile"]
    Build --> Dep["DependencyAnalyzer\nGraphML 解析"]
    Dep --> Diff["DependencyDiffEngine\n依赖变动"]
    Diff --> Jar["JarLocator\n定位 jar"]
    Jar --> BDiff["BytecodeDiffEngine\nbytecode diff"]
    BDiff --> CG["CallGraphEngine\n构建 Call Graph"]
    CG --> Trace["ImpactTracer\n反向追踪"]
    Trace --> Report["ReportGenerator\nHTML/Markdown"]
    Diag -.-> WS
    Diag -.-> Build
    Diag -.-> Dep
    Diag -.-> Diff
    Diag -.-> Jar
    Diag -.-> BDiff
    Diag -.-> CG
    Diag -.-> Trace
    Diag -.-> Report
```

## Key Terms

- `side` - 分析的一侧，取值为 `baseline`、`target` 或 `current`。
- `workspace` - 一个 side 对应的文件系统路径，可以是临时 worktree 或用户当前工作目录。
- `reactor module` - 多模块 Maven 项目中，属于同一构建反应的模块，不作为第三方依赖处理。
- `DependencyNode` - resolved dependency tree 中的一个节点，包含 artifact 坐标、scope 和子节点。
- `ModuleDependencyTree` - 一个 Maven 模块的完整依赖树，包含模块坐标和所有 DependencyNode。

## Architecture Decision Records

- **使用用户环境默认 `mvn`**：不内嵌 Maven Resolver，不绕过用户 `settings.xml`、mirror、proxy、local repository。保证构建行为与用户真实环境一致。
- **Git worktree 隔离**：baseline 和 target commit 使用 `git worktree` 创建临时目录，避免 checkout 污染用户工作区。current workspace 不 checkout、不 stash。
- **GraphML 作为依赖树交换格式**：通过 `maven-dependency-plugin:tree -DoutputType=graphml` 获取 resolved dependency tree，保留传递依赖和依赖调解结果。
- **阶段线性编排**：每个阶段独立可验证，后续阶段依赖前序阶段产物。任一阶段失败即终止，不继续后续阶段。

## Runtime Flow

1. CLI 解析参数并校验（validation stage）。
2. `WorkspaceManager` 准备 baseline 和 target workspace。
3. `BuildRunner` 对每个 side 执行 `mvn compile`，收集 main classes。
4. `DependencyAnalyzer` 对 baseline 执行 `mvn dependency:tree`，解析 GraphML，提取 reactor 模块坐标。
5. `DependencyAnalyzer` 对 target 执行 `mvn dependency:tree`，排除 reactor 模块。
6. `DependencyDiffEngine` 对比两侧依赖树，按模块维度 union diff，生成 `DependencyChange` 清单。
7. `JarLocator` 为 `version_changed` 依赖定位 old/new jar。
8. `BytecodeDiffEngine` 对 jar 做 bytecode diff，生成 `ChangePoint`。
9. `CallGraphEngine` 基于 target build 的 main classes 构建全局 Call Graph。
10. `ImpactTracer` 从变化点反向追踪受影响业务方法，生成 `ImpactResult`。
11. `ReportGenerator` 生成最终 HTML 或 Markdown 报告。
