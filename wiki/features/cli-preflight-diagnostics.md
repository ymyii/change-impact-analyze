---
title: "CLI Preflight and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact/tree pipeline 与 exit code"
  - path: "wiki/features/report-generator.md"
    desc: "Preflight 与 diagnostics 输出"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "spring-backend options、validation、exit code"
  - path: "src/main/java/io/github/dependencyanalysis/preflight/PreflightRunner.java"
    desc: "check DAG"
---

# Feature: CLI Preflight and Diagnostics

## Summary

CLI 在昂贵分析前执行结构化 Preflight。`impact` 首版只支持 `--analysis-target spring-backend`、JDK 8、Maven project；`tree` 保持独立 contract。

## Impact Options

- `--baseline <ref>` required；`--target <ref>` 默认 current checkout。
- `--path <path>`：reactor root 或 leaf Module。
- `--analysis-target spring-backend`：默认且唯一 target。
- `--module-parallelism <N>`：默认 `2`，必须 `>=1`；不按 CPU 数静默截断，超过 CPU 输出 warning。
- `--call-graph-timeout-seconds <N>`：默认 `0`；按 Module、从实际 WALA build 开始计时。
- `--format html`：唯一有效格式；`md` fail fast。
- `--java-home`：必须是完整 JDK 8；analyzer JVM 可为 Java 17。

## Exit Codes

- `SUCCESS`、`INCONCLUSIVE`：`0`。
- `PARTIAL_SUCCESS`、`FAILED`：`2`。
- Argument validation、Preflight、global preparation failure：`1`。

`INCONCLUSIVE` 表示 analysis 在公开 model 内完成，但存在 JAR diff、ServiceLoader 或 SSA uncertainty；它不是 hard failure。

## Preflight Boundary

- 校验 path/Git/root POM/output/JDK 8/Maven version/Maven arguments/workspace/GraphML capability。
- Preflight failure 不启动 pipeline，不触碰旧 Report。
- Reactor/leaf mode、Module coordinate collision、physical artifact ambiguity 属于 preparation failure。
- Module scope/Call Graph failure属于 handled Module result，可产生 partial Report。

## Diagnostics

- Pipeline stage 记录 start/end/failure、elapsed、worker count和 graph metrics。
- WALA heartbeat 使用 `WALA heartbeat`，不再出现 RTA-specific wording。
- Console 与 HTML Report 消费同一 result/status，不重新推断结论。
