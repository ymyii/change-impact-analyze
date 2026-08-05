---
title: "Impact Tracing"
type: feature
relations:
  - path: "wiki/features/call-graph-engine.md"
    desc: "live WALA graph、Context、model evidence 与 read-only boundary"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "BoundChangePoint 输入"
  - path: "wiki/features/report-generator.md"
    desc: "Impact Path、Structural Reference Path 与 code evidence 输出"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleImpactTracer.java"
    desc: "seed resolution、deterministic reverse BFS 与 representative path"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DynamicCallEvidenceIndex.java"
    desc: "fixed-point 期间登记的 reachable bootstrap/handle evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/StructuralScanResult.java"
    desc: "构图前生成的 immutable winner-only structural metadata"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactPath.java"
    desc: "ordered nodes/edges/terminal"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointDisposition.java"
    desc: "ChangePoint 最终 disposition contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/SsaEquivalenceEngine.java"
    desc: "global serial candidate-only filtering"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "repository-backed old/new decompiled Java 与 ASM fallback"
---

# Feature: Impact Tracing

## Summary

Impact Tracing 在 target Call Graph 完成后执行 read-only query。它从 reachable WALA node、reachable IR、构图期 dynamic evidence 与 precomputed structural metadata 解析 seed；直接读取 WALA predecessors，为同一 `ChangePoint + affected PROJECT method` 跨 seed/Context 保留一条 deterministic representative shortest path。Query 不创建 overlay node/edge，不重新打开 scope JAR，也不复制 whole-graph predecessor index。

## Seed Resolution

- `METHOD_BODY_CHANGED`：按 new owner/name/descriptor 匹配全部 reachable `CGNode` Context。
- `METHOD_REMOVED`/`METHOD_DESCRIPTOR_CHANGED`：扫描 reachable WALA IR 的 ordinary invoke `declaredTarget`，以 old owner/name/descriptor 建 caller terminal seed。
- Lambda、method reference 与 unknown bootstrap 的 removed/descriptor-changed implementation：使用 `DynamicCallEvidenceIndex` 中 fixed-point 期间登记的 direct method-handle evidence。不存在的 implementation 不要求 callee node。
- Field removal/descriptor change：扫描 reachable field access 的 declared field。
- Class removal：扫描 allocation、cast、`instanceof`、array、metadata、method/field type reference。
- `*_ADDED` 不触发 query，disposition 为 `CHANGE_KIND_NOT_ANALYZED`。
- Dependency duplicate 判断使用 `ArtifactCoord` logical source。Changed coordinate 是 loser 时为 `SHADOWED_BY_DUPLICATE`；不把 loser bytecode 绑定到 winner WALA node。

## Reverse BFS and Representative Path

- 每个 exact seed node 执行一次 deterministic reverse BFS；结果按 query 内 seed cache 复用。
- Traversal identity 是 exact `CGNode`，禁止使用 `Context.toString()` 作为 stable identity。
- Reverse BFS 访问全部 predecessor；每个访问到的 `CodeOrigin.PROJECT` node 都是 affected method，包括 call chain 中间的 PROJECT method。
- 同一 `ChangePoint + affected PROJECT method` 汇总不同 seed 与不同 Context：先选 edge 数最少的 path，再按 method identity、graph node id、bytecode PC、edge kind/evidence稳定决胜。
- Callsite 使用 `getPossibleSites(caller, callee)`；按 bytecode PC、declared target 排序，只保留第一条，因此同一 caller/callee 的多个 site 使用最小 PC。
- Fake root/world-clinit 不进入 Report。Seed origin 是 PROJECT 时 classification 为 `DIRECT`，否则为 `TRANSITIVE`。
- 不同 Module 独立 query、独立 disposition、独立 Report。

## Dynamic Terminal Evidence

