---
title: "Impact Tracing Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Impact Tracing

本页验收 [Impact Tracing](../../implementation/impact-tracing.md)。

## Functional

1. 场景：从 evidence 反向追踪入口
   - Given：有效 ChangePoint 已绑定 Module evidence，冻结 Call Graph 中存在到 entrypoint 的 caller path。
   - When：执行 reverse query。
   - Then：
     - 输出 affected path 从业务 root 连接到 ChangePoint terminal。
     - Path step 保留稳定 method identity 和 evidence mechanism。

2. 场景：处理循环调用图
   - Given：候选路径经过一个或多个 SCC。
   - When：选择代表 root 与 predecessor。
   - Then：
     - Query 有限终止。
     - 相同 topology 重复执行选择相同代表路径。

## Failure

1. 场景：无法证明 CHA receiver type
   - Given：caller-local IR 不足以解析 receiver。
   - When：CHA path pruning 检查该 edge。
   - Then：
     - 原 CHA edge 被保留。
     - Module status 不因该 unknown 单独降级。

## Non-Functional

- [ ] 当 QueryNode 集合相同而 worker 数不同，最终 affected path identity、排序和 pruning metrics 完全相同。
