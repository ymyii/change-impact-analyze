---
title: "Call Graph Engine"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "per-Module pipeline 与并发边界"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "coordinate-based JAR repository 初始化输入"
  - path: "wiki/features/impact-tracing.md"
    desc: "read-only query 消费 live session"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphAlgorithm.java"
    desc: "command-wide 算法标识与默认选择"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphEngine.java"
    desc: "per-Module scope、CHA、timeout 与 strategy 编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphAlgorithmStrategy.java"
    desc: "算法 strategy 边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaCallGraphStrategy.java"
    desc: "BasicRTABuilder 与 RTA-local model 安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaCallGraphStrategy.java"
    desc: "class-based ZeroCFA 构建"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedZeroOneCfaCallGraphStrategy.java"
    desc: "allocation-sensitive optimized 0-1-CFA 构建"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/WalaReflectionOptions.java"
    desc: "command-wide WALA ReflectionOptions Value Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointClassIndex.java"
    desc: "current Module target/classes 的 immutable root class selection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DeclaredTypesEntrypoint.java"
    desc: "单 candidate declared-type 参数建模"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointSyntheticTypeRegistry.java"
    desc: "per-CHA interface/abstract synthetic placeholder复用"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphSession.java"
    desc: "live graph、CHA、cache、ownership 与 immutable model metadata"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphTopologyAnalyzer.java"
    desc: "benchmark-only CGNode父榜、IMethod子榜、shortest path与SCC cycle"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphNodeIdentity.java"
    desc: "Method、WALA Context、graph node id、walaSynthetic与sentinelRole组成的精确CGNode identity"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphNodeReachabilityPath.java"
    desc: "typed root及其deterministic shortest CGNode chain"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphRankedNode.java"
    desc: "父榜CGNode、related计数、IR与reachability path快照"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphRelatedMethod.java"
    desc: "父CGNode下按IMethod聚合的related CGNode子榜"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CallGraphDiagnosticsExporter.java"
    desc: "Schema v3 CGNode topology/source/IR JSON原子输出"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CallGraphMethodSourceBuilder.java"
    desc: "PROJECT/reactor/dependency/JDK exact bytecode source 与 ASM fallback"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ServiceLoaderProtocolIndex.java"
    desc: "immutable resource/provider protocol facts"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ServiceLoaderProtocol.java"
    desc: "algorithm-independent JDK 8 protocol method/type identities"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaServiceLoaderModel.java"
    desc: "RTA-only provider reachability summaries，无return carrier"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaServiceLoaderSummaryIrFactory.java"
    desc: "RTA load/iterator summary IR，无points-to value传播"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaServiceLoaderModel.java"
    desc: "ZeroCFA-owned exact/aggregate provider-return execution"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaServiceLoaderSummaryIrFactory.java"
    desc: "ZeroCFA-owned load/iterator summary IR"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedServiceLoaderModel.java"
    desc: "optimized-owned allocation-sensitive provider execution"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedServiceLoaderSummaryIrFactory.java"
    desc: "optimized-owned load/iterator summary IR"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ServiceLoaderProviderSummaryMethods.java"
    desc: "无状态ZeroX provider instruction emission helper"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaServiceLoaderInstaller.java"
    desc: "RTA-owned selector、Context与summary interpreter安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaServiceLoaderContextInterpreter.java"
    desc: "RTA-owned ServiceLoader summary cache与Context interpretation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaServiceLoaderInstaller.java"
    desc: "ZeroCFA constant receiver与aggregate Context安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaServiceLoaderContextInterpreter.java"
    desc: "ZeroCFA-owned ServiceLoader summary cache与Context interpretation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedServiceLoaderInstaller.java"
    desc: "optimized allocation-site ServiceLoader Context安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedServiceLoaderContextInterpreter.java"
    desc: "optimized-owned ServiceLoader summary cache与Context interpretation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaServiceContractResolver.java"
    desc: "RTA caller-local IR/checkcast contract解析"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaMethodHandleInstaller.java"
    desc: "RTA caller-local MethodHandle selector/interpreter安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaMethodHandleSummaryFactory.java"
    desc: "stable synthetic application bridge与summary cache"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaInvokeDynamicInstaller.java"
    desc: "RTA invokedynamic selector与build-time metadata state"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/InvokeDynamicBootstrapModelRegistry.java"
    desc: "immutable exact-key invokedynamic model registry"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/InvokeDynamicModelResolver.java"
    desc: "algorithm-independent bootstrap解码与typed resolution"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaInvokeDynamicTargetSelector.java"
    desc: "RTA-owned reachable bootstrap execution与metadata capture"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaInvokeDynamicTargetSelector.java"
    desc: "ZeroCFA-owned reachable bootstrap execution与metadata capture"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedInvokeDynamicTargetSelector.java"
    desc: "optimized-owned reachable bootstrap execution与metadata capture"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/AltMetafactoryBootstrapModel.java"
    desc: "Java 8 altMetafactory synthetic lambda model"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassOwnershipIndex.java"
    desc: "binary-name ownership、classpath precedence 与 duplicate evidence"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/JdkCallbackReachabilityTest.java"
    desc: "三种algorithm通过target JDK 8 callback dispatch的集成验证"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/WalaFixedPointModelsTest.java"
    desc: "三种algorithm的ServiceLoader、MethodHandle与altMetafactory关键路径"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassSource.java"
    desc: "dependency ArtifactCoord logical source identity"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphTimeoutMonitor.java"
    desc: "WALA cooperative timeout/cancel monitor"
