---
name: "Git"
type: software-system
relations: []
---

## Overview

Git 为 Dependency Analyzer 提供 local repository、commit-ish、current workspace 状态与 detached worktree 能力。

## Responsibilities

- 解析本地 ref 与 commit identity。
- 暴露 current workspace 状态并管理独立 worktree。

## Boundaries

该页面只表示 Analyzer 直接调用的外部版本控制系统；不包含远程托管平台、network fetch 或用户 repository 内容的所有权。
