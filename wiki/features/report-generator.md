---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "status、partial result 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Path、Structural Impact、SSA 与 disposition"
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

`impact` 只生成 HTML：用户 `--output` 是 Index；同级 command-owned `<stem>-modules/` 保存非-skip Module detail page。Markdown renderer 与 legacy overload 已移除。`tree` 的 repository/reactor HTML contract 保持独立。

## Index

顶部固定展示 `Analysis Model Boundaries`：Vanilla 0-1-CFA over-approximation、all-method entrypoints、Reflection/MethodHandle best-effort、ServiceLoader conservative overlay、Spring/custom classloader non-goals、JDK exclusions、SSA model boundary、target-only Call Graph。

Index 记录：

- Overall mode/status、WALA/JDK/Maven version。
- Configured/actual Module parallelism、JAR diff workers、SSA serial worker。
- Baseline dependency、target build、front preparation、target dependency、JAR diff、Module analysis、SSA elapsed。
- Dependency changes、raw ChangePoints、candidate/equivalent-filtered/final paths 与 SSA status counts。
- 每 Module status/reason/link、candidate/filtered/final、direct/transitive、affected methods/classes、Structural Impacts、scope/entrypoint、CG nodes/edges/contexts、SSA/limitation counts。
- Preflight 与 Diagnostics。

## Module Page

- Status/reason/detail、PROJECT/classes、REACTOR_DEPENDENCY/DEPENDENCY classpath、JDK exclusions。
- Entrypoint/parameter candidate/CG metrics。
- Dependency artifacts、`DependencyUpgradeKey` old/new coordinate、scope、canonical physical path 与 `BoundChangePoint` provenance。
- 每 ChangePoint old/new descriptor/hash、disposition、SSA status/reason。
- Ordered Impact Path nodes/edges/terminal、Structural Impacts、coverage limitations；其中 isolated bytecode diff failure 保留 exact physical JAR pair 与 failure reason。
- Scope validation、Call Graph、ServiceLoader、query elapsed metrics 与 Module-scoped Diagnostics。
- 空态固定为“在声明的analysis model内未发现Impact Path”。

## Publication

- Staging 中先写 Module pages，再写 Index。
- 最后 atomic move command-owned Module directory 和 Index；不支持 filesystem atomic move 时使用同 filesystem replace fallback。
- Handled Module failure 仍发布 partial/all-failed Report。
- Global preparation 或 report publication failure 不主动替换旧 Report；Module directory replace failure尝试恢复 backup。
- Dynamic content 全部 HTML escaping；Module filename 由 sanitized coordinate + stable SHA-256 prefix 生成。

## Format Contract

- `--format html` 接受。
- `--format md` 保留 parser compatibility，但在 pipeline 前 fail fast，提示 Markdown 已移除。
