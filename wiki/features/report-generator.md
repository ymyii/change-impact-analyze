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
    desc: "run status 与 metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisResult.java"
    desc: "Module detail result"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "独立 tree HTML renderer"
---

# Feature: Report Generator

## Summary

`impact` 只生成英文 offline HTML：用户 `--output` 是 Overall Index；同级 command-owned `<stem>-modules/` 为每个非 `SKIPPED` Module 保存 Module Index、Affected Call Chains、Dependency Changes 三页。Markdown renderer 与 legacy overload 已移除。`tree` 的 repository/reactor HTML contract 保持独立。

## Index

顶部提供 `How to read this report`、`Analysis scope and limitations` 与 `Terminology`。主视图面向中级 Java 程序员，用 plain-language 说明可能的调用关系、coverage limitation、direct/transitive impact；WALA、Call Graph、0-1-CFA、conservative/false-positive、Context、SSA equivalence、Reflection、ServiceLoader 在 Terminology 中解释。

Index 记录：

- Overall mode/status、JDK/Maven version、`Maven Dependency Plugin: embedded 3.6.1`。
- Configured/actual analysis parallelism、Module/JAR diff/decompile workers、SSA serial worker。
- Baseline dependency、target build、front preparation、target dependency、JAR diff、Module analysis、SSA、decompile elapsed。
- Dependency changes、raw ChangePoints、candidate/equivalent-filtered/final paths、duplicate conflict/shadowed ChangePoint 与 SSA status counts。
- 每 Module status/reason/link、candidate/filtered/final、direct/transitive、affected methods/classes、Structural Reference Paths、entrypoint selector/matching、scope、CG nodes/edges/contexts、SSA/limitation counts。
- Preflight 与 Diagnostics 整体默认折叠。

## Module Pages

- Module Index：status/reason、scope、entrypoint selection、affected method/class/path/dependency/member counts、stage/worker/entry/call metrics、coverage limitations 和 sibling links。`Duplicate class resolution` 表展示 binary name、winner origin/logical source、shadowed logical source 与 precedence reason；dependency source 使用 Maven coordinate。Duplicate warning 不进入 Coverage limitations，也不改变 Module status。
- Affected Call Chains：一级按 changed JAR，二级按 changed member 与 affected application method；最终链展示 Java method sequence 及 `Direct dependency impact`/`Transitive dependency impact`。SSA-equivalent candidate chains 独立默认折叠。Structural Reference Chains 显示 PROJECT boundary 到 changed class 的完整关系；raw Context/edge evidence 位于 `Technical details`。Changed member 链接到 Dependency Changes anchor。
- Dependency Changes：展示至少关联 candidate/final Impact Path、Structural Reference Path，或 disposition 为 `SHADOWED_BY_DUPLICATE` 的 changed member，按 Maven coordinate/JAR 分组。其他 raw changes 只保留总数和未展示数；JAR diff failure 转移到 Module limitations/Diagnostics。
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
