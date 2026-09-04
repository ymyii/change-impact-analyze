---
name: "Assess Upgrade Impact"
type: use-case
---

## Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 比较 baseline 与 target 的 Maven dependency upgrade，并获得静态 evidence 支持的业务 affected path、无路径结果或不确定状态。本 Use Case 从执行 `impact` 开始，到离线 Impact Report 完整发布或 command-level failure 结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Preconditions

- Analysis path 位于 local Git repository，且直接包含 readable `pom.xml`。
- Baseline 可解析为 local commit；显式 target 同样可解析，未指定 target 时使用 current workspace。
- Maven runtime 可用，`--java-home` 指向包含 `java`、`javac` 与 `rt.jar` 的完整 JDK 8。
- Output parent 可写，dependency 与 entrypoint selector 合法。

## Trigger

Software Developer 执行 `dependency-analyzer impact` 并指定 baseline 与 output。

## Main Success Scenario

1. [Command Control](../c4/components/dependency-analyzer-cli-command-control.md) 验证参数、Git、POM、JDK、Maven 与 output，确认可以启动分析。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 准备 baseline 与 target，并在两侧解析由入口 POM 限定的 Maven 范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 编译 target，并通过 [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md) 采集两侧 dependency evidence。
4. [Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md) 选择版本发生变化的 artifact pair，结合表示依赖变化点的 ChangePoint、Call Graph 与引用证据生成 Module results。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 原子发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)，保留 status、limitation 与 affected paths。
6. Software Developer 在本地浏览器打开 report 并解释结果边界。

## Extensions

- 主流程步骤 `1`，分支 `a`：参数或 Preflight contract 不满足。
  1. Command 返回 exit code `1`。
  2. 系统不启动 pipeline，也不替换既有 Report。
- 主流程步骤 `2`，分支 `a`：未指定 target。
  1. 系统直接分析 current workspace。
  2. Report 记录 dirty 状态。
- 主流程步骤 `4`，分支 `a`：单个 JAR pair 或 Module 分析失败，但 command boundary 仍可继续。
  1. 系统保留 handled failure 或 inconclusive status。
  2. 系统不把缺失 evidence 表示为成功路径。
- 主流程步骤 `5`，分支 `a`：Staging、schema 或 publication 校验失败。
  1. 系统不提交不完整 Report。
  2. Command 返回 command-level failure。

## Success Guarantee

输出包含每个相关 Module 的 dependency changes、ChangePoint、affected path 或明确的无路径/不确定状态；用户 checkout 不被切换，报告可以通过 `file://` 离线打开。

## Acceptance Criteria

### Functional

1. 比较两个 local ref
   - Given: Baseline 与 target 均可解析为 local commit，入口 POM、Maven 与完整 JDK 8 可用。
   - When: Software Developer 执行 `impact` 并指定输出文件。
   - Then:
     - Analyzer 不切换用户 current checkout。
     - 输出包含每个相关 Module 的 dependency changes、ChangePoint、affected path 或明确的无路径/不确定状态。

2. 比较 baseline 与 current workspace
   - Given: Baseline 可解析且未指定 target，current workspace 含未提交修改。
   - When: Software Developer 执行 `impact`。
   - Then:
     - Target 分析包含 current workspace 的未提交修改。
     - Report 标识 target workspace 状态。

### Failure

1. Preflight 阻断分析
   - Given: JDK、Maven、入口 POM、Git ref 或 selector 不满足 command contract。
   - When: Software Developer 执行 `impact`。
   - Then:
     - Command 返回 exit code `1`。
     - 既有输出 Report 不被替换。

### Non-Functional

- [ ] Analyzer 在 baseline、target、options 与 dependency artifact 完全相同时，并行度为 `1` 与大于 `1` 的两次运行产生完全相同顺序的 Module、ChangePoint 和代表路径 identity。
