---
name: "Dependency Analyzer Wiki"
type: index
---

## Overview

本 Wiki 维护 Dependency Analyzer 的 C4 架构底座，以及面向 coding agent 的实现协作、工程约束和操作流程。代码是实现细节的权威来源；Wiki 解释稳定边界、契约、失败语义和资源生命周期。

## Structure

- `c4/`：System Context、Actor、Software System、Container、Component 和关键 Code element。
- `use-case-realizations/`：Actor 目标的实现协作、成功路径、替代路径和异常路径。
- `adr/`：已接受的重要架构决策。
- `rules/`：可复用工程约束。
- `runbooks/`：可执行操作与失败入口。
- `glossary/`：重要、稳定且容易产生歧义的项目术语。

## In-scope Software Systems

- [Dependency Analyzer](c4/software-systems/dependency-analyzer.md)：读取本地代码库和 Maven 依赖证据，分析升级影响并生成离线报告。
