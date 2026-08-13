---
title: "Call Graph Engine"
type: feature
relations:
  - path: "wiki/features/jdk-method-models.md"
    desc: "非CHA algorithm的JDK 8 Synthetic IR selector与严格安装contract"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "per-Module pipeline 与并发边界"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "coordinate-based JAR repository 初始化输入"
  - path: "wiki/features/impact-tracing.md"
    desc: "统一Evidence collector与冻结session上的Impact query"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphAlgorithm.java"
    desc: "command-wide 算法标识与默认选择"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphPolicy.java"
    desc: "algorithm相关JDK model默认值与统一validation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphStrategyCapabilities.java"
    desc: "points-to、JDK body、local constant与dynamic protocol capability contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/JdkModelSelection.java"
    desc: "command-wide JDK Method Model标识与默认选择"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/JdkModelInstallation.java"
    desc: "四种非CHA strategy共用的per-graph严格安装边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphBuildRequest.java"
    desc: "algorithm、k-object depth、ReflectionOptions、JDK model与其他model input"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphConfiguration.java"
    desc: "command-wide algorithm、k-object depth与ReflectionOptions配置"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphEngine.java"
    desc: "per-Module scope、CHA、timeout 与 strategy 编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphAlgorithmStrategy.java"
    desc: "算法 strategy 边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ChaCallGraphStrategy.java"
    desc: "WALA CHACallGraph与Analyzer-owned no-body/protocol interpreter"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ChaDispatchFilteringClassHierarchy.java"
    desc: "CHA Object.toString/hashCode的Diff-directed target过滤边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/LocalConstantResolver.java"
    desc: "caller-local SSA String/Class常量有限回溯"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/RtaCallGraphStrategy.java"
    desc: "BasicRTABuilder 与 RTA-local model 安装"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ZeroCfaCallGraphStrategy.java"
    desc: "class-based ZeroCFA 构建"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/OptimizedZeroOneCfaCallGraphStrategy.java"
    desc: "allocation-sensitive optimized 0-1-CFA 构建"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/KObjCallGraphStrategy.java"
    desc: "可配置receiver allocation string深度的纯k-object-sensitive构建"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/KObjCallGraphBuilder.java"
    desc: "复用WALA default selector的k-object builder"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/KObjContextSelector.java"
    desc: "WALA 1.8.0 n-object语义与ClassFactory Context兼容合并"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/WalaReflectionOptions.java"
    desc: "command-wide WALA ReflectionOptions Value Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointClassIndex.java"
    desc: "current Module target/classes 的 immutable root class selection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointClassScanner.java"
    desc: "InnerClasses privacy与non-private concrete method root筛选"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DeclaredTypesEntrypoint.java"
    desc: "单 candidate declared-type 参数建模"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointSyntheticTypeRegistry.java"
    desc: "per-CHA interface/abstract synthetic placeholder复用"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphSession.java"
    desc: "live graph、CHA、cache、ownership 与 immutable model metadata"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DependencyBodyBoundary.java"
    desc: "四种非CHA algorithm共享的no-op/factory interpreter与typed boundary detection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DependencyBodyBoundaryMetadata.java"
    desc: "real/no-op/factory method counts 与 immutable evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleChangedPathSelection.java"
    desc: "resolved logical artifact 的 real-IR/no-op policy source"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/DependencyBodyBoundaryTest.java"
    desc: "CHA leaf boundary与四种非CHA no-op/factory/dangerous transfer regression"
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
    desc: "Schema v7 topology、capability、Evidence与local constant统计JSON原子输出"
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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/KObjServiceLoaderInstaller.java"
    desc: "k-obj-owned ServiceLoader allocation Context与interpreter安装"
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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/KObjInvokeDynamicTargetSelector.java"
    desc: "k-obj-owned reachable bootstrap execution与metadata capture"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/AltMetafactoryBootstrapModel.java"
    desc: "Java 8 altMetafactory synthetic lambda model"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassOwnershipIndex.java"
    desc: "binary-name ownership、classpath precedence 与 duplicate evidence"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/JdkCallbackReachabilityTest.java"
    desc: "三种broad real-JDK与k-obj focused Primordial callback dispatch验证"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/MinimalJdk8RuntimeFixture.java"
    desc: "高精度MethodHandle与Thread callback的最小Java 8 Primordial bytecode"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/WalaFixedPointModelsTest.java"
    desc: "四种algorithm fixed-point、k-object深度、递归收敛与private root关键路径"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/KObjContextSelectorTest.java"
    desc: "ClassFactory单一Context、receiver priority与allocation key验证"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/KObjClassFactoryContextRegressionTest.java"
    desc: "完整JDK 8 Reflection路径与ClassFactory Context shape门禁"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassSource.java"
    desc: "dependency ArtifactCoord logical source identity"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphTimeoutMonitor.java"
    desc: "WALA cooperative timeout/cancel monitor"
