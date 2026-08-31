---
title: "Impact Dependency Diff Engine Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Impact Dependency Diff Engine

本页验收 [Impact Dependency Diff Engine](../../implementation/dependency-diff-engine.md)。

## Functional

1. 场景：识别 artifact version change
   - Given：同一 Module 的同一 artifact key 在 baseline 与 target 均存在但 version 不同。
   - When：执行 dependency diff。
   - Then：
     - 输出一个 `VERSION_CHANGED`。
     - Change 同时保留 old 与 new logical coordinate。

2. 场景：处理单侧 Module
   - Given：Module 只存在于 baseline 或 target。
   - When：执行 dependency diff。
   - Then：
     - Baseline-only dependency 标记为 `REMOVED`。
     - Target-only dependency 标记为 `ADDED`。

## Failure

1. 场景：构造非法 change
   - Given：ChangeType 与 old / new artifact 空值组合不一致。
   - When：创建 `DependencyChange`。
   - Then：
     - 构造立即失败。
     - 非法对象不进入返回集合。

## Non-Functional

- [ ] 当两侧 tree 内容相同但输入集合顺序不同时，返回的 immutable change list 内容与顺序完全相同。
