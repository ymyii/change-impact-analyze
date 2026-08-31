---
title: "Repository Dependency Tree Report Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Repository Dependency Tree Report

本页验收 [Repository Dependency Tree Report](../../features/repository-dependency-tree-report.md)。

## Functional

1. 场景：分析 bounded Maven scope
   - Given：入口 path 指向 active aggregator、owned leaf 或 standalone POM。
   - When：用户执行 `tree analyze`。
   - Then：
     - Report 只包含 resolved bounded scope 中的 Reactor 与分析 Module。
     - 每个 dependency occurrence 保留 requested / resolved version、scope、directness 和 resolution source。

2. 场景：分析 local ref
   - Given：`--ref` 可解析为 local commit，且同一 Git-relative path 在该 commit 中存在可读 POM。
   - When：用户执行 `tree analyze`。
   - Then：
     - 分析在 detached snapshot 中执行。
     - 用户 current checkout 不发生切换。

## Failure

1. 场景：证据能力不完整
   - Given：所选 Maven Dependency Plugin 或 evidence runtime 不能提供完整 schema capability。
   - When：Preflight 验证 tree command。
   - Then：
     - Command 在依赖分析前失败。
     - 既有 Report 不被不完整结果替换。

## Non-Functional

- [ ] 当一个 Reactor 完成 publication 时，该 Reactor 的 HTML 与全部引用 shard 均可在无 HTTP server 的 `file://` 环境加载。
