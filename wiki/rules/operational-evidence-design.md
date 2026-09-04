---
name: "Operational Evidence Design"
type: rule
---

## Overview

本规则要求 [Command Control](../c4/components/dependency-analyzer-cli-command-control.md) 及高成本分析 Component 同时提供可关联的 metrics、progress 与 audit evidence，并在数据生成前应用 verbosity 成本门禁。

## Scope

- 新建或实质修改 CLI command、analysis pipeline、并发、external process、cache 或 report publication。
- 修改 Stage lifecycle、Diagnostic level、sampling、identity、elapsed time 或 failure evidence。

## Rules

- **必须**让设计覆盖主要工作量、完成量、Stage elapsed time、并发/队列状态、资源量和异常计数。
- **必须**让 progress 覆盖 `started`、处理中、`completed` 与 `failed`，并以稳定业务 identity 关联 Stage。
- **必须**记录影响执行路径或成本的决策、关键状态转换、结果与异常，使一次 command 可重建。
- **必须**让 `INFO`、`WARN`、`ERROR` 只生成有界低成本摘要，`DEBUG` 只增加低频有界详情，高频或高成本 evidence 只在 `TRACE` 启用后生成。
- **必须**在采集、计算与 message construction 前执行 verbosity 判断；elapsed time 使用 monotonic timing。
- **必须**让并发单元按稳定业务 identity 归属，不以 thread name 作为事实 identity。
- **禁止**在任何 level 输出 credential、token、settings 内容或未过滤的完整 user arguments。

## Verification

- Review metrics、progress、audit 三类用途是否都有 data source、emission point、correlation fields 与验收方式。
- 执行 Diagnostic、Runtime Metrics 与受影响 pipeline tests，确认禁用 level 时不产生高成本工作。
- 检查 failure 与 limitation 能通过 Stage、substage 和 identity 定位，且输出通过敏感信息审查。

## Non-Goals

- 不要求为同一事实建立三套重复输出系统。
- 不允许以 observability 为由改变分析结果或绕过成本门禁。
