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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/cha/ChaImpactPathPruningEngine.java"
    desc: "固定caller-local extension registry、edge cache与fail-open执行"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/cha/ChaReceiverTypeResolver.java"
    desc: "caller-local IR、Def-Use、callsite、receiver与dispatch cache"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/ImpactPathPruningSummary.java"
    desc: "Module级统一metrics与bounded caller-to-callee edge evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "command-wide common pool、串行Module生命周期与session释放边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyArtifactSelection.java"
    desc: "进入Evidence与Impact Query前的changed-member来源边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleImpactTracer.java"
    desc: "Evidence anchor驱动的QueryNode reverse BFS与representative path"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ReferenceEvidence.java"
    desc: "算法无关terminal evidence与stable key"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointEvidenceCollector.java"
    desc: "完成图的唯一reachable reference扫描与ChangePoint绑定"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointEvidenceIndex.java"
    desc: "resolution事实主索引与exact QueryNode Reverse BFS辅助索引"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisSnapshotter.java"
    desc: "path node、terminal Evidence与session的live/frozen边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/StableEvidenceAnchor.java"
    desc: "不持有WALA对象的冻结Evidence identity"
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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/protocol/invokedynamic/DynamicCallEvidenceIndex.java"
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
    desc: "root method、ordered affected PROJECT methods与root kind contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactPathRootKind.java"
    desc: "普通method root与root SCC分类"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/topology/StronglyConnectedComponents.java"
    desc: "Call Graph topology与Impact root selection复用的canonical SCC实现"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointTerminal.java"
    desc: "路径末端BoundChangePoint与exact ReferenceEvidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/SeedProgressReporter.java"
    desc: "-vv下单个QueryNode独立计时、state metrics与10秒心跳"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointDisposition.java"
    desc: "ChangePoint 最终 disposition contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeSsaFilter.java"
    desc: "ChangePoint收集期pair-local normalized SSA comparison"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/MethodBodyComparisonCache.java"
    desc: "方法体Java-first分阶段证据cache与retained body Unified diff"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "Impact/Structural member的repository-backed decompiled Java diff"
---

# Feature: Impact Tracing

## Summary

Impact Tracing消费fixed point已完成、拓扑只读但仍处于live期的`ModuleCallGraphSession`。`StructuralImpactScanner`先扫描全部effective class metadata；随后算法无关`ChangePointEvidenceCollector`在同一线程顺序遍历最终Call Graph一次。Query按exact `QueryNode`分组执行deterministic reverse breadth-first search（BFS，广度优先搜索），并在固定CHA caller-local extension裁剪后的QueryNode slice上计算root strongly connected component（SCC，强连通分量）。Impact Path只物化“root PROJECT method → seed → changed member”的确定性最短代表路径；路径中的PROJECT methods通过`getAffectedMethods()`统一报告，不再为每个中间method生成后缀路径。

## Design Decisions

- None.

## Actors / Entrypoints

- per-Module pipeline在selected Call Graph完成后调用query。
- 输入是`ModuleAnalysisUnit`、read-only `ModuleCallGraphSession`和已通过`DependencyArtifactSelection`选择的BoundChangePoint；输出是immutable `ModuleImpactQueryResult`。

## Behavior Contract

- 每个BoundChangePoint在`ChangePointEvidenceIndex`中恰有一个`MATCHED|NONE|UNSUPPORTED|INCONCLUSIVE`resolution，并在query后恰有一个最终disposition。
- `ChangePointEvidenceIndex.resolutions`是Evidence事实真源；`reverseBfsBindings`只保存exact reachable `QueryNode -> ChangePointTerminal`导航关系。构造器要求每个binding terminal存在于对应resolution，缺损时fail-fast。
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
- Collector使用phase-local JVM identity matcher查method、field与class ChangePoint。每条instruction先提取实际引用type再查索引，不遍历全部changed owner。
- Structural metadata仍扫描全部effective class；collector只在唯一node循环中查询phase-local method/owner lookup。class/field级metadata绑定caller class的全部reachable Context，method级metadata只绑定exact method；PROJECT Structural Reference直接报告，不伪造node binding。

## Reverse BFS and Representative Path

