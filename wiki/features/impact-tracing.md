---
title: "Impact Tracing"
type: feature
relations:
  - path: "wiki/features/call-graph-engine.md"
    desc: "WALA topology、strategy artifacts、统一collector与冻结session"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "BoundChangePoint 输入"
  - path: "wiki/features/report-generator.md"
    desc: "Impact Path、Structural Reference Path 与 code evidence 输出"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "试验性bytecode semantic comparison的CLI opt-in"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "query后的可选串行SSA stage与session释放边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleImpactTracer.java"
    desc: "Evidence anchor驱动的deterministic reverse BFS与representative path"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ReferenceEvidence.java"
    desc: "算法无关terminal evidence与stable key"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointEvidenceCollector.java"
    desc: "完成图的唯一reachable reference扫描与ChangePoint绑定"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointEvidenceIndex.java"
    desc: "每个BoundChangePoint恰有一个resolution的冻结index"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/JvmAccessChecker.java"
    desc: "algorithm-independent Java 8 JVM access policy"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AccessReferenceEvidence.java"
    desc: "typed access decision 与 representative reference evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleImpactQueryResult.java"
    desc: "path、disposition、observation 与 query limitation Result Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/DependencyBoundaryEvidence.java"
    desc: "changed instance 进入 no-op dependency 的 typed evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/DependencyFactoryEvidence.java"
    desc: "flow-to-cast factory materialization evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DynamicCallEvidenceIndex.java"
    desc: "fixed-point 期间登记的 reachable bootstrap/handle evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/StructuralReferenceIndex.java"
    desc: "构图前生成的immutable winner-only raw structural facts"
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
    desc: "ordered QueryNode与完整ChangePointTerminal组成的node-only path"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointTerminal.java"
    desc: "路径末端BoundChangePoint与exact ReferenceEvidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/SeedProgressReporter.java"
    desc: "-vv下单个逻辑seed独立计时、visited与10秒心跳"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointDisposition.java"
    desc: "ChangePoint 最终 disposition contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/SsaEquivalenceEngine.java"
    desc: "显式启用后的global serial candidate-only filtering"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "repository-backed old/new decompiled Java 与 ASM fallback"
---

# Feature: Impact Tracing

## Summary

Impact Tracing只消费冻结的`ModuleCallGraphSession`。在Call Graph完成后、session冻结前，算法无关`ChangePointEvidenceCollector`扫描reachable method一次，将ordinary invoke、field/type、Class.forName、ServiceLoader、dynamic handle和raw structural/resource fact统一转换成`ReferenceEvidence`并绑定每个`BoundChangePoint`。Query不再扫描IR发现reference，只从Evidence method anchor执行deterministic reverse breadth-first search（BFS，广度优先搜索）。Impact Path只保存有序`QueryNode`与完整`ChangePointTerminal`；Removed subject永远是terminal，不进入WALA topology。

## Design Decisions

- Access query假设pre-existing consumer bytecode在old dependency下合法，只用target CHA/IR判断new access；不构建baseline CHA/Call Graph。
- `ACCESSIBLE`不建seed；`INACCESSIBLE`与`POTENTIALLY_INACCESSIBLE`保守建seed。Potential path不改变Module status，只有可能漏报的typed limitation才使Module `INCONCLUSIVE`。
- Runtime package必须同时匹配class loader identity与package name；protected receiver只读取caller-local verifier type，不读取points-to dataflow。
- 构图strategy不得定义私有terminal evidence或绑定ChangePoint；dynamic observation必须在session冻结前转换为公共Evidence。
- `ModuleImpactTracer`只负责Evidence anchor materialization、access decision、reverse BFS与disposition reduction。
- Impact Path不物化中间callsite edge。中间节点关系由Call Graph predecessor topology保证；命中具体ChangePoint的可审计原因由末端`ReferenceEvidence`表达。

## Actors / Entrypoints

- per-Module pipeline在selected Call Graph完成后调用query。
- 输入是`ModuleAnalysisUnit`、read-only `ModuleCallGraphSession`和BoundChangePoint；输出是immutable `ModuleImpactQueryResult`。

## Behavior Contract

- 每个BoundChangePoint在`ChangePointEvidenceIndex`中恰有一个`MATCHED|NONE|UNSUPPORTED|INCONCLUSIVE`resolution，并在query后恰有一个最终disposition。
- Query resolution gap通过`QueryLimitation`回传pipeline，不改写scope validation或strategy metadata。
- Evidence anchor分为精确WALA Context的`MethodEvidenceAnchor`、structural class/member的`StructuralEvidenceAnchor`及service resource的`ResourceEvidenceAnchor`。
- `EvidenceKind`固定为method、field、type、structural和resource reference；mechanism描述declaration、declared invoke、bytecode field/type、Class.forName local constant、ServiceLoader provider、invokedynamic、MethodHandle或structural metadata。
- `ChangePointTerminal`保留`BoundChangePoint`与exact `ReferenceEvidence`。其中target标识被使用的类/member/descriptor；location保留实际使用source及terminal bytecode PC；kind、mechanism、detail与anchor共同说明路径最后一个seed method如何命中ChangePoint。该terminal PC不属于中间调用边，禁止随路径edge清理而删除。

