---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "status、partial result 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Path、Structural Reference Path、SSA 与代码 evidence"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "Overall 与三个 Module pages 的持续完整性校验"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "impact HTML Index 与 Module pages"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AnalysisRunResult.java"
    desc: "run status、selected algorithm 与 metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisResult.java"
    desc: "Module detail result"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "独立 tree HTML renderer"
---

# Feature: Report Generator

## Summary

`impact` 只生成英文 offline HTML：用户 `--output` 是 Overall Index；同级 command-owned `<stem>-modules/` 为每个非 `SKIPPED` Module 保存 Module Index、Affected Call Chains、Dependency Changes 三页。Markdown renderer 与 legacy overload 已移除。`tree` 的 repository/reactor HTML contract 保持独立。

## Design Decisions

- Report只投影typed run/module/query结果；Algorithm、WALA ReflectionOptions、access decision与coverage reason不从Diagnostic/summary string反向解析。
- `ACCESS_REMAINS_VALID`是用户可见的非影响结果：进入Dependency Changes，但不伪造Affected Call Chain。
- `POTENTIALLY_INACCESSIBLE`与Structural Reference只描述potential compatibility risk，不宣称一定发生linkage error。

## Actors / Entrypoints

- `impact` command在全部Module analysis、SSA filtering与code comparison完成后触发原子HTML publication。
- 用户从Overall Index进入每个Module的三页视图。

## Behavior Contract

- Overall technical details展示command实际使用的Algorithm与WALA ReflectionOptions。
- Access narrowing member展示old/new access、typed decision/reason及代表性caller/reference evidence。
- 全部coverage limitation保留；Module单一reason使用typed precedence。

## Index

顶部提供 `How to read this report`、`Analysis scope and limitations` 与 `Terminology`。主视图面向中级 Java 程序员，用 plain-language 说明可能的调用关系、coverage limitation、direct/transitive impact；WALA、Call Graph、RTA/ZeroCFA/optimized 0-1-CFA、conservative/false-positive、Context、SSA equivalence、Reflection、ServiceLoader 在 Terminology 中解释。

Index 记录：

- Overall mode/status、JDK/Maven version、`Maven Dependency Plugin: embedded 3.6.1`。
- Configured/actual analysis parallelism、Module/JAR diff/decompile workers、SSA serial worker。
- Baseline dependency、target build、front preparation、target dependency、JAR diff、Module analysis、SSA、decompile elapsed。
- Dependency changes、raw ChangePoints、candidate/equivalent-filtered/final paths、duplicate conflict/shadowed ChangePoint 与 SSA status counts。
- 每 Module status/reason/link、candidate/filtered/final、direct/transitive、affected methods/classes、Structural Reference Paths、entrypoint selector/matching、scope、CG nodes/edges/contexts、SSA/limitation counts。
- Preflight 与 Diagnostics 整体默认折叠。
- Algorithm与WALA ReflectionOptions读取command-wide `AnalysisRunResult`：默认显示`rta`、global instantiated-compatible-class precision及`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`；显式ZeroX/Reflection配置展示实际选择。

## Module Pages

- Module Index：status/reason、scope、entrypoint selection、affected method/class/path/dependency/member counts、stage/worker/entry/call metrics、coverage limitations 和 sibling links。`Duplicate class resolution` 表展示 binary name、winner origin/logical source、shadowed logical source 与 precedence reason；dependency source 使用 Maven coordinate。Duplicate warning 不进入 Coverage limitations，也不改变 Module status。
- Affected Call Chains：一级按 changed JAR，二级按 changed member 与 affected application method；最终链展示 Java method sequence 及 `Direct dependency impact`/`Transitive dependency impact`。SSA-equivalent candidate chains 独立默认折叠。Structural Reference Chains 显示 PROJECT boundary 到 changed class 的完整关系；raw Context/edge evidence 位于 `Technical details`。Changed member 链接到 Dependency Changes anchor。
- Dependency Changes：展示至少关联candidate/final Impact Path、Structural Reference Path，或disposition为`SHADOWED_BY_DUPLICATE`/`ACCESS_REMAINS_VALID`的changed member，按Maven coordinate/JAR分组。Access member展示`PUBLIC->PROTECTED`等transition、`ACCESSIBLE`/`INACCESSIBLE`/`POTENTIALLY_INACCESSIBLE` decision与representative evidence。其他raw changes只保留总数和未展示数；JAR diff failure转移到Module limitations/Diagnostics。
- 每个相关 member 默认折叠，并使用 `Affected`、`Equivalent (filtered)`、`Structural impact`、`Shadowed by duplicate` badge。Shadowed member 明确说明未生成 Impact Path 的原因、actual winner 与 precedence；dependency winner 使用 logical coordinate，不展示 physical path。其他 member 的 `View code changes` 展示由 dependency bytecode 生成的 old/new Unified diff，明确标记为 `Decompiled Java representation`；反编译失败或文本相同时展示 ASM fallback/unavailable reason。
- 默认展开区只显示 Maven/Module coordinate、scope/version、Java package/class/member、影响链和核心 metrics。Dependency JAR physical path 在所有区域均不输出；Workspace、classpath、Maven executable、JDK/config/temp/output 等其他 filesystem path 进入 `Technical details`。
- Call chain 空态固定为 `No affected call chain was found within the documented analysis scope.`，不声明确定性 no impact。

所有页面共享 top breadcrumbs、Module sibling navigation、sticky side TOC；窄屏下 TOC 回到正文顶部。Navigation 使用纯 HTML/CSS，无 JavaScript、CDN 或外部 asset。Dynamic text、href 和 anchor attribute 均 HTML escaping；anchor 使用 stable hash。

## Publication

- Staging 中先写每个非-skip Module 的三页，再写 Overall Index。
- 最后 atomic move command-owned Module directory 和 Index；不支持 filesystem atomic move 时使用同 filesystem replace fallback。
- Handled Module failure 仍发布 partial/all-failed Report。
- Global preparation 或 report publication failure 不主动替换旧 Report；Module directory replace failure尝试恢复 backup。
- 原 Module detail URL 的 base filename 保留为 Module Index；新增 `-impact`、`-changes` sibling。Module filename 由 sanitized coordinate + stable SHA-256 prefix 生成；owned directory 整体 replacement 会清理 stale page。

## Format Contract

- `--format html` 接受。
- `--format md` 保留 parser compatibility，但在 pipeline 前 fail fast，提示 Markdown 已移除。

## Core Flow

1. 从immutable AnalysisRunResult归并Overall、Module、path、disposition、limitation与code evidence view。
2. 在staging中生成Overall及每个非skip Module的三页HTML。
3. 完整escaping、navigation与owned-file校验后原子替换旧Report。

## Acceptance Criteria

### Functional

- Given default command configuration；When发布Report；Then technical details显示`rta`与`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。
- Given access reference全部仍合法；When发布Dependency Changes；Then显示`ACCESS_REMAINS_VALID`与old/new access，Affected Call Chains中不存在虚假path。
- Given potential access reference；When发布Report；Then明确标注`Potential access incompatibility`且不改变Module status。

### Non-Functional

- [ ] 所有页面offline、无JavaScript/CDN，dynamic text/href/anchor均escaping。
- [ ] Publication使用staging与command-owned atomic replacement，handled Module failure仍可发布partial Report。

## Edge Cases

- Empty path文案只表示declared analysis scope内未发现路径。
- Code comparison unavailable只影响evidence，不改写Impact或Module status。

## Implementation Boundaries

- Report不计算Call Graph、access legality或coverage precedence；只消费上游typed result。
