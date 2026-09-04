---
name: "Apache Maven"
type: software-system
relations: []
---

## Overview

Apache Maven 在用户项目的实际 settings、profile 和 repository 语境中执行 bounded build，并承载 Dependency Evidence Plugin goals。

## Responsibilities

- 解析 active Maven project model 与 dependency graph。
- 执行 Analyzer 规划的 compile 和 evidence collection goals。

## Boundaries

该页面只表示 Analyzer 直接启动的外部 build system；不包含 Dependency Analyzer 自有 Plugin，也不代表 Maven Central 或其他 artifact repository。
