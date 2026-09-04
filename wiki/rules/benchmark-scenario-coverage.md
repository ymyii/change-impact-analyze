---
name: "Benchmark Scenario Coverage"
type: rule
---

## Overview

本规则确保 [Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md) 的可观察能力与 canonical benchmark contract 同步，同时把 benchmark execution 保持为需要用户明确授权的独立操作。

## Scope

- Impact CLI、dependency planning、Call Graph、ChangePoint、affected path、boundary evidence 或 Report 的可观察语义。
- `benchmarks/impact-medium/` fixture、verification、expected baseline 与 snapshot publication。

## Rules

- **必须**在新增或扩展可观察能力时，同步更新相关 fixture、semantic verification 与 expected baseline；纯内部重构只需保持既有 contract。
- **必须**让 canonical matrix 固定执行 `changed-paths` 与 `full`，每个 scope 使用 1 次 warm-up 与 5 次 formal CHA process。
- **必须**让 `k-obj` 由聚焦 unit/integration capability tests 验收，不混入 CHA canonical baseline。
- **必须**让 calibration 只生成 candidate；人工确认非 `PENDING` baseline 后，才允许后续授权的正式 matrix 发布 snapshot。
- **禁止**在用户未明确授权时运行 `run-benchmark.sh`、`run-suite.sh` 或 `run-scope-matrix.sh`；只执行脚本 contract tests 与 syntax checks。
- **禁止**在任一 scope 的 semantic、topology、HTML 或 publication gate 失败时发布部分 snapshot。

## Verification

- 未授权时执行 `python3 -m unittest discover -s benchmarks/impact-medium/tests -p 'test_*.py'`。
- 未授权时对 `benchmarks/impact-medium/*.sh` 与 `benchmarks/impact-medium/scripts/*.sh` 执行 `sh -n`。
- 获得明确授权后按 [Impact Benchmark](../runbooks/impact-benchmark.md) 执行完整 matrix。

## Non-Goals

- 本规则本身不构成 benchmark execution 授权。
- 本规则不为 `k-obj` 建立 canonical performance baseline。