## Unified Evidence Resolution

- `METHOD_BODY_CHANGED`：reachable target `CGNode`生成`METHOD_REFERENCE + METHOD_DECLARATION`。
- Removed/descriptor-changed method：ordinary invoke生成`METHOD_REFERENCE + DECLARED_INVOKE`；dynamic handle转换为`INVOKEDYNAMIC_*`或`METHOD_HANDLE_TARGET`。
- Removed/descriptor-changed field：field access生成`FIELD_REFERENCE + BYTECODE_FIELD_REFERENCE`。
- Removed class：allocation、cast、`instanceof`、array、metadata和method/field descriptor生成`TYPE_REFERENCE + BYTECODE_TYPE_REFERENCE`；局部constant `Class.forName`生成`CLASS_FOR_NAME_LOCAL_CONSTANT`。
- Service provider class与registration change：baseline service relation生成`SERVICE_LOADER_PROVIDER`，resource-only change附加`RESOURCE_REFERENCE`；reachable load caller另有method anchor用于Impact Path。
- Access narrowing引用在collector阶段采集；target access legality、receiver type和最终decision在query阶段计算。Inherited member先经target CHA解析actual declaration。
- Reachable `invokedynamic` bootstrap argument中的typed MethodHandle evidence按handle reference kind执行相同access check；普通`ldc CONSTANT_MethodHandle`用途与programmatic `MethodHandles.Lookup`不伪造确定结果。
- `*_ADDED` resolution为`UNSUPPORTED`，disposition为`CHANGE_KIND_NOT_ANALYZED`。
- Dependency duplicate 判断使用 `ArtifactCoord` logical source。Changed coordinate 是 loser 时为 `SHADOWED_BY_DUPLICATE`；不把 loser bytecode 绑定到 winner WALA node。

## Reverse BFS and Representative Path

- 每个 exact seed node 执行一次 deterministic reverse BFS；结果按 query 内 seed cache 复用。
- Traversal identity 是 exact `CGNode`，禁止使用 `Context.toString()` 作为 stable identity。
- Reverse BFS 访问全部 predecessor；每个访问到的 `CodeOrigin.PROJECT` node 都是 affected method，包括 call chain 中间的 PROJECT method。
- Impact Path与Structural Reference Path只保存有序node sequence和terminal，不调用`getPossibleSites(caller, callee)`反查中间callsite。
- 同一 `ChangePoint + affected PROJECT method` 汇总不同 seed 与不同 Context：先选hop数最少的path，再逐node按method owner/name/descriptor、module/source、origin和graph node id稳定决胜。
- Fake root/world-clinit 不进入 Report。Seed origin 是 PROJECT 时 classification 为 `DIRECT`，否则为 `TRANSITIVE`。
- 不同 Module 独立 query、独立 disposition、独立 Report。

## Per-Seed TRACE Progress

- `-vv`为每个普通逻辑seed和非PROJECT structural seed输出transient Console-only `seed-started`与`seed-completed`；PROJECT direct structural reference没有QueryNode seed，不输出。
- 每个seed在自身开始日志之后从`elapsedMs=0`、`visited=0`独立计时。计时只覆盖该seed的cache查询、cache miss reverse BFS、path materialization与representative selection，不包含resolver或其他seed。
- 单个seed运行满10秒后输出首个`seed-progress`，以后按该seed自身的20、30、40秒周期输出。seed切换时elapsed、visited、recent CGNode、phase与heartbeat序号全部重置；10秒内完成的seed没有progress日志。
- BFS期间visited表示该seed当前已发现节点数，recent CGNode表示最近开始查询predecessor的节点；BFS完成后visited固定为该seed使用的`ReverseTrace.visited`总数，recent node随path materialization与representative selection更新。cache reuse只复用ReverseTrace，不复用tracker状态。
- 心跳使用query-scoped daemon scheduler与per-seed thread-safe snapshot，因此分析线程停留在一次WALA调用时仍可输出；scheduler不遍历正在修改的graph collection，也不读取method body、IR或SSA instruction。
- seed正常完成时先停止心跳再输出total elapsed与visited；异常路径只停止心跳并保持原异常传播，不伪造completed。INFO/DEBUG不创建scheduler；全部seed日志不进入retained events、JSON或HTML Report。

## Dynamic Terminal Evidence

- `INVOKEDYNAMIC_BOOTSTRAP` 表示 reachable bootstrap method handle。
- `INVOKEDYNAMIC_HANDLE_REFERENCE` 表示 direct bootstrap argument method handle，例如 lambda implementation 或 method reference target。
- Unknown bootstrap 的 handle 只作为 terminal evidence；不会自动变成 Call Graph edge。
- Standard `metafactory/altMetafactory` implementation 已删除或 descriptor 改变时，caller terminal evidence仍可报告，不伪造 callee。

