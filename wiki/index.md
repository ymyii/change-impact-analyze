---
name: "Dependency Analyzer Wiki"
type: index
---

## Overview

本 Wiki 维护 Dependency Analyzer 的 C4 架构底座，以及面向 coding agent 的 Use Case、工程约束和操作流程。代码仍是实现细节的权威来源；Wiki 解释跨文件边界、稳定契约和失败语义。

## Structure

- `c4/`：Actor、Software System、Container、Component 和关键 Code element。
- `use-cases/`：Actor 目标、场景和 Acceptance Criteria。
- `adr/`：已接受的重要架构决策。
- `rules/`：可复用工程约束。
- `runbooks/`：可执行操作与失败入口。

## In-scope Software Systems

- [Dependency Analyzer](c4/software-systems/dependency-analyzer.md)：比较 Maven dependency 状态与 Java bytecode，定位升级影响，并生成可离线浏览的分析报告。