---

# Feature: Call Graph Engine

## Summary

每个 relevant target Module 使用 command-wide `--call-graph-algorithm` 构建一个独立 WALA Call Graph。默认 `rta` 直接使用 `BasicRTABuilder` 的全局已实例化 compatible class reachability；`zero-cfa` 与 `optimized-0-1-cfa` 继续提供两种 ZeroX precision。Reflection、ServiceLoader、MethodHandle 与注册的 `invokedynamic` model 均在 `makeCallGraph(...)` 前进入 fixed point。构图完成后 session 只暴露 graph、CHA、IR cache、ownership 与 immutable model metadata，不暴露 builder 或 installer。

## Design Decisions

- `rta` 是 CLI 默认值；strategy 直接创建 `BasicRTABuilder`，不经过 `ZeroXCFABuilder`，也不伪造 points-to value。
- `zero-cfa` policy 只启用 `CONSTANT_SPECIFIC`：普通 allocation按 concrete class合并，constant继续保持 identity。
- `optimized-0-1-cfa` 使用 `ALLOCATIONS | CONSTANT_SPECIFIC | SMUSH_MANY | SMUSH_PRIMITIVE_HOLDERS | SMUSH_STRINGS | SMUSH_THROWABLES`，保持原 allocation-sensitive contract。
- 算法在 command 级选择并应用到全部 Module；不存在 per-Module override、timeout fallback或同一 run 混用算法。
- 三种算法由唯一 Factory 选择独立 strategy。Request/Result 与 model metadata 是 immutable boundary；builder、selector、interpreter、installer与build-time collector都是strategy-local state。`ModuleCallGraphEngine`在strategy选择前只创建一次immutable `ServiceLoaderProtocolIndex`，三套installer分别消费，不在共享model中判断algorithm。
- `--wala-reflection-options`（alias `--reflection-options`）接受 WALA `ReflectionOptions` enum name，大小写不敏感；默认 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。该 bounded default启用WALA原生string reflection、`Method.invoke`与一次flow-to-casts配置，同时避免WALA 1.8.0 `FULL`在RTA + default bypass下的无界扩张。实际target closure仍受selected builder的WALA能力边界约束；用户可显式选择`FULL`或其他原生enum value。
- ZeroCFA 对 modeled ServiceLoader allocation 使用 service/loader-specific `ConstantKey`，使 constant service receiver Context不因普通 class-based allocation丢失；真正的 class-based ServiceLoader receiver使用 deterministic `ALL_CONFIGURED_SERVICES` aggregate fallback。

