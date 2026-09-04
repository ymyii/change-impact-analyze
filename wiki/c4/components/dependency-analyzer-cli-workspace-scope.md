---
name: "Workspace Scope"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/software-systems/git]]"
    description: "解析代码库状态并创建隔离的快照 worktree。"
    mechanism: "Git CLI"
  - target: "[[c4/components/dependency-analyzer-cli-maven-runtime]]"
    description: "提供有界的 aggregator、owned-leaf 或 standalone 执行计划。"
---

## Overview

Workspace Scope 将 current workspace 与 local commit-ish 转换为受 owner identity 约束的 snapshot，并以入口 POM 计算有界的 active Maven reactor。

## Responsibilities

- 保留用户 checkout，按需创建 baseline 或 target detached worktree。
- 在每个 side 重新验证 Git-relative entry path、readable POM、symlink 与 repository boundary。
- 规划 `FULL_REACTOR`、`SINGLE_MODULE` 或 `STANDALONE` scope，不枚举无关 POM。

## Interfaces

- Workspace preparation：输入 repository path、side 和可选 local ref；输出带 owner identity、commit 与 dirty metadata 的 snapshot。
- Reactor inventory：输入 entry POM 与 Maven activation context；输出 immutable Module ownership 与 execution mode。

## State and Data

Owned worktree、run directory 和 marker 必须同时匹配 owner identity 才能清理；current workspace 的未提交修改只在其作为 target 时参与分析。

## Boundaries

该 Component 负责 snapshot 与 scope identity；不执行 remote fetch、不切换用户 checkout、不解析 dependency evidence，也不发现非祖先 aggregator。