- `INVOKEDYNAMIC_BOOTSTRAP` 表示 reachable bootstrap method handle。
- `INVOKEDYNAMIC_HANDLE_REFERENCE` 表示 direct bootstrap argument method handle，例如 lambda implementation 或 method reference target。
- Unknown bootstrap 的 handle 只作为 terminal evidence；不会自动变成 Call Graph edge。
- Standard `metafactory/altMetafactory` implementation 已删除或 descriptor 改变时，caller terminal evidence仍可报告，不伪造 callee。

## Structural Reference Path

- Structural metadata 在 Call Graph 前，从 ownership winner 的 PROJECT/reactor directory 与 repository-backed dependency JAR 收集；session 只保存 immutable `StructuralScanResult`。
- superclass、interface、annotation、generic signature、method/field descriptor、throws reference 形成 `StructuralReference`。
- PROJECT metadata reference 直接展示 `application class/member -> structural relation -> changed dependency class`，不虚构 method call。
- REACTOR_DEPENDENCY/DEPENDENCY reference 使用 live WALA graph 做同样的 read-only reverse BFS，恢复 PROJECT boundary。
- 每个 affected PROJECT method保留一条 shortest representative structural path；无法回到 PROJECT 时为 `UNREACHABLE_STRUCTURAL_REFERENCE`。

## ChangePoint Disposition

- `IMPACT_REPORTED`
- `FILTERED_EQUIVALENT`
- `CHANGE_KIND_NOT_ANALYZED`
- `SHADOWED_BY_DUPLICATE`
- `TARGET_NOT_FOUND`
- `DECLARED_REFERENCE_NOT_FOUND`
- `NO_PROJECT_PATH`
- `UNATTRIBUTABLE_CLASS_REFERENCE`
- `UNREACHABLE_STRUCTURAL_REFERENCE`

每个 `BoundChangePoint` 恰有一个最终 disposition；多个 terminal seed/evidence 可汇总到同一 affected method representative path。

## SSA Equivalence

- 只处理已有 candidate Impact Path 的唯一 `METHOD_BODY_CHANGED`；所有 Module query join 后全局串行执行。
- Target IR 来自 target session；old IR 使用 baseline ArtifactCoord closure、repository lease 与同一 JDK 8 构建 old-side CHA，不构建 baseline Call Graph。
- 两侧使用相同 `SSAOptions` 和独立 cache。比较 typed constants、Def-Use、normal/exception CFG、catch type、declared references、phi/pi/catch 与 side-effect order。
- `PROVEN_EQUIVALENT` 删除该 ChangePoint 的全部 path；`DIFFERENT`、`UNKNOWN` 保留。`UNKNOWN` 将原 `SUCCESS` Module 转为 `INCONCLUSIVE`。

## Code Comparison Evidence

- 只为 candidate/final Impact Path 或 Structural Reference Path 关联的 `BoundChangePoint` 构建 evidence。
- JAR 通过 `IJarRepository.open(ArtifactCoord)` 获取；physical path 只由当前 temporary `JarLease.jarFile()` handle 传给 Vineflower/ASM，不进入 domain key 或 Report dependency detail。
- Vineflower 使用 exact old/new artifact 与 JDK 8 context；结果按 logical old/new coordinate 与 member identity 去重。
- 输出 Git-style Unified diff；反编译失败或 bytecode 不同但 Java text 相同时保留 ASM fallback。
- Code evidence 不参与 Impact/SSA 判定，失败不改变 Module status。

## Read-only Boundary

Call Graph 完成后，Impact query 只读取 graph、IR、model metadata 与 precomputed structural metadata。禁止 post-build ServiceLoader overlay、post-build `invokedynamic` whole-scope ASM scan、node/edge mutation或第二张 target Call Graph。

“未发现 Impact Path”只适用于公开 analysis model。Reflection target、Spring dynamic semantics、custom classloader 与未注册 bootstrap 不保证完整；model limitation 必须以 `INCONCLUSIVE` 表达，不能输出确定性的“无影响”。