## Behavior Contract

- Diagnostic与HTML Report使用实际稳定算法标识 `rta`、`zero-cfa` 或 `optimized-0-1-cfa`，并展示实际 WALA ReflectionOptions；Report Terminology随算法变化。
- Call Graph保持 conservative over-approximation；class-based merging或smushing可能改变 nodes、edges、contexts与候选 Impact Path数量，但不改变 Module status、timeout、query和publication contract。
- ServiceLoader、MethodHandle与注册的 `invokedynamic` model limitation 使用 typed reason/code/location/detail；RTA 无法从 caller-local IR推导 MethodHandle 或 ServiceLoader contract 时保留 empty/default protocol behavior并使 Module `INCONCLUSIVE`，不猜测业务 target。

## Scope and Logical Ownership

- `PROJECT`：当前 Module `target/classes`。
- `REACTOR_DEPENDENCY`：resolved reactor closure 的 `target/classes`。
- `DEPENDENCY`：`ArtifactCoord` logical source；physical JAR path 只由 command-scoped `IJarRepository` 持有。
- `JDK`：显式 `--java-home` 的 JDK 8 boot/ext JAR。
- `SYNTHETIC`：WALA lambda、`altMetafactory` lambda 与 ServiceLoader provider iterator。
- Scope load 前按 `JDK > PROJECT > REACTOR_DEPENDENCY > DEPENDENCY` 建立 single-winner ownership。Reactor/external tier 保留当前 Module 的 Maven GraphML traversal order。
- Dependency duplicate evidence、`MethodId.sourceId` 与 Report source 均使用 coordinate；PROJECT/reactor/JDK 可继续使用非 dependency path identity。
- byte-identical duplicate 静默去重。内容不同的 duplicate 记录 winner、loser logical source 与 precedence reason；不改变 Module status。
- `module-info.class` 与 `META-INF/versions/**` 不参与 ownership。Ownership filter 只隐藏 loser class entry，JAR 内其他唯一 class/resource 仍向 WALA 暴露。

## Actors / Entrypoints

- `impact` per-Module pipeline触发构图；用户通过 command-wide `--call-graph-algorithm` 选择算法，并通过 Module选择和 repeatable entrypoint selector控制 PROJECT roots。
- Entrypoint class只由当前 Module `target/classes/**/*.class` 的 immutable `EntrypointClassIndex` 提供，不通过 CHA ownership或 classpath precedence识别。ASM internal name是唯一 identity；duplicate name、CHA missing class或 binary name不一致使当前 Module fail。
- Scanner排除 `module-info.class`、interface与annotation。Abstract class保留；每个 selected class选择全部 non-abstract declared methods，包括 constructor、`<clinit>`、static、native、concrete bridge/synthetic method，不自动加入 inherited method。
- Slash selector直接匹配无前缀 JVM internal name。普通 segment支持 `*` 与 `?`；`**` 只能作为最后一个完整 segment。Include取并集，exclude优先。Interface-only匹配等同于零匹配。
- Selector门禁与 Call Graph构造复用同一个 index。`--entrypoint-include`/`--entrypoint-exclude` 不裁剪 scope、CHA、Reflection、model provider或其他 origin reachability。
- 每个 JVM parameter slot只有一个 candidate：primitive、array、concrete或 unresolved reference保留 declared `TypeReference`；resolved interface/abstract reference使用 Module/CHA 内共享的 `BypassSyntheticClass` placeholder。普通 instance method的 `this`遵循相同规则；abstract class的 concrete instance method与 constructor因此使用 fake receiver。
- Placeholder仅提供 concrete、assignable identity，不生成 abstract method body，也不枚举或连接真实 subtype/implementor。该边界可能遗漏 implementation-only impact path。`parameterCandidateCount` 是所有 entrypoint JVM parameter slot总数，包含 instance `this`，不随 subtype数量增长。
- `REACTOR_DEPENDENCY`、`DEPENDENCY`、`JDK` 只通过 reachability 进入；零 target entrypoint 使当前 Module fail。

