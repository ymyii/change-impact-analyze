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
- **必须**由当前程序日志系统控制自身日志级别；高频或高成本运行证据只在对应级别启用后生成。
- **必须**将日志级别映射为外部程序支持的参数，由外部程序决定日志内容；已经产生的日志实时转发，不按前缀或级别二次过滤。
- **必须**区分命令返回数据与日志；需要解析的 stdout 保持原始文本，stderr 独立读取并输出。日志型命令同时读取两个流。
- **必须**让失败结果携带操作、退出码和简短原因；已经输出的日志不进入异常消息、预检查摘要或报告。报告可以保留经过确认的版本、路径、状态和退出码等结构化事实。
- **禁止**为外部日志建立临时日志、失败补齐、回放或完整内存缓存。
- **必须**在采集、计算与 message construction 前执行 verbosity 判断；elapsed time 使用 monotonic timing。
- **必须**让并发单元按稳定业务 identity 归属，不以 thread name 作为事实 identity。
- **禁止**在任何 level 输出 credential、token、settings 内容或未过滤的完整 user arguments。

## Verification

- Review metrics、progress、audit 三类用途是否都有 data source、emission point、correlation fields 与验收方式。
- 执行 Diagnostic、Process Console、Runtime Metrics 与受影响 pipeline tests，确认程序自身日志按级别生成，外部日志在进程结束前可见，且无丢失或重复。
- 检查 failure 与 limitation 能通过 Stage、substage 和 identity 定位，且输出通过敏感信息审查。

## Non-Goals

- 不要求为同一事实建立三套重复输出系统。
- 不允许以 observability 为由改变分析结果或绕过成本门禁。
