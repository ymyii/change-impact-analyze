---
title: "Impact Benchmark"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "benchmark 持续验证打包后的 impact pipeline"
  - path: "wiki/features/call-graph-engine.md"
    desc: "Call Graph topology、dependency body policy 与 boundary evidence"
  - path: "wiki/features/jdk-method-models.md"
    desc: "默认jdk8与none semantic control的验收contract"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "100 ms heap observation 与 command final peak summary"
  - path: "wiki/features/impact-tracing.md"
    desc: "semantic verifier 校验 Impact Path 与 boundary behavior"
  - path: "wiki/features/report-generator.md"
    desc: "fixture semantic contract 依赖 Overall、Module pages 与 scope metadata"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "matrix 使用已打包的 shaded Analyzer JAR"
  - path: "wiki/rules/benchmark-scenario-coverage.md"
    desc: "Analyzer 能力新增的 benchmark scenario coverage gate"
code_refs:
  - path: "benchmarks/impact-medium/README.md"
    desc: "用户手册、环境变量、Schema 与 failure entrypoint"
  - path: "benchmarks/impact-medium/run-scope-matrix.sh"
    desc: "changed-paths/full canonical matrix 与双 scope 原子发布"
  - path: "benchmarks/impact-medium/run-suite.sh"
    desc: "单scope的4 warm-up + 20 formal + 4 none control suite"
  - path: "benchmarks/impact-medium/run-benchmark.sh"
    desc: "单 JVM fixture preparation、资源采样、impact 执行与校验"
  - path: "benchmarks/impact-medium/scripts/invoke-impact.sh"
    desc: "显式scope与none control、默认jdk8的固定CLI invocation"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "42 dependencies、scope baseline、path 与 boundary evidence 验收"
  - path: "benchmarks/impact-medium/scripts/generate-report.py"
    desc: "scope HTML、TSV Schema 与 topology drift validation"
  - path: "benchmarks/impact-medium/scripts/add-scope-comparison.py"
    desc: "双 Report cross-scope absolute change 与 ratio"
  - path: "benchmarks/impact-medium/scripts/publish-scope-matrix.sh"
    desc: "双 scope candidate set 的 transaction publication"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "scope + model + algorithm semantic baseline"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/JdkModelUseCase.java"
    desc: "Stream.map private Function callback到changed dependency fixture"
  - path: "benchmarks/impact-medium/results/changed-paths/samples.tsv"
    desc: "最近一次成功 changed-paths suite 的 20 个正式样本"
  - path: "benchmarks/impact-medium/results/full/samples.tsv"
    desc: "最近一次成功 full suite 的 20 个正式样本"
---

# Runbook: Impact Benchmark

## Summary

Canonical入口显式执行`changed-paths`与`full`两种dependency analysis scope。每种scope对四种algorithm分别执行1次默认`jdk8` warm-up、5次默认`jdk8` formal sample和1次`none` semantic control，共28个独立Java Virtual Machine（JVM）进程；双scope合计56个JVM。输出两份只包含默认`jdk8` performance数据的self-contained HTML Report，并以一个transaction发布两组tracked snapshot。

## Prerequisites

- Java 17 用于启动 `target/dependency-analyzer.jar`。
- `JAVA8_HOME` 指向包含 `java`、`javac`、`jar` 和 `rt.jar` 的完整 JDK 8。
- Maven 3.6.3–3.x、Git、Python 3 与 POSIX shell。
- Maven local repository 已缓存 fixture 所需 Plugin 与 artifact；fixture 使用 offline Maven。
- macOS `/usr/bin/time -lp` 或 GNU `/usr/bin/time -v`。

## Canonical Command