## Fixed-point Installation Order

`ModuleCallGraphEngine` 完成 ownership、raw Structural Reference、scope、CHA 与 entrypoints 后，构造 immutable `CallGraphBuildRequest`。`CallGraphStrategyFactory` 一次性选择 strategy；每个 strategy 自己创建 `AnalysisOptions`、安装 selected ReflectionOptions、default selectors/bypass 与 model Decorator，并只调用一次 `makeCallGraph(...)`：

```text
CallGraphBuildRequest
  → RtaCallGraphStrategy → BasicRTABuilder
  → ZeroCfaCallGraphStrategy → ZeroXCFABuilder(CONSTANT_SPECIFIC)
  → OptimizedZeroOneCfaCallGraphStrategy → ZeroXCFABuilder(ALLOCATIONS + CONSTANT_SPECIFIC + smushing)
  → CallGraphStrategyResult(CallGraph, immutable StrategyModelMetadata)
```

RTA 使用 caller-local `IR`/`DefUse` 解析 `Lookup.findStatic*` 到 `invokeExact`/`invokeWithArguments` target。解析结果以 operation、callsite descriptor与真实target binary identity为cache key，进入 application loader下的stable synthetic `wala/methodhandle/RtaBridge` summary；graph路径为caller → bridge →真实target，不直接伪造caller →真实target边。bridge只为fixed point表达已解析调用，不传播具体`MethodHandle`/`MethodType` value。unresolved target产生`RTA_METHOD_HANDLE_LOCAL_TARGET_UNRESOLVED`并委托default target。两种 ZeroX strategy继续使用 WALA MethodHandle extension。三者均保留 WALA default lambda selector、项目 `altMetafactory` model 与 ServiceLoader model。

`AnalysisCacheImpl` 使用 `SSAOptions.defaultOptions()`。Builder/query 在 Module 内单线程；不同 Module 受 `--analysis-parallelism` 控制。`CallGraphTimeoutMonitor` 仅使用 WALA cooperative cancel；`0` 表示无限等待，timeout 不输出 partial graph。

## Core Flow

1. 建立 winner-only ownership、structural metadata、WALA scope与CHA。
2. 从 immutable PROJECT class index生成 declared-type entrypoints。
3. Factory选择独立 strategy，安装 selected ReflectionOptions、default selectors/bypass、MethodHandle、`invokedynamic`与ServiceLoader model。
4. 单线程求解 selected RTA/points-to 与 Call Graph fixed point；timeout仅通过 cooperative monitor取消，失败使用 typed `CallGraphFailureKind.TIMEOUT`。
5. 将 graph、IR cache、ownership和immutable model metadata封装为只读 query session。

## ServiceLoader Model

