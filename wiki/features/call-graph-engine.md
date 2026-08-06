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
    desc: "command-wide 算法标识与 WALA instance policy"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphEngine.java"
    desc: "per-Module selected builder 与 extension 安装顺序"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointClassIndex.java"
    desc: "current Module target/classes 的 immutable root class selection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DeclaredTypesEntrypoint.java"
    desc: "单 candidate declared-type 参数建模"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointSyntheticTypeRegistry.java"
    desc: "per-CHA interface/abstract synthetic placeholder复用"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphSession.java"
    desc: "live graph、CHA、cache、ownership 与 immutable model metadata"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ServiceLoaderFixedPointModel.java"
    desc: "JDK 8 ServiceLoader ContextSelector/SSAContextInterpreter model"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/InvokeDynamicBootstrapModelRegistry.java"
    desc: "immutable exact-key invokedynamic model registry"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/InvokeDynamicModelTargetSelector.java"
    desc: "reachable bootstrap evidence 与 outer MethodTargetSelector"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/AltMetafactoryBootstrapModel.java"
    desc: "Java 8 altMetafactory synthetic lambda model"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassOwnershipIndex.java"
    desc: "binary-name ownership、classpath precedence 与 duplicate evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassSource.java"
    desc: "dependency ArtifactCoord logical source identity"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphTimeoutMonitor.java"
    desc: "WALA cooperative timeout/cancel monitor"
---

# Feature: Call Graph Engine

## Summary

每个 relevant target Module 使用 command-wide `--call-graph-algorithm` 构建一个独立 WALA Call Graph。默认 `zero-cfa` 使用 class-based allocation 与 constant-specific keys；`optimized-0-1-cfa` 保留 allocation-site/constant identity并smush高成本对象。ServiceLoader 与已注册 `invokedynamic` 协议在 `makeCallGraph(...)` 前安装，allocation、constructor call、implementation call 与 points-to 一起进入 fixed point。构图完成后 session 只暴露 graph、IR、ownership、model evidence、limitations 与 synthetic edge metadata。

## Design Decisions

- `zero-cfa` 是 CLI 默认值，其 policy 只启用 `CONSTANT_SPECIFIC`：普通 allocation按 concrete class合并，constant继续保持 identity。
- `optimized-0-1-cfa` 使用 `ALLOCATIONS | CONSTANT_SPECIFIC | SMUSH_MANY | SMUSH_PRIMITIVE_HOLDERS | SMUSH_STRINGS | SMUSH_THROWABLES`，保持原 allocation-sensitive contract。
- 算法在 command 级选择并应用到全部 Module；不存在 per-Module override、timeout fallback或同一 run 混用算法。
- ZeroCFA 对 modeled ServiceLoader allocation 使用 service/loader-specific `ConstantKey`，使 constant service receiver Context不因普通 class-based allocation丢失；真正的 class-based ServiceLoader receiver使用 deterministic `ALL_CONFIGURED_SERVICES` aggregate fallback。

## Behavior Contract

- Diagnostic与HTML Report使用实际稳定算法标识 `zero-cfa` 或 `optimized-0-1-cfa`；Report Terminology随算法变化。
- Call Graph保持 conservative over-approximation；class-based merging或smushing可能改变 nodes、edges、contexts与候选 Impact Path数量，但不改变 Module status、timeout、query和publication contract。
- Reflection继续使用 `FULL`；ServiceLoader、MethodHandle与注册的 `invokedynamic` model必须保持 fixed-point内行为。

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

```java
AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
options.setReflectionOptions(AnalysisOptions.ReflectionOptions.FULL);
Util.addDefaultSelectors(options, hierarchy);
Util.addDefaultBypassLogic(options, Util.class.getClassLoader(), hierarchy);
SSAPropagationCallGraphBuilder builder =
        ZeroXCFABuilder.make(
                Language.JAVA, hierarchy, options, cache, null, null,
                selectedAlgorithm.instancePolicy());
MethodHandles.analyzeMethodHandles(options, builder);
options.setSelector(new InvokeDynamicModelTargetSelector(...));
serviceLoaderModel.install(builder);
CallGraph graph = builder.makeCallGraph(options, monitor);
```

