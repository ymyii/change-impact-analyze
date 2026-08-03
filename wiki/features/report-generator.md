---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "status、partial result 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Path、Structural Impact、SSA 与 disposition"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "Overall 与三个 Module pages 的持续完整性校验"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "impact HTML Index 与 Module pages"
  - path: "src/main/java/io/github/dependencyanalysis/impact/AnalysisRunResult.java"
    desc: "run status 与 metrics"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisResult.java"
    desc: "Module detail result"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "独立 tree HTML renderer"
---

# Feature: Report Generator

## Summary

`impact` 只生成英文 offline HTML：用户 `--output` 是 Overall Index；同级 command-owned `<stem>-modules/` 为每个非 `SKIPPED` Module 保存 Module Index、Affected Call Chains、Dependency Changes 三页。Markdown renderer 与 legacy overload 已移除。`tree` 的 repository/reactor HTML contract 保持独立。

## Index

顶部提供 `How to read this report`、`Analysis scope and limitations` 与 `Terminology`。主视图面向中级 Java 程序员，用 plain-language 说明可能的调用关系、coverage limitation、direct/transitive impact；WALA、Call Graph、0-1-CFA、conservative/false-positive、Context、SSA equivalence、Reflection、ServiceLoader 在 Terminology 中解释。

Index 记录：

- Overall mode/status、JDK/Maven version、`Maven Dependency Plugin: embedded 3.6.1`。
- Configured/actual Module parallelism、JAR diff workers、SSA serial worker。
- Baseline dependency、target build、front preparation、target dependency、JAR diff、Module analysis、SSA elapsed。
- Dependency changes、raw ChangePoints、candidate/equivalent-filtered/final paths 与 SSA status counts。
- 每 Module status/reason/link、candidate/filtered/final、direct/transitive、affected methods/classes、Structural Impacts、scope/entrypoint、CG nodes/edges/contexts、SSA/limitation counts。
- Preflight 与 Diagnostics。

## Module Pages

- Module Index：status/reason、scope、affected method/class/path/dependency/member counts、stage/worker/entry/call metrics、coverage limitations、Module Diagnostics 和 sibling links。Classpath、raw status、WALA metrics 位于 `Technical details`。
- Affected Call Chains：一级按 changed JAR，二级按 changed member 与 affected application method；主视图展示 Java method sequence 及 `Direct dependency impact`/`Transitive dependency impact`。Structural Impact raw evidence 位于 `Technical details`。Changed member 链接到 Dependency Changes anchor。
- Dependency Changes：按 `(groupId, artifactId, type, classifier, oldVersion, newVersion)` 分组，完整展示 added/removed/updated/non-JAR dependency、Methods/Fields/Classes、plain-language disposition 与 structured JAR diff failure。Descriptor/hash、raw enum/disposition、SSA status/reason 和 exact physical path 位于 `Technical details`。
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
