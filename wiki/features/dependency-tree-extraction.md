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
  - path: "src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java"
    desc: "跨平台命令解析，Windows 上通过 cmd.exe /c 包裹命令"
---

# Feature: Dependency Tree Extraction

## Summary

基于 Maven 实际解析结果提取 resolved dependency tree。调用 `maven-dependency-plugin:tree` 输出 GraphML 格式，解析为结构化依赖树，保留传递依赖和依赖调解结果。支持通过 `--build-java-home` 覆盖 Maven 子进程的 `JAVA_HOME`，使工具运行在 JDK 17+ 的同时目标项目可使用旧版 JDK 执行依赖解析。

## Design Decisions

- 与 BuildRunner 一致，通过 `ProcessBuilder.environment()` 覆盖 `JAVA_HOME` 实现 JDK 隔离。
- 提供向后兼容的 4 参数构造函数重载，不传 `buildJavaHome` 时 Maven 继承当前 JVM 的 `JAVA_HOME`，行为与旧版本一致。
- Windows 上通过 `CommandResolver.resolve()` 将命令包裹为 `cmd.exe /c ...`，利用 `cmd.exe` 的 `PATHEXT` 解析能力找到 `mvn.cmd`。Linux/macOS 不经过任何转换。所有 ProcessBuilder 命令调用必须使用 `CommandResolver.resolve()`。

## Behavior

- 调用 `mvn dependency:tree -DoutputType=graphml -DoutputFile=dep-tree.graphml -B`。Windows 上通过 `CommandResolver.resolve()` 将命令包裹为 `cmd.exe /c mvn ...`，使 `cmd.exe` 负责 `PATHEXT` 解析，解决 `ProcessBuilder` 无法直接找到 `mvn.cmd` 的问题。Linux/macOS 上命令列表不经过任何转换。
- 为每个模块提取 GraphML 文件。
- 解析 GraphML：提取节点标签（artifact 坐标）和边（依赖关系）。
- 生成 `DependencyNode` 树结构。
- 保留 scope：compile、runtime、provided。
- 忽略 scope：test。
- 排除 reactor module 依赖（通过传入的 reactor 坐标集合）。
- 保留 module 归属（`ModuleDependencyTree` 包含模块坐标和模块路径）。
- GraphML 缺失、为空、不可解析时抛出 `DependencyAnalysisException`。
- 当构造时传入 `buildJavaHome`（非 null），在 `ProcessBuilder.environment()` 中覆盖 `JAVA_HOME` 为该路径的绝对路径。
- 当 `buildJavaHome` 为 null 时，不覆盖环境变量，Maven 继承当前进程的 `JAVA_HOME`。

## Flow

1. `DependencyAnalyzer` 接收 side 名称、workspace 路径、reactor 模块坐标集合、DiagnosticCollector 和可选的 `buildJavaHome`。
2. `analyze()` 执行 `mvn dependency:tree`，日志写入临时文件。
3. `runDependencyTree()` 构建命令列表后通过 `CommandResolver.resolve()` 处理（Windows 包裹 `cmd.exe /c`），再构建 ProcessBuilder，若 `buildJavaHome` 非 null 则覆盖 `JAVA_HOME` 环境变量。
4. plugin 执行失败时抛出 `DependencyAnalysisException`。
5. 查找所有 `dep-tree.graphml` 文件。
6. 对每个 GraphML 文件调用 `GraphMLParser.parse()`。
7. `GraphMLParser` 解析 XML，提取节点和边，构建树结构。
8. 返回 `ModuleDependencyTree` 列表。

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
- 单元测试：`src/test/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzerConstructorTest.java`
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/dependency/DependencyAnalyzerIT.java`
- 单模块 dependency tree 可解析。
- 多模块 dependency tree 可解析。
- compile/runtime/provided scope 被保留。
- test scope 被忽略。
- reactor module 不作为第三方依赖。
- GraphML 缺失/不可解析时报错。
- 4 参数构造函数向后兼容，`buildJavaHome` 默认为 null。
- 5 参数构造函数传入 `buildJavaHome` 时，`ProcessBuilder` 环境变量 `JAVA_HOME` 被正确覆盖。
