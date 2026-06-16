---
title: "Jar Locator"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Jar 定位是分析流水线的第六阶段"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "Jar 定位依赖前序阶段产出的 DependencyChange 清单"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "Jar 定位产物供 Bytecode Diff Engine 执行 bytecode diff"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java"
    desc: "Jar 定位主实现"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocationResult.java"
    desc: "Jar 定位结果数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/jar/JarLocatorException.java"
    desc: "Jar 定位异常，携带诊断字段"
---

# Feature: Jar Locator

## Summary

为 `VERSION_CHANGED` 类型的依赖变动定位 Maven local repository 中的 old/new jar 文件。根据 artifact 坐标计算标准 Maven repository 路径，校验文件存在性，缺失时抛出携带完整诊断信息的异常。

## Behavior

- 仅处理 `ChangeType.VERSION_CHANGED` 的 `DependencyChange`，其余类型（ADDED、REMOVED）过滤跳过。
- 默认使用 `~/.m2/repository` 作为 Maven local repository 根路径，支持自定义路径。
- 路径计算规则：`{repoRoot}/{groupId 的 . 替换为 /}/{artifactId}/{version}/{artifactId}-{version}[-{classifier}].{type}`。
- classifier 为空字符串时文件名不含 classifier 段；非空时插入 `-{classifier}`。
- old jar 和 new jar 均使用 `Files.exists()` 校验存在性。
- 任一 jar 缺失时抛出 `JarLocatorException`，携带 `artifactCoord`、`side`（"old" 或 "new"）、`expectedPath`、`repositoryRoot` 四个诊断字段。
- 返回结果为 `JarLocationResult` 列表，每个结果持有原始 `DependencyChange` 和 old/new jar 的 `Path`。

## Flow

1. `JarLocator.locate()` 接收 `List<DependencyChange>`。
2. 遍历列表，跳过非 `VERSION_CHANGED` 条目。
3. 对每条变动的 `oldArtifact` 和 `newArtifact` 分别调用 `resolveJarPath()` 计算期望路径。
4. 校验 old jar 路径存在性，缺失时抛异常。
5. 校验 new jar 路径存在性，缺失时抛异常。
6. 构建 `JarLocationResult` 并添加到结果列表。
7. 返回完整结果列表。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/jar/JarLocator.java` - 路径计算、过滤、存在性校验。
- `src/main/java/io/github/changeimpact/analyze/jar/JarLocationResult.java` - 不可变结果类，持有 DependencyChange + oldJar + newJar。
- `src/main/java/io/github/changeimpact/analyze/jar/JarLocatorException.java` - checked exception，携带 artifactCoord/side/expectedPath/repositoryRoot 诊断字段。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/jar/JarLocatorTest.java`、`JarLocationResultTest.java`、`JarLocatorExceptionTest.java`。
- 路径计算覆盖 groupId `.` → `/` 转换、classifier 空/非空、嵌套 groupId。
- VERSION_CHANGED 过滤逻辑正确，ADDED/REMOVED 被跳过。
- 异常诊断信息完整（artifactCoord、side、expectedPath、repositoryRoot）。
- 默认无参构造使用 `~/.m2/repository`。
