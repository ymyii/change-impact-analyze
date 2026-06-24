---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Bytecode diff 是分析流水线的第七阶段"
  - path: "wiki/features/cli-validation-diagnostics.md"
    desc: "CLI 将 ChangePointKind 过滤集合传递给 Bytecode Diff Engine"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "Bytecode diff 依赖前序阶段产出的 VERSION_CHANGED DependencyChange"
  - path: "wiki/features/jar-locator.md"
    desc: "Bytecode diff 依赖 Jar Locator 定位的 old/new jar 文件"
  - path: "wiki/features/impact-tracing.md"
    desc: "Bytecode diff 产出的 ChangePoint 是影响追踪输入"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java"
    desc: "Bytecode diff 核心引擎，编排 class、method 和 field diff"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java"
    desc: "不可变 bytecode 变化点数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java"
    desc: "9 种变化类型枚举和默认过滤集合"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java"
    desc: "Bytecode diff checked exception，携带 jarPath 和 className"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java"
    desc: "Jar 到 class index 的两遍扫描索引器"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/StableHashMethodVisitor.java"
    desc: "忽略 debug 信息的 method body SHA-256 hash visitor"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ClassInfo.java"
    desc: "包级内部 class 索引模型"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/MethodInfo.java"
    desc: "包级内部 method 索引模型，含 bodyHash"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/FieldInfo.java"
    desc: "包级内部 field 索引模型"
---

# Feature: Bytecode Diff Engine

## Summary

Bytecode Diff Engine 对 `VERSION_CHANGED` 依赖的 old/new jar 执行 bytecode 级别 diff，生成 `ChangePoint` 清单。它使用 ASM 读取 class 文件，并通过稳定 SHA-256 method body hash 检测方法体变化。

## Design Decisions

- 无参构造默认使用 `ChangePointKind.DEFAULT_INCLUDED_KINDS`，默认排除 ADDED 类型，降低报告噪音并聚焦移除和变更风险。
- `BytecodeDiffEngine(Set<ChangePointKind>)` 支持调用方显式过滤 kind，过滤在每个 ChangePoint 产生点执行。
- Method body hash 忽略 debug 信息、line number、local variable table 和 stack map frames，只关注结构性指令。
- Abstract 和 native 方法 bodyHash 为 null，不参与 method body 变化比较。

## Actors / Entrypoints

- CLI pipeline 在 Jar Locator 成功后创建 `BytecodeDiffEngine`。
- `BytecodeDiffEngine.diff(jarLocationResult)` 是单个依赖版本变更的 diff 入口。
- `JarClassIndexer.index()` 是 jar class/method/field 索引入口。

## Behavior Contract

- 输入为 `JarLocationResult`，包含 `DependencyChange`、old jar path 和 new jar path。
- 输出为不可变 `List<ChangePoint>`。
- `ChangePoint` 携带 artifact、kind、owner、name、descriptor、oldHash 和 newHash。
- 支持 9 种 `ChangePointKind`：`CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- Class 级别只检测新增和移除。
- Method 级别先按 `name:descriptor` 匹配增删和 body hash，再按 name 检测 descriptor 变化。
- Field 级别按 name 匹配，检测增删和 descriptor 变化。
- includedKinds 为空集合时不产出任何 ChangePoint。
- Corrupt jar 或 class 读取失败时抛出 `BytecodeDiffException`。

## Core Flow

1. `diff()` 接收 `JarLocationResult`。
2. `JarClassIndexer.index()` 对 old jar 和 new jar 分别构建 class index。
3. `diffClasses()` 对两侧 class name 集合做 union。
4. Class 只在一侧存在时按 includedKinds 产出 `CLASS_ADDED` 或 `CLASS_REMOVED`。
5. Class 两侧都存在时调用 `diffMethods()` 和 `diffFields()`。
6. Method diff 产出新增、移除、descriptor 变化和 body hash 变化。
7. Field diff 产出新增、移除和 descriptor 变化。
8. 返回不可变 ChangePoint 列表。

## Acceptance Criteria

### Functional

- Given old jar 中 class 不存在于 new jar，When 执行 diff，Then 在 kind 允许时产出 `CLASS_REMOVED`。
- Given new jar 中 class 不存在于 old jar，When 执行 diff，Then 在 kind 允许时产出 `CLASS_ADDED`。
- Given 方法 descriptor 改变，When 执行 diff，Then 在 kind 允许时产出 `METHOD_DESCRIPTOR_CHANGED`。
- Given 方法结构性指令改变，When 执行 diff，Then 在 kind 允许时产出 `METHOD_BODY_CHANGED`。
- Given field descriptor 改变，When 执行 diff，Then 在 kind 允许时产出 `FIELD_DESCRIPTOR_CHANGED`。
- Given includedKinds 为空，When 执行 diff，Then 返回空 ChangePoint 列表。

### Non-Functional

- [ ] Hash 结果必须忽略 debug-only 差异，避免报告无业务意义的 method body 变化。
- [ ] 输出必须不可变，避免后续 call graph、impact 或 report 阶段修改变化点。
- [ ] Diff 行为必须 deterministic，支撑报告 snapshot 和审计。

## Edge Cases

- Abstract 和 native 方法没有 body hash，不参与 body hash 比较。
- 同名但 descriptor 不同的方法会产生 descriptor 变化，而不是被误判为两个无关方法。
- Corrupt class 需要携带 jarPath 和 className，便于定位损坏输入。
- ADDED 类型默认被过滤，但调用方可以通过 `--include-change-kinds` 显式纳入。

## Implementation Boundaries

- Bytecode Diff Engine 不定位 jar、不筛选 `VERSION_CHANGED`；这些由 Jar Locator 和 Dependency Diff Engine 保证。
- `ChangePointKind.DEFAULT_INCLUDED_KINDS` 是 CLI 和 engine 共享的默认过滤合同。
- `ChangePoint` 是 Impact Tracing 和 Report Generator 的输入边界。
