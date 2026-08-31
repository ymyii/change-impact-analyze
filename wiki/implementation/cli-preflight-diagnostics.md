---
title: "CLI Preflight and Diagnostics"
type: implementation
---

# Implementation: CLI Preflight and Diagnostics

## Background

三个 CLI 用户目标会访问 Git、Maven、JDK、filesystem 和外部 process。输入或 runtime 不满足前置条件时必须在产生新分析副作用前失败，同时让并发阶段的 Console Diagnostic 保持可定位、可审计且不泄露敏感内容。

## Overview

该机制将 argument validation、command-specific Preflight、五段 Console prefix、Stage lifecycle、verbosity 和 Runtime Metrics 组织为统一边界。HTML 不保存 Console event；显式 topology JSON 与 Report schema 相互独立。

## Core Flow

1. Picocli 先验证 option 组合和值域；解析失败不启动 command metrics 或 Preflight。
2. Preflight 以有向检查顺序验证 path、Git、POM、output、JDK、Maven runtime、settings activation 和嵌入 artifact capability。
3. 成功 dispatch 后，Stage 使用 immutable context 输出 `started`、`completed` 或 `failed` lifecycle line。
4. Maven subprocess output 根据 verbosity 逐行进入 Console，failure 仅在内存保留 bounded tail。
5. `-vv` 启用高成本审计和 Runtime Metrics；command 结束时关闭 scheduler 并输出 final summary。

## Key Mechanisms

- 每个物理行使用 `[时间][日志级别][阶段][子阶段][额外信息] message`；第五段只容纳稳定 identity，不容纳 path、progress、status 或计数。
- `INFO`、`DEBUG`、`TRACE` 决定证据生成成本；高成本 ASM、Intermediate Representation（IR，中间表示）与 normalized text 只有门禁通过后才构造。
- Preflight failure 不启动 pipeline，也不触碰旧 Report。Handled Module failure可以产生 partial Report；argument 或 global preparation failure 返回 command-level failure。
- Runtime Metrics 只在 TRACE 创建 command-scoped daemon scheduler；采样失败只产生 transient Diagnostic，不改变 status、Report 或 exit code。
- Stack trace 逐物理行重复同一 context，仅进入 Console；credential、settings 内容和未过滤的完整 user arguments 禁止输出。

## Design Decisions

- None.

## Acceptance

完整验收条件见 [CLI Preflight and Diagnostics Acceptance](../acceptance/implementation/cli-preflight-diagnostics.md)。
