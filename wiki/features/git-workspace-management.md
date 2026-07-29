---
title: "Git Workspace Management"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact workspace 与 tree snapshot 的架构边界"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree repository snapshot 和 Git file set"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git process 的跨平台约束"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/workspace/WorkspaceManager.java"
    desc: "impact baseline/target/current workspace"
  - path: "src/main/java/io/github/dependencyanalysis/workspace/GitCommandRunner.java"
    desc: "impact Git command runner"
  - path: "src/main/java/io/github/dependencyanalysis/tree/GitSnapshotProvider.java"
    desc: "tree current checkout/local-ref repository snapshot"
  - path: "src/main/java/io/github/dependencyanalysis/tree/ReactorInventoryBuilder.java"
    desc: "tracked/non-ignored untracked POM 与 submodule filtering"
---

# Feature: Git Workspace Management

## Summary

Git Workspace Management 为 `impact` 准备 baseline/target project workspace，为 `tree` 准备 current checkout 或 local-ref repository snapshot。临时 detached worktree 由 owner cleanup；current checkout 保留 dirty 与 eligible untracked POM。

## Design Decisions

- `impact` baseline 和显式 target 使用 detached worktree；未传 target 时使用 current project directory。
- `tree --ref` 使用 detached repository worktree；未传 ref 时直接分析 current checkout。
- `tree --path` 先解析真实 directory 和所属 Git root，保存 Git-root-relative analysis path；detached snapshot 必须存在同一路径。
- Tree inventory 以 relative analysis path 计算 direct-match `requestedPoms`；命中 reactor root 时进入 full-reactor mode，否则由 collector 计算 bounded dependency closure。
- Local ref 只通过 `rev-parse --verify <ref>^{commit}` 解析，不 fetch。
- Git file discovery 使用 tracked + non-ignored untracked，并排除 stage mode `160000` Git submodule path。

## Actors / Entrypoints

- `impact` preflight 调用 `WorkspaceManager.prepare(baseline, target)`。
- `tree` preflight 调用 `GitSnapshotProvider.open(path, ref)`。

## Behavior Contract

- Project 可位于 Git root 子目录；impact worktree 保持同一 relative project path。
- Current tree snapshot metadata 包含 repository root、branch、commit 和 dirty flag。
- Tree snapshot metadata 同时包含 user input path、resolved Git root 与 relative analysis path。
- Local-ref snapshot 只包含对应 commit，不包含 current dirty/untracked 文件。
- 成功、失败和 command close 路径都 best-effort 执行 `git worktree remove --force`。

## Core Flow

- 将 input path `toRealPath()`，解析 Git root、relative analysis path 和 commit。
- 对 local ref 创建 detached temporary worktree；current checkout 不 checkout/stash。
- 将 relative analysis path 映射到 detached worktree；路径不存在时在 command Preflight 阻断。
- Pipeline 复用 prepared path。
- Owner close 时清理 worktree；Git remove 失败时清理 temporary directory。

## Acceptance Criteria

### Functional

- Given current dirty/untracked eligible POM；When tree inventory；Then POM 可被发现。
- Given local ref；When snapshot；Then report commit 等于 ref resolved commit。
- Given Git submodule 内 POM；When discovery；Then POM 被排除。

### Non-Functional

- [ ] 不修改 current branch、index 或 tracked file。
- [ ] Worktree cleanup 对成功和 failure path 生效。

## Edge Cases

- Invalid local ref、非 Git directory 或 Git command failure 在 preflight 阻断相应 scope。
- Input path 不存在、不是 directory、越出 resolved Git root 或在 local ref snapshot 中不存在时阻断 command。
- External module normalized path 越出 snapshot root 时 reactor model check failure。

## Implementation Boundaries

- Workspace/snapshot 只负责 Git isolation 和 metadata，不执行 Maven 或 report rendering。
