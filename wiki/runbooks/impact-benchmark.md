---
name: "Impact Benchmark"
type: runbook
---

## Purpose and Scope

本 Runbook 在用户明确授权后，对 [Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md) 执行 CHA-only `impact-medium` canonical benchmark。它定义 `changed-paths` 与 `full` 两个 scope 的 schedule、semantic gate 和 atomic publication；页面本身不构成执行授权。

## Prerequisites

- 用户在当前请求中明确授权执行 benchmark；未授权时立即停止。
- 已按 [Build Test and Package](build-test-and-package.md) 生成 `target/dependency-analyzer.jar`。
- Java 17 启动 Analyzer；`JAVA8_HOME` 指向包含 `java`、`javac`、`jar` 与 `rt.jar` 的完整 JDK 8。
- Maven 3.6.3–3.x、Git、Python 3、POSIX shell 与 fixture artifacts 可用。
- `expected-results.tsv` 的两个 scope baseline 已经人工确认；若仍为 `PENDING`，只能执行获得授权的 calibration。

## Procedure

1. 在不执行 benchmark 的普通开发阶段，验证 Python contract tests。

   ```sh
   python3 -m unittest discover \
     -s benchmarks/impact-medium/tests -p 'test_*.py'
   ```

2. 在不执行 benchmark 的普通开发阶段，验证 shell syntax。

   ```sh
   for script in benchmarks/impact-medium/*.sh \
       benchmarks/impact-medium/scripts/*.sh; do
     sh -n "$script"
   done
   ```

3. 若 baseline 为 `PENDING` 且已获得 calibration 授权，生成 candidate。

   ```sh
   BENCHMARK_CALIBRATION=1 \
   JAVA8_HOME=/absolute/path/to/jdk8 \
     benchmarks/impact-medium/run-scope-matrix.sh calibration-label
   ```

4. 人工确认 `changed-paths` 与 `full` candidate，并更新 expected baseline；calibration 不发布 tracked snapshot。

5. 在 baseline 非 `PENDING` 且已获得正式执行授权后，运行 canonical matrix。

   ```sh
   JAVA8_HOME=/absolute/path/to/jdk8 \
     benchmarks/impact-medium/run-scope-matrix.sh
   ```

6. 检查每个 scope 的 1 次 warm-up、5 次 formal、topology stability、semantic result 与 HTML verification。

7. 仅在全部 gate 成功后，接受 publisher 原子创建 `results/changed-paths` 与 `results/full` snapshot。

## Success Criteria

- 两个 scope 各完成 1 次 warm-up 与 5 次 formal，共 12 个独立 Java Virtual Machine（JVM）process。
- 每个 scope 的 topology stable，两个 semantic baseline 均非 `PENDING`。
- `samples.tsv` 每个 scope 有 5 个 formal CHA samples；`summary.tsv` 每个 scope有 1 行 min/median/max。
- 两份 HTML 通过 verification，publisher 一次性提交两个 scope snapshot。
- `k-obj`、algorithm comparison、historical ZeroCFA data 与 removed refinement options 不进入 canonical output。

## Failure Entry Points

- 未获得当前用户明确授权：停止，不执行 calibration、canonical 或 diagnostic benchmark runner。
- Baseline 仍为 `PENDING`：只在 calibration 授权后生成 candidate，等待人工确认。
- 任一 process、topology、semantic、HTML 或 publication gate 失败：不发布任何 tracked snapshot，检查 `tmp-files/impact-medium-benchmark/`。
