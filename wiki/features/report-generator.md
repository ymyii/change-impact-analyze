---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "ReportGenerator 是分析流水线的最终输出阶段"
  - path: "wiki/features/cli-validation-diagnostics.md"
    desc: "CLI 编排 ReportGenerator 并传入诊断事件"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java"
    desc: "报告生成核心类，支持 HTML 和 Markdown 两种格式"
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportException.java"
    desc: "报告生成失败时的运行时异常"
  - path: "src/main/java/io/github/changeimpact/analyze/report/package-info.java"
    desc: "report 包定义"
  - path: "src/test/java/io/github/changeimpact/analyze/report/ReportGeneratorTest.java"
    desc: "报告生成单元测试，包含 snapshot 测试"
---

# Feature: Report Generator

## Summary

生成单文件 HTML 或 Markdown 变更影响分析报告。报告包含 Summary、Dependency Changes（按模块分组）、Internal Changes（ChangePoint 清单）、Impact Paths 和 Diagnostics（含耗时列）五个 section。

## Behavior

- 支持 `OutputFormat.HTML` 和 `OutputFormat.MD` 两种格式。
- `generate()` 写入文件，`generateToString()` 返回字符串。
- Dependency Changes 按 `DependencyChange.getModule()` 分组，使用 `LinkedHashMap` 保持插入顺序。
- 依赖变动类型使用 CSS class 区分颜色：ADDED（绿色）、REMOVED（红色）、VERSION_CHANGED（橙色）。
- `provided` scope 的 VERSION_CHANGED 依赖标记 `[API risk]`。
- Impact Paths 为空时显示 "No static confirmed impact."。
- Diagnostics 表格包含 Stage、Level、Message、Elapsed(ms) 四列。
- HTML 报告包含内联 CSS，为单文件自包含。
- 写入失败抛出 `ReportException`。

## Flow

1. CLI pipeline 收集所有阶段的 `DependencyChange`、`ChangePoint`、`ImpactResult`、`DiagnosticEvent`。
2. 调用 `ReportGenerator.generate()` 传入数据和输出格式。
3. 根据 format 选择 HTML 或 Markdown 生成路径。
4. 拼接报告内容为字符串，写入输出文件。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java` - 报告生成核心，HTML/Markdown 双格式输出。
- `src/main/java/io/github/changeimpact/analyze/report/ReportException.java` - 报告生成运行时异常。
- `src/test/java/io/github/changeimpact/analyze/report/ReportGeneratorTest.java` - 12 个测试，覆盖格式生成、依赖变动展示、ChangePoint 展示、Impact Path 展示、API risk 标记、模块分组、snapshot 确定性。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/report/ReportGeneratorTest.java`
- HTML 和 Markdown 格式均可生成有效文件。
- 依赖变动按模块分组，保持顺序。
- Snapshot 测试验证输出确定性（时间戳归一化后比较）。
- 空数据场景显示 "No static confirmed impact." 或 "No dependency changes."。
