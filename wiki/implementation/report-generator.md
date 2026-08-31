---
title: "Report Generator"
type: implementation
---

# Implementation: Report Generator

## Background

Impact、Tree Analyze 与 Tree Diff 的结果规模可能超过单个 HTML script block 的可维护边界，并且 Report 必须通过 `file://` 离线打开。Publication 还必须防止 partial write、script injection 和前端重新推导分析语义。

## Overview

Report Generator 将三条 pipeline 的冻结结果投影为各自 versioned schema、callback shard 与 HTML navigation。Shard 使用 script-safe JSON 和固定 callback boundary；Impact 单文件原子替换，Tree pipeline 可按 Reactor 增量发布。

## Core Flow

1. Pipeline 完成 domain classification 后冻结 report input；generator 不再访问 Maven、Git、JAR 或 Call Graph session。
2. Projection 将 identity、status、limitation、path 和 code evidence 转换为 feature-specific schema。
3. 大型数据按 Repository、Reactor、Module 或 affected path 分片，先写临时文件并验证 callback signature。
4. HTML、CSS、JavaScript 与 shard 在 publication boundary 内提交；Tree 已完成 Reactor 可以独立浏览。
5. 静态 gate 与 Playwright `file://` browser fixture 验证双 viewport navigation、lazy loading 和 escaping。

## Key Mechanisms

- Impact、Tree Analyze 与 Tree Diff 使用独立 schema version，禁止用前端条件分支合并会丢失语义的领域模型。
- JSON 在进入 `<script>` 前执行 script-safe escaping；callback 名称和 shard manifest由固定 signature 校验。
- Source code 只按用户交互从 command report cache 延迟加载；Console stack trace、credential 与 settings 内容不进入 HTML。
- Tree publication 以 Reactor 为完成单元；缺少 complete marker 或 shard 校验失败时不暴露半成品入口。
- Browser gate 同时验证桌面和窄 viewport，确保纯 `file://` 环境不依赖 HTTP server。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Report Generator Acceptance Criteria](../ac/implementation/report-generator.md)。
