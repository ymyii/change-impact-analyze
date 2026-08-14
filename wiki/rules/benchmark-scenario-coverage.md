---
title: "Benchmark Scenario Coverage"
type: rule
relations:
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "能力场景的 canonical 双 scope benchmark gate"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "编码、测试、打包与 benchmark 的完成条件"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Analyzer 能力与 benchmark semantic coverage 的架构边界"
code_refs:
  - path: "AGENTS.md"
    desc: "benchmark执行必须由用户明确授权的repository规则"
  - path: "benchmarks/impact-medium/run-scope-matrix.sh"
    desc: "changed-paths/full canonical matrix 与原子发布入口"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "scope-aware semantic verification gate"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "scope + JDK model + Call Graph algorithm + k-object depth + result refinement semantic baseline"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/ChaLocalReceiverUseCase.java"
    desc: "CHA local receiver result-refinement semantic fixture"
---

# Rule: Benchmark Scenario Coverage

## Summary

任何 Analyzer 能力新增或现有分析能力扩展，必须在同一编码任务中同步新增或更新对应 benchmark fixture、semantic expected result 和 verification。Canonical benchmark 是显式授权后的验收入口；未经用户明确要求不得执行，Wiki、runbook或既有完成条件本身不构成执行授权。

## Rules

- 新增 Analyzer 能力或扩展现有分析行为时，必须在同一任务中新增或更新能够触发该行为的 benchmark fixture 场景。
- 只有用户明确要求执行benchmark时才可运行canonical或诊断benchmark命令；没有授权时保留未执行状态并如实报告，不得以文档中的命令或验收要求推定授权。
- benchmark 场景必须具有可自动验证的 semantic expected result；只增加 wall、heap、Resident Set Size（RSS）等性能采集不满足本规则。
- 新增 CLI mode、Call Graph model、ChangePoint、Impact Path、boundary evidence 或 Report 能力时，必须覆盖默认模式以及与该能力相关的对照模式。
- 能力代码、fixture 场景、verification logic 和 expected baseline 必须在同一任务中完成并通过。
- `impact-medium` canonical benchmark 的任一 scope、algorithm、topology、semantic baseline、HTML Report 或 snapshot publication gate 失败时，能力新增任务不得标记完成。
- 单纯内部 refactor 且无可观察行为变化时可以不新增场景；现有benchmark regression仍受显式执行授权约束。
- `changed-paths` 是当前默认 dependency analysis scope；`full` 是对应的完整分析对照。Canonical matrix 必须显式传入两种 scope，不能依赖 CLI 默认值。
- JDK Method Model默认依algorithm解析：CHA为`none`；四种非CHA algorithm为`jdk8`。Canonical CHA warm-up/formal同时省略algorithm/model，非CHA显式algorithm并省略model；非CHA增加显式`none`control，每个scope另增加一个CHA local-receiver-only control。
- `k-obj` canonical run必须省略`--k-obj-depth`以验证默认`1`；`k=2`由集成测试覆盖，不增加canonical JVM数量。

## Applicability

- 新增或扩展 Analyzer 的 CLI、dependency planning、Call Graph、dynamic model、ChangePoint、Impact tracing、boundary evidence、Report 或其他可观察分析能力时适用。
- 修改 `benchmarks/impact-medium` fixture、verification、expected baseline 或 canonical execution/publish logic 时适用。
- 已证明无可观察行为变化的内部 refactor 只需通过现有 regression，不要求人为新增 scenario。

## Stable Verification

以下命令仅在用户明确授权执行benchmark后运行：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

该入口执行五种Call Graph algorithm与两种dependency analysis scope，共20组scope/model/algorithm/depth/refinement semantic baseline。每种scope包含5次warm-up、25次formal、4次非CHA`none`control和1次CHA local-only control，双scope共70个独立Java Virtual Machine（JVM）进程。

## Completion Gate

- 70个run全部成功。
- 两种 scope 内 warm-up 与 formal topology 稳定。
- 20组scope/model/algorithm/depth/refinement semantic baseline全部通过且不含`PENDING`；所有SSA组合必须包含private static递归、Class.forName和ServiceLoader evidence；非CHA默认`jdk8`还必须包含`Stream.map` callback路径。CHA local-only组合必须保留ChangedReceiver真实路径、删除UnrelatedReceiver虚假caller且报告非零pruned edge。
- `benchmark-report-changed-paths.html` 与 `benchmark-report-full.html` 均成功生成。
- 两组 tracked snapshot 通过同一个 matrix transaction 原子发布；任一 scope 失败时旧 snapshot 全部保留。

## Reference Files

- `benchmarks/impact-medium/run-scope-matrix.sh` - canonical 能力覆盖入口。
- `benchmarks/impact-medium/scripts/verify-report.sh` - semantic result、path topology 和 boundary evidence 验收。
- `benchmarks/impact-medium/expected-results.tsv` - `dependency_analysis_scope + jdk_model + call_graph_algorithm + k_obj_depth + result_refinement_algorithms` baseline。

## Non-Goals

- 不设置未经实测的固定性能提升阈值。
- 不要求无行为变化的机械 refactor 人为增加 fixture。
- 不以 benchmark 替代 unit/integration test；两者都是任务完成条件。
- 不把Wiki、runbook、release gate或历史执行记录视为当前任务的benchmark执行授权。
