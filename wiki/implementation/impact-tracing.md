---
title: "Impact Tracing"
type: implementation
---

# Implementation: Impact Tracing

## Background

Call Graph node 可达性本身不能说明 dependency ChangePoint 如何影响业务入口。系统还必须把 bytecode、structural reference、dynamic protocol 与 JVM access 证据绑定到稳定 query seed，并在 cycle 和多路径图中选择可解释的代表路径。

## Overview

Impact Tracing 在冻结的 [Call Graph Engine](call-graph-engine.md) 结果上统一收集 evidence、构造 QueryNode、执行 reverse query，并投影为 [Dependency Impact Analysis](../features/dependency-impact-analysis.md) 的 affected path。它不修改 Call Graph topology；CHA 的 caller-local path pruning 只影响 query edge acceptance。

## Core Flow

1. 将 [Bytecode Diff Engine](bytecode-diff-engine.md) 产生的 effective ChangePoint 绑定到 Module upgrade provenance。
2. 对 class、method、field、resource 和 dynamic protocol 运行一次统一 evidence scan，并按稳定 identity 聚合 seed。
3. 由 ChangePoint 与 evidence 建立 QueryNode；多个 QueryNode 可并行，但每个 reverse traversal 使用只读 topology snapshot。
4. Reverse breadth-first search（BFS，广度优先搜索）从 seed 沿 caller 方向扩展，记录 boundary、pruned edge 和 inconclusive reason。
5. 在 SCC-aware root set 中按固定排序选择代表路径，并生成 structural reference、code comparison 与 coverage limitation projection。

## Key Mechanisms

- Effective ChangePoint 是 Java text 与 normalized SSA 双阶段过滤后的集合；被抑制 candidate 不进入 query。
- Evidence binding 使用 logical artifact、member identity 和 Module ownership，不以 source line 或 physical JAR path 作为主键。
- CHA caller-local receiver inference 只在能够证明 receiver type 时裁剪 edge；unknown 时保留原 edge，不降低 Module status。
- Dynamic terminal evidence 可在无法构造普通 method seed 时保留 ServiceLoader、MethodHandle 或 `invokedynamic` 的结构性关系。
- 代表路径排序与并行执行顺序无关；cycle 先通过 canonical SCC utility 收敛，再选择稳定 root 和 predecessor。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Impact Tracing Acceptance Criteria](../ac/implementation/impact-tracing.md)。
