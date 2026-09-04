---
name: "Report Publication"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/containers/dependency-analyzer-offline-report]]"
    description: "写入版本化 schema、callback shard 与静态用户界面资源。"
    mechanism: "Local filesystem"
---

## Overview

Report Publication 将冻结的 Impact、Tree Analyze 与 Tree Diff result 投影为各自 versioned schema，并在完整性边界内发布离线应用。

## Responsibilities

- 保持三套 schema 的领域语义，不让 browser 重新分类分析结果。
- 生成 script-safe JSON、固定 callback signature、HTML navigation 与 lazy source shard。
- 对 Impact 执行整体原子替换，对 Tree 以 Reactor 为增量完成单元。

## Interfaces

- Frozen report input：只接受已结束 Maven、Git、JAR 与 Call Graph session 的 immutable domain result。
- Publication boundary：staging 内容通过 marker、signature 与 path 检查后提交；失败时不暴露半成品入口。

## State and Data

Command report cache 可以暂存源码和 comparison evidence；成功或失败后清理，不跨 command 复用。已发布报告只包含允许的冻结 projection。

## Boundaries

该 Component 负责 projection 与 filesystem publication；不访问 live analysis session，不保存 credential、Console stack trace 或 settings 内容，也不合并会丢失语义的 schema。