---

# Feature: Call Graph Engine

## Summary

每个relevant target Module使用独立strategy构建一张WALA Call Graph。默认算法为Class Hierarchy Analysis（CHA），默认组合是`cha + changed-paths + jdk-model none`。CHA保留JDK leaf但不遍历JDK body，不构建points-to；只允许从`Class.forName(String)`和`ServiceLoader.load(Class)`参数出发，在单个reachable caller的Static Single Assignment（SSA，静态单赋值）definition上执行有界常量回溯。RTA、ZeroCFA、optimized 0-1-CFA与`k-obj`保留既有传播模型。所有strategy由同一capability contract描述，构图后再由算法无关collector生成并绑定统一Evidence。

## Design Decisions

- `cha`是CLI、pipeline与直接Java API默认值；strategy使用WALA 1.8.0 `CHACallGraph(hierarchy, false)`和`Everywhere` Context。
- RTA strategy直接创建`BasicRTABuilder`，不经过`ZeroXCFABuilder`，也不伪造points-to value。
- `zero-cfa` policy 只启用 `CONSTANT_SPECIFIC`：普通 allocation按 concrete class合并，constant继续保持 identity。
- `optimized-0-1-cfa` 使用 `ALLOCATIONS | CONSTANT_SPECIFIC | SMUSH_MANY | SMUSH_PRIMITIVE_HOLDERS | SMUSH_STRINGS | SMUSH_THROWABLES`，保持原 allocation-sensitive contract。
- `k-obj`使用基于`ZeroXCFABuilder`的`KObjCallGraphBuilder`与`ALLOCATIONS | CONSTANT_SPECIFIC`，不启用smushing，也不安装外层`nCFAContextSelector`。Analyzer-owned `KObjContextSelector`保持WALA 1.8.0 n-object语义：instance Context的allocation string最多保留配置的`k`层；普通static调用复用object Context，直接或间接递归最终命中已有Context并收敛。较大的`k`仍可能扩大有限状态空间、内存与耗时。
- WALA 1.8.0的`k-obj`使用Analyzer-side ClassFactory compatibility merge。Builder复用`ZeroXCFABuilder`已创建且包含Reflection selectors的唯一default selector，不额外创建或调用`ClassFactoryContextSelector`。普通调用保持k-object-first；仅当原base为ClassFactory返回合法`JavaTypeContext`时使用base-first，使`RECEIVER`保持`TypeAbstraction`，同时保留k-object Context的`ALLOCATION_STRING_KEY`等非冲突key。每个有效ClassFactory Context只包含一个语义`JavaTypeContext`。
- 算法在 command 级选择并应用到全部 Module；不存在 per-Module override、timeout fallback或同一 run 混用算法。
- Dependency analysis scope 在 command 级请求，但 `changed-paths` 的 occurrence graph/path recovery 异常会使单个 Module actual mode fallback 到 `full`。`full` 不安装 dependency body boundary。
- 五种算法由唯一、穷尽Factory选择独立strategy。`CallGraphAlgorithmStrategy`只负责topology、protocol summary、standard metadata与typed coverage limitation；ChangePoint绑定、Impact Path、disposition和Report规则在strategy之外。
- `--wala-reflection-options`（alias `--reflection-options`）接受WALA `ReflectionOptions` enum name。CHA不应用该设置，Report显示`not applied by cha`；其他algorithm保持WALA原生Reflection行为。
- `--jdk-model`接受`jdk8`或`none`。CHA固定`none`，显式`cha + jdk8`失败；其他algorithm未指定时默认`jdk8`且仍可显式`none`。CLI、pipeline与直接Java API共用`CallGraphPolicy`校验。
- ZeroCFA 对 modeled ServiceLoader allocation 使用 service/loader-specific `ConstantKey`，使 constant service receiver Context不因普通 class-based allocation丢失；真正的 class-based ServiceLoader receiver使用 deterministic `ALL_CONFIGURED_SERVICES` aggregate fallback。
- Method-body policy 依据 resolved `IMethod.getDeclaringClass()` 的 logical artifact source判断；call-site declared owner不参与。Selected subclass override 即使覆盖 unselected base declaration，仍按 override 的 declaring source使用真实 IR。

## Behavior Contract

- Diagnostic与HTML Report使用effective algorithm、Reflection applied状态、JDK model和strategy capabilities；仅`k-obj`展示实际深度。CHA的Reflection状态固定为not applied。
- Call Graph保持 conservative over-approximation；class-based merging或smushing可能改变 nodes、edges、contexts与候选 Impact Path数量，但不改变 Module status、timeout、query和publication contract。
- ServiceLoader、Class.forName、MethodHandle与注册的`invokedynamic` limitation使用typed reason/code/location/detail。局部常量无法唯一解析时不猜测业务target，Module为`INCONCLUSIVE`。
- 路径外 external method 的普通 no-op 不单独改变 Module status。Dangerous transfer、flow-to-cast factory 或其他明确 typed boundary limitation 使 Module 为 `INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`。