- ordinary与structural terminal都来自`reverseBfsBindings`并按exact `QueryNode`分组；ordinary resolver生成的seed必须能回查到同一binding，否则query fail-fast。一个QueryNode query处理该节点关联的全部Evidence。PROJECT direct structural reference直接生成结果，不进入QueryNode query计数。
- QueryNode与其evidence binding先按stable key排序，再分配Module-local稳定ordinal。不同Call Graph Context仍是不同QueryNode，不按method文本合并。
- 每个QueryNode query使用局部queue、`visited`、`next`和唯一`ReverseTrace`执行一次deterministic reverse BFS；state identity固定为exact `QueryNode`，Module级`traceCache`不存在。
- worker只返回immutable ordinary path、structural path与recovery结果，不返回`ReverseTrace`、`visited`或`next`。query返回前释放局部`ReverseTrace`强引用；同时存活的反向切片不超过active QueryNode worker数。
- Traversal node identity 是 exact `CGNode`；禁止使用 `Context.toString()` 作为 stable identity。
- Reverse BFS在加入predecessor前以`(caller,callee)`运行`cha-local-receiver-inference`。只有extension返回`PROVEN_INFEASIBLE`才跳过调用边；`UNKNOWN`、`NOT_APPLICABLE`和非中断异常均fail-open保留，中断继续传播。
- `visited`、`next`、incoming/successor adjacency与canonical SCC均基于`QueryNode`；WALA fake root与fake world-clinit先移除，不参与root判断或Report。
- canonical SCC utility对QueryNode slice分解组件。没有外部incoming edge的SCC是root component；普通单node root必须是无真实caller的PROJECT method。
- root SCC优先选择declared PROJECT entrypoint；不存在时选择stable comparator最小的PROJECT node。root component不含PROJECT node时不生成Impact Path，并沿用`NO_PROJECT_PATH` disposition。
- 每个root component只生成一条到seed的deterministic shortest path；多个root component分别保留。A→B→seed只物化A root path，B通过affected methods关系报告，不执行path containment比较。
- Local extension保留caller-local规则：只有关联的全部callsite及其全部`IR.getCalls(site)` invoke instance均证明排除callee时才跳过；任一feasible、unknown、fixed dispatch、单原始target或缺失证据均保留。它识别`new`非数组reference exact type、exact `phi`并集、透明`pi`、单一可解析非数组reference `checkcast`与既有upper bound；显式`null`不产生正常target。
- Extension不跨方法映射formal parameter/actual argument，也不追踪producer callsite return。bridge、factory和其他跨方法receiver flow保守保留；这是固定分析范围，不改变Module status。
- `this`、field/array load、collection content、数组、多类型或unresolved cast、缺失IR/callsite及其他unsupported SSA instruction均unknown并保留。推导使用iterative worklist和visited value set，不递归、不设数值预算。
- `ChaReceiverTypeResolver`复用IR、Def-Use、callsite、receiver与dispatch resolution cache。exact集合逐个执行JVM method dispatch resolution，再与candidate callee method reference比较；不使用class name直接相等，因此保留继承方法、interface default method与合法override。Upper bound只在完整class/interface cone排除callee时裁剪。
- Impact Path与Structural Reference Path只保存有序node sequence和terminal，不调用`getPossibleSites(caller, callee)`反查中间callsite。
- completion result到达后立即归并，不保存全部query结果。同一`changed member + root MethodId`汇总不同seed、Context和路线：先选hop数最少的path，再比较exact node sequence，最后按terminal evidence stable key决胜。serial与parallel执行得到相同代表路径。
- `ImpactPath.getRootMethod()`返回路径首个PROJECT method；`getAffectedMethods()`返回路径内全部PROJECT `MethodId`，按路径顺序并跨Context去重；`getRootKind()`区分普通method root与root SCC。旧的单数affected method接口不保留。
- Fake root/world-clinit 不进入 Report。Seed origin 是 PROJECT 时 classification 为 `DIRECT`，否则为 `TRANSITIVE`。
- 不同 Module 独立 query、独立 disposition、独立 Report。
- ChangePoint来源边界在JAR Diff前确定；Evidence collection、seed、Impact/Structural path和code comparison不得重新引入被排除的`groupId:artifactId`。该边界不删除Call Graph scope中的中间dependency method body。

## QueryNode Concurrency and TRACE Progress

