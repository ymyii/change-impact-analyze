---
name: "Inspect Dependency Tree"
type: use-case
---

## Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 查看 current workspace 或 local ref 所限定 Maven 范围内的 resolved dependency tree、版本来源与 class conflict。本 Use Case 从执行 `tree analyze` 开始，到每个可完成 Reactor 的离线页面发布或 command failure 结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Preconditions

- Analysis path 位于 local Git repository，且直接包含 readable `pom.xml`。
- 可选 `--ref` 能解析为 local commit，且同一 Git-relative path 在该 commit 中存在。
- Maven runtime、settings 与所需 project dependency 可用。
- Output directory 可创建或可写。

## Trigger

Software Developer 执行 `dependency-analyzer tree analyze` 并指定 output directory。

## Main Success Scenario

1. [Command Control](../c4/components/dependency-analyzer-cli-command-control.md) 验证 command、runtime capability 与 output boundary。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 选择 current workspace 或 detached local-ref snapshot，并建立由入口 POM 限定的范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 执行所需 compile 与 evidence goals。
4. [Dependency Trees](../c4/components/dependency-analyzer-cli-dependency-trees.md) 保留 occurrence、requested/resolved version、scope、directness、resolution source 与 class conflict。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 为已完成 Reactor 增量发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)。
6. Software Developer 通过 Repository、Reactor 与 Module navigation 检查结果。

## Extensions

- 主流程步骤 `2`，分支 `a`：Entry POM 是被 active ancestor aggregator 拥有的 leaf。
  1. 系统选择最外层 matching aggregator。
  2. 系统使用 `-pl <path> -am` 构建，但 Report 只纳入入口 Module。
- 主流程步骤 `2`，分支 `b`：Entry POM 没有 matching ancestor aggregator。
  1. 系统按 `STANDALONE` 执行。
  2. 系统不扫描 repository 中的其他 POM。
- 主流程步骤 `3`，分支 `a`：Evidence runtime 缺少完整 schema capability。
  1. Command 在 dependency analysis 前失败。
  2. 既有 Report 不被替换。
- 主流程步骤 `4`，分支 `a`：个别 Module 失败或 classpath 不完整。
  1. Reactor 保留 `FAILED` 或 `DEGRADED` 及可行动原因。
  2. 完整 Module 仍可发布。

## Success Guarantee

Report 只包含 selected bounded scope，并让每个 dependency occurrence 保留 Maven resolution 与 classpath 解释所需的稳定 evidence。

## Acceptance Criteria

### Functional

1. 分析 bounded Maven scope
   - Given: Entry path 指向 active aggregator、owned leaf 或 standalone POM。
   - When: Software Developer 执行 `tree analyze`。
   - Then:
     - Report 只包含 resolved bounded scope 中的 Reactor 与分析 Module。
     - 每个 dependency occurrence 保留 requested/resolved version、scope、directness 和 resolution source。

2. 分析 local ref
   - Given: `--ref` 可解析为 local commit，且同一 Git-relative path 在该 commit 中存在 readable POM。
   - When: Software Developer 执行 `tree analyze`。
   - Then:
     - 分析在 detached snapshot 中执行。
     - 用户 current checkout 不发生切换。

### Failure

1. Evidence capability 不完整
   - Given: 所选 Maven Dependency Plugin 或 evidence runtime 不能提供完整 schema capability。
   - When: Preflight 验证 `tree analyze`。
   - Then:
     - Command 在 dependency analysis 前失败。
     - 既有 Report 不被不完整结果替换。

### Non-Functional

- [ ] Offline Report 在一个 Reactor 完成 publication 时，该 Reactor 的 HTML 与全部引用 shard 均可在无 HTTP server 的 `file://` 环境加载。
