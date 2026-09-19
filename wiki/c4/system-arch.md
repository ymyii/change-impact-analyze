---
name: "Dependency Analyzer System Context"
type: system-context
children:
  - target: "[[c4/software-systems/dependency-analyzer]]"
---

## Overview

Dependency Analyzer 面向 Software Developer，读取本地 Git repository，并通过 Apache Maven 获取依赖证据，最后生成可离线浏览的升级影响与依赖树报告。

## System Context Diagram

```mermaid
C4Context
    title Dependency Analyzer system context
    Person(developer, "Software Developer", "选择代码库、版本与分析命令")
    System(analyzer, "Dependency Analyzer", "分析依赖变化并发布离线报告")
    System_Ext(git, "Git", "提供本地版本、工作区与隔离 worktree")
    System_Ext(maven, "Apache Maven", "解析项目并执行有界构建")
    Rel(developer, analyzer, "启动分析并查看报告")
    Rel(analyzer, git, "读取 ref、状态并创建 detached worktree")
    Rel(analyzer, maven, "执行有界构建并采集依赖证据")
```

## Boundaries

图示只覆盖 [Dependency Analyzer](software-systems/dependency-analyzer.md) 与其直接交互的角色和外部系统。CLI、Dependency Evidence Plugin、Offline Report 及其组件属于下级 C4 页面；远程 Git 服务、Maven repository、部署节点和浏览器不在本系统上下文中。