- pipeline在scope planning后创建唯一command-wide managed `common`固定线程池。front preparation与JAR diff完成后，各Module Impact Query依次复用；全部Module query完成后，并发code comparison继续复用。线程数严格等于`analysisParallelism`，单Module滚动提交最多`min(analysisParallelism, queryNodes)`个query，完成一个再提交一个。
- 直接构造`ModuleImpactTracer`的Java调用保持inline串行，不创建线程池。
- 每个Module的`impact-query` INFO start在planning前输出，包含`changes`与`evidenceBindings`。planning完成后DEBUG输出`seeds`、去重后`queryNodes`与该Module worker上限；INFO completion输出最终计数与耗时。
- `evidence-analysis` INFO覆盖Structural scan与唯一node scan的start/end/fail；`-vv`由collector协调线程按5秒时间门限输出`scannedNodes/scannedBodies/scannedInstructions/evidence/bindings`，不创建heartbeat scheduler。
- `-vv`为每个QueryNode输出transient Console-only `query-node-started`、可选`query-node-progress`与`query-node-completed`；Phase位于五段prefix第五段最前面，值保持`REVERSE_BFS`、`PATH_MATERIALIZATION`与`REPRESENTATIVE_SELECTION`，message不再重复`phase=`。字段包含稳定`queryNodeOrdinal`、`evidenceSeeds`、elapsed、recent node，以及本QueryNode独立的`visited`、`edgeChecks`与`prunedEdges`。PROJECT direct structural reference不输出QueryNode事件。
- 每个QueryNode从`elapsedMs=0`与零state metrics独立计时。单个query运行满10秒后输出首个progress，以后按自身20、30、40秒周期输出；query之间不继承elapsed、metrics、recent node、Phase或heartbeat ordinal。
- BFS期间`visited`表示已发现的exact QueryNode数，`edgeChecks`与`prunedEdges`表示当前query检查及删除的caller-to-callee edge数；BFS完成后固定为该QueryNode局部`ReverseTrace`总数。心跳使用fixed-delay，只读取per-QueryNode thread-safe snapshot，不遍历正在修改的graph collection，也不读取method body、IR或SSA instruction。
- QueryNode正常完成时先停止心跳再输出total elapsed与visited；异常路径只停止心跳并传播异常，不伪造completed。INFO/DEBUG不创建scheduler；这些line只进入Console，不进入JSON或HTML Report。
- 任一QueryNode失败时取消并等待当前Module已启动的其他query退出，再将该Module转换为`FAILED_ANALYSIS`。共享pool继续服务后续Module；pipeline结束或全局异常时统一关闭。

## Dynamic Terminal Evidence

- Call Graph strategy不得保留私有terminal evidence或直接绑定ChangePoint；dynamic observation必须在session冻结前转换为算法无关的公共Evidence。
- `INVOKEDYNAMIC_BOOTSTRAP` 表示 reachable bootstrap method handle。
- `INVOKEDYNAMIC_HANDLE_REFERENCE` 表示 direct bootstrap argument method handle，例如 lambda implementation 或 method reference target。
- Unknown bootstrap 的 handle 只作为 terminal evidence；不会自动变成 Call Graph edge。
- Standard `metafactory/altMetafactory` implementation 已删除或 descriptor 改变时，caller terminal evidence仍可报告，不伪造 callee。

## Structural Reference Path

- Structural metadata在Call Graph完成后从ownership winner收集raw fact；统一collector在唯一node循环中绑定为`STRUCTURAL_REFERENCE + STRUCTURAL_METADATA`。Query不再执行ChangePoint绑定或按reference重扫Call Graph，只做target Class Hierarchy access decision与path materialization。
- superclass、interface、annotation、generic signature、method/field descriptor、throws reference在ASM visitor中直接形成typed `MetadataReference(kind, member, target, evidence)`，再投影为`StructuralReference`；method member保存name与descriptor，field member保存`name:descriptor`，class-level member为空；kind/member不从evidence文字反向解析。
- PROJECT metadata reference 直接展示 `application class/member -> structural relation -> changed dependency class`，不虚构 method call。
- REACTOR_DEPENDENCY/DEPENDENCY reference 使用 live WALA graph 做同样的 read-only reverse BFS，恢复 PROJECT boundary。
- 每个PROJECT root component保留一条shortest representative structural path；无法回到PROJECT时为`UNREACHABLE_STRUCTURAL_REFERENCE`。