固定顺序：selected ZeroX policy与WALA default builder/selectors（含 `LambdaMethodTargetSelector`）→ `MethodHandles.analyzeMethodHandles(...)` → `InvokeDynamicBootstrapModelRegistry` outer selector → ServiceLoader delegating `ContextSelector`/`SSAContextInterpreter`与ZeroCFA constant receiver keys → `makeCallGraph(...)`。

`AnalysisCacheImpl` 使用 `SSAOptions.defaultOptions()`。Builder/query 在 Module 内单线程；不同 Module 受 `--analysis-parallelism` 控制。`CallGraphTimeoutMonitor` 仅使用 WALA cooperative cancel；`0` 表示无限等待，timeout 不输出 partial graph。

## Core Flow

1. 建立 winner-only ownership、structural metadata、WALA scope与CHA。
2. 从 immutable PROJECT class index生成 declared-type entrypoints。
3. 安装 selected instance policy、default selectors/bypass、MethodHandle、`invokedynamic`与ServiceLoader model。
4. 单线程求解 points-to与Call Graph fixed point；timeout仅通过 cooperative monitor取消。
5. 将 graph、IR cache、ownership和immutable model metadata封装为只读 query session。

## ServiceLoader Model

- CHA 建立后、fixed point 前，从 Application scope `ModuleEntry` 合并 `META-INF/services/*`。Directory classes root 通过 resource-only Module 暴露配置；dependency JAR resource 来自 repository-backed WALA Module。
- 配置以 UTF-8 读取，删除 `#` comment、空行与 duplicate provider，service/provider 稳定排序。
- Provider 必须 public、非 abstract/interface、assignable，并有 public zero-arg constructor；非法或 unresolved 配置形成 stable limitation。
- 覆盖 JDK 8 `load(Class)`、`load(Class, ClassLoader)`、`loadInstalled(Class)` → `iterator()` → `next()`。
- Constant service `Class` 进入 `ServiceTypeContext`。显式 loader `InstanceKey` 或 implicit load-site identity 一起进入 Context；optimized 模式通过 allocation key传播，ZeroCFA通过 service/loader-specific `ConstantKey`传播。
- ZeroCFA 遇到无法恢复 exact Context 的 class-based ServiceLoader receiver时，使用 `ALL_CONFIGURED_SERVICES` aggregate iterator，按 service/provider稳定排序并去重，保守保留全部有效 provider constructor path。
- `load` synthetic IR 分配 service-specific `ServiceLoader` instance；`iterator` 分配 service-specific provider iterator；`next` 分配全部有效 provider、调用 constructor，并合并返回 points-to set。
- 非 constant service type、missing service 或 unresolved provider 使用隔离的 empty-provider Context，并记录 limitation；不会回退到真实 JDK ServiceLoader 实现，也不会 broad-match compatible callsite。
- Provider constructor edge 标记 `SERVICE_LOADER`；应用 interface/virtual invoke 到 provider implementation 保留真实 invoke kind。
- 不覆盖 Java 9 `stream`、`Provider.get` 或 `ModuleLayer`。

## invokedynamic Registry

