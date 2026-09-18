---
name: "Compare Dependency Trees"
type: use-case-realization
---

## Realized Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 比较 baseline 与 target 的依赖出现位置、scope、directness、resolution source 和 Reactor structure。本页面从执行 `tree diff` 开始，到双侧差异报告增量发布或命令级失败结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Participating C4 Elements

- [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md)：校验双侧输入并协调差异分析。
- [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md)：分别发布 baseline 与 target 的结构化证据。
- [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)：展示双侧 metadata、Module 状态和 dependency diff。
- [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md)：独立准备两侧 snapshot 与 [Bounded Maven Scope](../glossary/bounded-maven-scope.md)。
- [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md)：配对结构与依赖出现位置，并分类变化。
- [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md)：按 Reactor 增量发布差异报告。

## Preconditions

- Baseline 可解析为本地 commit。
- 显式 target 可解析为本地 commit；未指定 target 时使用 current workspace。
- 同一 Git-relative entry path 在待分析 side 中存在可读 POM。
- Maven runtime、项目依赖来源与 output directory 可用。

## Trigger

Software Developer 执行 `dependency-analyzer tree diff`，并指定 baseline 与 output directory。

## Main Success Scenario

1. [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md) 验证双侧输入、runtime capability 与 output boundary。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 独立准备 baseline 与 target，并计算各自的 Maven 分析范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 在两侧采集保留依赖出现位置的 dependency 与 classpath 证据。
4. [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md) 配对 Reactor 与 Module identity，再分类 dependency、version、scope、directness 与 chain 变化。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 按 Reactor 增量发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)。
6. Software Developer 查看双侧 metadata、summary 与逐项 diff。

## Alternative Flows

- 主流程步骤 `2`，分支 `a`：未指定 target。
  1. Workspace Scope 使用 current workspace 作为 target，并记录 commit 与 dirty 状态。
  2. 流程重新汇入主流程步骤 `3`。

## Exception Flows

- 主流程步骤 `4`，分支 `a`：两侧 Reactor 或 Module identity 不一致。
  1. Dependency Trees 将对应范围标记为 `STRUCTURE_MISMATCH`，不推导整 Module 全量新增或删除。
  2. Software Developer 在报告中看到不可比较状态。
  3. 其他可比较范围继续至主流程步骤 `5`；没有可比较 Module 时流程终止为 `FAILED`。
- 主流程步骤 `5`，分支 `a`：某个 Reactor 无法完成 analysis 或 publication。
  1. Report Publication 保留其他已完整发布的 Reactor 页面，并拒绝暴露失败 Reactor 的半成品。
  2. Software Developer 看到 `COMPLETED_WITH_ISSUES` 或 `FAILED` 及 exit code `2`。
  3. 本次流程终止；修复失败原因后可重新触发。

## Minimal Guarantee

用户 current checkout 不被切换；结构不兼容不被误报为依赖全量变化；已完整发布的 Reactor 页面不因后续失败变成半成品。

## Success Guarantee

结构兼容范围内的每条 diff 保留双侧 chain、scope、directness 与 resolution source；结构不兼容范围只报告明确 mismatch。