## ChangePoint Disposition

- `IMPACT_REPORTED`
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

- Access query假设pre-existing consumer bytecode在old dependency下合法，只使用target Class Hierarchy与Intermediate Representation判断new access；不构建baseline Class Hierarchy或Call Graph。
- `ACCESSIBLE` reference不建立seed；`INACCESSIBLE`和`POTENTIALLY_INACCESSIBLE`保守建立seed。Potential path本身不改变Module status，只有明确的typed limitation才进入`INCONCLUSIVE` reduction。
- Runtime package必须同时匹配class loader identity与package name；只匹配package文本不足以获得package-private或protected same-package访问权。
- Class `PACKAGE_PRIVATE`只允许same runtime package；member `PRIVATE`在Java 8下只允许declaring class自身。
- `PROTECTED`跨package先要求caller是declaring class的subclass，并校验symbolic owner关系。Static member随后可访问；instance member继续判断`THIS`、合法`SUPER`、exact `POINT`或`CONE` verifier receiver。
- Protected receiver只使用caller-local verifier type与target hierarchy关系，不读取points-to dataflow。
- Exact receiver明确不是caller subtype时为`INACCESSIBLE`；无法证明整个cone或unknown receiver合法时为`POTENTIALLY_INACCESSIBLE`。
- 找到reference且全部仍合法时为`ACCESS_REMAINS_VALID`；完全未找到reference才是`DECLARED_REFERENCE_NOT_FOUND`。
- Target type/declaration无法解析时生成`ACCESS_TARGET_TYPE_UNRESOLVED`或`ACCESS_DECLARATION_UNRESOLVED`，reason为`INCONCLUSIVE_SCOPE_VALIDATION`。

## Fixed Method Body Filtering and Impact Path Pruning

- `--result-refinement-algorithms`、selection、converter与pipeline配置字段已删除；旧option作为未知参数返回exit code `1`。
- 方法体equivalence固定在唯一logical JAR pair的ChangePoint收集阶段运行。全部descriptor相同且body hash不同的`METHOD_BODY_CHANGED`先执行decompiled Java精确文本比较，不按class major version门禁。Java `IDENTICAL`立即抑制并短路；只有Java `DIFFERENT/UNKNOWN`才使用同一目标JDK 8建立pair-local old/new SSA Class Hierarchy与独立cache，不构建Call Graph，也不复用Module target session。
- Module binding前按`Java text identical || SSA MATCHED`的顺序和短路语义抑制ChangePoint。Java命中只记录`JAVA_TEXT_IDENTICAL`；Java miss后SSA命中只记录`SSA_MATCHED`，不存在双命中。其余状态组合保留。实际执行的比较为`UNKNOWN`时不改变Module status/reason，compact证据进入Module input、HTML与Schema 13 diagnostics。
- ChangePoint Evidence collection、Changed members、Impact/Structural Path、disposition与Module status只消费分阶段过滤后的effective ChangePoint；不得在后续阶段恢复已抑制的method body change。
- CHA固定执行experimental `cha-local-receiver-inference` extension；`k-obj`不执行Impact Path pruning，但方法体Java-first filtering仍固定启用。`ModuleAnalysisResult`只保存统一`ImpactPathPruningSummary`，不保存selection、candidate/final双路径或path-level SSA result。
- Extension统一输出requests、unique evaluations、cache hits、pruned、feasible、unknown、not applicable与fail-open errors，并附带callsite/invoke及exact/upper-bound/null/unknown resolution计数。
- 只有TRACE或显式diagnostics请求才格式化最多10条稳定example，包含caller/callee identity、program counter、decision、reason与inferred receiver summary。INFO/DEBUG不承担该字符串构造成本。

## Code Comparison Evidence