```sh
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

可选 matrix label 只接受字母、数字、dot、underscore 与 hyphen；同名 raw/candidate 结果存在时拒绝覆盖。Matrix 必须显式执行：

```text
--dependency-analysis-scope=changed-paths
--dependency-analysis-scope=full
```

即使 `changed-paths` 是 CLI 默认值，也禁止 benchmark 依赖默认值。

## Schedule

- `changed-paths`：4次默认`jdk8` warm-up、20次默认`jdk8` formal run和4次`none` control；共28个JVM。
- `full`：同样28个JVM。
- 双scope共56个JVM，其中48个默认`jdk8` run与8个`none` control。每个run使用全新进程。
- 每种 scope 的 algorithm 列表按 round 循环左移，降低固定顺序偏差。
- Warm-up 启用 `--call-graph-diagnostics-output`，采集 topology、source、IR 与 dependency path evidence。
- Formal 不启用 diagnostics capture，只采集语义、性能与 graph totals。
- Control显式传`--jdk-model none`且不启用diagnostics capture；warm-up/formal省略option以验证CLI默认`jdk8`。
- 每种 scope 内 Entrypoint、CGNode、CGEdge、body-policy counts 和 dependency path topology 必须在 warm-up/formal 间稳定。

## Fixture and Semantic Contract

- application POM 固定 42 个 compile-scope direct dependency；增加 path、sink、factory artifact 时以等量 vendor dependency 替换，避免规模变化干扰 scope 对比。
- `scenario-api:1.0.0 -> 2.0.0` 固定产生 9 类 raw change。
- application direct `scenario-api:${scenario.api.version}` 是Maven winner；target为`2.0.0`，`path-a -> path-c`与`path-x -> path-y`继续请求`1.0.0`并在verbose GraphML形成loser occurrence。`changed-paths`必须将两条path都规范化到winner `2.0.0`并完整保留；reactor path也必须使用selected version。
- direct dependency总数保持42：新增direct `scenario-api`后，`vendor-lib-35`改由`path-sibling`transitive引入，external artifact集合不变。
- `path-a` sibling、seed downstream、external sink/factory/plain 均不位于到达 seed 的 external path，`changed-paths` 将其 method body 设为 no-op 或 flow-to-cast factory。
- dangerous transfer 必须产生 `CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY` 并使 Module 为 `INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`。
- factory `Object` result 经 PROJECT assignment/checkcast 必须产生 factory evidence 和 included target reachability；`full` 必须使用真实 factory body且不产生 approximation evidence。
- 普通 no-op external call 不单独使 Module `INCONCLUSIVE`；reactor method 在 `changed-paths` 下始终使用真实 IR。
- `JdkModelUseCase.execute`接收`Stream<String>`并经lazy`Stream.map`注册private`ChangedMapper.apply`，callback调用`ScenarioApi.bodyChanged`；默认`jdk8`必须报告该call chain，`none`按algorithm锁定真实JDK bytecode语义下的candidate/final baseline。
- `expected-results.tsv`以`dependency_analysis_scope + jdk_model + call_graph_algorithm`为key，维护16组semantic baseline。

## Metrics Contract

每个 formal sample 记录：

- Total wall、Call Graph stage time。
- Peak Heap Used/Committed、Heap Max、heap sample count。
- Process-tree peak Resident Set Size（RSS）。
- Entrypoint、CGNode、CGEdge、status、exit code。
- real-IR/no-op external artifact 数量。
- real/no-op/factory method node 数量与 dangerous transfer 数量。
- Analyzer SHA-256、Git state、OS、architecture、Analyzer Java、target JDK 与 Maven identity。

两份 Report 均展示相同 algorithm 在 `changed-paths` 与 `full` 之间的 absolute change 和 ratio。项目不设置固定提速阈值；Report 只展示实测结果。

## Outputs

固定 HTML：

```text
tmp-files/impact-medium-benchmark/benchmark-report-changed-paths.html
tmp-files/impact-medium-benchmark/benchmark-report-full.html
```

Tracked snapshot：

```text
benchmarks/impact-medium/results/changed-paths/samples.tsv
benchmarks/impact-medium/results/changed-paths/summary.tsv
benchmarks/impact-medium/results/changed-paths/topology.tsv