## Scope and Logical Ownership

- `PROJECT`：当前 Module `target/classes`。
- `REACTOR_DEPENDENCY`：resolved reactor closure 的 `target/classes`。
- `DEPENDENCY`：`ArtifactCoord` logical source；physical JAR path 只由 command-scoped `IJarRepository` 持有。
- `JDK`：显式 `--java-home` 的 JDK 8 boot/ext JAR。
- `SYNTHETIC`：WALA lambda、`altMetafactory` lambda 与 ServiceLoader provider iterator。
- `changed-paths` selected external source：必须是target evidence中的selected binding，且至少位于一条`Module root -> normalized changed dependency occurrence`完整路径中；seed的child/downstream不自动selected，mediation loser不能进入policy。
- `changed-paths` unselected external source：class、method declaration、resource、ownership、CHA 与 resolution仍真实存在，仅 method body被 boundary解释。
- Scope load 前按`JDK > PROJECT > REACTOR_DEPENDENCY > DEPENDENCY`建立single-winner ownership。Reactor/external tier只使用Schema v3 selected projection的Maven traversal order；raw occurrence traversal不参与classpath排序。
- Dependency duplicate evidence、`MethodId.sourceId` 与 Report source 均使用 coordinate；PROJECT/reactor/JDK 可继续使用非 dependency path identity。
- byte-identical duplicate 静默去重。内容不同的 duplicate 记录 winner、loser logical source 与 precedence reason；不改变 Module status。
- `module-info.class` 与 `META-INF/versions/**` 不参与 ownership。Ownership filter 只隐藏 loser class entry，JAR 内其他唯一 class/resource 仍向 WALA 暴露。

## Actors / Entrypoints

- `impact` per-Module pipeline触发构图；用户通过 command-wide `--call-graph-algorithm` 选择算法，并通过 Module选择和 repeatable entrypoint selector控制 PROJECT roots。
- Entrypoint class只由当前 Module `target/classes/**/*.class` 的 immutable `EntrypointClassIndex` 提供，不通过 CHA ownership或 classpath precedence识别。ASM internal name是唯一 identity；duplicate name、CHA missing class或 binary name不一致使当前 Module fail。
- Scanner排除 `module-info.class`、interface、annotation与private nested class；nested privacy读取class自身`InnerClasses` entry access flags，不能依赖class header。Abstract class保留；每个selected class只选择non-private、non-abstract declared method。Public、protected、package-private、static、native与concrete bridge/synthetic method保留；private constructor、static method与instance method不成为root。
- Slash selector直接匹配无前缀 JVM internal name。普通 segment支持 `*` 与 `?`；`**` 只能作为最后一个完整 segment。Include取并集，exclude优先。Interface-only匹配等同于零匹配。
- Selector门禁与 Call Graph构造复用同一个 index。Engine生成roots时再以`IClass.isPrivate()`、nested-class metadata与`IMethod.isPrivate()`防守性过滤，手工index也不能产生private root。`--entrypoint-include`/`--entrypoint-exclude`与privacy过滤均不裁剪scope、CHA、Reflection、model provider或其他origin reachability；从non-private root可达的private method仍作为普通CGNode参与Impact tracing。
- 每个 JVM parameter slot只有一个 candidate：primitive、array、concrete或 unresolved reference保留 declared `TypeReference`；resolved interface/abstract reference使用 Module/CHA 内共享的 `BypassSyntheticClass` placeholder。普通 instance method的 `this`遵循相同规则；abstract class的 concrete instance method与 constructor因此使用 fake receiver。
- Placeholder仅提供 concrete、assignable identity，不生成 abstract method body，也不枚举或连接真实 subtype/implementor。该边界可能遗漏 implementation-only impact path。`parameterCandidateCount` 是所有 entrypoint JVM parameter slot总数，包含 instance `this`，不随 subtype数量增长。
- `REACTOR_DEPENDENCY`、`DEPENDENCY`、`JDK` 只通过 reachability 进入；零 target entrypoint 使当前 Module fail。

## Strategy Build Order

`ModuleCallGraphEngine`完成ownership、raw Structural Reference、scope、CHA、ServiceLoader target provider index与entrypoints后，构造immutable`CallGraphBuildRequest`。Factory选择唯一strategy。CHA直接初始化`CHACallGraph`；其他strategy创建自己的builder并安装selected ReflectionOptions、JDK Method Model和dynamic protocol model。strategy返回graph、capabilities、protocol edge metadata与typed limitation；统一Evidence collector随后扫描完成图并冻结session：