- CHA 建立后、fixed point 前，`ServiceLoaderProtocolIndex` 从 Application scope `ModuleEntry` 合并 `META-INF/services/*`。Directory classes root 通过 resource-only Module 暴露配置；dependency JAR resource 来自 repository-backed WALA Module。
- 配置以 UTF-8 读取，删除 `#` comment、空行与 duplicate provider，service/provider 稳定排序。
- Provider 必须 public、非 abstract/interface、assignable，并有 public zero-arg constructor；非法或 unresolved 配置形成 stable limitation。
- 覆盖 JDK 8 `load(Class)`、`load(Class, ClassLoader)`、`loadInstalled(Class)` → `iterator()` → `next()`。
- 三套strategy各自创建ServiceLoader Context、selector与`SSAContextInterpreter`/IR cache；共享层只保留immutable provider facts、protocol identity与typed contract resolution。Constant service `Class`进入strategy-owned Context。显式 loader `InstanceKey`或基于caller method binary identity、bytecode PC与operation的implicit load-site identity一起进入Context；optimized模式通过allocation key传播，ZeroCFA通过service/loader-specific `ConstantKey`传播。
- RTA 不使用 actual points-to value；只读取当前 Application caller 的 Class metadata constant、local definition-use 与直接 `load(...).iterator()` 链来确定 service contract。RTA-owned summary分配`ServiceLoader`、synthetic iterator与有效provider并调用provider constructor，使Basic RTA的全局instantiated-type fixed point闭合，但`load`、`iterator`、`next`均返回`null`，不传播representative ServiceLoader/iterator/provider value，也不创建provider `phi`。无法确定时使用隔离的 empty-provider Context并记录 `RTA_SERVICE_LOADER_CONTRACT_UNRESOLVED`。
- ZeroCFA 遇到无法恢复 exact Context 的 class-based ServiceLoader receiver时，使用 `ALL_CONFIGURED_SERVICES` aggregate iterator，按 service/provider稳定排序并去重，保守保留全部有效 provider constructor path。
- ZeroX `load` synthetic IR分配service-specific `ServiceLoader` instance；`iterator`分配service-specific provider iterator；`next`分配全部有效provider、调用constructor，并合并返回points-to set。该provider-return summary不被RTA复用。
- 非 constant service type、missing service 或 unresolved provider 使用隔离的 empty-provider Context，并记录 limitation；不会回退到真实 JDK ServiceLoader 实现，也不会 broad-match compatible callsite。
- Provider constructor edge 标记 `SERVICE_LOADER`；应用 interface/virtual invoke 到 provider implementation 保留真实 invoke kind。
- 不覆盖 Java 9 `stream`、`Provider.get` 或 `ModuleLayer`。

## invokedynamic Registry

- `InvokeDynamicBootstrapModelRegistry` 以 bootstrap owner/name/descriptor 精确注册；构建后 immutable，duplicate key fail-fast。
- Registry可通过`toBuilder()`保留既有模型后增补自定义bootstrap；扩展JDK默认registry不会意外移除内建`altMetafactory`。
- `InvokeDynamicModelResolver`只负责bootstrap解码；三套strategy-specific `MethodTargetSelector`只在reachable callsite被WALA求解时执行。它们记录bootstrap handle与direct bootstrap argument method handle，并按stable caller/PC/kind/handle identity deduplicate。
- Standard `metafactory` 委托 WALA default `LambdaMethodTargetSelector`；两种 ZeroX 使用 WALA MethodHandle extension，RTA 使用 caller-local MethodHandle model。
- 内建 `altMetafactory` 解析 `samMethodType`、`implMethod`、`instantiatedMethodType`、flags、marker interfaces 与 bridge descriptors；支持 `FLAG_SERIALIZABLE`、`FLAG_MARKERS`、`FLAG_BRIDGES`。
- `altMetafactory` 创建 synthetic factory/lambda class、captured fields、SAM/bridge trampoline；allocation 与 implementation invoke 参与同一 fixed point。
- Implementation method 已删除或 descriptor 改变时，synthetic trampoline 保留 declared target，但 WALA 不产生不存在的 callee node/edge；构图期 direct method-handle terminal evidence 继续可用。协议本身无法解析时才产生 explicit unsupported limitation。
- Unknown bootstrap 不把 argument handle 强制转换为 graph edge。reachable unknown bootstrap 记录 terminal evidence 与 limitation，使 Module `INCONCLUSIVE_INVOKEDYNAMIC_MODEL`；unreachable bootstrap 不产生 evidence/limitation。
- 当前 target 是 JDK 8；不承诺 `ConstantDynamic` 或 newer-JDK bootstrap coverage。新增协议通过 registry 扩展。

## Implementation Boundaries

- Winner-only Structural Reference metadata在`makeCallGraph(...)`前经repository lease收集，并作为immutable `StructuralReferenceIndex`放入session；query阶段由`StructuralReferenceResolver`绑定ChangePoint。
- 构图后禁止新增 node/edge、attachment overlay、重新扫描 whole scope classfile 或构建第二张 Call Graph。
- Query 可 read-only 访问 graph predecessor、possible sites、reachable IR、dynamic evidence、model limitations、synthetic edge metadata 与 precomputed structural metadata。

