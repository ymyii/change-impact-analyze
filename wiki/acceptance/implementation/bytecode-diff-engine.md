---
title: "Bytecode Diff Engine Acceptance"
type: acceptance
---

# Acceptance: Bytecode Diff Engine

本页验收 [Bytecode Diff Engine](../../implementation/bytecode-diff-engine.md)。

## Functional

1. 场景：方法体分阶段过滤
   - Given：old / new method body hash 不同。
   - When：反编译文本为 `IDENTICAL`，或文本未命中但 normalized SSA 为 `MATCHED`。
   - Then：
     - `METHOD_BODY_CHANGED` 不进入 effective ChangePoint。
     - Evidence 记录实际短路阶段，未执行的后续阶段标记为 skipped。

2. 场景：ServiceLoader registration 删除
   - Given：baseline 存在 provider registration。
   - When：target 只删除 registration，或同时删除 provider class。
   - Then：
     - 只删除 registration 时生成 typed resource ChangePoint。
     - 同时删除 provider class 时只保留 class removal。

## Failure

1. 场景：单个 JAR pair 无法读取
   - Given：一个 pair 含损坏 JAR 或 class，其他 pair 可用。
   - When：执行并行 bytecode diff。
   - Then：
     - 失败 pair 产生 `INCONCLUSIVE_BYTECODE_DIFF` Diagnostic。
     - 其他 pair 继续完成比较。

## Non-Functional

- [ ] 当 logical coordinate pair、JAR bytes 与 include kinds 相同时，串行和并行执行产生完全相同的 ChangePoint identity 与排序。