```text
CallGraphBuildRequest
  → ChaCallGraphStrategy → CHACallGraph(Everywhere)
  → RtaCallGraphStrategy → BasicRTABuilder
  → ZeroCfaCallGraphStrategy → ZeroXCFABuilder(CONSTANT_SPECIFIC)
  → OptimizedZeroOneCfaCallGraphStrategy → ZeroXCFABuilder(ALLOCATIONS + CONSTANT_SPECIFIC + smushing)
  → KObjCallGraphStrategy → KObjCallGraphBuilder(ZeroX, ALLOCATIONS + CONSTANT_SPECIFIC)
    → KObjContextSelector(k, existing default selector) → makeCallGraph
  → CallGraphStrategyResult(CallGraph, immutable StrategyModelMetadata)
  → ChangePointEvidenceCollector
  → ModuleCallGraphSession(CallGraph + ChangePointEvidenceIndex)
```

RTA使用caller-local`IR`/`DefUse`解析`Lookup.findStatic*`到`invokeExact`/`invokeWithArguments`target。三种points-to strategy继续使用WALA MethodHandle extension。CHA保留WALA自带lambda metafactory；unresolved invokedynamic与MethodHandle只生成typed limitation，不切换algorithm。

`AnalysisCacheImpl` 使用 `SSAOptions.defaultOptions()`。Builder/query 在 Module 内单线程；不同 Module 受 `--analysis-parallelism` 控制。`CallGraphTimeoutMonitor` 仅使用 WALA cooperative cancel；`0` 表示无限等待，timeout 不输出 partial graph。

## Core Flow

1. 建立 winner-only ownership、structural metadata、WALA scope与CHA。
2. 从 immutable PROJECT class index生成 declared-type entrypoints。
3. Factory选择strategy并校验capabilities；CHA安装no-body/protocol interpreter，其他algorithm安装各自WALA model与dependency body boundary。
4. 单线程构建selected Call Graph；interpreter/node/callsite处理检查cooperative timeout，超时映射`FAILED_CALL_GRAPH_TIMEOUT`。
5. 统一collector扫描可达method，绑定method/field/type/structural/resource/dynamic reference evidence。
6. 将graph、Evidence、limitation、IR cache、ownership和strategy metadata冻结为只读query session。

## CHA Strategy

- 普通target method使用`Everywhere` Context；PROJECT、reactor dependency和允许展开的external dependency委托context-insensitive interpreter。
- JDK method保留caller到JDK leaf edge，call/new site为空；普通JDK leaf不产生coverage limitation，且永远不安装JDK Method Model。
- `changed-paths`路径外external dependency method是no-op leaf。只有实际到达该leaf时生成`DEPENDENCY_BODY_BOUNDARY_REACHED`；factory与dangerous transfer计数固定为零。`full`正常展开external method body。
- CHA不安装factory、dangerous transfer、flow-to-cast、points-to或heap推断；不执行跨method或通用数据流fixed point。

### Diff-directed Object Dispatch

- 只对declared owner内部名为`java/lang/Object`且selector精确为`toString()Ljava/lang/String;`或`hashCode()I`的virtual dispatch过滤；`String.toString()`、custom base class同selector与其他method保持标准CHA语义。
- Analyzer-owned `IClassHierarchy` decorator在WALA `getPossibleTargets()`的两个overload返回候选时过滤，因此无关target不会先创建`CGNode`再删除。Decorator不缓存target；`addClass()`、`clearCaches()`与其余hierarchy API透明委托，兼容lambda class加入后重新闭合。
- 永远保留`java/lang/Object`基础实现、PROJECT、reactor dependency与synthetic target。Application loader下无法可靠分类的target fail-open保留；其他JDK override裁剪。
- External dependency只有在target classpath winner artifact一致，且同owner/name/target descriptor存在`METHOD_ADDED`、`METHOD_BODY_CHANGED`或`METHOD_ACCESS_NARROWED`时保留。`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`和`CLASS_REMOVED`不存在可构图target，继续只作为统一Evidence terminal。
- 该filter同时作用于`changed-paths`与`full`，不改变其他四种algorithm。已接受的精度边界是：未变更external `toString`/`hashCode`内部通往其他ChangePoint的传递路径可能被裁剪；filter本身不生成coverage limitation。

## Bounded Local Constant Resolution

`LocalConstantResolver`只接收caller `IR`、`DefUse`、argument value number与`STRING|CLASS`kind。允许SymbolTable direct constant、Class metadata、普通SSA复用、`checkcast`、`pi`及所有输入都解析为同一值的`phi`；visited value set终止cycle。

明确不解析method参数、instance/static field、array、method return、跨method传播、points-to、字符串拼接、`StringBuilder`、string-concat `invokedynamic`、不同常量或部分unresolved的`phi`。结果只有`RESOLVED`、`UNRESOLVED`与`NOT_APPLICABLE`；不创建node或执行path query。

