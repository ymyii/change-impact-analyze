---
title: "Repository Dependency Tree Diff Acceptance"
type: acceptance
---

# Acceptance: Repository Dependency Tree Diff

本页验收 [Repository Dependency Tree Diff](../../features/repository-dependency-tree-diff.md)。

## Functional

1. 场景：比较 baseline 与 current workspace
   - Given：baseline 可 peel 为 local commit，target 未指定，current workspace 可能为 dirty。
   - When：用户执行 `tree diff`。
   - Then：
     - Target dependency evidence来自 current workspace。
     - Report 记录 target dirty 状态，并按 occurrence identity 展示 added、removed 与 changed dependency。

2. 场景：比较两个 local commit
   - Given：baseline 与 target 均可 peel 为 local commit，且 Reactor / Module 结构一致。
   - When：用户执行 `tree diff`。
   - Then：
     - 每条 diff 保留双侧 chain、scope、directness 与 resolution source。
     - 已完成 Reactor 可独立打开其离线页面。

## Failure

1. 场景：两侧结构不一致
   - Given：baseline 与 target 的 Reactor 或 Module identity 不一致。
   - When：系统配对两侧 scope。
   - Then：
     - 对应范围标记为 `STRUCTURE_MISMATCH`。
     - 系统不把整个缺失结构转换为 dependency 全量新增或删除。

## Non-Functional

- [ ] 当相同 occurrence 以不同输入遍历顺序提供时，输出 diff 分类、PathKey 配对和 shard 顺序完全相同。
