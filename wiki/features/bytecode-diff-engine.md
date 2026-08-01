---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "resolved physical old/new JAR path"
  - path: "wiki/features/impact-tracing.md"
    desc: "BoundChangePoint 与 deferred SSA filtering"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffEngine.java"
    desc: "class/method/field diff"
  - path: "src/main/java/io/github/dependencyanalysis/bytecode/StableHashMethodVisitor.java"
    desc: "ASM MethodNode canonical encoder"
  - path: "src/main/java/io/github/dependencyanalysis/bytecode/ChangePoint.java"
    desc: "old/new descriptor 与 hash"
  - path: "src/main/java/io/github/dependencyanalysis/impact/BoundChangePoint.java"
    desc: "Module 与 dependency upgrade provenance"
---

# Feature: Bytecode Diff Engine

## Summary

对唯一 physical `(oldJar,newJar)` pair 执行一次 ASM bytecode diff，再将 raw ChangePoint 重新绑定到各 Module 的 `DependencyUpgradeKey`。Pool 大小为 `max(1, availableProcessors / 2)`；merge 与排序 deterministic。

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
- Output 按 class/member stable key 排序。

## Failure Contract

- Corrupt JAR/class 抛出 `BytecodeDiffException`。
- 单个 pair failure 不取消其他 JAR diff task；关联 Module 记录 `INCONCLUSIVE_BYTECODE_DIFF`。
- Pair failure 且无其他可分析 ChangePoint 时不构建 Call Graph，但仍生成 Module detail page。
- Raw bytecode diff 不对全部 changed method 构建 SSA；semantic filtering 延迟到 candidate path 之后。

## Acceptance

- Jump、switch、try/catch、bootstrap-only、`ConstantDynamic`、typed constant 变化可检出。
- Line/debug-only 变化不产生 body ChangePoint。
- 同一 physical pair 只 diff 一次；结果可绑定多个 Module。
- Parallel/sequential fixture 的 ChangePoint 集合和排序一致。
