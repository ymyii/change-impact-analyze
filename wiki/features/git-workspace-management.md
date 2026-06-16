---
title: "Git Workspace Management"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Workspace 管理是分析流水线的第二阶段"
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
---

# Feature: Git Workspace Management

## Summary

安全准备 baseline、target、current workspace 三类分析输入。使用 git worktree 隔离 baseline 和 target commit，避免污染用户工作区。current workspace 不 checkout、不 stash，未提交变更参与分析。

## Behavior

- baseline 使用 `git worktree add --detach` 创建临时目录。
- 指定 `--target` 时，target 也使用 `git worktree` 创建临时目录。
- 未指定 `--target` 时，target side 指向 current workspace（非临时）。
- current workspace 不执行 checkout、不执行 stash。
- 分析结束后（正常或异常）尽力清理临时 worktree。
- 实现 `AutoCloseable`，支持 try-with-resources 自动清理。

## Flow

1. `WorkspaceManager` 接收 project 路径和 DiagnosticCollector。
2. `prepare(baselineRef, targetRef)` 解析 commit、创建 worktree。
3. 返回 `WorkspaceResult`，包含 baseline 和 target 的 `WorkspaceSideInfo`。
4. `close()` 清理所有临时 worktree 和父目录。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceManager.java` - worktree 创建、commit 解析、清理逻辑。
- `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandRunner.java` - 封装 `git` 命令执行，返回 exit code/stdout/stderr。
- `src/main/java/io/github/changeimpact/analyze/workspace/GitCommandResult.java` - Git 命令执行结果。
- `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceResult.java` - baseline + target 的 workspace 信息。
- `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSideInfo.java` - 单个 side 的 path/commit/whetherTemporary。
- `src/main/java/io/github/changeimpact/analyze/workspace/WorkspaceSide.java` - 枚举：BASELINE、TARGET、CURRENT。
- `src/main/java/io/github/changeimpact/analyze/workspace/WorkspacePrepareException.java` - 包含 side/commit/path/exitCode/stderr 的异常。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/workspace/` 下的测试类。
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/workspace/WorkspaceManagerIT.java`
- baseline + current workspace 模式可准备 workspace。
- baseline + target commit 模式可准备 workspace。
- current workspace 不被 checkout/stash。
- commit 不存在时失败可诊断。
- 正常结束后临时 worktree 被清理。
