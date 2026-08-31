---
title: "Call Graph Engine Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Call Graph Engine

本页验收 [Call Graph Engine](../../implementation/call-graph-engine.md)。

## Functional

1. 场景：构建 CHA Module graph
   - Given：Module classpath、entrypoint index 与 dependency body policy 有效，algorithm 为 `cha`。
   - When：Engine 构建 Call Graph。
   - Then：
     - JDK model selection 为 `none`。
     - 输出包含冻结 topology、stats 和 JDK declared dispatch pruning metadata。

2. 场景：构建实验性 `k-obj` graph
   - Given：algorithm 为 `k-obj`，depth 为正整数，JDK 8 model 完整可用。
   - When：Engine 构建 Call Graph。
   - Then：
     - 输出 metadata 保留实际 depth 与 model identity。
     - Impact domain object 不进入 strategy input 或 output contract。

## Failure

1. 场景：Module 构图超时
   - Given：该 Module 配置正数 timeout，WALA build 超过限制。
   - When：timeout monitor 终止当前 build。
   - Then：
     - 当前 Module 形成 handled Call Graph failure。
     - 其他 Module 不被取消。

## Non-Functional

- [ ] 当输入 classpath、entrypoint、algorithm 与 policy 相同时，重复构图输出的 topology node identity 和 SCC 排序完全相同。
