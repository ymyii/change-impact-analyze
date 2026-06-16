---
title: "Dependency Tree Extraction"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "依赖树提取是分析流水线的第四阶段"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "依赖树提取产物供 Dependency Diff Engine 对比"
  - path: "wiki/features/jar-locator.md"
    desc: "ArtifactCoord 的 classifier 字段用于 Jar 定位时的文件名计算"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java"
    desc: "调用 Maven dependency plugin 并解析 GraphML"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/GraphMLParser.java"
    desc: "GraphML 文件解析器"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/ArtifactCoord.java"
    desc: "Maven artifact 坐标"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyNode.java"
    desc: "依赖树节点"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyScope.java"
    desc: "依赖 scope 枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/ModuleDependencyTree.java"
    desc: "模块依赖树"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalysisException.java"
    desc: "依赖分析异常"
---

# Feature: Dependency Tree Extraction

## Summary

基于 Maven 实际解析结果提取 resolved dependency tree。调用 `maven-dependency-plugin:tree` 输出 GraphML 格式，解析为结构化依赖树，保留传递依赖和依赖调解结果。

## Behavior

- 调用 `mvn dependency:tree -DoutputType=graphml -DoutputFile=dep-tree.graphml -B`。
- 为每个模块提取 GraphML 文件。
- 解析 GraphML：提取节点标签（artifact 坐标）和边（依赖关系）。
- 生成 `DependencyNode` 树结构。
- 保留 scope：compile、runtime、provided。
- 忽略 scope：test。
- 排除 reactor module 依赖（通过传入的 reactor 坐标集合）。
- 保留 module 归属（`ModuleDependencyTree` 包含模块坐标和模块路径）。
- GraphML 缺失、为空、不可解析时抛出 `DependencyAnalysisException`。

## Flow

1. `DependencyAnalyzer` 接收 side 名称、workspace 路径、reactor 模块坐标集合和 DiagnosticCollector。
2. `analyze()` 执行 `mvn dependency:tree`，日志写入临时文件。
3. plugin 执行失败时抛出 `DependencyAnalysisException`。
4. 查找所有 `dep-tree.graphml` 文件。
5. 对每个 GraphML 文件调用 `GraphMLParser.parse()`。
6. `GraphMLParser` 解析 XML，提取节点和边，构建树结构。
7. 返回 `ModuleDependencyTree` 列表。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzer.java` - Maven plugin 调用、GraphML 文件发现。
- `src/main/java/io/github/changeimpact/analyze/dependency/GraphMLParser.java` - GraphML XML 解析，树结构构建。
- `src/main/java/io/github/changeimpact/analyze/dependency/ArtifactCoord.java` - artifact 坐标（groupId/artifactId/type/version/classifier），支持 `parse()` 和 `diffKey()`。
- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyNode.java` - 依赖树节点（artifact/scope/children）。
- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyScope.java` - scope 枚举：COMPILE、RUNTIME、PROVIDED、TEST。
- `src/main/java/io/github/changeimpact/analyze/dependency/ModuleDependencyTree.java` - 模块依赖树（moduleCoord/modulePath/dependencies）。
- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyAnalysisException.java` - 依赖分析异常。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/dependency/` 下的测试类。
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzerIT.java`
- 单模块 dependency tree 可解析。
- 多模块 dependency tree 可解析。
- compile/runtime/provided scope 被保留。
- test scope 被忽略。
- reactor module 不作为第三方依赖。
- GraphML 缺失/不可解析时报错。
