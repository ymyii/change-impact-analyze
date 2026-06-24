---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Report Generator 是分析流水线的最终输出阶段"
  - path: "wiki/features/cli-validation-diagnostics.md"
    desc: "CLI 编排 Report Generator 并传入诊断事件"
  - path: "wiki/features/impact-tracing.md"
    desc: "Report Generator 展示影响追踪产物"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportGenerator.java"
    desc: "报告生成核心类，支持 HTML 和 Markdown 两种格式"
  - path: "src/main/java/io/github/changeimpact/analyze/report/ReportException.java"
    desc: "报告生成失败时的运行时异常"
  - path: "src/main/java/io/github/changeimpact/analyze/report/package-info.java"
    desc: "report 包定义"
  - path: "src/test/java/io/github/changeimpact/analyze/report/ReportGeneratorTest.java"
    desc: "报告生成 snapshot 和行为测试"
---

# Feature: Report Generator

## Summary

Report Generator 生成多文件 HTML 或 Markdown 变更影响分析报告。文件输出包含 index 以及 dependencies、internal changes、impact paths 三个子文件；字符串输出用于单文件预览或测试。

## Design Decisions

- 文件输出采用 index + dependencies/internal-changes/impact-paths 的多文件结构，避免单页报告过长，同时保留 Summary 和 Diagnostics 入口。
- `generateToString()` 保留单文件字符串生成能力，便于测试和调用方需要内存结果的场景。
- 报告生成保持 deterministic 输出，便于 snapshot 测试和跨次运行审计。
- `provided` scope 的 VERSION_CHANGED 依赖显示 `[API risk]`，把 compile-time API risk 暴露给报告读者。

## Actors / Entrypoints

- CLI pipeline 在收集 dependency changes、ChangePoint、ImpactResult 和 diagnostics 后调用报告生成。
- `ReportGenerator.generate(...)` 是文件输出入口。
- `ReportGenerator.generateToString(...)` 是字符串输出入口。

## Behavior Contract

- 支持 `OutputFormat.HTML` 和 `OutputFormat.MD`。
- `generate()` 写入 index 文件和三个子文件，子文件后缀由输出格式决定。
- Index 文件包含 Summary 和 Diagnostics，并链接到三个子文件。
- Dependencies 子文件按 module 展示依赖变动。
- Internal Changes 子文件展示 ChangePoint。
- Impact Paths 子文件展示影响路径和未报告原因统计。
- Dependency Changes 按 module 分组，保留传入顺序。
- HTML 中依赖变动类型使用 CSS class 区分 ADDED、REMOVED 和 VERSION_CHANGED。
- 写入失败时抛出 `ReportException`。

## Core Flow

1. CLI pipeline 调用 `generate()` 并传入完整分析产物。
2. Report Generator 根据 format 选择 HTML 或 Markdown 渲染路径。
3. `resolveSubPath()` 计算 dependencies、internal-changes 和 impact-paths 子文件路径。
4. 先写入三个子文件内容。
5. 再写入 index 文件，Summary section 包含子文件链接。
6. Diagnostics section 渲染所有诊断事件。

## Acceptance Criteria

### Functional

- Given HTML 格式，When `generate()` 执行，Then index 和三个子文件都使用 HTML 页面结构。
- Given Markdown 格式，When `generate()` 执行，Then index 和三个子文件都使用 Markdown 标题与链接。
- Given 依赖变动包含 provided scope 的 VERSION_CHANGED，When 报告渲染，Then 该条显示 `[API risk]`。
- Given Impact Paths 为空，When 报告渲染，Then 显示 "No static confirmed impact."。
- Given dependency changes 为空，When 报告渲染，Then 显示 "No dependency changes."。
- Given 文件写入失败，When `generate()` 执行，Then 抛出 `ReportException`。

### Non-Functional

- [ ] 报告输出必须 deterministic，支撑 snapshot 测试和审计对比。
- [ ] 报告必须保留诊断事件，方便失败或部分结果排查。
- [ ] 多文件输出的相对链接必须能从 index 导航到子文件。

## Edge Cases

- 空依赖变动、空 ChangePoint 或空 Impact Paths 都必须显示明确空态。
- Diagnostics 为空时仍生成完整报告结构。
- 输出路径 stem 决定子文件名称，避免覆盖无关文件。

## Implementation Boundaries

- Report Generator 只负责渲染和写文件，不执行分析、不修改诊断事件。
- 输出格式枚举由 CLI 层解析，报告层只消费 `OutputFormat`。
- Snapshot 稳定性依赖上游排序和报告自身 deterministic 渲染共同保证。
