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

- `impact` baseline 和显式 target 使用 detached worktree；未传 target 时使用 current project directory。
- `tree analyze --ref`使用detached repository worktree；未传ref时直接分析current checkout。
- `tree diff` baseline总是detached worktree；显式target同样使用detached worktree，省略target时直接使用当前工作区。
- tree common `--path`先解析真实directory和所属Git root，保存Git-root-relative analysis path；每个detached snapshot必须存在同一路径。
- Tree与impact都把relative analysis path作为唯一入口scope；入口必须直接包含POM。Scope resolver只读取入口active graph和祖先aggregator，不枚举repository POM。
- Local ref 只通过 `rev-parse --verify <ref>^{commit}` 解析，不 fetch。
- Current checkout入口与active module POM可为tracked或non-ignored untracked；ignored POM、stage mode `160000` Git submodule内POM和symlink逃逸被拒绝。
- 每次 command 使用 UUID run directory、有效 owner marker 和 `<config>/locks` file lock；cleanup 只能删除当前 owned run。
- `impact` dependency evidence 只写 `impact/tmp/<run-id>/dependency-evidence/{baseline|target}/<nonce>`，不写 baseline、target 或 current source directory。
- `impact`与两个tree模式的Report中间数据只写当前`<command>/tmp/<run-id>/report-cache`。Tree Diff在同一cache内使用baseline/target稳定namespace，不跨run复用。Cache复用同一run ID、owner marker、active lock与stale recovery，不创建第二套root或锁。
- 当前工作区作为Tree Diff target时，commit与dirty flag必须在Maven执行前采集；Analyzer不删除、恢复或stash用户workspace，Maven正常build output可保留。
- Current target 的 Maven compile 仍可在对应 workspace 生成 `target/`；该 build output 不属于 dependency evidence cache。
- 启动时只回收 owner marker 有效且无法取得 active lock 的 stale run，随后执行 `git worktree prune` 清理对应 metadata；无 marker 目录和其他 run 不删除。

## Actors / Entrypoints

- `impact` preflight 创建 `CommandRunDirectory("impact")`，再调用 `WorkspaceManager.prepare(baseline, target)`。
- `tree analyze` preflight创建`CommandRunDirectory("tree")`，再调用`GitSnapshotProvider.open(path, ref, workspaceDirectory)`。
- `tree diff`创建同一`tree`命令族run，再调用`WorkspaceManager.prepare(baseline, target)`；不创建`tree-diff`配置root。

## Behavior Contract

- Project可位于Git root子目录；impact baseline/target worktree和tree local-ref snapshot都保持同一relative analysis path，且该路径必须存在directory与POM。
- Current tree snapshot或Tree Diff current target metadata包含repository root、branch、commit和dirty flag。
- Tree snapshot metadata 同时包含 user input path、resolved Git root 与 relative analysis path。
- Local-ref snapshot 只包含对应 commit，不包含 current dirty/untracked 文件。
- 成功、失败和 command close 路径都 best-effort 执行 `git worktree remove --force`。
- Config layout 固定为：

```text
<config-dir>/
  runtime/
  locks/
  impact/workspaces/<run-id>/
  impact/tmp/<run-id>/
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
- Dependency Analyzer 将 command tmp 作为 evidence cache parent，单次 Maven 调用完成后删除 nonce。
- Report fragment使用stable-hash filename，先写temporary file，再atomic rename并写schema complete marker；未经验证的Module/reactor名称不进入filename。Tree Diff baseline/target projection使用独立cache namespace。
- Report发布成功或任一failure后显式删除`report-cache`。删除只允许当前owned UUID temporary directory下已验证的cache child；symbolic link与越界path拒绝。错误schema、run ID、command或complete marker使fragment读取/发布fail-fast。
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
