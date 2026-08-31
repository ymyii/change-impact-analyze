---
title: "Report Generator Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Report Generator

本页验收 [Report Generator](../../implementation/report-generator.md)。

## Functional

1. 场景：离线加载 Report
   - Given：一个 pipeline 已冻结有效 report data 并完成 publication。
   - When：用户通过 `file://` 打开入口 HTML。
   - Then：
     - 页面无需 HTTP server 即可导航。
     - 所引用 callback shard 能按 manifest 加载并通过 signature 校验。

2. 场景：延迟加载 code evidence
   - Given：Affected Path 拥有可用 code comparison evidence。
   - When：用户展开对应 UI。
   - Then：
     - 页面从独立 shard 加载内容。
     - 初始 HTML 不内嵌完整 source evidence。

## Failure

1. 场景：shard publication 不完整
   - Given：JSON、callback signature 或 complete marker 校验失败。
   - When：generator 提交 publication。
   - Then：
     - 不暴露指向半成品 shard 的入口。
     - 既有完整 Report 保持可用。

## Non-Functional

- [ ] 当 browser fixture 在桌面与窄 viewport 通过 `file://` 运行时，所有门禁 navigation 与 lazy-load assertion 的失败数为 `0`。
