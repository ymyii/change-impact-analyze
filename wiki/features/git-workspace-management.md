---
title: "Git Workspace Management"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Workspace 管理是分析流水线的第二阶段"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git 命令执行必须遵守跨平台命令解析规则"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java"
    desc: "Workspace 管理核心，worktree 创建和清理"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/GitCommandRunner.java"
    desc: "Git 命令执行器"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/GitCommandResult.java"
    desc: "Git 命令执行结果"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceResult.java"
    desc: "Workspace 准备结果"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSideInfo.java"
    desc: "单个 side 的 workspace 信息"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSide.java"
    desc: "Workspace side 枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/workspace/WorkspacePrepareException.java"
    desc: "Workspace 准备异常"
  - path: "src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java"
    desc: "跨平台命令解析工具"
---

# Feature: Git Workspace Management

## Summary

Git Workspace Management 安全准备 baseline、target 和 current workspace 三类分析输入。baseline 和显式 target commit 使用 git worktree 隔离，current workspace 保持用户当前目录和未提交变更。

## Design Decisions

- baseline 和显式 target 使用 `git worktree add --detach` 创建临时目录，避免 checkout 污染用户工作区。
- current workspace 不执行 checkout、不 stash，使用户未提交变更可以作为 target 参与分析。
- project 可以指向 git repository 子目录；worktree 创建在仓库根后再追加相对路径，保证多模块或子项目分析路径一致。
- Git 命令统一经过 `CommandResolver.resolve()`，确保 Windows 上可解析 `git.cmd` 或 `git.exe`。

## Actors / Entrypoints

- CLI pipeline 通过 `new WorkspaceManager(projectPath, diagnostics)` 创建 workspace 管理器。
- `WorkspaceManager.prepare(baselineRef, targetRef)` 是准备 baseline/target 输入的功能入口。
- `WorkspaceManager.close()` 是临时 worktree 清理入口，通常由 try-with-resources 调用。

## Behavior Contract

- project 必须位于 git repository 内；否则构造阶段抛出 `IllegalStateException`。
- baseline ref 必须解析为 commit，并始终创建临时 detached worktree。
- target ref 存在时必须解析为 commit，并创建临时 detached worktree。
- target ref 不存在时 target side 使用 current workspace，且 `whetherTemporary=false`。
- 子目录 project 在 baseline 和 target worktree 中必须解析到同一相对子目录。
- 正常和异常退出时都尽力移除由本次准备创建的临时 worktree 和父目录。
- workspace 准备失败时抛出包含 side、commit、path、exitCode 和 stderr 的 `WorkspacePrepareException`。

## Core Flow

1. 构造 `WorkspaceManager` 时运行 `git rev-parse --show-toplevel` 解析 repository root。
2. 计算 project 相对 repository root 的 `relativePath`。
3. `prepare()` 解析 baseline commit，并创建 baseline worktree。
4. target ref 存在时解析 target commit 并创建 target worktree。
5. target ref 不存在时将 target side 指向 current project 目录。
6. 返回 `WorkspaceResult`，包含 baseline 与 target 的 `WorkspaceSideInfo`。
7. `close()` 遍历临时 workspace，执行清理。

## Acceptance Criteria

### Functional

- Given project 在 git repository 内，When 创建 `WorkspaceManager`，Then 能解析 repository root 和 project relative path。
- Given baseline ref 合法，When 调用 `prepare()`，Then baseline side 指向 detached temporary worktree。
- Given target ref 为空，When 调用 `prepare()`，Then target side 指向 current workspace 且不 checkout、不 stash。
- Given target ref 合法，When 调用 `prepare()`，Then target side 指向 detached temporary worktree。
- Given project 是 repository 子目录，When 创建 worktree，Then side path 追加同一子目录 relative path。
- Given worktree 创建失败，When `prepare()` 抛出异常，Then 异常包含失败 side 和 git stderr。

### Non-Functional

- [ ] 用户当前工作区不得被 checkout、stash 或删除。
- [ ] 临时 workspace 清理必须 best-effort，不掩盖原始分析失败。
- [ ] Git 命令执行必须跨平台，遵守 `CommandResolver` 规则。

## Edge Cases

- project 不在 git repository 内时构造失败，不进入后续分析。
- baseline 或 target ref 不存在时，失败在 workspace 阶段暴露。
- 清理阶段可能遇到已经被外部删除的临时目录；实现应保持 best-effort。

## Implementation Boundaries

- Workspace 模块只负责 git 输入准备和清理，不执行 Maven build 或依赖分析。
- Git 命令执行封装在 `GitCommandRunner`；跨平台命令转换由 `CommandResolver` 统一负责。
- Workspace 输出通过 `WorkspaceResult` 传递给后续 build 和 dependency 阶段。