benchmarks/impact-medium/results/full/samples.tsv
benchmarks/impact-medium/results/full/summary.tsv
benchmarks/impact-medium/results/full/topology.tsv
```

- `samples.tsv`每个默认`jdk8` formal run一行，并记录model。
- `summary.tsv`每个algorithm一行，记录`jdk8`、min/median/max、stable graph/body totals、successful sample与scope内相对`zero-cfa` ratio。
- `topology.tsv`来自默认`jdk8` warm-up；除Call Graph ranking/path外，保存model、requested/actual scope、fallback、real/no-op/factory counts和全部dependency path evidence。
- Raw run、topology JSON、candidate TSV 与 failure detail 保留在 `tmp-files/impact-medium-benchmark/`，不提交 Git。

## Atomic Publication

只有以下条件全部满足，matrix 才发布 tracked snapshot：

- 两种scope的56个run全部成功。
- 每种 scope 内 warm-up 与 formal topology 稳定。
- 16组scope/model/algorithm semantic baseline全部通过。
- 两份 HTML Report 均成功生成并完成 cross-scope comparison 注入。
- 两个 candidate result directory 均通过 Schema 与 scope validation。

Publication 一次替换 `results/changed-paths` 与 `results/full`。任一 scope 失败时，旧 tracked snapshot 全部保留；两份 HTML 仍尽量输出 success/failure detail，任务不能标记完成。

## Success Criteria

- 56个独立JVM全部返回semantic success。
- 每个scope的4次默认`jdk8` warm-up和20次formal sample topology稳定；4次`none` control只验收语义。
- `expected-results.tsv`中16组`scope + model + algorithm` baseline全部通过。
- 两份 self-contained HTML Report 存在，包含5个formal sample、body-policy metrics、semantic result、path evidence与cross-scope comparison。
- `results/changed-paths` 与 `results/full` 同时发布，candidate/failure detail保留在`tmp-files/`。

## Configuration

- `JAVA8_HOME`：完整 JDK 8 absolute path，必填。
- `BENCHMARK_WALA_REFLECTION_OPTIONS`：默认 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`，并作为 suite 稳定性条件。
- `BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE`：底层 diagnosis run 使用，必须显式为 `changed-paths` 或 `full`；canonical matrix自行设置两种值。
- `BENCHMARK_JDK_MODEL`：底层diagnosis run必须显式为`jdk8`或`none`；canonical matrix自动调度默认与control。
- `BENCHMARK_CALL_GRAPH_ALGORITHM`、`BENCHMARK_RUN_KIND`、`BENCHMARK_CAPTURE_TOPOLOGY`：仅用于底层单 run diagnosis；canonical matrix负责完整调度。

## Diagnostic Single Run

单 run 只用于定位故障，不是任务验收入口：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE=changed-paths \
  BENCHMARK_CALL_GRAPH_ALGORITHM=zero-cfa \
  BENCHMARK_JDK_MODEL=jdk8 \
  BENCHMARK_RUN_KIND=formal \
  BENCHMARK_CAPTURE_TOPOLOGY=0 \
  benchmarks/impact-medium/run-benchmark.sh diagnosis-01
```

## Failure Entrypoints

- `<run>/logs/stderr.log`：Preflight、CLI、runtime metrics 与 pipeline failure。
- `<run>/logs/verification.txt`：scope-aware semantic failure。
- `<run>/logs/metrics.tsv`：单样本 status 与可恢复指标。
- `<run>/topology.json`：warm-up Schema、Call Graph topology、scope/path/boundary evidence。
- `<suite>-candidate-results/failure.txt`：environment、sample count、Schema 或 topology drift。
- `benchmark-report-<scope>.html`：单 scope success/failure 用户入口。
- `run-scope-matrix.sh`：唯一 canonical failure entrypoint；不能用单个 `run-suite.sh` 代替完成验收。
