---
title: "Dependency Diff Engine"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "依赖变动对比是分析流水线的第五阶段"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "依赖变动对比依赖前序阶段产出的 resolved dependency tree"
  - path: "wiki/features/jar-locator.md"
    desc: "VERSION_CHANGED coordinate 由 command-scoped repository 打开 JAR"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "VERSION_CHANGED 依赖变动最终供 Bytecode Diff Engine 执行 bytecode diff"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyDiffEngine.java"
    desc: "依赖变动对比引擎主实现"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyChange.java"
    desc: "单条依赖变动不可变数据类"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ChangeType.java"
    desc: "变动类型枚举"
---

# Feature: Dependency Diff Engine

## Summary

Dependency Diff Engine 对比 baseline 和 target 的 resolved dependency tree，按模块维度生成 `DependencyChange` 列表。它识别新增、移除和版本变更三类依赖变动，并输出稳定排序的不可变结果。

## Design Decisions

- 依赖 diff 以模块为边界，对 baseline 和 target 的模块集合做 union diff，避免只比较共同模块时遗漏整模块新增或移除。
- 依赖树 flatten 使用 DFS 和 `putIfAbsent` 先入为主去重，使同一 artifact 的传递重复路径只产生一个比较对象。
- 输出列表保持不可变并执行稳定排序，保证 CLI 报告和 snapshot 测试具有确定性。
- `provided` scope 的版本变更通过 `isCompileTimeApiRisk()` 标记 compile-time API risk，供报告层展示。

## Actors / Entrypoints

- CLI pipeline 在 baseline 和 target dependency tree 都准备完成后调用 diff。
- `DependencyDiffEngine.diff(baselineTrees, targetTrees)` 是功能入口。

## Behavior Contract

- 两侧模块集合取 union，模块 key 使用 module artifact 的 `diffKey()`。
- 仅存在于 target 的模块，其所有依赖标记为 `ADDED`。
- 仅存在于 baseline 的模块，其所有依赖标记为 `REMOVED`。
- 两侧均存在的模块按 artifact `diffKey()` 对比版本和存在性。
- 仅存在于 target 的 artifact 标记为 `ADDED`；仅存在于 baseline 的 artifact 标记为 `REMOVED`。
- 两侧均存在但版本不同的 artifact 标记为 `VERSION_CHANGED`。
- 两侧均存在且版本相同的 artifact 不产生变动。
- 返回结果排序规则为 module 字典序、ChangeType ordinal、artifact diffKey 字典序。
- `DependencyChange` 构造器必须校验 `ChangeType` 与 old/new artifact 空值组合的一致性。

## Core Flow

1. `diff()` 接收 baseline 和 target 的 `ModuleDependencyTree` 列表。
2. 分别按 module `diffKey()` 索引模块。
3. 对模块 key 做 union，逐模块执行比较。
4. 模块缺失时将另一侧 flatten 后的依赖全部标记为新增或移除。
5. 模块两侧都存在时，对依赖树做 DFS flatten 并按 artifact key 比较。
6. 构造 `DependencyChange` 并收集到结果列表。
7. 对结果执行稳定排序并返回不可变列表。

## Acceptance Criteria

### Functional

- Given baseline 和 target 都为空，When 执行 diff，Then 返回空列表。
- Given 模块只存在于 baseline，When 执行 diff，Then 该模块依赖全部标记为 `REMOVED`。
- Given 模块只存在于 target，When 执行 diff，Then 该模块依赖全部标记为 `ADDED`。
- Given 同一 artifact 版本不同，When 执行 diff，Then 生成 `VERSION_CHANGED`。
- Given 同一 artifact 版本相同，When 执行 diff，Then 不生成变动。
- Given provided scope 的 `VERSION_CHANGED`，When 报告判断风险，Then `isCompileTimeApiRisk()` 返回 true。

### Non-Functional

- [ ] diff 输出必须 deterministic，支持报告 snapshot 和跨次运行审计。
- [ ] 返回集合必须不可变，避免后续阶段意外修改依赖变动结果。
- [ ] 依赖比较必须以 `ArtifactCoord.diffKey()` 为稳定边界，而不是原始显示文本。

## Edge Cases

- 依赖树中同一 artifact 通过多条传递路径出现时，flatten 保留第一次出现的节点。
- `DependencyChange` 的 old/new artifact 组合与 ChangeType 不匹配时立即抛出 `IllegalArgumentException`。
- 新增和移除依赖不进入 JAR diff；只有 `VERSION_CHANGED` 会继续通过 `IJarRepository` 进入 bytecode diff。

## Implementation Boundaries

- Dependency Diff Engine 不解析 Maven、不过滤 scope，也不访问 JAR。
- `DependencyChange` 是后续 coordinate repository、Bytecode Diff Engine 和 Report Generator 的共享 logical 数据合同，不包含 dependency JAR path。
- API risk 判断只基于依赖 scope，不推断调用路径或实际业务影响。
