---
name: "Assess Upgrade Impact"
type: use-case-realization
---

## Realized Goal and Scope

[Software Developer](../c4/actors/software-developer.md) 比较 Maven 依赖升级前后的解析结果与 Java bytecode，获得静态证据支持的受影响业务入口和调用路径。本页面从执行 `impact` 开始，到离线 Impact Report 完整发布或命令级失败结束。

## Primary Actor and Supporting Actors

- Primary Actor: [Software Developer](../c4/actors/software-developer.md)
- Supporting Actors: [Git](../c4/software-systems/git.md)、[Apache Maven](../c4/software-systems/apache-maven.md)。

## Participating C4 Elements

- [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md)：校验输入，协调工作区、构建、分析和发布生命周期。
- [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md)：在目标 Maven session 中发布结构化依赖与 classpath 证据。
- [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)：向开发者展示冻结的分析结果、限制和调用路径。
- [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md)：准备 baseline 与 target，并为每侧计算 [Bounded Maven Scope](../glossary/bounded-maven-scope.md)。
- [Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md)：将依赖变化绑定为查询起点，并追踪到业务入口。
- [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md)：验证并原子发布 Impact Report。

## Preconditions

- 分析路径位于本地 Git repository，且直接包含可读的 `pom.xml`。
- Baseline 可解析为本地 commit；显式 target 同样可解析，未指定 target 时使用 current workspace。
- Analyzer 使用 Java 17 启动，`--java-home` 指向包含 `java`、`javac` 与 `rt.jar` 的完整 JDK 8。
- Maven runtime、依赖来源与可写 output directory 可用。

## Trigger

Software Developer 执行 `dependency-analyzer impact`，并指定 baseline 与 output directory。

## Main Success Scenario

1. [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md) 验证参数、Git、入口 POM、JDK、Maven 与 output boundary。
2. [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 准备 baseline 与 target snapshot，并计算两侧 Maven 分析范围。
3. [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 编译 target，并通过 [Dependency Evidence Plugin](../c4/containers/dependency-analyzer-evidence-plugin.md) 采集两侧依赖证据。
4. [Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md) 选择版本变化的 artifact pair，把有效的 [Change Point](../glossary/change-point.md) 与结构证据绑定到 target Call Graph，并生成 Module results。
5. [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 原子发布 [Offline Report](../c4/containers/dependency-analyzer-offline-report.md)，保留状态、限制和 affected paths。
6. Software Developer 在 output directory 的 `index.html` 打开报告并解释结果边界；Module 页面和 `*-impact-data` 辅助文件位于同一目录的 `modules/` 下。

## Alternative Flows

- 主流程步骤 `2`，分支 `a`：未指定 target。
  1. Workspace Scope 使用 current workspace 作为 target，并记录 commit 与 dirty 状态。
  2. 流程重新汇入主流程步骤 `3`。

## Exception Flows

- 主流程步骤 `1`，分支 `a`：参数或 Preflight contract 不满足。
  1. Dependency Analyzer CLI 返回 exit code `1`，不启动分析 pipeline。
  2. Software Developer 在 Console 中看到阻断原因，既有报告保持可用。
  3. 本次流程终止；修正输入后可重新触发。
- 主流程步骤 `4`，分支 `a`：单个 JAR pair 或 Module 分析失败，但命令仍可继续。
  1. Impact Tracing 保留 `FAILED`、`PARTIAL_SUCCESS` 或 `INCONCLUSIVE` 及其原因。
  2. Software Developer 在报告中看到可用结果和缺失覆盖范围。
  3. 流程继续至主流程步骤 `5`，发布带明确限制的报告。
- 主流程步骤 `5`，分支 `a`：staging、schema 或 publication 校验失败。
  1. Report Publication 拒绝提交不完整报告。
  2. Software Developer 收到命令级失败，既有报告不被半成品替换。
  3. 本次流程终止。

## Minimal Guarantee

用户 current checkout 不被切换；未通过完整性校验的 staging 内容不替换既有报告；命令只清理自身拥有的临时资源。

## Success Guarantee

报告包含每个相关 Module 的依赖变化、Change Point、affected path 或明确的无路径与不确定状态，并可通过 `file://` 离线打开。
