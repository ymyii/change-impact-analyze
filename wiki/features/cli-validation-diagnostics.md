---
title: "CLI Validation and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "CLI 是分析流水线的入口和总编排"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java"
    desc: "CLI 主类，参数定义和校验逻辑"
  - path: "src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java"
    desc: "输出格式枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java"
    desc: "诊断事件收集器"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java"
    desc: "诊断事件数据模型"
  - path: "src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java"
    desc: "诊断级别枚举"
---

# Feature: CLI Validation and Diagnostics

## Summary

CLI 提供命令行入口，负责参数解析、校验、退出码控制和诊断事件收集。使用 picocli 框架定义命令参数，校验通过后进入分析流程。

## Behavior

- 必填参数：`--project`、`--baseline`、`--output`。
- 可选参数：`--target`（默认使用 current workspace）、`--format`（默认 `html`）。
- 校验规则：project 必须存在且为目录；baseline 不能为空；output 父目录必须存在且可写；format 只允许 `html` 或 `md`。
- 退出码：`0` 成功，非 `0` 失败。
- 诊断事件贯穿所有阶段，记录 stage/level/message/side/module/artifact/path/elapsedMillis。

## Flow

1. picocli 解析命令行参数。
2. `call()` 方法执行校验逻辑。
3. 校验失败时记录诊断事件并返回退出码 `1`。
4. 校验通过后进入分析阶段（当前为 no-op）。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java` - picocli `@Command` 定义，参数校验，诊断记录。
- `src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java` - 输出格式枚举（HTML、MD）。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java` - 诊断事件收集器，支持 startStage/endStage/failStage/info/warn。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java` - 不可变诊断事件，Builder 模式。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java` - 诊断级别：INFO、WARN、ERROR。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCliTest.java`
- 单元测试：`src/test/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollectorTest.java`
- 缺少必填参数时报错。
- 非法 format 报错。
- 默认 format 为 html。
- `--help` 不执行分析。
- 参数错误返回非 `0`。
- 诊断事件可被测试断言。