CHA中“无数据流分析”是指不执行points-to、heap、interprocedural或通用data-flow analysis；只允许以上单caller、单API argument、definition-only有限回溯。

## Dependency Method-body Boundary

### No-op Summary

- 只覆盖路径并集外 external JAR 的 resolved method；PROJECT、REACTOR_DEPENDENCY、JDK、SYNTHETIC、selected external method 与现有 WALA model不覆盖。
- 不向 analysis cache 请求被覆盖 method 的原始 IR。
- `void`、constructor 与 class initializer生成空 body和正常 return；primitive返回 JVM 默认值；reference返回 `null`。
- 不生成内部 call、field read/write、callback、exception或thread behavior。Caller 到 resolved no-op callee 的 edge保留，callee不继续展开真实实现。
- 普通 no-op summary按 resolved method稳定复用。

### Flow-to-cast Factory

No-op callee只有满足全部条件时才使用 caller/call-site-specific factory IR：invoke具有未丢弃的reference result；同一 caller `DefUse` 仅经direct use或bounded `phi`/`pi`到达`checkcast`；cast target可由当前CHA解析，且是concrete、非interface、非abstract的PROJECT、reactor或selected external class。Field、array、collection、unknown call、跨method flow及另一个no-op external target均不推断。

Factory summary使用真实 resolved callee owner、method与descriptor，生成稳定排序、去重后的 `NewInstruction` 和 return，不创建 synthetic owner且不调用constructor。四种builder按自己的 `InstanceKey` policy消费allocation。Evidence保存caller、callee、logical artifact、bytecode PC、cast位置、推断类型与Context。

### Dangerous Transfer

对 reachable no-op external invoke 检查 receiver、argument、array与varargs。Descriptor、`TypeInference`或bounded `DefUse` 能证明值可能是包含 `ChangePoint` 的 class实例时，记录 `CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY`。只声明为`Object`且无法恢复实际类型时不猜测。Evidence保存caller/origin、PC、invocation kind、resolved callee/artifact、parameter位置、changed class、typed proof和对应 dependency paths。

## ServiceLoader Model

- CHA 建立后、fixed point 前，`ServiceLoaderProtocolIndex` 从 Application scope `ModuleEntry` 合并 `META-INF/services/*`。Directory classes root 通过 resource-only Module 暴露配置；dependency JAR resource 来自 repository-backed WALA Module。
- 配置以 UTF-8 读取，删除 `#` comment、空行与 duplicate provider，service/provider 稳定排序。
- Provider 必须 public、非 abstract/interface、assignable，并有 public zero-arg constructor；非法或 unresolved 配置形成 stable limitation。
- CHA只覆盖JDK 8精确签名`ServiceLoader.load(Class)`。从Class参数执行局部常量解析；成功后将target有效provider的真实public zero-argument constructor加入caller topology，edge标记`SERVICE_LOADER`并用原load PC与provider target生成稳定synthetic callsite identity。
- CHA不创建synthetic provider、removed node或return carrier；所有target有效provider都是可能provider。参数、字段、method return或不同Class常量`phi`生成`SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED`。
- 非CHA传播模型继续覆盖`load(Class)`、`load(Class, ClassLoader)`、`loadInstalled(Class)` → `iterator()` → `next()`。
- 四套strategy各自创建ServiceLoader Context、selector与`SSAContextInterpreter`/IR cache；共享层只保留immutable provider facts、protocol identity与typed contract resolution。Constant service `Class`进入strategy-owned Context。显式 loader `InstanceKey`或基于caller method binary identity、bytecode PC与operation的implicit load-site identity一起进入Context；allocation-sensitive strategy通过allocation key传播，ZeroCFA通过service/loader-specific `ConstantKey`传播。
- RTA 不使用 actual points-to value；只读取当前 Application caller 的 Class metadata constant、local definition-use 与直接 `load(...).iterator()` 链来确定 service contract。RTA-owned summary分配`ServiceLoader`、synthetic iterator与有效provider并调用provider constructor，使Basic RTA的全局instantiated-type fixed point闭合，但`load`、`iterator`、`next`均返回`null`，不传播representative ServiceLoader/iterator/provider value，也不创建provider `phi`。无法确定时使用隔离的 empty-provider Context并记录 `RTA_SERVICE_LOADER_CONTRACT_UNRESOLVED`。
- ZeroCFA 遇到无法恢复 exact Context 的 class-based ServiceLoader receiver时，使用 `ALL_CONFIGURED_SERVICES` aggregate iterator，按 service/provider稳定排序并去重，保守保留全部有效 provider constructor path。
- ZeroX `load` synthetic IR分配service-specific `ServiceLoader` instance；`iterator`分配service-specific provider iterator；`next`分配全部有效provider、调用constructor，并合并返回points-to set。该provider-return summary不被RTA复用。
- 非 constant service type、missing service 或 unresolved provider 使用隔离的 empty-provider Context，并记录 limitation；不会回退到真实 JDK ServiceLoader 实现，也不会 broad-match compatible callsite。
- Provider constructor edge 标记 `SERVICE_LOADER`；应用 interface/virtual invoke 到 provider implementation 保留真实 invoke kind。
- 不覆盖 Java 9 `stream`、`Provider.get` 或 `ModuleLayer`。

