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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/JvmAccessChecker.java"
    desc: "algorithm-independent Java 8 JVM access policy"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AccessReferenceEvidence.java"
    desc: "typed access decision 与 representative reference evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleImpactQueryResult.java"
    desc: "path、disposition、observation 与 query limitation Result Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DynamicCallEvidenceIndex.java"
    desc: "fixed-point 期间登记的 reachable bootstrap/handle evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/StructuralReferenceIndex.java"
    desc: "构图前生成的immutable winner-only raw structural facts"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ReachableReferenceCollector.java"
    desc: "一次性收集reachable IR reference facts"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointSeedResolverRegistry.java"
    desc: "按ChangePointKind唯一选择typed seed resolver"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AccessNarrowingSeedResolver.java"
    desc: "reachable与dynamic access reference resolution"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/StructuralReferencePreparation.java"
    desc: "Structural Reference access decision与path materialization前置边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointDispositionReducer.java"
    desc: "observation、seed与structural path的pure disposition reduction"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointSeedResolution.java"
    desc: "seed、三态observation、typed evidence与query limitation不变量"
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

Impact Tracing 在 target Call Graph 完成后执行 read-only query。它从 reachable WALA node/IR、构图期 typed dynamic evidence与precomputed structural metadata解析seed；access narrowing先通过target CHA解析declaration，再由algorithm-independent `JvmAccessChecker`判断new access。`ChangePointSeedResolution`集中验证`NONE`、`ACCESSIBLE_ONLY`、`IMPACTING`不变量，`StructuralReferencePreparation`把access decision与path materialization分离，最终由pure `ChangePointDispositionReducer`归并结果。Query直接读取WALA predecessors，为同一 `ChangePoint + affected PROJECT method` 跨seed/Context保留一条deterministic representative shortest path，不创建overlay node/edge、baseline Call Graph或whole-graph predecessor copy。

## Design Decisions

- Access query假设pre-existing consumer bytecode在old dependency下合法，只用target CHA/IR判断new access；不构建baseline CHA/Call Graph。
- `ACCESSIBLE`不建seed；`INACCESSIBLE`与`POTENTIALLY_INACCESSIBLE`保守建seed。Potential path不改变Module status，只有可能漏报的typed limitation才使Module `INCONCLUSIVE`。
- Runtime package必须同时匹配class loader identity与package name；protected receiver只读取caller-local verifier type，不读取points-to dataflow。
- Seed resolver只产生typed seed/observation/evidence/limitation；Structural Reference access filtering和最终disposition分别由独立对象处理，`ModuleImpactTracer`只负责编排与path materialization。

## Actors / Entrypoints

- per-Module pipeline在selected Call Graph完成后调用query。
- 输入是`ModuleAnalysisUnit`、read-only `ModuleCallGraphSession`和BoundChangePoint；输出是immutable `ModuleImpactQueryResult`。

## Behavior Contract

- 每个BoundChangePoint恰有一个最终disposition；path与observation携带typed evidence。
- Query resolution gap通过`QueryLimitation`回传pipeline，不改写scope validation或strategy metadata。

## Seed Resolution

- `METHOD_BODY_CHANGED`：按 new owner/name/descriptor 匹配全部 reachable `CGNode` Context。
- `METHOD_REMOVED`/`METHOD_DESCRIPTOR_CHANGED`：扫描 reachable WALA IR 的 ordinary invoke `declaredTarget`，以 old owner/name/descriptor 建 caller terminal seed。
- Lambda、method reference 与 unknown bootstrap 的 removed/descriptor-changed implementation：使用 `DynamicCallEvidenceIndex` 中 fixed-point 期间登记的 direct method-handle evidence。不存在的 implementation 不要求 callee node。Evidence stable key由caller method binary identity、bytecode PC、typed source/handle kind与target identity组成，不依赖`CGNode`编号或`Context.toString()`。
- Field removal/descriptor change：扫描 reachable field access 的 declared field。
- Class removal：扫描 allocation、cast、`instanceof`、array、metadata、method/field type reference。
- Access narrowing：method/constructor扫描reachable invoke；field扫描reachable field access；class扫描reachable type reference并复用raw Structural Reference。Inherited member先经target CHA解析actual declaration，不能只比较symbolic owner。
- Reachable `invokedynamic` bootstrap argument中的typed MethodHandle evidence按handle reference kind执行相同access check；普通`ldc CONSTANT_MethodHandle`用途与programmatic `MethodHandles.Lookup`不伪造确定结果。
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

- Structural metadata在Call Graph前，从ownership winner的PROJECT/reactor directory与repository-backed dependency JAR收集；session只保存immutable `StructuralReferenceIndex`。Query先由`StructuralReferenceResolver`绑定ChangePoint，再执行target CHA access decision与path materialization。
- superclass、interface、annotation、generic signature、method/field descriptor、throws reference在ASM visitor中直接形成typed `MetadataReference(kind, member, target, evidence)`，再投影为`StructuralReference`；kind/member不从evidence文字反向解析。
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
- `ACCESS_REMAINS_VALID`
- `NO_PROJECT_PATH`
- `UNATTRIBUTABLE_CLASS_REFERENCE`
- `UNREACHABLE_STRUCTURAL_REFERENCE`