- `InvokeDynamicBootstrapModelRegistry` 以 bootstrap owner/name/descriptor 精确注册；构建后 immutable，duplicate key fail-fast。
- Outer selector 只在 reachable callsite 被 WALA 求解时执行。它记录 bootstrap handle 与 direct bootstrap argument method handle，按 caller node、bytecode PC、kind 与 handle identity deduplicate。
- Standard `metafactory` 委托 WALA default `LambdaMethodTargetSelector`；MethodHandle 行为继续由 `MethodHandles.analyzeMethodHandles(...)` 处理。
- 内建 `altMetafactory` 解析 `samMethodType`、`implMethod`、`instantiatedMethodType`、flags、marker interfaces 与 bridge descriptors；支持 `FLAG_SERIALIZABLE`、`FLAG_MARKERS`、`FLAG_BRIDGES`。
- `altMetafactory` 创建 synthetic factory/lambda class、captured fields、SAM/bridge trampoline；allocation 与 implementation invoke 参与同一 fixed point。
- Implementation method 已删除或 descriptor 改变时，synthetic trampoline 保留 declared target，但 WALA 不产生不存在的 callee node/edge；构图期 direct method-handle terminal evidence 继续可用。协议本身无法解析时才产生 explicit unsupported limitation。
- Unknown bootstrap 不把 argument handle 强制转换为 graph edge。reachable unknown bootstrap 记录 terminal evidence 与 limitation，使 Module `INCONCLUSIVE_INVOKEDYNAMIC_MODEL`；unreachable bootstrap 不产生 evidence/limitation。
- 当前 target 是 JDK 8；不承诺 `ConstantDynamic` 或 newer-JDK bootstrap coverage。新增协议通过 registry 扩展。

## Implementation Boundaries

- Winner-only Structural Reference metadata 在 `makeCallGraph(...)` 前经 repository lease 收集，并作为 immutable `StructuralScanResult` 放入 session。
- 构图后禁止新增 node/edge、attachment overlay、重新扫描 whole scope classfile 或构建第二张 Call Graph。
- Query 可 read-only 访问 graph predecessor、possible sites、reachable IR、dynamic evidence、model limitations、synthetic edge metadata 与 precomputed structural metadata。

## Module Classification

- Blocking：PROJECT/reactor 命中 excluded JDK class、scope unreadable、零 PROJECT entrypoint、CHA/Call Graph failure、timeout。
- Coverage warning：external excluded JDK reference、ServiceLoader limitation、reachable unsupported `invokedynamic`、SSA `UNKNOWN`。
- `INCONCLUSIVE_INVOKEDYNAMIC_MODEL` 优先表达 dynamic model gap；ServiceLoader gap 使用对应 `INCONCLUSIVE` reason；duplicate warning 不进入 Coverage limitations。

## Acceptance

### Functional

- Given constant ServiceLoader配置；When任一算法完成 fixed point；Then provider constructor、implementation和内部调用存在于 graph；optimized allocation Context与ZeroCFA constant receiver key都不丢失 exact service path。
- Given standard lambda、MethodHandle或 supported `altMetafactory`；When reachable callsite被求解；Then既有 synthetic allocation、trampoline和implementation edge contract保持成立。
- Given reachable unknown bootstrap；When registry无对应 model；Then Module为 `INCONCLUSIVE`；unreachable bootstrap不产生 evidence或limitation。
- Given Call Graph成功完成；When生成 Diagnostic与Report；Then算法标识等于 command选择；默认值为 `zero-cfa`，显式 `optimized-0-1-cfa` 保持可选。

### Non-Functional

- Given任意 Module进入构图；When选择 `zero-cfa`；Then policy恰为 `CONSTANT_SPECIFIC`；When选择 `optimized-0-1-cfa`；Then `ALLOCATIONS`、`CONSTANT_SPECIFIC`与四类 smushing policy作为固定组合启用。
- Given Module analysis开始；When执行 builder与query；Then Module内保持单线程，Module间并发边界不变。
- Given fixed point完成；When进入Impact query；Then不执行 overlay、whole-scope重扫或第二张 target Call Graph。
- Given版本、scope、selector与环境相同；When重复执行分析；Then输出保持 deterministic。

## Edge Cases

- nonconstant/missing/invalid ServiceLoader配置形成 stable limitation，不回退到 broad compatible-callsite matching。
- timeout不发布 partial graph；Module按 `FAILED_CALL_GRAPH_TIMEOUT`处理，其他 Module继续。
- 零 PROJECT entrypoint、scope unreadable或CHA/Call Graph failure属于 blocking Module结果。
- class-based merging或smushing可能增加 conservative edge与candidate path；只有后续 `PROVEN_EQUIVALENT` SSA结果允许删除候选路径。