## Class.forName Evidence

- 只识别`java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;`。
- 从String参数执行`LocalConstantResolver`；binary name规范为internal name后，与`CLASS_REMOVED`匹配并生成`TYPE_REFERENCE + CLASS_FOR_NAME_LOCAL_CONSTANT` terminal evidence。
- Evidence anchor是精确caller `CGNode` Context，location包含caller identity与bytecode PC；stable key不使用graph node number。
- 无唯一局部常量生成`CLASS_FOR_NAME_LOCAL_CONSTANT_UNRESOLVED`；非法class name生成`CLASS_FOR_NAME_LITERAL_INVALID`。空字符串与纯空白字符串分别使用非空稳定detail `literal=<empty>`和`literal=<blank>`，其他非法值使用`literal=<原始值>`；这些limitation使Module进入`INCONCLUSIVE_REFLECTION`，不会中断Call Graph session冻结。
- 不创建WALA class node、removed node、synthetic method或reflection Call Graph edge；三参数overload不在当前contract。

## invokedynamic Registry

- `InvokeDynamicBootstrapModelRegistry` 以 bootstrap owner/name/descriptor 精确注册；构建后 immutable，duplicate key fail-fast。
- Registry可通过`toBuilder()`保留既有模型后增补自定义bootstrap；扩展JDK默认registry不会意外移除内建`altMetafactory`。
- `InvokeDynamicModelResolver`只负责bootstrap解码；四套strategy-specific `MethodTargetSelector`只在reachable callsite被WALA求解时执行。它们记录bootstrap handle与direct bootstrap argument method handle，并按stable caller/PC/kind/handle identity deduplicate。
- Standard `metafactory` 委托 WALA default `LambdaMethodTargetSelector`；三种points-to strategy使用WALA MethodHandle extension，RTA使用caller-local MethodHandle model。
- 内建 `altMetafactory` 解析 `samMethodType`、`implMethod`、`instantiatedMethodType`、flags、marker interfaces 与 bridge descriptors；支持 `FLAG_SERIALIZABLE`、`FLAG_MARKERS`、`FLAG_BRIDGES`。
- `altMetafactory` 创建 synthetic factory/lambda class、captured fields、SAM/bridge trampoline；allocation 与 implementation invoke 参与同一 fixed point。
- Implementation method 已删除或 descriptor 改变时，synthetic trampoline 保留 declared target，但 WALA 不产生不存在的 callee node/edge；构图期 direct method-handle terminal evidence 继续可用。协议本身无法解析时才产生 explicit unsupported limitation。
- Unknown bootstrap 不把 argument handle 强制转换为 graph edge。reachable unknown bootstrap 记录 terminal evidence 与 limitation，使 Module `INCONCLUSIVE_INVOKEDYNAMIC_MODEL`；unreachable bootstrap 不产生 evidence/limitation。
- 当前 target 是 JDK 8；不承诺 `ConstantDynamic` 或 newer-JDK bootstrap coverage。新增协议通过 registry 扩展。

## Implementation Boundaries

- Winner-only Structural Reference metadata在构图前经repository lease收集raw facts；统一Evidence collector在session冻结前绑定ChangePoint。
- 构图后禁止新增 node/edge、attachment overlay、重新扫描 whole scope classfile 或构建第二张 Call Graph。
- Query只读取graph predecessor、possible sites、`ChangePointEvidenceIndex`、model limitations与synthetic edge metadata；不重扫IR发现reference。

## Benchmark-only Topology Capture

