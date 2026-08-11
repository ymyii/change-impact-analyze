---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "resolved artifact ingestion 与 coordinate repository"
  - path: "wiki/features/impact-tracing.md"
    desc: "BoundChangePoint 与 deferred SSA filtering"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "固定 9 类 raw ChangePoint 的持续 benchmark fixture"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffEngine.java"
    desc: "class/method/field diff"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/IJarRepository.java"
    desc: "coordinate 到短生命周期 JarLease 的访问边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/StableHashMethodVisitor.java"
    desc: "ASM MethodNode canonical encoder"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/ChangePoint.java"
    desc: "old/new descriptor、hash 与 validated access transition"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/JvmAccess.java"
    desc: "JVM visibility normalization 与 strict narrowing order"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/AccessTransition.java"
    desc: "不可表示非 narrowing 状态的 old/new access Value Object"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/BoundChangePoint.java"
    desc: "Module 与 dependency upgrade provenance"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/MethodBodyDecompiler.java"
    desc: "path-related exact member/class Vineflower decompilation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/UnifiedDiffGenerator.java"
    desc: "3 行 context 的完整 Unified diff"
---

# Feature: Bytecode Diff Engine

## Summary

对唯一 logical `(oldCoordinate,newCoordinate)` pair 通过 `IJarRepository` 执行一次 ASM bytecode diff，再将 raw ChangePoint 重新绑定到各 Module 的 `DependencyUpgradeKey`。除结构、descriptor 与 method body 外，index保留 normalized `JvmAccess`，并检测 Java 8 pre-existing bytecode 的 class/method/constructor/field access narrowing。Pool 大小使用 `--analysis-parallelism`，再按 task 数计算 actual workers；merge 与排序 deterministic。physical path 不进入 domain key。

## Design Decisions

- Access narrowing是 JVM binary compatibility analysis，不是 source compatibility；PROJECT source因收敛后无法 compile 时沿用 target build failure。
- `JvmAccess`只包含 `PUBLIC`、`PROTECTED`、`PACKAGE_PRIVATE`、`PRIVATE`；其他 modifier不混入 visibility。
- `AccessTransition`必须是 strict narrowing。三个 access kind必须携带该 Value Object，其他 kind禁止携带，避免成对 nullable old/new access。
- Descriptor变化只保留既有 `*_DESCRIPTOR_CHANGED`，不猜测不同 descriptor 是同一 member；body与access同时变化时保留两个独立 ChangePoint。

## Actors / Entrypoints

- `impact` pipeline 在 baseline/target dependency version变化后，以 logical coordinate pair触发JAR diff。
- `--include-change-kinds` 可筛选输出；三个 access narrowing kind属于默认集合。

## Behavior Contract

- 相同输入JAR与include集合产生稳定排序、相同identity的ChangePoint。
- Access narrowing只比较相同binary identity，且只描述target access相对baseline的strict narrowing。

## Core Flow

1. 通过command-scoped repository lease读取old/new JAR并建立class/member index。
2. 比较class存在性、actual class access、method/field identity、descriptor、member access与method body hash。
3. 创建validated ChangePoint，按stable key排序并绑定回各Module。

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
- Pair failure 且无其他可分析 ChangePoint 时不构建 Call Graph，但仍生成 Module detail page。
- Raw bytecode diff 不对全部 changed method 构建 SSA；semantic filtering 延迟到 candidate path 之后。
- 反编译同样延迟到 candidate/Structural path 完成后，只处理 Report 相关 member；pool 使用 `--analysis-parallelism`，每个 Vineflower task 内固定单线程。

## Acceptance Criteria

### Functional

- Jump、switch、try/catch、bootstrap-only、`ConstantDynamic`、typed constant 变化可检出。
- Line/debug-only 变化不产生 body ChangePoint。
- 同一 logical coordinate pair 只 diff 一次；结果可绑定多个 Module。
- Parallel/sequential fixture 的 ChangePoint 集合和排序一致。
- Given相同binary identity发生strict visibility narrowing；When执行 diff；Then生成对应默认启用的 access ChangePoint并保留old/new access。
- Given descriptor变化、access expansion或非法modifier组合；When执行diff/构造domain object；Then不猜测access narrowing或立即fail fast。

### Non-Functional

- [ ] Domain key不包含physical JAR path，parallel/sequential结果一致。
- [ ] Access kind/transition不变量由canonical constructor集中验证。

## Edge Cases

- `<clinit>` 不生成access ChangePoint；synthetic/bridge method和constructor按真实bytecode identity处理。
- `InnerClasses` source-level modifier不用于确定性JVM binary impact。

## Implementation Boundaries

- Diff不构建baseline CHA或Call Graph；access legality在target Call Graph完成后的Impact query中判断。
- Reflection、JNI、custom ClassLoader与Java 9 module exports不属于access diff结论。
