---
title: "CLI Validation and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "CLI 是分析流水线的入口和总编排"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "CLI 将 ChangePointKind 过滤条件传递给 Bytecode Diff Engine"
  - path: "wiki/features/report-generator.md"
    desc: "诊断事件最终进入报告输出"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java"
    desc: "CLI 主类，参数定义、校验逻辑和 pipeline 编排"
  - path: "src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java"
    desc: "输出格式枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java"
    desc: "诊断事件收集器"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java"
    desc: "诊断事件数据模型"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java"
    desc: "诊断级别枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/bytecode/ChangePointKind.java"
    desc: "CLI 共享的 ChangePointKind 默认过滤集合"
---

# Feature: CLI Validation and Diagnostics

## Summary

CLI 提供命令行入口，负责参数解析、校验、退出码控制和诊断事件收集。校验通过后进入完整分析流水线，校验或 pipeline 失败时通过退出码和诊断事件暴露失败原因。

## Design Decisions

- 参数校验在进入 pipeline 前完成，校验失败直接返回退出码 `1` 并记录 validation 诊断事件，避免后续阶段基于无效输入执行。
- `--project` 可省略并默认使用当前工作目录，使最常见的本仓库内执行方式保持低摩擦。
- `--include-change-kinds` 默认使用 `ChangePointKind.DEFAULT_INCLUDED_KINDS`，默认报告聚焦移除和变更类风险；调用方可显式选择需要纳入的 ChangePointKind。
- picocli enum 解析启用大小写不敏感，降低 `--format` 和 `--include-change-kinds` 的 CLI 输入成本。

## Actors / Entrypoints

- 用户通过 `java -jar target/change-impact-analyze.jar ...` 或 IDE 运行 `ChangeImpactAnalyzeCli.main()`。
- picocli 调用 `ChangeImpactAnalyzeCli.call()` 作为命令执行入口。
- 完整 pipeline 从 `runPipeline()` 开始，按固定阶段编排分析。

## Behavior Contract

- 必填参数为 `--baseline` 和 `--output`。
- 可选参数包括 `--project`、`--target`、`--format`、`--build-java-home` 和 `--include-change-kinds`。
- `--project` 未传时使用当前工作目录；`--target` 未传时 target side 使用 current workspace。
- `--format` 支持 HTML 和 Markdown，默认 HTML。
- `--build-java-home` 只影响 Maven 子进程的 `JAVA_HOME`，不改变工具自身运行 JDK。
- `--include-change-kinds` 是逗号分隔的 `ChangePointKind` 名称集合，大小写不敏感。
- 退出码 `0` 表示成功，`1` 表示参数校验失败，`2` 表示 pipeline 执行失败。
- 诊断事件记录 stage、level、message、side、module、artifact、path 和 elapsedMillis 等上下文，并最终进入报告。

## Core Flow

1. picocli 解析命令行参数并填充 CLI 字段。
2. `call()` 处理 `--project` 默认值并启动 validation stage。
3. 校验 project、baseline、output parent 和输出格式相关约束。
4. 校验失败时记录诊断事件、输出错误信息并返回退出码 `1`。
5. 校验成功后调用 `runPipeline()`，按 workspace、build、dependency、bytecode、call graph、impact、report 顺序执行。
6. pipeline 异常时记录 pipeline error，输出 stack trace 并返回退出码 `2`。
7. pipeline 成功时写入报告并返回退出码 `0`。

## Acceptance Criteria

### Functional

- Given `--project` 未传且当前目录存在，When CLI 执行，Then project 使用当前工作目录。
- Given project 路径不存在或不是目录，When CLI 校验参数，Then 返回退出码 `1` 并记录 validation 失败。
- Given baseline 为空，When CLI 校验参数，Then 返回退出码 `1` 并提示 baseline 不可为空。
- Given output 父目录不存在或不可写，When CLI 校验参数，Then 返回退出码 `1` 并记录输出路径失败原因。
- Given `--include-change-kinds` 传入合法大小写混合值，When CLI 解析参数，Then 传给 BytecodeDiffEngine 的 kind 集合保持语义正确。
- Given pipeline 任一阶段抛出异常，When CLI 捕获异常，Then 返回退出码 `2` 并记录 pipeline error 诊断事件。

### Non-Functional

- [ ] CLI 失败必须可诊断：用户能通过 stderr 和报告中的 Diagnostics 定位失败阶段。
- [ ] 默认行为必须兼容不传 `--target`、`--project` 和 `--build-java-home` 的常见执行方式。
- [ ] 参数解析和默认过滤集合必须保持 deterministic，避免报告内容因输入大小写或集合顺序产生不稳定输出。

## Edge Cases

- `--help` 由 picocli 处理，不进入分析 pipeline。
- `--target` 为空时不创建 target worktree，target side 指向 current workspace。
- `--include-change-kinds` 为空集合时 BytecodeDiffEngine 不产出任何 ChangePoint，后续 call graph 和 impact 阶段会被跳过。
- pipeline 无版本变更时跳过 bytecode diff；无 ChangePoint 时跳过 call graph 和 impact tracing。

## Implementation Boundaries

- CLI 层只负责参数、诊断和阶段编排，不实现各分析阶段的内部算法。
- 诊断模型是跨阶段共享边界；阶段实现只通过 `DiagnosticCollector` 写入事件。
- 输出格式选择只传递给 ReportGenerator，报告文件结构由报告模块拥有。
