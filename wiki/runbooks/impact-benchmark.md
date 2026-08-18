---
title: "Impact Benchmark"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "打包后的 impact pipeline 与 artifact"
  - path: "wiki/features/call-graph-engine.md"
    desc: "CHA topology、ancestor retention 与 pruning contract"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "benchmark 前必须先完成 artifact 与常规验证"
  - path: "wiki/rules/benchmark-scenario-coverage.md"
    desc: "显式执行授权与场景覆盖规则"
code_refs:
  - path: "benchmarks/impact-medium/README.md"
    desc: "环境、suite、output 与 semantic contract"
  - path: "benchmarks/impact-medium/run-scope-matrix.sh"
    desc: "changed-paths/full canonical matrix"
  - path: "benchmarks/impact-medium/run-suite.sh"
    desc: "单 scope 1 warm-up + 5 formal"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "CHA semantic verification gate"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "两个scope的semantic baseline"
---

# Runbook: Impact Benchmark

## Summary

`impact-medium` 是 CHA-only canonical benchmark。显式执行 `changed-paths` 与 `full` 两种 dependency analysis scope。每个 scope 使用6个独立Java Virtual Machine（JVM）进程：1次warm-up、5次formal；SSA equivalence与`cha-local-receiver-inference`固定启用。双scope共12个进程。

本 runbook 只定义执行 contract。未经用户明确授权，禁止运行 canonical、calibration 或 diagnostic benchmark 命令。

## Prerequisites

- 已按 build runbook 生成 `target/dependency-analyzer.jar`。
- Java 17 用于启动 Analyzer。
- `JAVA8_HOME` 指向完整 JDK 8，包含 `java`、`javac`、`jar` 与 `rt.jar`。
- Maven 3.6.3–3.x、Git、Python 3、POSIX shell。
- fixture 所需 artifact 与 Plugin 已在 local Maven repository 中。

## Canonical Schedule

每个 scope 固定：

1. `cha + jdk-model none` warm-up，开启Schema 13 topology capture。
2. 相同配置执行 5 次 formal，每次启动新 JVM。

`run-scope-matrix.sh` 依次显式设置 `changed-paths`、`full`。CHA 不应用 WALA ReflectionOptions。Suite 不执行algorithm轮换、不包含JDK model control、不包含`k-obj`数据，也不再设置已删除的result refinement环境变量或CLI option。

## Fixture Contract

- application POM 固定 40 个 direct dependencies。
- `scenario-api:1.0.0 -> 2.0.0` 产生固定 raw change set与多条 dependency occurrence path。
- `BoundaryUseCase` 保留CHA external-target pruning场景；JDK声明分派由Object/Runnable等现有场景验证非JDK target被删除，并要求Report输出限制文本和非零指标。
- external ancestor chain 保留 reachable concrete method并连接 PROJECT override；同 artifact 的无关 `ExternalPlain.call` 被裁剪。
- 固定local receiver extension保留`ChangedReceiver`，排除caller-local `unrelatedReceiverPath`，并把edge指标写入Schema 13与Report；Schema同时校验decompiled Java first、normalized SSA on miss的短路状态和计数。bridge参数和factory return不执行跨方法裁剪。
- dynamic protocol 的精细行为由 `k-obj` capability unit tests覆盖，不进入 CHA canonical fixture。

## Semantic Baseline

`expected-results.tsv` 以 `dependency_analysis_scope` 为key，只包含：

- `changed-paths`
- `full`

两个Impact call-chain baseline当前为`PENDING`。只有显式授权calibration后才能生成candidate artifact，人工确认后再锁定数值。不得混入`k-obj`historical snapshot。

## Contract Tests

以下命令只测试 benchmark 脚本和生成器，不执行 benchmark：

```sh
python3 -m unittest discover \
  -s benchmarks/impact-medium/tests -p 'test_*.py'

for script in benchmarks/impact-medium/*.sh \
    benchmarks/impact-medium/scripts/*.sh; do
  sh -n "$script"
done
```

## Authorized Calibration

只有用户明确授权后执行：

```sh
BENCHMARK_CALIBRATION=1 \
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh calibration-label
```

Calibration 生成两份 HTML 和 candidate TSV，不发布 tracked snapshot。人工确认两个 baseline 后，更新 `expected-results.tsv`，再经单独授权执行非 calibration matrix。

## Output Contract

- `samples.tsv`：每个 scope 的 5 个 formal CHA 样本。
- `summary.tsv`：每个 scope 1 行 CHA min/median/max；无 `k_obj_depth` 和 ZeroCFA ratio。
- `topology.tsv`：CHA warm-up topology、scope、dependency path、ancestor retention、JDK声明分派裁剪与固定extension evidence。
- HTML 不含 algorithm comparison tab；两份 Report 可加入 cross-scope absolute change 与 ratio。
- raw run 与 candidate 只位于 `tmp-files/impact-medium-benchmark/`。

当前没有 tracked TSV。只有12个进程全部成功、topology稳定、两个baseline非`PENDING`且两份HTML成功时，publisher才原子创建`results/changed-paths`与`results/full`。
