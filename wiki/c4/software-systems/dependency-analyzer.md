---
name: "Dependency Analyzer"
type: software-system
children:
  - target: "[[c4/containers/dependency-analyzer-cli]]"
  - target: "[[c4/containers/dependency-analyzer-evidence-plugin]]"
  - target: "[[c4/containers/dependency-analyzer-offline-report]]"
relations:
  - target: "[[c4/software-systems/git]]"
    description: "读取本地代码库状态并准备隔离的 commit worktree。"
    mechanism: "Git CLI"
  - target: "[[c4/software-systems/apache-maven]]"
    description: "执行有界项目构建并采集依赖证据。"
    mechanism: "CLI subprocess 与 Plugin goal invocation"
---

## Overview

Dependency Analyzer 帮助 [Software Developer](../actors/software-developer.md) 理解 Maven 依赖解析结果，并评估依赖升级对 Java 业务调用路径的静态影响。分析结果以明确状态和离线报告呈现，不把静态可达性解释为运行时故障证明。

## Responsibilities

- 将用户选择的 repository path 与 local ref 限制为可验证的 [Bounded Maven Scope](../../glossary/bounded-maven-scope.md)。
- 采集 dependency、classpath、bytecode 和 Call Graph evidence，并保留 [Coverage Limitation](../../glossary/coverage-limitation.md) 与失败状态。
- 发布无需 HTTP server 的 Impact、Tree Analyze 和 Tree Diff 报告。

## Container Diagram

```mermaid
C4Container
    title Container diagram for Dependency Analyzer
    System_Boundary(analyzer, "Dependency Analyzer") {
        Container(cli, "CLI", "Java 17 / Picocli", "协调命令、分析和发布")
        Container(plugin, "Evidence Plugin", "Maven Plugin", "在 Maven session 内采集结构化证据")
        Container(report, "Offline Report", "HTML / CSS / JavaScript", "展示冻结的分析结果")
    }
    Rel(cli, plugin, "请求依赖与 classpath 证据")
    Rel(cli, report, "写入离线报告数据与资源")
```

## Boundaries

系统拥有 Analyzer CLI、配套 Dependency Evidence Plugin 与生成的 Offline Report 应用；不拥有用户项目、Git、Apache Maven、Maven repository 或浏览器，也不执行远程 Git fetch。
