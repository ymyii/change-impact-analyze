---
title: "Git Workspace Management"
type: implementation
---

# Implementation: Git Workspace Management

## Background

`impact` 与 `tree diff` 需要比较两个 side，`tree analyze` 需要 current checkout 或 local-ref snapshot。Workspace 必须避免切换用户 checkout、保留 Git-relative 入口路径，并只清理当前 command 自己创建的资源。

## Overview

该机制把 local commit-ish、current workspace、detached worktree 和 command run directory 统一为有 owner identity 的 snapshot lifecycle。它服务三个 CLI Feature，同时保持 target dirty metadata、symlink boundary 和 owned cleanup 语义。

## Core Flow

1. 解析 repository root、入口 path 和 local commit-ish；不执行 fetch，也不把 remote name 当作隐式 ref 来源。
2. Baseline 与显式 target 创建 command-owned detached worktree；省略 target 时直接使用 current workspace。
3. 将用户入口 path 转换为 Git-relative path，并在每个 side 重新验证 directory 与 readable `pom.xml`。
4. 在 Maven 执行前记录 current workspace dirty 状态，并为每个 side 创建隔离的 command cache。
5. `close()` 只移除匹配 owner identity 的 worktree 和 run directory；用户 workspace 与非 owned 路径不参与清理。

## Key Mechanisms

- Owner identity 使用 command-scoped UUID，resource path 与 owner marker 必须同时匹配后才能回收。
- Symlink 逃逸、Git root 外 module 和 side 中缺失的入口 POM 直接拒绝，禁止 repository-wide fallback。
- Current workspace 保留未提交修改参与分析；显式 target 始终从 commit 创建 detached workspace。
- Git process 统一遵守 [Process Command Resolution](../rules/process-command-resolution.md)，避免平台相关 executable lookup 分叉。

## Design Decisions

- None.

## Acceptance

完整验收条件见 [Git Workspace Management Acceptance](../acceptance/implementation/git-workspace-management.md)。