## Benchmark-only Topology Capture

- `impact --call-graph-diagnostics-output <json>`只在显式设置时启用；未设置时`ModuleCallGraphEngine`不创建topology analyzer、不遍历ranking、不计算path、不执行decompilation。
- Capture读取同一张已完成Call Graph并作为nullable immutable metadata进入session；不新增edge、不运行第二个builder、不改变Impact query或最终analysis status。JSON使用`schemaVersion: 3`并原子替换目标文件。
- 父榜以精确CGNode为单位，不合并WALA Context。CGNode identity包含`owner + name + descriptor + origin + Context + graphNodeId + walaSynthetic + sentinelRole`。Caller/Callee先按related CGNode count降序，再按distinct related IMethod、raw CGEdge与stable CGNode identity排序，各保留Top 10。
- 每个父榜CGNode包含一个按IMethod聚合的Top 10子榜；子榜按该IMethod代表的related CGNode count、raw CGEdge与stable Method identity排序。每个子项保留完整count、deterministic前10个exact CGNode/Context example与omitted count；这一层用于定位同一Method因Context或points-to传播产生的节点膨胀，同时限制HTML与tracked TSV体积。不输出独立points-to set排行榜。
- `getFakeRootNode()`、`getFakeWorldClinitNode()`及其incident edge与普通CGNode/CGEdge相同，参与父榜、IMethod子榜、raw edge count、strongly connected component（SCC）和shortest chain。Node的`sentinelRole`固定为`FAKE_ROOT`、`FAKE_WORLD_CLINIT`或`NONE`。
- 对每个Top CGNode执行reverse breadth-first search（BFS），root集合包含所有declared entrypoint以及WALA fake root/fake world-clinit。每个可达root输出一条deterministic shortest chain，`rootKind`区分`DECLARED_ENTRYPOINT`、`FAKE_ROOT`与`FAKE_WORLD_CLINIT`；同距离next step按stable CGNode identity解tie。BFS使用visited distance map，不重复展开recursion。
- Iterative Kosaraju SCC标记self-loop与多CGNode cycle；path step和父榜CGNode同时输出`cycle`。Diagnostics不再生成`UNREACHABLE_FROM_DECLARED_ENTRYPOINTS`；sentinel-only reachability由完整WALA chain及step上的`sentinelRole`直接表达。
- 每个父榜CGNode输出WALA IR。Capture保留`IMethod.isWalaSynthetic()`，避免将声明在JDK或dependency class上的`SummarizedMethod`误报为真实bytecode source。Source定位使用winner ownership：PROJECT/reactor classes directory、dependency `ArtifactCoord`对应repository JAR、target JDK 8 boot/ext JAR。Vineflower按exact owner/name/descriptor反编译；失败时输出ASM Method instructions；synthetic/WALA summary无bytecode时source为`UNAVAILABLE`，但仍展示IR。Multiline source与IR进入JSON/benchmark HTML，tracked TSV只保存各自status与SHA-256。

## Module Classification

- Blocking：PROJECT/reactor 命中 excluded JDK class、scope unreadable、零 PROJECT entrypoint、CHA/Call Graph failure、timeout。
- Coverage warning：external excluded JDK reference、MethodHandle/ServiceLoader limitation、reachable unsupported `invokedynamic`、SSA `UNKNOWN`。
- Typed coverage reason precedence为 bytecode diff > `INCONCLUSIVE_INVOKEDYNAMIC_MODEL` > `INCONCLUSIVE_METHOD_HANDLE_MODEL` > `INCONCLUSIVE_SERVICE_LOADER` > `INCONCLUSIVE_SCOPE_VALIDATION`；全部 limitation仍保留。Duplicate warning不进入 Coverage limitations。

## Acceptance

### Functional

