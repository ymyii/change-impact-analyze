---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "resolved artifact ingestion 与 coordinate repository"
  - path: "wiki/features/impact-tracing.md"
    desc: "effective BoundChangePoint 与固定Java-first分阶段filtering"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "JAR pair failure message、stack trace 与 retained/transient 边界"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "固定10类raw ChangePoint与dynamic loading场景的benchmark fixture"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffEngine.java"
    desc: "class/method/field diff 与SSA候选门禁"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeSsaFilter.java"
    desc: "JAR pair-local old/new WALA SSA session 与fail-open filtering"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffResult.java"
    desc: "effective ChangePoint、raw计数、SSA与decompiled Java证据"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/SsaComparisonEvidence.java"
    desc: "方法、class major version、hash、状态、原因与耗时"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/DecompileComparisonEvidence.java"
    desc: "Vineflower old/new文本、三态结果与短路抑制原因"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/MethodBytecodeTextRenderer.java"
    desc: "SSA审计与Call Graph source fallback共享的method ASM文本入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/IJarRepository.java"
    desc: "coordinate 到短生命周期 JarLease 的访问边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/StableHashMethodVisitor.java"
    desc: "ASM MethodNode canonical encoder"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/ChangePoint.java"
    desc: "bytecode或typed resource ChangePoint subject"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/ServiceLoaderResourceDiffEngine.java"
    desc: "baseline/target META-INF/services resource Diff与deduplication"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/ServiceProviderRegistration.java"
    desc: "service registration typed subject"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/JvmAccess.java"
    desc: "JVM visibility normalization 与 strict narrowing order"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/AccessTransition.java"
    desc: "不可表示非 narrowing 状态的 old/new access Value Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/BoundChangePoint.java"
    desc: "Module 与 dependency upgrade provenance"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "logical pair去重、parallel diff与Module binding"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/JarDiffFailureDiagnostic.java"
    desc: "隔离的 JAR pair failure message 与 stack trace 输出"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/MethodBodyDecompiler.java"
    desc: "ChangePoint收集期与非body code evidence共享的Vineflower入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/MethodBodyComparisonCache.java"
    desc: "logical JAR pair方法体证据fragment与缓存diff读取"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/UnifiedDiffGenerator.java"
    desc: "3 行 context 的完整 Unified diff"
---

# Feature: Bytecode Diff Engine

## Summary

对唯一logical `(oldCoordinate,newCoordinate)` pair执行ASM bytecode Diff、ChangePoint收集期Vineflower优先、normalized Static Single Assignment（SSA，静态单赋值）后置的分阶段filtering，以及ServiceLoader resource Diff。生成的immutable effective ChangePoint与不含源码的compact证据由各Module共享；源码只进入当前command的`report-cache`。除class/member结构、descriptor、method body与JVM access narrowing外，比较baseline/target的`META-INF/services/<service>`有效provider registration。Resource ChangePoint使用typed subject，physical path不进入domain key。

## Design Decisions

- Access narrowing是 JVM binary compatibility analysis，不是 source compatibility；PROJECT source因收敛后无法 compile 时沿用 target build failure。
- `JvmAccess`只包含 `PUBLIC`、`PROTECTED`、`PACKAGE_PRIVATE`、`PRIVATE`；其他 modifier不混入 visibility。
- `AccessTransition`必须是 strict narrowing。三个 access kind必须携带该 Value Object，其他 kind禁止携带，避免成对 nullable old/new access。
- Descriptor变化只保留既有 `*_DESCRIPTOR_CHANGED`，不猜测不同 descriptor 是同一 member；body与access同时变化时保留两个独立 ChangePoint。
- Module binding只增加`DependencyUpgradeKey` provenance；`BoundChangePoint`要求ChangePoint artifact等于upgrade target artifact，不通过对象重建改变diff identity。
- ServiceLoader resource只比较发生dependency upgrade的外部artifact，不扩展PROJECT source resource。
- baseline provider必须存在、可解析并assignable给service，才参与registration removal判断。
- 所有descriptor相同、old/new body hash均存在且不同的`METHOD_BODY_CHANGED`都是分阶段比较候选；class major version是否相同不再是门禁。method removal、descriptor change、access、field、class与resource ChangePoint不参与。
- 每个候选先执行old/new Vineflower反编译。Java text为`IDENTICAL`时立即抑制并跳过normalized SSA；仅对Java `DIFFERENT`或`UNKNOWN`的候选执行normalized SSA，`MATCHED`时抑制。固定求值语义为`Java text identical || SSA MATCHED`，遵循短路原则，不存在两个条件同时命中。
- Java text identical指现有换行规范化后使用`String.equals`完全相等，不额外忽略空白、comment或import差异。任一侧反编译不可用时为`UNKNOWN`并fail-open。

