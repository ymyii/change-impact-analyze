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

- 必填参数：`--baseline`、`--output`。
- 可选参数：`--project`（默认当前执行命令所在目录）、`--target`（默认使用 current workspace）、`--format`（默认 `html`）、`--build-java-home`（Maven 子进程使用的 JAVA_HOME 路径，不传时 Maven 继承当前 JVM 的 JAVA_HOME）、`--include-change-kinds`（逗号分隔的 `ChangePointKind` 名称，大小写不敏感，默认包含 6 种非 ADDED 类型：`CLASS_REMOVED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`；无效值返回退出码 2）。
- 校验规则：project 必须存在且为目录；baseline 不能为空；output 父目录必须存在且可写；format 只允许 `html` 或 `md`。
- 退出码：`0` 成功，非 `0` 失败。
- 诊断事件贯穿所有阶段，记录 stage/level/message/side/module/artifact/path/elapsedMillis。

## Flow

1. picocli 解析命令行参数。
2. `call()` 方法执行校验逻辑。
3. 校验失败时记录诊断事件并返回退出码 `1`。
4. 校验通过后调用 `runPipeline()` 执行完整分析流水线：WorkspaceManager → BuildRunner → DependencyAnalyzer → DependencyDiffEngine → JarLocator → BytecodeDiffEngine → CallGraphEngine → ImpactTracer → ReportGenerator。
5. Pipeline 异常时返回退出码 `2`。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java` - picocli `@Command` 定义，参数校验，诊断记录。
- `src/main/java/io/github/changeimpact/analyze/cli/OutputFormat.java` - 输出格式枚举（HTML、MD）。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollector.java` - 诊断事件收集器，支持 startStage/endStage/failStage/info/warn。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticEvent.java` - 不可变诊断事件，Builder 模式。
- `src/main/java/io/github/changeimpact/analyze/diagnostic/DiagnosticLevel.java` - 诊断级别：INFO、WARN、ERROR。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCliTest.java`
- 单元测试：`src/test/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCliBuildJavaHomeTest.java`
- 单元测试：`src/test/java/io/github/changeimpact/analyze/diagnostic/DiagnosticCollectorTest.java`
- 缺少必填参数（`--baseline`、`--output`）时报错。
- `--project` 不传时默认使用当前目录。
- 非法 format 报错。
- 默认 format 为 html。
- `--help` 不执行分析。
- 参数错误返回非 `0`。
- 诊断事件可被测试断言。
- `--build-java-home` 选项正确解析并传递给 BuildRunner 和 DependencyAnalyzer。
- `--include-change-kinds` 逗号分隔解析、大小写不敏感、默认值引用 `ChangePointKind.DEFAULT_INCLUDED_KINDS`、无效值退出码 2。