- `impact --call-graph-diagnostics-output <json>`只在显式设置时启用；未设置时`ModuleCallGraphEngine`不创建topology analyzer、不遍历ranking、不计算Call Graph path、不执行decompilation。
- Capture读取同一张已完成Call Graph并作为nullable immutable metadata进入session；不新增edge、不运行第二个builder、不改变Impact query。JSON使用`schemaVersion: 7`并原子替换目标文件；记录strategy capabilities、Reflection applied状态、Evidence resolution/kind/mechanism汇总和local constant success/unresolved计数。Evidence不作为topology node/edge输出。
- 父榜以精确CGNode为单位，不合并WALA Context。CGNode identity包含`owner + name + descriptor + origin + Context + graphNodeId + walaSynthetic + sentinelRole`。Caller/Callee先按related CGNode count降序，再按distinct related IMethod、raw CGEdge与stable CGNode identity排序，各保留Top 10。
- 每个父榜CGNode包含一个按IMethod聚合的Top 10子榜；子榜按该IMethod代表的related CGNode count、raw CGEdge与stable Method identity排序。每个子项保留完整count、deterministic前10个exact CGNode/Context example与omitted count；这一层用于定位同一Method因Context或points-to传播产生的节点膨胀，同时限制HTML与tracked TSV体积。不输出独立points-to set排行榜。
- `getFakeRootNode()`、`getFakeWorldClinitNode()`及其incident edge与普通CGNode/CGEdge相同，参与父榜、IMethod子榜、raw edge count、strongly connected component（SCC）和shortest chain。Node的`sentinelRole`固定为`FAKE_ROOT`、`FAKE_WORLD_CLINIT`或`NONE`。
- 对每个Top CGNode执行reverse breadth-first search（BFS），root集合包含所有declared entrypoint以及WALA fake root/fake world-clinit。每个可达root输出一条deterministic shortest chain，`rootKind`区分`DECLARED_ENTRYPOINT`、`FAKE_ROOT`与`FAKE_WORLD_CLINIT`；同距离next step按stable CGNode identity解tie。BFS使用visited distance map，不重复展开recursion。
- Iterative Kosaraju SCC标记self-loop与多CGNode cycle；path step和父榜CGNode同时输出`cycle`。Diagnostics不再生成`UNREACHABLE_FROM_DECLARED_ENTRYPOINTS`；sentinel-only reachability由完整WALA chain及step上的`sentinelRole`直接表达。
- 每个父榜CGNode输出WALA IR。Capture保留`IMethod.isWalaSynthetic()`，避免将声明在JDK或dependency class上的`SummarizedMethod`误报为真实bytecode source。Source定位使用winner ownership：PROJECT/reactor classes directory、dependency `ArtifactCoord`对应repository JAR、target JDK 8 boot/ext JAR。Vineflower按exact owner/name/descriptor反编译；失败时输出ASM Method instructions；synthetic/WALA summary无bytecode时source为`UNAVAILABLE`，但仍展示IR。Multiline source与IR进入JSON/benchmark HTML，tracked TSV只保存各自status与SHA-256。

## Module Classification

- Blocking：PROJECT/reactor 命中 excluded JDK class、scope unreadable、零 PROJECT entrypoint、CHA/Call Graph failure、timeout。
- Coverage warning：dependency body boundary、external excluded JDK reference、MethodHandle/ServiceLoader limitation、reachable unsupported `invokedynamic`、SSA `UNKNOWN`。
- CHA中reachable unresolved Class.forName、ServiceLoader local constant、unknown bootstrap、unsupported MethodHandle与实际dependency no-op leaf均是coverage warning。
- Typed coverage reason包含 `INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`，并与 bytecode diff、dynamic model和scope limitation按 reducer 固定 precedence归并；全部 limitation仍保留。Duplicate warning不进入 Coverage limitations。

## Acceptance

### Functional

