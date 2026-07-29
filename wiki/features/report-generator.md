---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Report Generator 是分析流水线的最终输出阶段"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "CLI 编排 Report Generator 并传入诊断事件"
  - path: "wiki/features/impact-tracing.md"
    desc: "Report Generator 展示影响追踪产物"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "Repository/reactor tree HTML 的输出契约"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/report/ReportGenerator.java"
    desc: "报告生成核心类，支持 HTML 和 Markdown 两种格式"
  - path: "src/main/java/io/github/dependencyanalysis/report/ReportException.java"
    desc: "报告生成失败时的运行时异常"
  - path: "src/main/java/io/github/dependencyanalysis/report/package-info.java"
    desc: "report 包定义"
  - path: "src/test/java/io/github/dependencyanalysis/report/ReportGeneratorTest.java"
    desc: "报告生成 snapshot 和行为测试"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "tree offline HTML、escaping 和 atomic publish"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeReportSession.java"
    desc: "tree incremental Index checkpoint session"
---

# Feature: Report Generator

## Summary

Report generation 包含两条独立输出：`impact` 生成 HTML/Markdown index 与 detail 文件，并追加 preflight/runtime metadata；`tree` 生成 repository index、每 reactor HTML 和本地 CSS/JavaScript assets。

## Design Decisions

- 文件输出采用 index + dependencies/internal-changes/impact-paths 的多文件结构，避免单页报告过长，同时保留 Summary 和 Diagnostics 入口。
- `generateToString()` 保留单文件字符串生成能力，便于测试和调用方需要内存结果的场景。
- 报告生成保持 deterministic 输出，便于 snapshot 测试和跨次运行审计。
- `provided` scope 的 VERSION_CHANGED 依赖显示 `[API risk]`，把 compile-time API risk 暴露给报告读者。
- Tree renderer 对全部动态内容 HTML escaping，使用 staging 后只替换 `index.html` 与 `dependency-report/`，保留 output root 其他内容。
- Tree command-level Preflight 成功后立即发布 `RUNNING 0/N`；每个 reactor page 先于对应 Index checkpoint 原子发布。
- Tree session 只持有 metadata、Command Preflight 与 `ReactorReportSummary`；完整 reactor/module/occurrence result 在 page 发布后可释放。
- Tree Index 的 Metadata 与 Summary 均表格化；Reactors table 分开统计 Internal conflicts 和 Cross-module conflicts，避免 key/value 文本摘要。
- Tree reactor page 将 Reactor/Module metadata 与 issue 表格化，不暴露内部 module role 或纳入原因；Cross-module conflicts 是 Reactor-level section，Module internal conflicts 位于各自 tab。
- Tree dependency tree 是无交互的 Maven-style verbose `<pre>`；每张 conflict table 独立承担检索、filter、排序和分页。
- Tree Report 只展示 occurrence 上实际存在的 management evidence；`managedFrom` 是 management 前值，不表示来源 POM/BOM。
- Tree assets 无 CDN、remote font、network API 或 server endpoint，可通过 `file://` 打开。

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
- Tree Index 状态为 `RUNNING`、`SUCCESS`、`COMPLETED_WITH_ISSUES` 或 `FAILED`。Handled failure 保留已有 pages；hard interruption 保留最后成功的 `RUNNING x/N` checkpoint。
- Tree Index 的 Metadata、Summary、Command Preflight 和 Reactors 都使用 table；Summary 汇总 Reactor、Module、Dependency 及两类 conflict 数，Reactors row 分别展示 Internal/Cross-module conflict 数。
- Reactor page 顺序为 Reactor metadata、Module metadata、问题、Cross-module conflicts、Module tabs。Module metadata 分别统计 Internal/Cross-module conflicts。
- 每个 Module tab 包含一张 `MODULE_MEDIATION` Internal conflicts table 和一个 `<pre class="dependency-tree">`。Cross-module conflicts 独立位于 Reactor level，不复制到 Module tabs。
- 两类 conflict table 都将 Evidence 作为最后一列。Internal Evidence 子表列为 Source、Dependency chain、Original version、Scope；Cross-module Evidence 额外在 Source 后加入 Module。`DEPENDENCY_MANAGEMENT` row 输出真正的空 `<td></td>` chain，不复用 occurrence path，也不推断来源 POM/BOM。
- 两类 conflict table 都有 component-scoped 全字段 search、Scope filter、sortable columns 与 10/50/100 pagination；Cross-module table 额外有 Module filter。每个 Module tab 和 Cross-module section 的交互状态独立；空表保留 header，但不渲染 controls。
- Dependency tree 使用 Maven 的 `+-`、`\-` 和缩进 chain。Verbose annotation 顺序为 version managed from、scope managed from、optional、omitted conflict/duplicate/cycle/raw、reactor module；tree 内不含 button、link、`<details>`、`title` 或 click 行为。

