---
title: "Dependency Tree Extraction"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "依赖树提取是分析流水线的第四阶段"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "依赖树提取产物供 Dependency Diff Engine 对比"
  - path: "wiki/features/jar-locator.md"
    desc: "ArtifactCoord 的 classifier 字段用于 Jar 定位时的文件名计算"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Maven dependency plugin 命令执行必须遵守跨平台命令解析规则"
  - path: "wiki/features/maven-runtime.md"
    desc: "impact dependency extraction 使用共享 Maven runtime"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "调用 Maven dependency plugin 并解析 GraphML"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/GraphMLParser.java"
    desc: "GraphML 文件解析器"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/ArtifactCoord.java"
    desc: "Maven artifact 坐标"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyNode.java"
    desc: "依赖树节点"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyScope.java"
    desc: "依赖 scope 枚举"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/ModuleDependencyTree.java"
    desc: "模块依赖树"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalysisException.java"
    desc: "依赖分析异常"
  - path: "src/main/java/io/github/dependencyanalysis/util/CommandResolver.java"
    desc: "跨平台命令解析工具"
---

# Feature: Dependency Tree Extraction

## Summary

Dependency Tree Extraction 是 `impact` pipeline 的兼容层，基于 Maven 实际解析结果调用 `maven-dependency-plugin:tree` 输出 GraphML，再解析为 baseline/target `ModuleDependencyTree`。Repository `tree` 功能使用独立 text parser，不替换此算法。

## Design Decisions

- 使用 Maven dependency plugin 的 GraphML 输出作为依赖树交换格式，避免自行实现 Maven 依赖调解。
- baseline 依赖树用于提取 reactor module 坐标，target 依赖树用这些坐标排除 reactor module 依赖。
- Maven executable、`--java-home` 和安全 user Maven arguments 来自 impact preflight prepared context。
- 保留既有 GraphML 算法，避免未经证明的 text parser migration 改变 impact 分析语义。
- Maven 命令统一经过 `CommandResolver.resolve()`，确保 Windows 上可解析 `mvn.cmd`。

## Actors / Entrypoints

- CLI pipeline 对 baseline 和 target side 分别创建 `DependencyAnalyzer`。
- `DependencyAnalyzer.analyze()` 是依赖树提取入口。
- `GraphMLParser.parse()` 是 GraphML 到 `ModuleDependencyTree` 的解析入口。

## Behavior Contract

- 执行命令为 `mvn dependency:tree -DoutputType=graphml -DoutputFile=dep-tree.graphml -B`。
- 每个模块的 `dep-tree.graphml` 被解析为一棵 `DependencyNode` 树。
- 解析保留 compile、runtime 和 provided scope，忽略 test scope。
- `ModuleDependencyTree` 保留模块坐标、模块路径和依赖树根节点集合。
- target side 解析时排除传入的 reactor module 坐标，避免把项目内部模块当作第三方依赖。
- GraphML 缺失、为空、不可解析或 plugin 失败时抛出 `DependencyAnalysisException`。
- `ArtifactCoord` 必须保留 groupId、artifactId、type、version 和 classifier，以支持后续 diff 和 jar 路径计算。

## Core Flow

1. `DependencyAnalyzer` 接收 side、workspace、reactor 模块坐标集合、DiagnosticCollector 和可选 `buildJavaHome`。
2. `analyze()` 执行 Maven dependency plugin，日志写入临时文件。
3. 命令经 `CommandResolver.resolve()` 处理，并在需要时覆盖 Maven 子进程 `JAVA_HOME`。
4. 查找 workspace 中生成的 `dep-tree.graphml` 文件。
5. 对每个 GraphML 文件调用 `GraphMLParser.parse()`。
6. GraphML parser 读取 XML 节点和边，构建 `DependencyNode` 树。
7. 返回 `ModuleDependencyTree` 列表。

## Acceptance Criteria

### Functional

- Given 单模块 Maven 项目，When `analyze()` 成功，Then 返回包含模块坐标和 resolved dependency tree 的列表。
- Given 多模块 Maven 项目，When `analyze()` 成功，Then 每个模块生成独立 `ModuleDependencyTree`。
- Given 依赖 scope 为 test，When GraphML 被解析，Then 该依赖不进入结果树。
- Given target analyzer 传入 reactor 坐标，When 解析 target dependency tree，Then reactor module 依赖被排除。
- Given GraphML 文件缺失或不可解析，When `analyze()` 处理结果，Then 抛出 `DependencyAnalysisException`。

### Non-Functional

- [ ] 依赖解析必须使用用户 Maven 环境，保证 mirror、proxy、settings 和 local repository 行为一致。
- [ ] GraphML 解析必须 deterministic，支撑后续 diff 和报告 snapshot 稳定。
- [ ] 命令执行必须跨平台，遵守 `CommandResolver` 规则。

## Edge Cases

- classifier 为空字符串时仍保留为空值语义，供 Jar Locator 生成无 classifier 的 jar 文件名。
- provided scope 保留在结果中，后续 Dependency Diff Engine 用它标记 compile-time API risk。
- dependency plugin 失败时保留日志摘要和日志文件路径，供诊断定位。

## Implementation Boundaries

- Dependency Tree Extraction 只生产 resolved dependency tree，不比较 baseline/target 差异。
- Reactor module 排除只影响 target 第三方依赖视图，不改变 Maven 原始解析结果。
- Artifact 坐标和 scope 是后续 diff、jar 定位和报告的稳定数据边界。
