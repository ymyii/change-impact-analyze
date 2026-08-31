---
title: "Benchmark Scenario Coverage"
type: rule
---

# Rule: Benchmark Scenario Coverage

## Summary

Analyzer 可观察能力变化必须同步维护 fixture、semantic verification 和 expected baseline。Benchmark 只能在用户明确授权后执行；Wiki、runbook、验收清单或历史执行记录都不构成授权。

## Rules

- 新增或扩展 CLI、dependency planning、Call Graph、ChangePoint、Impact Path、boundary evidence 或 Report 能力时，同一变更应更新相关 benchmark contract。
- 内部 refactor 无可观察行为变化时可不新增 fixture，但必须维护现有脚本合同。
- 未获授权时只运行 Python/shell contract tests，不运行 `run-benchmark.sh`、`run-suite.sh` 或 `run-scope-matrix.sh`。
- `impact-medium` canonical algorithm 固定为 CHA；`k-obj` 使用聚焦 unit/integration capability tests，不进入 canonical baseline。
- Matrix 必须显式覆盖`changed-paths`与`full`，每个scope为1 warm-up、5 formal；双scope共12个JVM。SSA equivalence与`cha-local-receiver-inference`固定启用，不建立selection control。
- `expected-results.tsv`只维护两种scope的两个baseline。
- `PENDING` 只允许在显式 calibration 中通过；人工确认前不得发布 tracked snapshot。
- 任一 scope 的语义、topology、HTML 或 publication gate 失败时，不发布任何新 snapshot。

## Applies To

- 修改 impact CLI、dependency planning、Call Graph、ChangePoint、Impact Path、boundary evidence 或 Report 的可观察语义。
- 修改 canonical benchmark fixture、verification、baseline 或 publication contract。

## Verification

普通开发可执行 generator unit tests 与所有 shell 的 `sh -n`。只有显式授权后，才执行：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

完整完成条件：12个进程成功、scope内topology稳定、两个baseline非`PENDING`、两份HTML成功、双scope snapshot原子发布。

## Non-Goals

- 本规则不授权执行 benchmark；授权只能来自用户当前明确指令。
- `k-obj` capability 由聚焦测试验收，不纳入 CHA canonical baseline。
