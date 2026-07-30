---
title: "CLI Preflight and Diagnostics"
type: feature
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "产品命名和 Public CLI 边界"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Root CLI 与两条 pipeline 的架构关系"
  - path: "wiki/features/maven-runtime.md"
    desc: "Maven runtime 和 version check"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree command 的 repository/reactor 检查图"
  - path: "wiki/features/report-generator.md"
    desc: "Preflight result 和 diagnostics 的报告输出"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root command、global options 和 subcommand dispatch"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "impact options 和 command-level preflight 消费"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "tree options、aggregate decision 和 exit code"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreePreflightService.java"
    desc: "tree Command Preflight graph 和 prepared context"
  - path: "src/main/java/io/github/dependencyanalysis/tree/TreeConsoleReporter.java"
    desc: "tree 三阶段 Console 与 Maven collection heartbeat"
  - path: "src/main/java/io/github/dependencyanalysis/preflight/PreflightRunner.java"
    desc: "稳定拓扑执行、dependency skip 和 decision 生成"
  - path: "src/main/java/io/github/dependencyanalysis/preflight/PreflightResult.java"
    desc: "共享结果 Schema"
  - path: "src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticCollector.java"
    desc: "pipeline diagnostics event 收集"
  - path: "src/main/java/io/github/dependencyanalysis/runtime/JavaRuntimeProbe.java"
    desc: "impact target JDK 8 Preflight probe"
---

# Feature: CLI Preflight and Diagnostics

## Summary

CLI 提供 Root/subcommand 契约，并在昂贵 pipeline 启动前执行结构化 Command Preflight。`tree` Console 将 command decision 与运行时 Analysis issue 分离，固定输出 `Preflight → Analysis → Summary`。

## Design Decisions

- Global options 使用 picocli inheritance，可放在 subcommand 前或后。
- `impact` 与 `tree` 统一使用 `-p, --path`；Root Java option 为 `-j, --java-home`。
- `impact` Preflight 要求完整 JDK 8，校验 `bin/java`、`bin/javac`、Java major、`rt.jar` 和 boot/ext properties；`tree` 不施加 JDK 8 限制。
- `PreflightRunner` 按 check declaration 和 `dependsOn` 形成稳定拓扑顺序；前置 failure 导致后继 `SKIPPED`。
- `--maven-arg` 每个值是独立 process token，拒绝 lifecycle/goal 和工具控制参数；settings 相对路径以 repository root 解析。
- `impact` 无安全 core fallback；`tree` 默认内置 evidence-complete plugin，高级 plugin override 能力不足时在 Command Preflight 阻断。
- Reactor model、Maven collection、output 与 evidence failure 是 Analysis issue，不属于 Preflight scope。

## Actors / Entrypoints

- 用户执行 `java -jar target/dependency-analyzer.jar [global-options] impact ...`。
- 用户执行 `java -jar target/dependency-analyzer.jar [global-options] tree ...`。
- `DependencyAnalyzerCli.main()` 是 JVM 入口；两个 command 的 `call()` 分别组装检查图并消费 decision。

## Behavior Contract

- Root command 无 subcommand 时返回 usage error；subcommand options 只在对应 subcommand 下解析。
- Result 包含 `checkId`、`command`、`scope`、`scopeId`、`requirement`、`status`、`decision`、`summary`、`evidence`、`fallback`、`dependsOn`、`elapsedMillis`。
- Command Preflight 使用 `COMMAND` 和 `SNAPSHOT` scope；`tree` 不生成 Reactor-level Preflight result。
- `status` 固定为 `PASS`、`WARN`、`FAIL`、`SKIPPED`；`decision` 固定为 `CONTINUE`、`DEGRADE`、`BLOCK_REACTOR`、`BLOCK_COMMAND`。
- Command-level `REQUIRED` failure 返回 exit `1`，pipeline 不启动且不生成 report。
- Tree command-level block 不初始化 incremental writer，因此已有 Report 不被删除。
- `tree` 全部 reactor 无 issue 时为 `SUCCESS` 并返回 `0`；已全部处理但有 issue 时为 `COMPLETED_WITH_ISSUES` 并返回 `2`；pipeline/report failure 为 `FAILED` 并返回 `2`。
- Command-level block 仍输出 Summary：`status=FAILED`、`report=NOT_GENERATED`。
- `impact` 完整成功返回 `0`，pipeline failure 返回 `2`。
- `--call-graph-timeout-seconds` 默认为 `0`；负数 validation 返回 `1`，正数用于 RTA cooperative timeout。

