---
title: "Impact Tracing"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Impact Tracing 是报告前的受影响路径计算阶段"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "Impact Tracing 消费 Bytecode Diff Engine 产出的 ChangePoint"
  - path: "wiki/features/call-graph-engine.md"
    desc: "Impact Tracing 通过 Call Graph 反向追踪调用路径"
  - path: "wiki/features/report-generator.md"
    desc: "Report Generator 展示 ImpactResult"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactTracer.java"
    desc: "影响追踪主流程"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ChangePointRefScanner.java"
    desc: "应用 bytecode 中 ChangePoint 引用扫描器"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactResult.java"
    desc: "影响追踪结果"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactPath.java"
    desc: "单条影响路径"
  - path: "src/main/java/io/github/dependencyanalysis/impact/NotReportedReason.java"
    desc: "未报告影响路径的原因枚举"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactException.java"
    desc: "影响追踪异常"
---

# Feature: Impact Tracing

## Summary

Impact Tracing 将 bytecode ChangePoint 映射到 target 应用 bytecode 中的引用方法，再沿 Call Graph 反向追踪到受影响业务入口，生成排序后的 `ImpactPath` 和未报告原因统计。

## Design Decisions

- 影响追踪只在存在 ChangePoint 时运行；无 ChangePoint 时 CLI 跳过 Call Graph 和 Impact Tracing，返回空 `ImpactResult`。
- Seed resolution 通过扫描 target main classes bytecode 完成，确保追踪基于升级后的业务代码。
- ADDED 类型默认不适合引用扫描，`CLASS_ADDED`、`METHOD_ADDED` 和 `FIELD_ADDED` 计为 `CHANGE_KIND_NOT_APPLICABLE`。
- 反向追踪使用 BFS 查找所有上游 caller，并从无 incoming edge 的 roots 构建路径。
- 输出路径按 affected method、ChangePoint owner/name/kind 稳定排序，保证报告 deterministic。

## Actors / Entrypoints

- CLI pipeline 在 ChangePoint 非空时调用 `computeImpact()`。
- `ImpactTracer.trace(changePoints, callGraph, buildResult)` 是影响追踪入口。
- `ChangePointRefScanner.scan()` 是 seed method 发现入口。

## Behavior Contract

- 输入包括 ChangePoint 列表、target Call Graph 和 target BuildResult。
- 只扫描可 scannable 的 ChangePoint kind。
- 每个 scannable ChangePoint 在 target classes 中查找直接引用它的 seed methods。
- Seed method 必须能在 Call Graph 中按 owner、name、descriptor 精确匹配。
- 对每个 seed 沿 incoming edges 反向 BFS，找到没有上游 caller 的 root methods。
- 每条 `ImpactPath` 包含 affected method、ChangePoint、路径边、涉及模块和跨模块边界。
- 未能形成路径的 ChangePoint 进入 `NotReportedReason` 统计。

## Core Flow

1. `trace()` 启动 impact-trace diagnostic stage。
2. `resolveSeeds()` 使用 `ChangePointRefScanner` 扫描 target classes。
3. 构建 Call Graph method index 和 owner 到 module 的映射。
4. 遍历 ChangePoint，跳过不可扫描 kind 并记录原因。
5. 对 seed 进行 Call Graph 精确解析。
6. 对解析成功的 seed 执行 reverse BFS。
7. 从 roots 到 seed 构建 `ImpactPath`，记录模块集合和跨模块边界。
8. 对 paths 执行稳定排序并返回 `ImpactResult`。

## Acceptance Criteria

### Functional

- Given ChangePoint kind 不可扫描，When `trace()` 处理该 ChangePoint，Then 增加 `CHANGE_KIND_NOT_APPLICABLE` 计数。
- Given target bytecode 中没有引用某 ChangePoint，When seed resolution 完成，Then 增加 `NO_SEED_FOUND` 计数。
- Given seed 无法在 Call Graph 中精确匹配，When 解析 seed，Then 增加 `INCOMPLETE_CHAIN` 计数。
- Given seed 有上游 callers，When reverse BFS 执行，Then 生成从 root 到 seed 的 `ImpactPath`。
- Given caller 和 callee 属于不同模块，When 构建路径，Then 记录 `callerModule->calleeModule` 边界。
- Given 多条路径，When `trace()` 返回，Then paths 按稳定 comparator 排序。

### Non-Functional

- [ ] 影响路径必须基于静态可确认调用链，不推断未确认动态行为。
- [ ] 结果必须不可变并保持 deterministic 排序。
- [ ] 未报告原因必须可统计，避免空路径被误解为分析成功覆盖全部风险。

## Edge Cases

- 所有 visited methods 都处于环中且无 root 时，seed 自身作为 root。
- Owner 到 module 映射缺失时模块名为空字符串，跨模块边界不会误报。
- 扫描 class 文件失败时抛出 `ImpactException` 并标记 impact-trace stage 失败。
- 只要 ChangePoint 列表为空，CLI 层会跳过整个 Call Graph 和 Impact Tracing 阶段。

## Implementation Boundaries

- Impact Tracing 不生成 ChangePoint，也不构建 Call Graph；它只消费 bytecode diff 和 call graph 产物。
- Seed scanning 只读取 target main classes，不扫描 test classes 或第三方 jar。
- Report Generator 负责展示 ImpactResult，Impact Tracing 不输出文件。
