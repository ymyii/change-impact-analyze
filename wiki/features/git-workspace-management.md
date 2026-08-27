---
title: "Git Workspace Management"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact workspace 与 tree snapshot 的架构边界"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree analyze repository snapshot和Git file set"
  - path: "wiki/features/repository-dependency-tree-diff.md"
    desc: "tree diff baseline/target workspace与dirty metadata合同"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "impact command temp 内的 dependency evidence cache"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git process 的跨平台约束"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/workspace/WorkspaceManager.java"
    desc: "impact与tree diff的baseline/target/current workspace"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/workspace/GitCommandRunner.java"
    desc: "impact Git command runner"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/GitSnapshotProvider.java"
    desc: "tree current checkout/local-ref repository snapshot"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/CommandRunDirectory.java"
    desc: "subcommand UUID run、owner marker、file lock 和 stale cleanup"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/ReportCache.java"
    desc: "owned temporary run下的report-cache安全边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorInventoryBuilder.java"
    desc: "入口module graph的Git边界、ignored与submodule eligibility"
---

# Feature: Git Workspace Management

## Summary

Git Workspace Management为`impact`与`tree diff`准备baseline/target project workspace，为`tree analyze`准备current checkout或local-ref repository snapshot。Detached worktree和command temporary files位于命令族独立的config subtree；current checkout保留dirty与eligible untracked POM。

## Design Decisions

- None.

## Actors / Entrypoints

- `impact` preflight 创建 `CommandRunDirectory("impact")`，再调用 `WorkspaceManager.prepare(baseline, target)`。
- `tree analyze` preflight创建`CommandRunDirectory("tree")`，再调用`GitSnapshotProvider.open(path, ref, workspaceDirectory)`。
- `tree diff`创建同一`tree`命令族run，再调用`WorkspaceManager.prepare(baseline, target)`；不创建`tree-diff`配置root。

## Behavior Contract

- `impact`的baseline和显式target使用detached worktree；省略target时使用current project directory。
- `tree analyze --ref`使用detached repository worktree；省略ref时使用current checkout。
- `tree diff`的baseline始终使用detached worktree；显式target同样使用detached worktree，省略target时使用current checkout。
- Project可位于Git root子目录；impact baseline/target worktree和tree local-ref snapshot都保持同一relative analysis path，且该路径必须存在directory与POM。
- Current tree snapshot或Tree Diff current target metadata包含repository root、branch、commit和dirty flag。
- Tree snapshot metadata 同时包含 user input path、resolved Git root 与 relative analysis path。
- Local-ref snapshot 只包含对应 commit，不包含 current dirty/untracked 文件。
- Current checkout入口和active Module POM可为tracked或non-ignored untracked文件；ignored POM、Git stage mode `160000` submodule内POM及解析后逃逸Git root的symbolic link均被拒绝。
- 成功、失败和 command close 路径都 best-effort 执行 `git worktree remove --force`。
- Config layout 固定为：

```text
<config-dir>/
  runtime/
  locks/
  impact/workspaces/<run-id>/
  impact/tmp/<run-id>/
    dependency-evidence/
      baseline/<nonce>/
      target/<nonce>/
    report-cache/
  tree/workspaces/<run-id>/
  tree/tmp/<run-id>/
    report-cache/
```

## Core Flow

- 将 input path `toRealPath()`，解析 Git root、relative analysis path 和 commit。
- 创建 command run、owner marker 和 active lock；对 local ref 在 `workspaces/<run-id>` 内创建 detached worktree，current checkout 不 checkout/stash。
- 将 relative analysis path 映射到 detached worktree；路径不存在时在 command Preflight 阻断。
- Pipeline 复用 prepared path。
- Current target的commit与dirty flag在Maven执行前采集；Analyzer不删除、恢复或stash用户workspace，Maven compile产生的`target/`可保留且不属于dependency evidence cache。
- Impact Dependency Evidence只写当前run的`dependency-evidence/{baseline|target}/<nonce>`；单次Maven调用完成后删除nonce，不写baseline、target或current source directory。
- Report fragment使用stable-hash filename，先写temporary file，再atomic rename并写schema complete marker；未经验证的Module/reactor名称不进入filename。Tree Diff baseline/target projection使用独立cache namespace。
- Report发布成功或任一failure后显式删除`report-cache`。删除只允许当前owned UUID temporary directory下已验证的cache child；symbolic link与越界path拒绝。错误schema、run ID、command或complete marker使fragment读取/发布fail-fast。
- 启动新run时只回收owner marker有效且可取得对应file lock的stale run；active lock、无有效marker目录和其他run保持不动。Workspace创建前执行`git worktree prune`清理已回收目录对应的Git metadata。
- Owner close 先清理 worktree，再删除当前 workspace/tmp run 和 lock。

## Acceptance Criteria

### Functional

- Given current dirty/non-ignored untracked入口或active module POM；When scope resolution；Then该POM可进入入口graph。
- Given local ref；When snapshot；Then report commit 等于 ref resolved commit。
- Given annotated tag；When用于impact、tree analyze或tree diff；Then统一通过`^{commit}`解析，不fetch且Report保存resolved commit。
- Given省略Tree Diff target且当前工作区dirty；When执行Maven采集；ThenReport保存采集前dirty状态，Analyzer不修改index或恢复workspace。
- Given Git submodule 内 POM；When discovery；Then POM 被排除。

### Non-Functional

- [ ] 不修改 current branch、index 或 tracked file。
- [ ] Worktree cleanup 对成功和 failure path 生效。
- [ ] 并发 run 不能互删；active locked run 不能被 stale recovery 回收。
- [ ] Maven 成功或失败后source repository均不出现dependency evidence JSON/GraphML中间产物。
- [ ] 并发`impact`/`tree` run拥有不同UUID cache；一个run的cleanup不能读取或删除另一个run。
- [ ] 显式cache cleanup失败作为command error；外层owner close仍回收整个run，进程异常退出由下次stale recovery回收。

## Edge Cases

- Invalid local ref、非 Git directory 或 Git command failure 在 preflight 阻断相应 scope。
- Input path 不存在、不是 directory、越出 resolved Git root 或在 local ref snapshot 中不存在时阻断 command。
- Active module normalized path越出snapshot root时scope preparation fail-fast，不扫描其他POM或回退。

## Implementation Boundaries

- Workspace/snapshot 只负责 Git isolation 和 metadata，不执行 Maven 或 report rendering。
