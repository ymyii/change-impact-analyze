---
name: "Compare Dependency Trees"
type: use-case
---

## Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 比较 baseline 与 target 的依赖出现位置、scope、directness、resolution source 和 Reactor structure。本 Use Case 从执行 `tree diff` 开始，到双侧差异报告增量发布或 command failure 结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Preconditions

- Baseline 可 peel 为 local commit。
- 显式 target 可 peel 为 local commit；未指定 target 时使用 current workspace。
- 同一 Git-relative entry path 在待分析 side 中存在 readable POM。
- Maven runtime 与 output directory 可用。

## Trigger

Software Developer 执行 `dependency-analyzer tree diff` 并指定 baseline 与 output directory。

## Main Success Scenario

1. [Command Control](../c4/components/dependency-analyzer-cli-command-control.md) 验证双侧输入与 runtime capability。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 独立准备 baseline 与 target，并计算各自有界范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 在两侧采集 occurrence-aware dependency 与 classpath evidence。
4. [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md) 配对 Reactor/Module identity，再分类 dependency、version、scope、directness 与 chain 变化。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 按 Reactor 增量发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)。
6. Software Developer 查看双侧 metadata、summary 与逐项 diff。

## Extensions

- 主流程步骤 `2`，分支 `a`：未指定 target。
  1. 系统直接使用 current workspace。
  2. 系统在 Maven execution 前记录 dirty 状态。
- 主流程步骤 `4`，分支 `a`：两侧 Reactor 或 Module identity 不一致。
  1. 系统将对应 scope 标记为 `STRUCTURE_MISMATCH`。
  2. 系统不把缺失结构转换为全量 dependency 新增或删除。
- 主流程步骤 `5`，分支 `a`：某个 Reactor 无法完成 analysis 或 publication。
  1. 已完整发布的其他 Reactor 页面保持可浏览。
  2. 失败 Reactor 保留状态且不暴露半成品。

## Success Guarantee

结构兼容范围内的每条 diff 保留双侧 chain、scope、directness 与 resolution source；结构不兼容范围只报告明确 mismatch。

## Acceptance Criteria

### Functional

1. 比较 baseline 与 current workspace
   - Given: Baseline 可 peel 为 local commit，target 未指定，current workspace 可能为 dirty。
   - When: Software Developer 执行 `tree diff`。
   - Then:
     - Target dependency evidence 来自 current workspace。
     - Report 记录 target dirty 状态，并按 occurrence identity 展示 added、removed 与 changed dependency。

2. 比较两个 local commit
   - Given: Baseline 与 target 均可 peel 为 local commit，且 Reactor/Module structure 一致。
   - When: Software Developer 执行 `tree diff`。
   - Then:
     - 每条 diff 保留双侧 chain、scope、directness 与 resolution source。
     - 已完成 Reactor 可独立打开其离线页面。

### Failure

1. 两侧 structure 不一致
   - Given: Baseline 与 target 的 Reactor 或 Module identity 不一致。
   - When: 系统配对两侧 scope。
   - Then:
     - 对应范围标记为 `STRUCTURE_MISMATCH`。
     - 系统不把整个缺失结构转换为 dependency 全量新增或删除。

### Non-Functional

- [ ] Dependency Trees 在相同 occurrence 以不同输入遍历顺序提供时，输出 diff classification、PathKey pairing 和 shard order 完全相同。
