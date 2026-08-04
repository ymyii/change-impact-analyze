---
title: "Jar Locator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Jar 定位是分析流水线的第六阶段"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "Jar 定位依赖前序阶段产出的 DependencyChange 清单"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "Jar 定位产物供 Bytecode Diff Engine 执行 bytecode diff"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/JarLocator.java"
    desc: "Jar 定位主实现"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/JarLocationResult.java"
    desc: "Jar 定位结果数据类"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/JarLocatorException.java"
    desc: "Jar 定位异常，携带诊断字段"
---

# Feature: Jar Locator

## Summary

Jar Locator 为 `VERSION_CHANGED` 类型的依赖变动定位 Maven local repository 中的 old/new jar 文件。它根据 artifact 坐标计算标准 Maven repository 路径，并在文件缺失时暴露完整诊断字段。

## Design Decisions

- Jar 定位只处理 `VERSION_CHANGED` 依赖，因为 bytecode diff 需要 old/new 两侧 jar；`ADDED` 和 `REMOVED` 不进入该阶段。
- 默认 repository root 使用 `~/.m2/repository`，同时保留构造函数注入能力以支持测试或自定义本地仓库。
- 通过 Maven local repository 标准路径计算 jar 位置，不解析远程仓库、不触发下载。
- 缺失文件由 `JarLocatorException` 暴露 artifact、side、expectedPath 和 repositoryRoot，便于用户修复本地 Maven 缓存。

## Actors / Entrypoints

- CLI pipeline 在 Dependency Diff Engine 之后调用 Jar Locator。
- `JarLocator.locate(changes)` 是定位入口。

## Behavior Contract

- 仅处理 `ChangeType.VERSION_CHANGED` 的 `DependencyChange`。
- 路径格式为 `{repoRoot}/{groupId path}/{artifactId}/{version}/{artifactId}-{version}[-{classifier}].{type}`。
- groupId 中的 `.` 必须转换为目录分隔路径。
- classifier 为空字符串时文件名不包含 classifier 段；非空时插入 `-{classifier}`。
- old jar 和 new jar 都必须存在。
- 任一 jar 缺失时抛出 `JarLocatorException`，并标明缺失 side 为 `old` 或 `new`。
- 成功结果为 `JarLocationResult` 列表，每项包含原始 `DependencyChange`、old jar path 和 new jar path。

## Core Flow

1. `locate()` 接收依赖变动列表。
2. 跳过非 `VERSION_CHANGED` 条目。
3. 对 old artifact 和 new artifact 分别调用 jar path 计算逻辑。
4. 校验 old jar 存在性。
5. 校验 new jar 存在性。
6. 构造 `JarLocationResult` 并加入结果列表。
7. 返回完整定位结果。

## Acceptance Criteria

### Functional

- Given `VERSION_CHANGED` 依赖且 old/new jar 都存在，When `locate()` 执行，Then 返回对应 `JarLocationResult`。
- Given `ADDED` 或 `REMOVED` 依赖，When `locate()` 执行，Then 该变动被跳过。
- Given artifact groupId 包含 `.`，When 计算路径，Then `.` 转换为 repository 子目录。
- Given classifier 为空，When 计算文件名，Then 文件名不包含 classifier 段。
- Given old jar 缺失，When `locate()` 执行，Then 抛出 side 为 `old` 的 `JarLocatorException`。
- Given new jar 缺失，When `locate()` 执行，Then 抛出 side 为 `new` 的 `JarLocatorException`。

### Non-Functional

- [ ] Jar 定位不得访问网络或修改 Maven repository。
- [ ] 缺失 jar 的诊断字段必须足够用户直接定位 expected path。
- [ ] 路径计算必须兼容 classifier 和非 jar type。

## Edge Cases

- 自定义 repository root 可用于测试或非默认 Maven local repository。
- classifier 非空时必须出现在 artifactId 与 type 扩展名之间。
- 缺失任一侧 jar 时整个定位阶段失败，不产出部分成功报告。

## Implementation Boundaries

- Jar Locator 不判断依赖是否需要 bytecode diff；该筛选由 ChangeType 合同决定。
- Jar Locator 不执行 Maven 下载，不解析远程仓库，也不校验 jar 内容。
- `JarLocationResult` 是 Bytecode Diff Engine 的输入边界。
