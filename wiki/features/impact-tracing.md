---
title: "Impact Tracing"
type: feature
relations:
  - path: "wiki/features/call-graph-engine.md"
    desc: "live WALA graph、Context 与 ServiceLoader overlay"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "BoundChangePoint 输入"
  - path: "wiki/features/report-generator.md"
    desc: "Impact Path、Structural Reference Path 与代码 evidence 输出"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "固定 Call Chain、Structural Reference Path、SSA 与代码 evidence 的持续验证"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/impact/ModuleImpactTracer.java"
    desc: "seed resolution 与 direct WALA reverse query"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactPath.java"
    desc: "ordered nodes/edges/terminal"
  - path: "src/main/java/io/github/dependencyanalysis/impact/StructuralImpactScanner.java"
    desc: "class metadata Structural Reference 扫描"
  - path: "src/main/java/io/github/dependencyanalysis/impact/StructuralReferencePath.java"
    desc: "带 PROJECT boundary 的结构引用链"
  - path: "src/main/java/io/github/dependencyanalysis/impact/SsaEquivalenceEngine.java"
    desc: "global serial candidate-only filtering"
  - path: "src/main/java/io/github/dependencyanalysis/impact/NormalizedSsaComparator.java"
    desc: "conservative normalized SSA/CFG comparison"
  - path: "src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "path-related old/new decompiled Java 与 ASM fallback"
---

# Feature: Impact Tracing

## Summary

Impact Tracing 在 target Call Graph 完成后解析 ChangePoint seed，直接读取 WALA predecessors，生成从当前 Module `PROJECT` method 到 `ChangePointTerminal` 的 representative shortest path。它保留 `CGNode` Context，不复制完整 domain graph。

## Seed Resolution

- Existing method：按 new owner/name/descriptor 匹配所有 WALA `CGNode` Context；virtual/interface/Reflection target 使用 WALA result。
- Removed/descriptor change：扫描 reachable IR declared invoke target；使用 old owner/name/descriptor；caller node 成为 seed。
- Field change：扫描 reachable field access 的 declared field。
- Class removal：扫描 allocation、cast、`instanceof`、array、metadata、method/field type reference。
- `*_ADDED` 不触发 Call Graph，disposition 为 `CHANGE_KIND_NOT_ANALYZED`。
- 任意 removal/modification ChangePoint 都会触发 target Call Graph；不存在 direct seed 的 skip gate。

## Reverse Query and Path Identity

- 每个唯一 seed 执行一次 reverse BFS；predecessor 按需直接读取 WALA graph 和 ServiceLoader overlay。
- Traversal identity 是 exact `CGNode`；禁止使用 `Context.toString()` 作为 stable key。
- 每个 `(Module, affected PROJECT method, seed, BoundChangePoint)` 跨 Context 保留一条 deterministic shortest path。
- Fake root/world-clinit 不进入 Report。
- Callsite evidence 通过 WALA possible-site API 恢复；equal-length path 按 method identity、bytecode PC、declared target、edge kind 稳定选择。
- Seed origin 为 `PROJECT` 时 `DIRECT`；其他 code origin 为 `TRANSITIVE`。

## Structural Reference Path

- Class metadata 的 superclass、interface、annotation、generic signature、method/field descriptor、throws reference 先形成结构化 `StructuralReference`。
- `PROJECT` metadata reference 直接展示 `application class/member -> structural relation -> changed dependency class`，不会虚构 method call。
- `REACTOR_DEPENDENCY`/`DEPENDENCY` reference 复用 live WALA graph 和 ServiceLoader overlay reverse traversal，恢复 `affected PROJECT method -> shortest call chain -> referencing class/member -> structural relation -> changed class`。
- 每个 affected PROJECT method 保留一条 deterministic shortest representative path；traversal 使用 exact `CGNode` identity并保留 Context。
- 无法回到 PROJECT boundary 时 disposition 为 `UNREACHABLE_STRUCTURAL_REFERENCE`，不输出伪造链。

## ChangePoint Disposition

- `IMPACT_REPORTED`
- `FILTERED_EQUIVALENT`
- `CHANGE_KIND_NOT_ANALYZED`
- `TARGET_NOT_FOUND`
- `DECLARED_REFERENCE_NOT_FOUND`
- `NO_PROJECT_PATH`
- `UNATTRIBUTABLE_CLASS_REFERENCE`
- `UNREACHABLE_STRUCTURAL_REFERENCE`

每个 `BoundChangePoint` 恰有一个最终 disposition；多 seed evidence 另行保留。

## SSA Equivalence

- 只处理已进入 candidate Impact Path 的唯一 `METHOD_BODY_CHANGED`；所有 Module query join 后全局串行执行。
- Target IR 来自 target Module session；old IR 使用 baseline resolved external classpath、exact old JAR、同一 JDK 8 创建 old-side CHA，不构建 baseline Call Graph。
- 两侧使用相同 `SSAOptions` 和独立 cache。
- Model 比较 typed constants、Def-Use、normal/exception CFG、catch type、declared references、phi/pi/catch、invoke/return/throw/monitor 与 side-effect order。
- `PROVEN_EQUIVALENT` 删除该 ChangePoint 的全部 paths；`DIFFERENT`、`UNKNOWN` 保留。
- `UNKNOWN` 使原 `SUCCESS` Module 转为 `INCONCLUSIVE`。

## Code Comparison Evidence

- 只为至少关联 candidate/final Impact Path 或 Structural Reference Path 的 `BoundChangePoint` 构建 evidence；无路径 raw change 不反编译、不逐项进入 Dependency Changes 页面。
- `METHOD_BODY_CHANGED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED` 使用 exact descriptor；field change 生成 bytecode-derived Java declaration；有路径的 `CLASS_REMOVED` 反编译 old class。
- Vineflower 在内存中、单线程使用 exact old/new artifact 与 JDK 8 context；多个 Module 复用相同 physical JAR pair/member 结果。
- 输出是完整 Git-style Unified diff，每个 hunk 3 行 context，并明确标记为 `Decompiled Java representation`。反编译失败或 bytecode 不同但文本相同时保留 ASM instruction diff fallback。
- SSA `PROVEN_EQUIVALENT` 的 candidate path 仍生成代码 evidence，供用户复核；代码 evidence 不参与 Impact 或 SSA 判定，失败也不改变 Module status。

## Boundary

“未发现 Impact Path”只适用于公开 analysis model。Reflection target 集合、Spring dynamic semantics、custom classloader 不保证完整；不得输出确定性的“无影响”。
