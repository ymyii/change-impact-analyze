---
name: "Software Developer"
type: actor
relations:
  - target: "[[c4/software-systems/dependency-analyzer]]"
    description: "执行依赖分析并查看离线报告。"
    mechanism: "CLI 与 file:// HTML"
---

## Overview

Software Developer 是在本地 Git repository 中维护 Maven 项目，并需要理解依赖解析结果或升级影响的开发者角色。

## Goals

- 在升级依赖前定位可能受影响的业务入口和调用路径。
- 查看由入口 POM 计算的 [Bounded Maven Scope](../../glossary/bounded-maven-scope.md)，以及其中实际解析的依赖、版本来源和类冲突。
- 比较两个 repository snapshot 的依赖出现位置与 Reactor 结构变化。

## Boundaries

该角色覆盖直接运行 Analyzer 并解释报告的开发者；不包含只审批项目但不操作工具的利益相关方，也不代表自动化外部系统。
