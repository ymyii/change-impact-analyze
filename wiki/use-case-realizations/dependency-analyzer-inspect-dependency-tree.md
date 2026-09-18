---
name: "Inspect Dependency Tree"
type: use-case-realization
---

## Realized Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 查看 current workspace 或本地 ref 所限定 Maven 范围内的 resolved dependency tree、版本来源与 class conflict。本页面从执行 `tree analyze` 开始，到每个可完成 Reactor 的离线页面发布或命令级失败结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Participating C4 Elements

- [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md)：校验命令并协调单侧依赖分析。
- [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md)：发布当前 Maven session 的依赖与 classpath 证据。
- [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)：展示 Repository、Reactor、Module、依赖出现位置和冲突信息。
- [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md)：选择 current workspace 或本地 ref snapshot，并计算 [Bounded Maven Scope](../glossary/bounded-maven-scope.md)。
- [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md)：保留依赖出现位置与解析事实，并识别 class conflict。
- [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md)：按 Reactor 增量发布完整页面。

## Preconditions

- 分析路径位于本地 Git repository，且直接包含可读的 `pom.xml`。
- 可选 `--ref` 可解析为本地 commit，且同一 Git-relative path 在该 commit 中存在。
- Maven runtime、settings、项目依赖来源和 output directory 可用。

## Trigger

Software Developer 执行 `dependency-analyzer tree analyze`，并指定 output directory。

## Main Success Scenario

1. [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md) 验证命令、runtime capability 与 output boundary。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 选择 current workspace 或 detached local-ref snapshot，并建立受入口 POM 限定的 Maven 分析范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 执行所需 compile 与 evidence goals。
4. [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md) 保留 occurrence、requested/resolved version、scope、directness、resolution source 与 class conflict。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 为已完成 Reactor 增量发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)。
6. Software Developer 通过 Repository、Reactor 与 Module navigation 检查结果。

## Alternative Flows

- 主流程步骤 `2`，分支 `a`：入口 POM 是被 active ancestor aggregator 拥有的 leaf。
  1. Workspace Scope 选择最外层 matching aggregator，并生成 `-pl <path> -am` 执行计划。
  2. Report 范围仍只纳入入口 Module，流程重新汇入主流程步骤 `3`。
- 主流程步骤 `2`，分支 `b`：入口 POM 没有 matching ancestor aggregator。
  1. Workspace Scope 使用 `STANDALONE` 执行计划，不扫描 repository 中的其他 POM。
  2. 流程重新汇入主流程步骤 `3`。

## Exception Flows

- 主流程步骤 `1`，分支 `a`：Evidence runtime 缺少完整 schema capability。
  1. Dependency Analyzer CLI 在依赖分析前阻断命令。
  2. Software Developer 看到 exit code `1` 与 Preflight 原因，既有报告不被替换。
  3. 本次流程终止；修复 runtime 后可重新触发。
- 主流程步骤 `4`，分支 `a`：个别 Module 失败或 classpath 不完整。
  1. Dependency Trees 将 Reactor 标记为 `FAILED` 或 `DEGRADED`，并保留可行动原因。
  2. Software Developer 在 Index 中看到 issue，完整 Module 仍可浏览。
  3. 流程继续至主流程步骤 `5`，只发布已经完成的 Reactor 页面。

## Minimal Guarantee

用户 current checkout 不被切换；Preflight 失败不替换既有报告；增量发布只暴露已经完整写入的 Reactor 页面。

## Success Guarantee

报告只包含选定 Maven 分析范围，并让每个 dependency occurrence 保留解释 Maven resolution 与 classpath 所需的稳定证据。