## Core Flow

- Root CLI 解析 global/subcommand options。
- Command 创建 `PreflightContext`，运行自己的 `PreflightPlan`。
- Runner 执行 DAG、缓存 prepared context，并输出 console result。
- Command 根据 report decision 启动 pipeline 或直接返回。
- Tree Analysis 逐 reactor 输出 collection、analysis、page publish 和 checkpoint；collection 每 10 秒 heartbeat，失败 Maven log 只保留最后 100 行。
- Pipeline/report 使用同一 result；context 关闭时清理 worktree/snapshot owner。

## Check Graph

`impact` command graph 使用以下稳定 checkId；括号内为直接 dependency：

- `impact.path`
- `impact.git-repository`（`impact.path`）
- `impact.root-pom`、`impact.output`、`impact.java-runtime`（`impact.path`）
- `impact.maven-runtime`（`impact.java-runtime`）
- `impact.maven-arguments`（`impact.git-repository`）
- `impact.maven-version`（`impact.maven-runtime`）
- `impact.workspace`（`impact.git-repository`、`impact.root-pom`），同时准备 baseline 与 target/current checkout。
- `impact.graphml-capability`（`impact.workspace`、`impact.maven-version`、`impact.maven-arguments`）。

`tree` 仅执行 command graph：

- Command：`tree.path` → `tree.snapshot`；`tree.path` 还直接约束 `tree.output`、`tree.scope-filter`、`tree.maven-runtime`。
- Command：`tree.snapshot` → `tree.maven-arguments`；`tree.maven-runtime` 与 `tree.snapshot` → `tree.maven-version`。
- Command：`tree.snapshot`、`tree.maven-arguments`、`tree.scope-filter` → `tree.reactor-inventory`。
- Command：内置/override plugin runtime 与 evidence capability 在启动 Analysis 前确定，不完整 override 不进入 pipeline。
- Reactor mode、active/requested module 数、Maven result 和 evidence 在 Analysis 记录；bounded `-pl/-am` 由工具生成，用户仍不能通过 `--maven-arg` 覆盖 selection。

CLI enum/format/change-kind/timeout validation 在 check graph 建立前完成；`impact.java-runtime` 负责唯一一次 target JDK probe，prepared descriptor 同时交给 Maven/build 和 WALA stages。

## Acceptance Criteria

### Functional

- Given 前置 required check failure；When 后继 check 被调度；Then 后继 status 为 `SKIPPED` 且 evidence 包含阻断 checkId。
- Given global option 位于 subcommand 前或后；When picocli 解析；Then command 得到同一 Root option value。
- Given command-level block；When command 结束；Then exit `1` 且不存在 report。

### Non-Functional

- [ ] Preflight probe 不经 shell 拼接，不重复运行同一个 DAG node。
- [ ] Result 顺序 deterministic。
- [ ] Console 和 report 不重新计算 status/decision。

## Edge Cases

- Check DAG 出现未知 dependency、重复 checkId 或 cycle 时，plan execution 明确失败。
- Maven version 小于 3.6.3 或大于等于 4.0.0 时 required check 阻断 command。
- `impact` 缺少 `--java-home`、JDK major 非 8、缺少 `javac` 或 `rt.jar` 时 required check 阻断 command；`tree` 可使用其他 Maven-compatible JDK。
- Analysis issue 必须带 severity、code、scope、message 和 remediation，不能冒充 Preflight success。

## Implementation Boundaries

- `cli` 负责 Public CLI token contract。
- `preflight` 负责 check orchestration 和 immutable result。
- `diagnostic` 只负责 pipeline event，不产生 execution decision。