## Structural Reference Path

- Structural metadata在Call Graph前从ownership winner收集raw fact；统一collector绑定为`STRUCTURAL_REFERENCE + STRUCTURAL_METADATA`。Query不再执行ChangePoint绑定，只做target CHA access decision与path materialization。
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

## Experimental SSA Equivalence

- `--experimental-bytecode-semantic-comparison`是command-wide opt-in，默认关闭；关闭时candidate Impact Path直接成为final path，`equivalenceResults`为空，不创建old-side SSA/Class Hierarchy，也不产生SSA limitation或stage metric。
- 显式启用后只处理已有candidate Impact Path的唯一`METHOD_BODY_CHANGED`；协调线程在每个Module query完成后串行执行，跨Module不并发。
- Target IR 来自 target session；old IR 使用 baseline ArtifactCoord closure、repository lease 与同一 JDK 8 构建 old-side CHA，不构建 baseline Call Graph。
- 两侧使用相同 `SSAOptions` 和独立 cache。比较 typed constants、Def-Use、normal/exception CFG、catch type、declared references、phi/pi/catch 与 side-effect order。
- `PROVEN_EQUIVALENT` 删除该 ChangePoint 的全部 path；`DIFFERENT`、`UNKNOWN` 保留。`UNKNOWN` 将原 `SUCCESS` Module 转为 `INCONCLUSIVE`。
- 该开关不控制基础Bytecode Diff或Code Comparison Evidence；关闭试验功能仍生成`METHOD_BODY_CHANGED`、Impact Path、decompiled Java和ASM fallback。

## Code Comparison Evidence

- 为candidate/final Impact Path、Structural Reference Path及用户可见的`ACCESS_REMAINS_VALID` access ChangePoint构建evidence；access-only变化不要求先存在Impact Path。
- JAR 通过 `IJarRepository.open(ArtifactCoord)` 获取；physical path 只由当前 temporary `JarLease.jarFile()` handle 传给 Vineflower/ASM，不进入 domain key 或 Report dependency detail。
- Vineflower 使用 exact old/new artifact 与 JDK 8 context；结果按 logical old/new coordinate 与 member identity 去重。
- 输出 Git-style Unified diff；反编译失败或 bytecode 不同但 Java text 相同时保留 ASM fallback。
- Code evidence 不参与 Impact/SSA 判定，失败不改变 Module status。

## Read-only Boundary

Session冻结后，Impact query只读取graph、`ChangePointEvidenceIndex`、strategy metadata与typed limitation。禁止扫描IR重新发现method/field/type/reflection/ServiceLoader reference、ServiceLoader overlay、`invokedynamic` whole-scope ASM scan、node/edge mutation或第二张target Call Graph。

“未发现 Impact Path”只适用于公开 analysis model。`changed-paths` 的 `SUCCESS` 只表示 selected path 与已建模 boundary 内未发现路径，不代表 no-op dependency 内部不存在影响。Reflection target、Spring dynamic semantics、custom classloader 与未注册 bootstrap 同样不保证完整；明确 typed limitation 必须以 `INCONCLUSIVE` 表达。

## Acceptance Criteria

### Functional

- Given access narrowing与reachable pre-existing bytecode reference；When new access明确不允许或protected receiver无法证明合法；Then保留definite/potential Impact Path及typed old/new access evidence。
- Given全部相关reference在new access下仍合法；When完成query；Then disposition为`ACCESS_REMAINS_VALID`，不生成Affected Call Chain。
- Given target CHA无法解析必要type/declaration；When完成query；Then limitation通过Result Object进入统一coverage reduction。
- Given未提供试验性semantic comparison开关；When完成query；Thencandidate/final path一致，SSA结果、limitation和stage metric均为空。
- Given显式启用试验性semantic comparison；When比较结果为`PROVEN_EQUIVALENT`或`UNKNOWN`；Then分别过滤对应path或保留path并按既有规则降级Module。

### Non-Functional

- [ ] Query保持Call Graph、CHA、strategy metadata与Evidence index只读。
- [ ] Path materialization不反查中间callsite；node sequence与完整ChangePointTerminal足以生成Report。
- [ ] 每个TRACE seed独立计时和统计，10秒心跳不跨seed继承状态。
- [ ] `TypeInference`按reachable node缓存，access checker不依赖algorithm或points-to value。
- [ ] 相同输入的representative path、evidence与limitation排序稳定。

## Edge Cases

- PROJECT source因access narrowing导致target compile失败时停留在build failure，不进入Impact Report。
- Potential access只表示local verifier evidence不足，不宣称运行时一定抛出linkage error。

## Implementation Boundaries

- 不分析source compatibility、Reflection/JNI/custom ClassLoader access或Java 9 module exports。
- Structural scanner只采集raw事实；统一collector负责绑定；access decision只在target CHA完成后执行。