- Given constant ServiceLoader配置；When任一算法完成 fixed point；Then provider constructor、implementation和内部调用存在于 graph；optimized allocation Context与ZeroCFA constant receiver key都不丢失 exact service path。
- Given RTA `ServiceLoader.load(Class<? extends Service>)`参数不是constant但provider值存在typed `checkcast Service`；When执行caller-local DefUse解析；Then只连接该contract的有效provider，且不产生unresolved limitation。完全无constant/checkcast evidence时使用empty-provider Context并记录typed limitation。
- Given standard lambda、MethodHandle或 supported `altMetafactory`；When reachable callsite被求解；Then既有 synthetic allocation、trampoline和implementation edge contract保持成立。
- Given caller-local `Lookup.findStatic`与`invokeExact`/`invokeWithArguments`；When三种algorithm分别构图；Then真实target均可达；RTA额外具有caller → stable application bridge →真实target路径和typed direct modeled handle evidence。
- Given reachable unknown bootstrap；When registry无对应 model；Then Module为 `INCONCLUSIVE`；unreachable bootstrap不产生 evidence或limitation。
- Given Call Graph成功完成；When生成 Diagnostic与Report；Then算法和 WALA ReflectionOptions等于 command选择；默认分别为 `rta` 与 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`，两种 legacy算法及其他 WALA enum value保持可选。
- Given Stream/Optional、Collection/Map、AbstractExecutorService/CompletableFuture或Thread callback；When三种algorithm使用target JDK 8构图；Then application callback存在来自非native、非synthetic且具有IR的JDK dispatch predecessor。callback测试使用`ReflectionOptions.NONE`隔离无关Reflection状态空间；默认ReflectionOptions由独立CLI与benchmark门禁验证。
- Given已完成Call Graph包含WALA fake root或fake world-clinit；When启用benchmark topology capture；Then sentinel node及incident edge参与CGNode ranking、IMethod子榜、SCC与shortest chain，chain step使用typed `sentinelRole`标记，且不输出declared-entrypoint unreachable状态。
- Given `AccessController.doPrivileged(PrivilegedAction)`；When target JDK 8构图；Then callback通过WALA内置`SummarizedMethod` native model可达。JDK 8该API本身是native，不能宣称经过真实JDK bytecode body。

### Non-Functional

- Given任意 Module进入构图；When选择 `rta`；Then实际 builder为 `BasicRTABuilder`且不注入 points-to value；When选择 `zero-cfa`；Then policy恰为 `CONSTANT_SPECIFIC`；When选择 `optimized-0-1-cfa`；Then `ALLOCATIONS`、`CONSTANT_SPECIFIC`与四类 smushing policy作为固定组合启用。
- Given Module analysis开始；When执行 builder与query；Then Module内保持单线程，Module间并发边界不变。
- Given fixed point完成；When进入Impact query；Then不执行 overlay、whole-scope重扫或第二张 target Call Graph。
- Given版本、scope、selector与环境相同；When重复执行分析；Then输出保持 deterministic。

## Edge Cases

- nonconstant/missing/invalid ServiceLoader配置形成 stable limitation，不回退到 broad compatible-callsite matching。
- timeout不发布 partial graph；Module按 `FAILED_CALL_GRAPH_TIMEOUT`处理，其他 Module继续。
- 零 PROJECT entrypoint、scope unreadable或CHA/Call Graph failure属于 blocking Module结果。
- class-based merging或smushing可能增加 conservative edge与candidate path；只有后续 `PROVEN_EQUIVALENT` SSA结果允许删除候选路径。
- WALA 1.8.0 `BasicRTABuilder`的`TypeBasedHeapModel`不提供metadata-object `InstanceKey`，且其`Class.newInstance` interpreter不枚举summary内constructor callsite。因此RTA即使选择包含`APPLICATION_GET_METHOD`的ReflectionOptions，也可能保留`Class.forName`/Reflection API node而无法闭合constructor或`Method.invoke`业务target。ZeroX保留metadata constant，但在完整target JDK 8 scope启用`Method.invoke`可能显著扩大fixed point。项目不用post-build补边或fake metadata value绕过该边界。