## Actors / Entrypoints

- `impact` pipeline 在 baseline/target dependency version变化后，以 logical coordinate pair触发JAR diff。
- `--include-change-kinds`可筛选输出；三个access narrowing kind与`SERVICE_PROVIDER_REGISTRATION_REMOVED`属于默认集合。

## Behavior Contract

- 相同输入JAR与include集合产生稳定排序、相同identity的ChangePoint。
- Access narrowing只比较相同binary identity，且只描述target access相对baseline的strict narrowing。
- 同一coordinate pair被多个Module引用时共享同一ChangePoint实例，descriptor、hash与`AccessTransition`保持不变。
- 同一coordinate pair反编译一次全部候选；仅当存在Java miss候选时构建一次old/new SSA session。实际执行的`SsaComparisonEvidence`与全部候选的不含源码`DecompileComparisonSummary`随pair结果共享给关联Module。
- service配置删除但provider class仍存在时生成`SERVICE_PROVIDER_REGISTRATION_REMOVED`；provider class和配置同时删除时只保留`CLASS_REMOVED`。
- 聚合INFO completion中的`changes`是所有成功logical pair各自去重后的ChangePoint数量之和；同一pair绑定多个Module不重复计数。失败pair计入`failedPairs`但不计入`changes`。

## Core Flow

1. 通过command-scoped repository lease读取old/new JAR并建立class/member index。
2. 比较class存在性、actual class access、method/field identity、descriptor、member access与method body hash，同时记录class file major version。
3. 对全部method body候选使用Vineflower反编译old/new exact method；现有换行规范化后精确比较文本。`IDENTICAL`候选立即抑制，不进入SSA。
4. 对Java `DIFFERENT`或`UNKNOWN`候选，在pair-local old/new WALA hierarchy与独立cache中比较normalized SSA/Control Flow Graph；`MATCHED`候选抑制，其余保留。没有Java miss时不创建SSA session。
5. 把双方源码或不可用原因、`ssaExecuted`、SSA执行结果或短路原因写入该logical pair的command cache fragment；compact证据附加到Module input。
6. 读取双方`META-INF/services/*`，删除comment/空行，规范binary name、去重、稳定排序，并校验baseline provider存在性与assignability。
7. 创建validated immutable bytecode/resource ChangePoint，完成class-removal deduplication，按stable key排序并附加Module upgrade provenance。

## ChangePoint 收集期方法体分阶段过滤

