---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "resolved artifact ingestion 与 coordinate repository"
  - path: "wiki/features/impact-tracing.md"
    desc: "BoundChangePoint 与 deferred SSA filtering"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "JAR pair failure message、stack trace 与 retained/transient 边界"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "固定10类raw ChangePoint与dynamic loading场景的benchmark fixture"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffEngine.java"
    desc: "class/method/field diff"
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
    desc: "path-related exact member/class Vineflower decompilation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/UnifiedDiffGenerator.java"
    desc: "3 行 context 的完整 Unified diff"
---

# Feature: Bytecode Diff Engine

## Summary

对唯一logical `(oldCoordinate,newCoordinate)` pair执行ASM bytecode Diff和ServiceLoader resource Diff，生成的immutable ChangePoint由各Module的`BoundChangePoint`共享。除class/member结构、descriptor、method body与JVM access narrowing外，比较baseline/target的`META-INF/services/<service>`有效provider registration。Resource ChangePoint使用typed subject，physical path不进入domain key。

## Design Decisions

- Access narrowing是 JVM binary compatibility analysis，不是 source compatibility；PROJECT source因收敛后无法 compile 时沿用 target build failure。
- `JvmAccess`只包含 `PUBLIC`、`PROTECTED`、`PACKAGE_PRIVATE`、`PRIVATE`；其他 modifier不混入 visibility。
- `AccessTransition`必须是 strict narrowing。三个 access kind必须携带该 Value Object，其他 kind禁止携带，避免成对 nullable old/new access。
- Descriptor变化只保留既有 `*_DESCRIPTOR_CHANGED`，不猜测不同 descriptor 是同一 member；body与access同时变化时保留两个独立 ChangePoint。
- Module binding只增加`DependencyUpgradeKey` provenance；`BoundChangePoint`要求ChangePoint artifact等于upgrade target artifact，不通过对象重建改变diff identity。
- ServiceLoader resource只比较发生dependency upgrade的外部artifact，不扩展PROJECT source resource。
- baseline provider必须存在、可解析并assignable给service，才参与registration removal判断。

## Actors / Entrypoints

- `impact` pipeline 在 baseline/target dependency version变化后，以 logical coordinate pair触发JAR diff。
- `--include-change-kinds`可筛选输出；三个access narrowing kind与`SERVICE_PROVIDER_REGISTRATION_REMOVED`属于默认集合。

## Behavior Contract

- 相同输入JAR与include集合产生稳定排序、相同identity的ChangePoint。
- Access narrowing只比较相同binary identity，且只描述target access相对baseline的strict narrowing。
- 同一coordinate pair被多个Module引用时共享同一ChangePoint实例，descriptor、hash与`AccessTransition`保持不变。
- service配置删除但provider class仍存在时生成`SERVICE_PROVIDER_REGISTRATION_REMOVED`；provider class和配置同时删除时只保留`CLASS_REMOVED`。

## Core Flow

1. 通过command-scoped repository lease读取old/new JAR并建立class/member index。
2. 比较class存在性、actual class access、method/field identity、descriptor、member access与method body hash。
3. 读取双方`META-INF/services/*`，删除comment/空行，规范binary name、去重、稳定排序，并校验baseline provider存在性与assignability。
4. 创建validated immutable bytecode/resource ChangePoint，完成class-removal deduplication，按stable key排序并附加Module upgrade provenance。

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
- 单个 pair failure 不取消其他 JAR diff task；关联 Module 记录 `INCONCLUSIVE_BYTECODE_DIFF`。
- Pair failure的WARN固定包含异常类型与完整message；`-v`/`-vv`再输出带同一pair context的完整stack trace和cause chain。WARN进入Report diagnostics，stack trace只进入Console。
- Pair failure 且无其他可分析 ChangePoint 时不构建 Call Graph，但仍生成 Module detail page。
- Raw bytecode diff 不对全部 changed method 构建 SSA；semantic filtering 延迟到 candidate path 之后。
- 反编译同样延迟到 candidate/Structural path 完成后，只处理 Report 相关 member；pool 使用 `--analysis-parallelism`，每个 Vineflower task 内固定单线程。

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

### Non-Functional

- [ ] Domain key不包含physical JAR path，parallel/sequential结果一致。
- [ ] Access kind/transition不变量由canonical constructor集中验证。

## Edge Cases

- `<clinit>` 不生成access ChangePoint；synthetic/bridge method和constructor按真实bytecode identity处理。
- `InnerClasses` source-level modifier不用于确定性JVM binary impact。

## Implementation Boundaries

- Diff不构建baseline CHA或Call Graph；access legality在target Call Graph完成后的Impact query中判断。
- Reflection、JNI、custom ClassLoader与Java 9 module exports不属于access diff结论。
