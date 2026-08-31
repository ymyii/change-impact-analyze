---
title: "CLI Preflight and Diagnostics Acceptance"
type: acceptance
---

# Acceptance: CLI Preflight and Diagnostics

本页验收 [CLI Preflight and Diagnostics](../../implementation/cli-preflight-diagnostics.md)。

## Functional

1. 场景：输出 Stage lifecycle
   - Given：一个 Stage 拥有 immutable Diagnostic context。
   - When：Stage 开始并完成或失败。
   - Then：
     - 每个物理行包含五段 prefix。
     - 完成或失败行在 message 中包含 `elapsedMs`。

2. 场景：TRACE Runtime Metrics
   - Given：command 使用 `-vv` 成功 dispatch。
   - When：command 运行并结束。
   - Then：
     - Session 输出初始 sample、周期 sample 与 final summary。
     - `close()` 停止 command-scoped scheduler。

## Failure

1. 场景：Preflight 检查失败
   - Given：任一 required check 不满足。
   - When：Preflight 运行到该检查。
   - Then：
     - Pipeline 不启动。
     - Diagnostic 给出失败 check 与可行动原因，旧 Report 保持不变。

## Non-Functional

- [ ] 当 message 含多行或 stack trace 时，每个物理行都重复相同 context prefix，且输出不包含 settings credential。