- SSA equivalence固定启用，不存在CLI关闭分支；旧`--result-refinement-algorithms`作为未知option返回exit code `1`。
- 每侧session只加载目标JDK 8 Primordial/Extension classpath及该侧单个dependency JAR，不构建Call Graph，不依赖Module target session。
- 比较保留typed constant、Def-Use、normal/exception Control Flow Graph、catch type、declared reference、phi/pi/catch与side-effect order；value number进行alpha normalization。
- SSA状态固定为`MATCHED`、`DIFFERENT`、`UNKNOWN`。`UNKNOWN`覆盖session、method lookup、Intermediate Representation（IR，中间表示）生成、unsupported instruction与normalization failure。
- Decompiled Java状态固定为`IDENTICAL`、`DIFFERENT`、`UNKNOWN`。两侧文本可用且完全相同时为`IDENTICAL`；任一侧不可用时为`UNKNOWN`。反编译失败不改变Module status，也不把logical pair标记为失败。
- 证据包含logical artifacts、owner/name/descriptor、old/new body hash、old/new class major version、decompiled Java状态与耗时、SSA executed/skipped及实际执行的三态与耗时、短路或抑制原因。Report与Schema 13 diagnostics可审计；源码不进入Module result、diagnostics或Affected Paths shard。
- `-vv`仅对实际执行且结果为SSA `DIFFERENT`或`UNKNOWN`的候选生成Console-only审计块。`jar-diff/ssa-equivalence-audit`上下文携带artifact与`method=<owner>#<name><descriptor>`，正文以`ssaDifferentCandidate`或`ssaUnknownCandidate`标识候选，并固定输出old/new ASM bytecode、old/new原始IR及old/new normalized IR。Java `IDENTICAL`候选不会进入该Stage。
- 高成本ASM、IR与normalized文本只在TRACE verbosity门禁通过后生成，不写入`SsaComparisonEvidence`、Report或Schema 13 diagnostics。Method ASM文本由`MethodBytecodeTextRenderer`统一生成，SSA审计与Call Graph source fallback不得各自维护格式。

## ServiceLoader Resource Diff

- `ServiceProviderRegistration`包含resource path、service internal name、provider internal name和target `ArtifactCoord`。
- provider class与配置行同时删除：只保留`CLASS_REMOVED`；baseline service relation稍后由统一collector生成`TYPE_REFERENCE + SERVICE_LOADER_PROVIDER`Evidence。
- 仅配置行删除：生成`SERVICE_PROVIDER_REGISTRATION_REMOVED`，统一collector绑定`RESOURCE_REFERENCE + SERVICE_LOADER_PROVIDER`Evidence。
- target仍声明removed、missing、non-assignable或非法provider：保留可用class-removal Evidence，并生成`SERVICE_LOADER_PROVIDER_INVALID`typed limitation。
- 资源读取或解析失败生成`INCONCLUSIVE_SERVICE_LOADER`，不终止其他ChangePoint分析。
- 同一service/provider/artifact只生成一个stable ChangePoint或Evidence。

## Stable Method Hash

`StableHashMethodVisitor` 使用 ASM `MethodNode` 生成 length-delimited canonical records：

- Label 按 method 内 semantic order 分配 stable ID。
- 编码 jump/switch target topology、try/catch range/handler。
- `Handle` 逐字段编码；`invokedynamic` 与 `ConstantDynamic` 递归编码 bootstrap handle/arguments。
- Typed `LDC` 保留 type；float/double 保留 exact bits。
- 忽略 line number、local variable table、stack map frame 和 debug-only metadata。
- 禁止使用 `Object.toString()` 作为 canonical evidence。

因此 source 换行或 debug-only 变化不产生 `METHOD_BODY_CHANGED`；data/control dependency、exception path、bootstrap metadata 变化仍可检出。

## Change Identity

- Removal 只保存 old descriptor；addition 只保存 new descriptor。
- `METHOD_BODY_CHANGED` 两侧 descriptor 相同，并保留 old/new hash。
- Descriptor change 同时保存 old/new descriptor，不重复产生同名 method 的 added/removed ChangePoint。
- `CLASS_ACCESS_NARROWED` 覆盖 actual class flags 的 `public -> package-private`。
- `METHOD_ACCESS_NARROWED`（含 `<init>`，不含 `<clinit>`）与 `FIELD_ACCESS_NARROWED` 覆盖 `public -> protected/package-private/private`、`protected -> package-private/private`、`package-private -> private`。
- Access expansion不产生 breaking ChangePoint。class/member同时收敛时分别保留；method body/access同时变化时也分别保留。
- Access transition进入 equality、hash、`BoundChangePoint.stableKey()` 与 Report，identity显式包含例如 `PUBLIC->PROTECTED`。
- Output 按 class/member stable key 排序。

## Failure Contract