每个 `BoundChangePoint` 恰有一个最终 disposition；多个 terminal seed/evidence 可汇总到同一 affected method representative path。`ChangePointDispositionReducer`按固定优先级读取PROJECT path、seed、Structural Reference状态与aggregate observation，不在tracer中多次覆盖map值。

## JVM Access Decision

- Class `PACKAGE_PRIVATE`只允许same runtime package；member `PRIVATE`在Java 8下只允许declaring class自身。
- `PROTECTED`跨package先要求caller是declaring class的subclass，并校验symbolic owner关系。Static member随后可访问；instance member继续判断`THIS`、合法`SUPER`、exact `POINT`或`CONE` verifier receiver。
- Exact receiver明确不是caller subtype时为`INACCESSIBLE`；无法证明整个cone或unknown receiver合法时为`POTENTIALLY_INACCESSIBLE`。
- 找到reference且全部仍合法时为`ACCESS_REMAINS_VALID`；完全未找到reference才是`DECLARED_REFERENCE_NOT_FOUND`。
- Target type/declaration无法解析时生成`ACCESS_TARGET_TYPE_UNRESOLVED`或`ACCESS_DECLARATION_UNRESOLVED`，reason为`INCONCLUSIVE_SCOPE_VALIDATION`。

## SSA Equivalence

- 只处理已有 candidate Impact Path 的唯一 `METHOD_BODY_CHANGED`；所有 Module query join 后全局串行执行。
- Target IR 来自 target session；old IR 使用 baseline ArtifactCoord closure、repository lease 与同一 JDK 8 构建 old-side CHA，不构建 baseline Call Graph。
- 两侧使用相同 `SSAOptions` 和独立 cache。比较 typed constants、Def-Use、normal/exception CFG、catch type、declared references、phi/pi/catch 与 side-effect order。
- `PROVEN_EQUIVALENT` 删除该 ChangePoint 的全部 path；`DIFFERENT`、`UNKNOWN` 保留。`UNKNOWN` 将原 `SUCCESS` Module 转为 `INCONCLUSIVE`。

## Code Comparison Evidence

- 为candidate/final Impact Path、Structural Reference Path及用户可见的`ACCESS_REMAINS_VALID` access ChangePoint构建evidence；access-only变化不要求先存在Impact Path。
- JAR 通过 `IJarRepository.open(ArtifactCoord)` 获取；physical path 只由当前 temporary `JarLease.jarFile()` handle 传给 Vineflower/ASM，不进入 domain key 或 Report dependency detail。
- Vineflower 使用 exact old/new artifact 与 JDK 8 context；结果按 logical old/new coordinate 与 member identity 去重。
- 输出 Git-style Unified diff；反编译失败或 bytecode 不同但 Java text 相同时保留 ASM fallback。
- Code evidence 不参与 Impact/SSA 判定，失败不改变 Module status。

## Read-only Boundary

Call Graph 完成后，Impact query 只读取 graph、IR、model metadata 与 precomputed structural metadata。禁止 post-build ServiceLoader overlay、post-build `invokedynamic` whole-scope ASM scan、node/edge mutation或第二张 target Call Graph。

“未发现 Impact Path”只适用于公开 analysis model。Reflection target、Spring dynamic semantics、custom classloader 与未注册 bootstrap 不保证完整；model limitation 必须以 `INCONCLUSIVE` 表达，不能输出确定性的“无影响”。

## Acceptance Criteria

### Functional

- Given access narrowing与reachable pre-existing bytecode reference；When new access明确不允许或protected receiver无法证明合法；Then保留definite/potential Impact Path及typed old/new access evidence。
- Given全部相关reference在new access下仍合法；When完成query；Then disposition为`ACCESS_REMAINS_VALID`，不生成Affected Call Chain。
- Given target CHA无法解析必要type/declaration；When完成query；Then limitation通过Result Object进入统一coverage reduction。

### Non-Functional

- [ ] Query保持Call Graph、CHA、strategy metadata与raw structural index只读。
- [ ] `TypeInference`按reachable node缓存，access checker不依赖algorithm或points-to value。
- [ ] 相同输入的representative path、evidence与limitation排序稳定。

## Edge Cases

- PROJECT source因access narrowing导致target compile失败时停留在build failure，不进入Impact Report。
- Potential access只表示local verifier evidence不足，不宣称运行时一定抛出linkage error。

## Implementation Boundaries

- 不分析source compatibility、Reflection/JNI/custom ClassLoader access或Java 9 module exports。
- Structural scanner只采集raw事实；access decision只在target CHA完成后执行。