## Core Flow

1. CLI pipeline 调用 `generate()` 并传入完整分析产物。
2. Report Generator 根据 format 选择 HTML 或 Markdown 渲染路径。
3. `resolveSubPath()` 计算 dependencies、internal-changes 和 impact-paths 子文件路径。
4. 先写入三个子文件内容。
5. 再写入 index 文件，Summary section 包含子文件链接。
6. Diagnostics section 渲染所有诊断事件。

Tree flow 独立执行：初始化 owned output → 写 `RUNNING 0/N` → 对每个 reactor 写 temporary page 并 atomic move → 写 temporary Index 并 atomic move → 转换轻量 summary → 最终写 `SUCCESS` 或 `COMPLETED_WITH_ISSUES`；异常路径尝试写 `FAILED`。

## Acceptance Criteria

### Functional

- Given HTML 格式，When `generate()` 执行，Then index 和三个子文件都使用 HTML 页面结构。
- Given Markdown 格式，When `generate()` 执行，Then index 和三个子文件都使用 Markdown 标题与链接。
- Given 依赖变动包含 provided scope 的 VERSION_CHANGED，When 报告渲染，Then 该条显示 `[API risk]`。
- Given Impact Paths 为空，When 报告渲染，Then 显示 "No static confirmed impact."。
- Given dependency changes 为空，When 报告渲染，Then 显示 "No dependency changes."。
- Given 文件写入失败，When `generate()` 执行，Then 抛出 `ReportException`。
- Given reactor page 已发布；When Index atomic move failure；Then page 保留，Index 不得链接未完整发布内容。
- Given 只有 duplicate 且 requested version 相同；When 渲染 reactor page；Then duplicate 保留在 verbose dependency tree annotation，但不产生 `MODULE_MEDIATION` row。
- Given 跨 Module selected version 不同；When 渲染 reactor page；Then conflict 只进入 Cross-module conflicts section，Module metadata 的 Cross-module count 同步反映该 Module。
- Given `DEPENDENCY_MANAGEMENT` Evidence；When 渲染 internal 或 cross-module table；Then其 Dependency chain cell 为空，Evidence 仍保留 source、version、scope 及 cross-module 的 Module。
- Given 无 dependency conflict；When 渲染对应 table；Then保留空 table header，交互 controls 不出现。
- Given 两个 Module tab 和 Reactor Cross-module section 都有 conflict；When 用户操作 search/filter/sort/page；Then各 component 的状态互不影响。

### Non-Functional

- [ ] 报告输出必须 deterministic，支撑 snapshot 测试和审计对比。
- [ ] 报告必须保留诊断事件，方便失败或部分结果排查。
- [ ] 多文件输出的相对链接必须能从 index 导航到子文件。

## Edge Cases

- 空依赖变动、空 ChangePoint 或空 Impact Paths 都必须显示明确空态。
- Diagnostics 为空时仍生成完整报告结构。
- 输出路径 stem 决定子文件名称，避免覆盖无关文件。
- Tree 新一次有效运行清理旧工具 pages；command-level Preflight failure 不触碰旧 Report。

## Implementation Boundaries

- Report Generator 只负责渲染和写文件，不执行分析、不修改诊断事件。
- 输出格式枚举由 CLI 层解析，报告层只消费 `OutputFormat`。
- Snapshot 稳定性依赖上游排序和报告自身 deterministic 渲染共同保证。
