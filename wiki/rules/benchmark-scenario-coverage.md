---
title: "Benchmark Scenario Coverage"
type: rule
relations:
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "CHA canonical 双 scope benchmark gate"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "编码、artifact、测试与 benchmark 的验证顺序"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Analyzer capability 与 semantic coverage 边界"
code_refs:
  - path: "AGENTS.md"
    desc: "benchmark 必须由用户明确授权"
  - path: "benchmarks/impact-medium/run-scope-matrix.sh"
    desc: "canonical matrix 与原子发布入口"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "scope-aware CHA semantic gate"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "scope + refinement semantic baseline"
---

# Rule: Benchmark Scenario Coverage

## Summary

Analyzer 可观察能力变化必须同步维护 fixture、semantic verification 和 expected baseline。Benchmark 只能在用户明确授权后执行；Wiki、runbook、验收清单或历史执行记录都不构成授权。

## Rules

- 新增或扩展 CLI、dependency planning、Call Graph、ChangePoint、Impact Path、boundary evidence 或 Report 能力时，同一任务应更新相关 benchmark contract。
- 内部 refactor 无可观察行为变化时可不新增 fixture，但必须维护现有脚本合同。
- 未获授权时只运行 Python/shell contract tests，不运行 `run-benchmark.sh`、`run-suite.sh` 或 `run-scope-matrix.sh`。
- `impact-medium` canonical algorithm 固定为 CHA；`k-obj` 使用聚焦 unit/integration capability tests，不进入 canonical baseline。
- Matrix 必须显式覆盖 `changed-paths` 与 `full`，每个 scope 为 1 warm-up、5 formal、1 local receiver control；双 scope 共 14 个 JVM。
- `expected-results.tsv` 只维护两种 scope × 两种 refinement 的四个 baseline。
- `PENDING` 只允许在显式 calibration 中通过；人工确认前不得发布 tracked snapshot。
- 任一 scope 的语义、topology、HTML 或 publication gate 失败时，不发布任何新 snapshot。

## Stable Verification

普通开发可执行 generator unit tests 与所有 shell 的 `sh -n`。只有显式授权后，才执行：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

完整完成条件：14 个进程成功、scope 内 topology 稳定、四个 baseline 非 `PENDING`、两份 HTML 成功、双 scope snapshot 原子发布。
