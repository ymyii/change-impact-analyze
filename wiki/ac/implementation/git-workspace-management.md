---
title: "Git Workspace Management Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Git Workspace Management

本页验收 [Git Workspace Management](../../implementation/git-workspace-management.md)。

## Functional

1. 场景：创建 detached side workspace
   - Given：local commit-ish 可 peel 为 commit，Git-relative 入口 path 在该 commit 中存在。
   - When：请求 baseline 或显式 target snapshot。
   - Then：
     - 创建 command-owned detached worktree。
     - 用户 current checkout 的 branch 与 worktree 不发生变化。

2. 场景：使用 current target
   - Given：target 未指定且 current workspace 为 dirty。
   - When：准备 target snapshot。
   - Then：
     - 未提交内容参与后续分析。
     - Dirty 状态在 Maven 执行前冻结。

## Failure

1. 场景：入口 path 逃逸
   - Given：入口或 Module 通过 symlink 指向 Git root 外。
   - When：workspace 验证 real path。
   - Then：
     - Snapshot preparation 失败。
     - 系统不扫描或清理 Git root 外路径。

## Non-Functional

- [ ] 当关闭 workspace manager 时，只移除同时匹配当前 owner UUID 与 marker 的 worktree / run directory，其他路径删除数量为 `0`。
