---
title: "Bytecode Diff Engine"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Bytecode diff 是分析流水线的第七阶段"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "Bytecode diff 依赖前序阶段产出的 DependencyChange 清单"
  - path: "wiki/features/jar-locator.md"
    desc: "Bytecode diff 依赖 JarLocator 定位的 old/new jar 文件"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java"
    desc: "Bytecode diff 核心引擎，编排 class/method/field 各级别 diff"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java"
    desc: "不可变 bytecode 变化点数据类"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java"
    desc: "9 种变化类型枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java"
    desc: "Bytecode diff checked exception，携带 jarPath 和 className"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java"
    desc: "Jar → class index 两遍扫描索引器"
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

对 `VERSION_CHANGED` 依赖的 old/new jar 执行 bytecode 级别 diff，生成 `ChangePoint` 清单。使用 ASM 9.7 读取 class 文件，通过 SHA-256 body hash 检测 method body 变化，忽略 debug 信息（行号、局部变量表、stack map frames）。

## Design Decisions

- 引擎支持通过 `BytecodeDiffEngine(Set<ChangePointKind>)` 构造函数按 kind 过滤输出。无参构造函数默认使用 `ChangePointKind.DEFAULT_INCLUDED_KINDS`（6 种非 ADDED 类型：`CLASS_REMOVED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`），排除 ADDED 类型。过滤在 9 个 ChangePoint 产生点逐一执行 `includedKinds.contains(...)` 检查，空 set 则不产出任何 ChangePoint。

## Behavior

- 输入为 `JarLocationResult`（包含 `DependencyChange` + old/new jar `Path`）。
- 输出为不可变 `List<ChangePoint>`，每个 `ChangePoint` 携带 artifact 坐标、`ChangePointKind`、owner（internal class name）、name、descriptor、oldHash、newHash。
- 9 种 `ChangePointKind`：`CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- `ChangePointKind.DEFAULT_INCLUDED_KINDS` 为不可变 `Set<ChangePointKind>`，包含 6 种非 ADDED 类型，供引擎和 CLI 共享默认值。
- 引擎支持 kind 过滤：构造时传入 `Set<ChangePointKind>` 指定允许的 kind，diff 过程中在每个 ChangePoint 产生点检查 `includedKinds.contains(...)`，不在集合中的 kind 不产出。
- class 级别：仅检测新增和移除。
- method 级别：通过 `name:descriptor` 复合 key 匹配。新增/移除直接产出 ChangePoint；匹配到的方法比较 body hash，不同则产出 `METHOD_BODY_CHANGED`；同名方法 descriptor 不同则产出 `METHOD_DESCRIPTOR_CHANGED`。
- field 级别：通过 name 匹配。新增/移除直接产出 ChangePoint；匹配到的 field 比较 descriptor，不同则产出 `FIELD_DESCRIPTOR_CHANGED`。
- abstract 和 native 方法的 bodyHash 为 null，不参与 body 比较。
- 引擎为纯 diff 工具，不关心调用方如何筛选输入；仅 `VERSION_CHANGED` 进入 diff 的约束由上游 `JarLocator` 保证。

## Flow

1. `BytecodeDiffEngine.diff()` 接收 `JarLocationResult`。
2. `JarClassIndexer.index()` 对 old jar 和 new jar 分别构建 `Map<String, ClassInfo>` 索引。
3. `diffClasses()` 对两侧 class name 集合做 union，检测 `CLASS_ADDED` / `CLASS_REMOVED`。
4. 对两侧都存在的 class，调用 `diffMethods()` 和 `diffFields()`。
5. `diffMethods()` 使用 `name:descriptor` key 匹配方法，检测增删和 body hash 变化；再按 name 匹配检测 descriptor 变化。
6. `diffFields()` 使用 name 匹配字段，检测增删和 descriptor 变化。
7. 返回不可变 `List<ChangePoint>`。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffEngine.java` - 核心 diff 引擎，编排各级别 diff。
- `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePoint.java` - 不可变变化点数据类。
- `src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java` - 9 种变化类型枚举。
- `src/main/java/io/github/changeimpact/analyze/bytecode/BytecodeDiffException.java` - checked exception，携带 jarPath 和 className 诊断字段。
- `src/main/java/io/github/changeimpact/analyze/bytecode/JarClassIndexer.java` - 包级内部，两遍扫描 jar 构建 class 索引。第一遍收集 class/method/field 结构，第二遍为每个非 abstract/native 方法计算 body hash。
- `src/main/java/io/github/changeimpact/analyze/bytecode/StableHashMethodVisitor.java` - 包级内部，基于 SHA-256 的 method body hash visitor。跳过 label、frame、debug 信息，仅 hash 结构性指令。
- `src/main/java/io/github/changeimpact/analyze/bytecode/ClassInfo.java` - 包级内部 class 索引模型。
- `src/main/java/io/github/changeimpact/analyze/bytecode/MethodInfo.java` - 包级内部 method 索引模型，含 bodyHash。
- `src/main/java/io/github/changeimpact/analyze/bytecode/FieldInfo.java` - 包级内部 field 索引模型。

## Verification

- 单元测试：`BytecodeDiffEngineTest`（20）、`ChangePointTest`、`ChangePointKindTest`（11）、`BytecodeDiffExceptionTest`、`JarClassIndexerTest`、`StableHashMethodVisitorTest`。
- 测试使用 ASM `ClassWriter` 动态生成 jar，无外部测试资源文件。
- 覆盖所有 9 种 `ChangePointKind` 的识别。
- 覆盖 corrupt jar、corrupt class、abstract/native 方法 hash 为 null 等边界场景。
- 覆盖 kind 过滤行为：默认排除 ADDED、自定义 set 过滤、空 set 不产出、正向断言验证过滤逻辑生效。
- 全量测试无回归，Checkstyle 零违规。