- Corrupt JAR/class 抛出 `BytecodeDiffException`。
- 单个pair failure不取消其他JAR comparison；关联Module记录`INCONCLUSIVE_BYTECODE_DIFF`。
- Pair failure的WARN固定包含异常类型与完整message；`-v`/`-vv`再输出带同一pair context的完整stack trace和cause chain。WARN进入Report diagnostics，stack trace只进入Console。
- Pair failure 且无其他可分析 ChangePoint 时不构建 Call Graph，但仍生成 Module detail page。
- 聚合日志除`changes/pairs/failedPairs/workers`外，固定输出`eligible`、`ssaExecuted/ssaSkipped`、SSA与Java三态计数、`unionSuppressed`、`retained`及两类elapsed milliseconds；空diff全部计数为`0`。
- 所有method body候选都执行Vineflower比较；只有Java未命中的候选执行SSA。Java或后续SSA命中即抑制，两Stage都未命中才保留。
- SSA或反编译`UNKNOWN`单独出现时fail-open；pair自身无法完成普通bytecode/resource diff时仍沿用JAR diff failure隔离。
- `report-cache`写入、读取、complete marker或JSON完整性校验失败是command-level failure，禁止在证据不完整时继续。

## Acceptance Criteria

### Functional

- Jump、switch、try/catch、bootstrap-only、`ConstantDynamic`、typed constant 变化可检出。
- Line/debug-only 变化不产生 body ChangePoint。
- 同一 logical coordinate pair 只 diff 一次；结果可绑定多个 Module。
- Given access narrowing ChangePoint被同一pair的多个Module共享；When绑定Module provenance；Then保持同一对象与完整`AccessTransition`，artifact不一致时立即fail fast。
- Parallel/sequential fixture 的 ChangePoint 集合和排序一致。
- Given相同binary identity发生strict visibility narrowing；When执行 diff；Then生成对应默认启用的 access ChangePoint并保留old/new access。
- Given descriptor变化、access expansion或非法modifier组合；When执行diff/构造domain object；Then不猜测access narrowing或立即fail fast。
- Given provider class与registration同时删除；When执行Diff；Then只生成`CLASS_REMOVED`。Given仅删除registration；Then生成typed resource ChangePoint。
- Given配置包含comment、空行与duplicate；When执行Diff；Then规范化结果和stable key保持deterministic。
- Given空diff、全部成功、部分失败或同一pair绑定多个Module；When聚合结束；Then`changes/pairs/failedPairs/workers`遵守唯一logical pair口径。
- Givenclass major相同或不同且body hash不同；When decompiled Java为`IDENTICAL`；Then抑制该`METHOD_BODY_CHANGED`、记录`JAVA_TEXT_IDENTICAL`，SSA计为skipped且没有SSA comparison evidence。
- Given decompiled Java为`DIFFERENT`或`UNKNOWN`；When normalized SSA为`MATCHED`；Then抑制并记录`SSA_MATCHED`。Given SSA为`DIFFERENT`或`UNKNOWN`；Thenfail-open保留。
- Given非`METHOD_BODY_CHANGED`；When收集ChangePoint；Then不进入方法体双Stage过滤。
- Given SSA返回`DIFFERENT`或`UNKNOWN`；When使用`-vv`；Then每个物理审计行可按method identity搜索，六个old/new representation section完整或带明确unavailable reason。Given INFO、DEBUG或`MATCHED`；Then不生成高成本审计文本。

### Non-Functional

- [ ] Domain key不包含physical JAR path，parallel/sequential结果一致。
- [ ] Access kind/transition不变量由canonical constructor集中验证。

## Edge Cases

- `<clinit>` 不生成access ChangePoint；synthetic/bridge method和constructor按真实bytecode identity处理。
- `InnerClasses` source-level modifier不用于确定性JVM binary impact。

## Implementation Boundaries

- Diff可为单个JAR pair构建old/new Class Hierarchy、SSA cache并执行Vineflower，但不构建baseline或target Call Graph；access legality仍在target Call Graph完成后的Impact query中判断。
- Reflection、JNI、custom ClassLoader与Java 9 module exports不属于access diff结论。