- Given `Object.toString()`或`Object.hashCode()` virtual call；When CHA枚举scope override；Then只为Object基础实现、PROJECT、reactor dependency、synthetic与winner-matched Diff-related external target建边，无关JDK和external override不创建reachable target node。
- Given declared owner不是`java/lang/Object`或selector不是两个精确签名；When CHA求解possible targets；Then完全使用原始hierarchy结果，不应用Diff-directed filter。
- Given CHA caller传入direct、local assignment或same-value phi Class constant；When调用`ServiceLoader.load(Class)`；Then每个target有效provider的真实constructor通过`SERVICE_LOADER`edge可达。
- Given CHA caller传入direct、local assignment或same-value phi String；When调用`Class.forName(String)`引用removed class；Then生成精确caller terminal evidence，且不存在removed WALA node或reflection synthetic edge。
- Given RTA `ServiceLoader.load(Class<? extends Service>)`参数不是constant但provider值存在typed `checkcast Service`；When执行caller-local DefUse解析；Then只连接该contract的有效provider，且不产生unresolved limitation。完全无constant/checkcast evidence时使用empty-provider Context并记录typed limitation。
- Given standard lambda、MethodHandle或 supported `altMetafactory`；When reachable callsite被求解；Then既有 synthetic allocation、trampoline和implementation edge contract保持成立。
- Given caller-local `Lookup.findStatic`与`invokeExact`/`invokeWithArguments`；When四种algorithm分别构图；Then真实target均可达；RTA额外具有caller → stable application bridge →真实target路径和typed direct modeled handle evidence。
- Given reachable unknown bootstrap；When registry无对应 model；Then Module为 `INCONCLUSIVE`；unreachable bootstrap不产生 evidence或limitation。
- Given未显式选择algorithm/model；When生成Diagnostic、Report与optional JSON；Theneffective值分别为`cha`、Reflection not applied与`none`；显式非CHA algorithm未指定model时为`jdk8`。
- Given Stream/Optional、Collection/Map、AbstractExecutorService/CompletableFuture或Thread callback；WhenRTA与两种ZeroX algorithm使用完整target JDK 8构图；Thenapplication callback存在来自non-native、non-synthetic且具有IR的JDK dispatch predecessor。`k-obj`使用最小Java 8 Primordial `Thread.run()` bytecode独立验证同一真实dispatch contract。callback测试使用`ReflectionOptions.NONE`隔离无关Reflection状态空间；默认ReflectionOptions由独立CLI与benchmark门禁验证。
- Given直接static递归或相互static递归；When分别以`k=1`、`k=2`构图；Then在短cooperative timeout内完成，保留self-loop/cycle edge，且所有Context均不提供`CALL_STRING`。
- Given嵌套receiver allocation；When分别以`k=1`、`k=2`构图；Then目标Context的allocation string最大长度分别为1和2，ServiceLoader等自定义selector不截断或嵌套该Context。
- Given完整JDK 8、`k-obj`、`k=1`、默认ReflectionOptions与`jdk8` model，且entrypoint通过`Method.invoke`到达`Class.forName`；When检查完整或cooperative timeout时的partial Call Graph；Then每个现存有效ClassFactory Context恰好包含一个语义`JavaTypeContext`且`RECEIVER`为`TypeAbstraction`，`Method.invoke`仍保留`ConstantKey<IMethod>` receiver，entrypoint到两个Reflection API的路径存在，且allocation string最大深度为1。
- Given已完成Call Graph包含WALA fake root或fake world-clinit；When启用benchmark topology capture；Then sentinel node及incident edge参与CGNode ranking、IMethod子榜、SCC与shortest chain，chain step使用typed `sentinelRole`标记，且不输出declared-entrypoint unreachable状态。
- Given `AccessController.doPrivileged(PrivilegedAction)`；When target JDK 8构图；Then callback通过WALA内置`SummarizedMethod` native model可达。JDK 8该API本身是native，不能宣称经过真实JDK bytecode body。
- Given`changed-paths`与路径外external method；WhenCHA实际到达该method；Then保留caller→leaf edge、method不展开、记录boundary limitation，factory/dangerous transfer计数为零。
- Given相同 fixture显式使用`full`；When构图；Then全部 external method使用真实 IR，不产生 no-op/factory/dangerous approximation evidence。

### Non-Functional

- Given任意Module进入构图；When选择`cha`；Then使用`CHACallGraph(hierarchy,false)`、全部普通node为`Everywhere`、points-to/JDK model/JDK body capability均为false；其他algorithm保持各自builder policy。
- Given`k-obj` ClassFactory compatibility merge；Whenproduction构造builder；Then复用唯一existing default selector且不显式创建`ClassFactoryContextSelector`或`UnionContextSelector`；Context shape门禁验证单一`JavaTypeContext`引用、`Context.isA()`、`Context.get()`与runtime `instanceof`，不根据格式化字符串猜测类型。
- Given Module analysis开始；When执行 builder与query；Then Module内保持单线程，Module间并发边界不变。
- Given Call Graph完成；When进入Impact query；ThenEvidence已完整冻结，query不扫描IR发现method/field/type/reflection/ServiceLoader reference，也不执行overlay、whole-scope重扫或第二张target Call Graph。
- Given版本、scope、selector与环境相同；When重复执行分析；Then输出保持 deterministic。

## Edge Cases

- nonconstant/missing/invalid ServiceLoader配置形成 stable limitation，不回退到 broad compatible-callsite matching。
- timeout不发布 partial graph；Module按 `FAILED_CALL_GRAPH_TIMEOUT`处理，其他 Module继续。
- 零 PROJECT entrypoint、scope unreadable或CHA/Call Graph failure属于 blocking Module结果。
- class-based merging或smushing可能增加 conservative edge与candidate path；只有后续 `PROVEN_EQUIVALENT` SSA结果允许删除候选路径。
- `ClassFactoryContextSelector`在类名无法解析时可能不产生`JavaTypeContext`，兼容合并器此时保持WALA原n-object顺序；因此仅检查异常消失不足以证明兼容性，真实Call Graph中ClassFactory `JavaTypeContext`的malformed和duplicate计数必须为0且有效Context计数必须大于0。
- WALA 1.8.0 `BasicRTABuilder`的`TypeBasedHeapModel`不提供metadata-object `InstanceKey`，且其`Class.newInstance` interpreter不枚举summary内constructor callsite。因此RTA即使选择包含`APPLICATION_GET_METHOD`的ReflectionOptions，也可能保留`Class.forName`/Reflection API node而无法闭合constructor或`Method.invoke`业务target。ZeroX保留metadata constant，但在完整target JDK 8 scope启用`Method.invoke`可能显著扩大fixed point。项目不用post-build补边或fake metadata value绕过该边界。