- 全部method body候选在JAR Diff期完成old/new Vineflower反编译与精确文本比较；每个logical pair写入command-owned cache。只有Impact Path与Structural Reference Path关联的retained ChangePoint才生成最终code comparison evidence。
- Retained `METHOD_BODY_CHANGED`只从cache读取old/new源码生成Unified diff；cache记录为`UNKNOWN`时直接显示`Unavailable`，不得重新反编译。非body ChangePoint仍按需读取exact old/new artifact。
- JAR通过`IJarRepository.open(ArtifactCoord)`获取；physical path只由当前temporary `JarLease.jarFile()` handle传给Vineflower或field declaration reader，不进入domain key、cache payload或Report。
- 输出decompiled Java Git-style Unified diff；状态为`AVAILABLE`、`JAVA_TEXT_IDENTICAL`或`UNAVAILABLE`。不生成ASM fallback；failure reason只写Console。Report按changed member只存一份diff。
- 非body Code evidence不参与Impact判定，失败不改变Module status；method body decompiled Java比较已是ChangePoint收集期正式过滤条件。

## Read-only Boundary

Call Graph fixed point完成后，Impact query只读graph、`ChangePointEvidenceIndex`、strategy metadata与typed limitation。禁止扫描IR重新发现method/field/type/reflection/ServiceLoader reference、按Structural Reference重扫graph、ServiceLoader overlay、`invokedynamic` whole-scope ASM scan、node/edge mutation或第二张target Call Graph。Report snapshot将`MethodEvidenceAnchor`替换为stable-only anchor，并清空`reverseBfsBindings`，确保completed result不持有`CGNode`或`SSAInstruction`。

“未发现 Impact Path”只适用于公开 analysis model。`changed-paths` 的 `SUCCESS` 只表示 selected path 与已建模 boundary 内未发现路径，不代表 no-op dependency 内部不存在影响。Reflection target、Spring dynamic semantics、custom classloader 与未注册 bootstrap 同样不保证完整；明确 typed limitation 必须以 `INCONCLUSIVE` 表达。

## Acceptance Criteria

### Functional

- Given access narrowing与reachable pre-existing bytecode reference；When new access明确不允许或protected receiver无法证明合法；Then保留definite/potential Impact Path及typed old/new access evidence。
- Given全部相关reference在new access下仍合法；When完成query；Then disposition为`ACCESS_REMAINS_VALID`，不生成Affected Call Chain。
- Given target CHA无法解析必要type/declaration；When完成query；Then limitation通过Result Object进入统一coverage reduction。
- Given JAR diff发现同major或跨major的body变化；When收集ChangePoint；Then固定先执行Vineflower比较，Java miss后才执行SSA，再决定Module是否需要构建Call Graph。
- GivenCHA且caller Receiver exact排除当前callee；When reverse BFS访问该edge；Then虚假predecessor不进入path、原图node/edge计数不变，且local pruned metric大于零。
- Givenreceiver事实只存在于bridge actual argument或factory return；When reverse BFS访问该跨方法flow；Then不执行跨边界推导并保守保留CHA edge。
- GivenReceiver推导为unknown、单target或不适用；When reverse BFS访问edge；Then保留原CHA关系，不新增coverage limitation。
- Given旧`--result-refinement-algorithms`；When解析Impact CLI；Then作为unknown option返回exit code `1`。Given`k-obj`；Then仍固定执行SSA与decompiled Java ChangePoint filtering，但不执行CHA Impact Path pruning。

### Non-Functional

- [ ] Query保持Call Graph、CHA、strategy metadata与Evidence index只读。
- [ ] Path materialization不反查中间callsite；node sequence与完整ChangePointTerminal足以生成Report。
- [ ] 每个TRACE QueryNode独立计时和统计，10秒心跳不跨QueryNode继承状态。
- [ ] parallelism为`1`或更大时，paths、dispositions、observations与limitations排序一致。
- [ ] `TypeInference`按reachable node缓存，access checker不依赖algorithm或points-to value。
- [ ] 相同输入的representative path、evidence与limitation排序稳定。

## Edge Cases

- PROJECT source因access narrowing导致target compile失败时停留在build failure，不进入Impact Report。
- Potential access只表示local verifier evidence不足，不宣称运行时一定抛出linkage error。

## Implementation Boundaries

- `ModuleImpactTracer`只负责Evidence anchor materialization、access decision、reverse BFS、representative path与disposition reduction；Evidence扫描/绑定、Call Graph构建和code comparison由各自边界拥有。
- 不分析source compatibility、Reflection/JNI/custom ClassLoader access或Java 9 module exports。
- Structural scanner只采集raw事实；统一collector负责绑定；access decision只在target CHA完成后执行。
