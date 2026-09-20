# Index 模板

只适用于 `wiki/index.md`，并使用 `type: index`。

---
name: "<Canonical English Project Wiki Name>"
type: index
---

## Overview

<最多三句说明该 Wiki 的项目范围、C4 底座和上层工程知识。>

## Structure

- `c4/`：System Context、Actor、Software System、Container、Component 和关键 Code element。
- `use-case-realizations/`：Actor 目标的实现协作、成功路径、替代路径和异常路径。
- `adr/`：已接受的重要架构决策。
- `rules/`：可复用工程约束。
- `runbooks/`：可执行操作与失败入口。
- `glossary/`：重要、稳定且容易产生歧义的项目术语。

## In-scope Software Systems

- [<Software System>](c4/software-systems/<system>.md)：<该系统提供的独立价值。>

> **填写说明**
>
> - Index 只解释 Wiki 结构，并链接主要 in-scope Software System。
> - 没有 Glossary 页面时，删除 `glossary/` 的 Structure 条目。
> - 每个 Software System 使用一个相对 Markdown 链接和一句价值摘要。
> - 不列出 external Software System。
> - 不枚举 Actor、Container、Component、Code、Use Case Realization、ADR、Rule、Runbook 或 Glossary term。
> - 不创建按页面类型展开的完整导航。
> - 不创建 `wiki/log.md`。
>
> 删除所有占位符和不适用的示例项。
