---
title: "Dependency Diff Engine"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "依赖变动对比是分析流水线的第五阶段"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "依赖变动对比依赖前序阶段产出的 resolved dependency tree"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java"
    desc: "依赖变动对比引擎主实现"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java"
    desc: "单条依赖变动不可变数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java"
    desc: "变动类型枚举（ADDED/REMOVED/VERSION_CHANGED）"
---

# Feature: Dependency Diff Engine

## Summary

对比 baseline 和 target 两侧的 resolved dependency tree，按模块维度生成依赖变动清单（`DependencyChange` 列表）。支持新增、移除和版本变更三类变动，结果按模块、变动类型、artifact 三级稳定排序。

## Behavior

- 以模块为粒度做 union diff：baseline 和 target 的模块集合取并集。
- 仅存在于 target 的模块：该模块所有依赖标记为 `ADDED`。
- 仅存在于 baseline 的模块：该模块所有依赖标记为 `REMOVED`。
- 两侧均存在的模块：逐 artifact 对比版本。
- 仅存在于 target 的 artifact：`ADDED`。
- 仅存在于 baseline 的 artifact：`REMOVED`。
- 两侧均存在但版本不同：`VERSION_CHANGED`。
- 两侧均存在且版本相同：不产生变动。
- 对依赖树做 DFS flatten，先入为主去重（`putIfAbsent`），以 `ArtifactCoord.diffKey()` 为去重键。
- `DependencyChange.module` 使用 `ArtifactCoord.toString()` 格式（`groupId:artifactId:type:version`）。
- `DependencyChange` 构造器校验 artifact 空值与 `ChangeType` 的一致性，不一致时抛出 `IllegalArgumentException`。
- `isCompileTimeApiRisk()` 返回 `true` 当且仅当 scope 为 `PROVIDED`，标识编译期 API 风险。
- 返回结果为不可变列表（`Collections.unmodifiableList`）。
- 两侧输入均为空列表时返回空列表。
- 排序规则：module 字典序 → ChangeType ordinal 序（ADDED < REMOVED < VERSION_CHANGED） → artifact diffKey 字典序。

## Flow

1. `DependencyDiffEngine.diff()` 接收 baseline 和 target 的 `ModuleDependencyTree` 列表。
2. 分别按 `ArtifactCoord.diffKey()` 索引为 Map。
3. 取两侧模块 key 的并集（`LinkedHashSet` 保持插入序）。
4. 对每个模块调用 `diffModule()`，处理三种情况（仅 baseline/仅 target/两侧均有）。
5. 模块内通过 `flatten()` 将依赖树 DFS 展开为 `LinkedHashMap<diffKey, DependencyNode>`。
6. 对展开后的 artifact 集合做 union diff，生成 `DependencyChange`。
7. 全部变动收集完毕后，按三级 comparator 排序。
8. 返回不可变列表。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyDiffEngine.java` - 对比引擎，模块索引、DFS flatten、artifact diff、稳定排序。
- `src/main/java/io/github/changeimpact/analyze/dependency/DependencyChange.java` - 单条变动记录，含构造器校验和 `isCompileTimeApiRisk()`。
- `src/main/java/io/github/changeimpact/analyze/dependency/ChangeType.java` - 变动类型枚举，ordinal 顺序决定排序优先级。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/dependency/ChangeTypeTest.java`、`DependencyChangeTest.java`、`DependencyDiffEngineTest.java`。
- 空输入返回空列表。
- 仅 baseline 模块的所有依赖标记为 REMOVED。
- 仅 target 模块的所有依赖标记为 ADDED。
- 版本变更检测正确。
- 版本相同不产生变动。
- DFS flatten 先入为主去重。
- 三级排序稳定（module → changeType ordinal → artifact diffKey）。
- `DependencyChange` 构造器对 artifact 空值与 ChangeType 不一致时抛异常。
- `isCompileTimeApiRisk()` 仅对 PROVIDED scope 返回 true。
- 返回结果不可变。
