---
title: "Impact Benchmark"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "benchmark 持续验证打包后的 impact pipeline"
  - path: "wiki/features/call-graph-engine.md"
    desc: "Call Graph topology、dependency body policy 与 boundary evidence"
  - path: "wiki/features/jdk-method-models.md"
    desc: "CHA默认none、非CHA默认jdk8与none control的验收contract"
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
    desc: "单scope的5 warm-up + 25 formal + 4非CHA none control suite"
  - path: "benchmarks/impact-medium/run-benchmark.sh"
    desc: "单 JVM fixture preparation、资源采样、impact 执行与校验"
  - path: "benchmarks/impact-medium/scripts/invoke-impact.sh"
    desc: "CHA省略algorithm/model与非CHA algorithm相关model默认值的固定CLI invocation"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "42 dependencies、scope baseline、path 与 boundary evidence 验收"
  - path: "benchmarks/impact-medium/scripts/generate-report.py"
    desc: "scope HTML、TSV Schema 与 topology drift validation"
  - path: "benchmarks/impact-medium/scripts/add-scope-comparison.py"
    desc: "双 Report cross-scope absolute change 与 ratio"
  - path: "benchmarks/impact-medium/scripts/publish-scope-matrix.sh"
    desc: "双 scope candidate set 的 transaction publication"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "scope + model + algorithm + k-object depth semantic baseline"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/JdkModelUseCase.java"
    desc: "Stream.map private Function callback到changed dependency fixture"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/RecursiveCallUseCase.java"
    desc: "private static递归到changed dependency fixture"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/DynamicLoadingUseCase.java"
    desc: "Class.forName和ServiceLoader direct/local/same-phi/unsupported fixture"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/src/main/java/com/acme/benchmark/AncestorRetentionUseCase.java"
    desc: "PROJECT override连接路径外external superclass/interface祖先链fixture"
  - path: "benchmarks/impact-medium/results/changed-paths/samples.tsv"
    desc: "最近一次成功 changed-paths suite 的 20 个正式样本"
  - path: "benchmarks/impact-medium/results/full/samples.tsv"
    desc: "最近一次成功 full suite 的 20 个正式样本"
---

# Runbook: Impact Benchmark

## Summary

Canonical入口显式执行`changed-paths`与`full`两种dependency analysis scope。每种scope对`cha`、`rta`、`zero-cfa`、`optimized-0-1-cfa`与`k-obj`分别执行1次warm-up、5次formal sample；四种非CHA algorithm另各执行1次`none`control，共34个独立Java Virtual Machine（JVM）进程。双scope合计68个JVM。CHA省略algorithm/model以验收默认`cha + none`；非CHA显式algorithm并省略model以验收默认`jdk8`。本runbook只定义执行contract；未获用户明确授权时禁止运行matrix。

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

- `changed-paths`：5次warm-up、25次formal和4次非CHA`none`control；共34个JVM。
- `full`：同样34个JVM。
- 双scope共68个JVM：10个CHA默认`none`、48个非CHA默认`jdk8`、8个非CHA`none`control。每个run使用全新进程。
- 每种 scope 的 algorithm 列表按 round 循环左移，降低固定顺序偏差。
- Warm-up 启用 `--call-graph-diagnostics-output`，采集 topology、source、IR 与 dependency path evidence。
- Formal 不启用 diagnostics capture，只采集语义、性能与 graph totals。
- CHA warm-up/formal同时省略algorithm/model；非CHA warm-up/formal显式algorithm并省略model；control显式传非CHAalgorithm和`--jdk-model none`。
- 每种 scope 内 Entrypoint、CGNode、CGEdge、body-policy counts 和 dependency path topology 必须在 warm-up/formal 间稳定。

## Fixture and Semantic Contract

- application POM 固定 42 个 direct dependencies；增加 path、sink、factory 或 scope-conflict fixture 时以等量 direct vendor dependency 替换，避免规模变化干扰 scope 对比。
- `scenario-api:1.0.0 -> 2.0.0`固定产生10类raw change，包含provider registration-only removal。
- application direct `scenario-api:${scenario.api.version}` 是Maven winner；target为`2.0.0`，`path-a -> path-c`与`path-x -> path-y`继续请求`1.0.0`并在raw occurrence graph形成loser path。`changed-paths`必须将两条path都规范化到winner `2.0.0`并完整保留；reactor path也必须使用selected version。
- direct dependency总数保持42：`vendor-lib-35`由`path-sibling`transitive引入；`vendor-lib-34`由`external-plain`transitive引入。Selected external classpath 规模与现有Call Graph baseline不变。
- `scope-conflict-marker` 是direct `test` winner，`external-plain` 同时引入transitive `compile` duplicate。Schema v3 必须删除marker及其occurrence，offline repository中不存在marker JAR，因此任何误请求都会使benchmark失败。
- `path-a` sibling、seed downstream、external sink/factory/plain均不位于到达seed的external path。CHA裁剪无关路径外external target；四种非CHA algorithm将其method body设为no-op或flow-to-cast factory。
- `AncestorRetentionUseCase extends ExternalAncestor extends ExternalGrandParent implements ExternalContract`，PROJECT override调用`ScenarioApi.bodyChanged`。CHA `changed-paths`必须保留三个external祖先type的reachablepublic/private/default concrete method并连接abstract dispatch；同artifact的普通`ExternalPlain.call`仍裁剪。
- CHA裁剪调用不产生`DEPENDENCY_BODY_BOUNDARY_REACHED`，ancestor-retained与pruned target计数必须非零，factory/dangerous transfer计数固定为零。其他algorithm继续验收`CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY`和flow-to-cast factory。
- `full`必须使用真实external body且不产生boundary approximation evidence。
- 普通 no-op external call 不单独使 Module `INCONCLUSIVE`；reactor method 在 `changed-paths` 下始终使用真实 IR。
- `JdkModelUseCase.execute`接收`Stream<String>`并经lazy`Stream.map`注册private`ChangedMapper.apply`，callback调用`ScenarioApi.bodyChanged`；默认`jdk8`必须报告该call chain，`none`按algorithm锁定真实JDK bytecode语义下的candidate/final baseline。
- `RecursiveCallUseCase.execute`进入private static递归，终止分支调用`ScenarioApi.bodyChanged`；五种algorithm和两种scope均必须报告该impact chain。
- `DynamicLoadingUseCase`覆盖Class.forName和ServiceLoader direct/local/same-value phi，以及concat/field/return等unsupported输入；fixture同时覆盖provider class删除和registration-only删除。
- `expected-results.tsv`以`dependency_analysis_scope + jdk_model + call_graph_algorithm + k_obj_depth`为key，维护18组semantic baseline；未授权执行新matrix前为`PENDING`，normal verifier拒绝发布，只有显式calibration run可生成review candidate。
- Verifier 同时要求fixture source repository 不存在`dep-tree-cia-*`、`resolved-artifacts-cia-*`或`module-*.json` evidence 中间产物；Maven `target/` 仍是current target compile的允许例外。

## Metrics Contract

每个 formal sample 记录：

- Total wall、Call Graph stage time。
- Peak Heap Used/Committed、Heap Max、heap sample count。
- Process-tree peak Resident Set Size（RSS）。
- Entrypoint、CGNode、CGEdge、status、exit code。
- `k_obj_depth`配置列；`k-obj`为`1`，其他算法为空。
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

- `samples.tsv`每个formal run一行；CHA为默认`none`，其他algorithm为默认`jdk8`。
- `summary.tsv`每个algorithm一行，记录`k_obj_depth`、`jdk8`、min/median/max、stable graph/body totals、successful sample与scope内相对`zero-cfa` ratio。
- `topology.tsv`来自五种algorithm的effective-default warm-up；Schema v8除Call Graph ranking/path外，保存`k_obj_depth`、model、requested/actual scope、fallback、real/no-op/factory counts、ancestor-retained/pruned target counts和全部dependency path evidence。
- Raw run、topology JSON、candidate TSV 与 failure detail 保留在 `tmp-files/impact-medium-benchmark/`，不提交 Git。

## Atomic Publication

只有以下条件全部满足，matrix 才发布 tracked snapshot：

- 两种scope的68个run全部成功。
- 每种 scope 内 warm-up 与 formal topology 稳定。
- 18组scope/model/algorithm/depth semantic baseline全部通过且不含`PENDING`。
- 两份 HTML Report 均成功生成并完成 cross-scope comparison 注入。
- 两个 candidate result directory 均通过 Schema 与 scope validation。

Publication 一次替换 `results/changed-paths` 与 `results/full`。任一 scope 失败时，旧 tracked snapshot 全部保留；两份 HTML 仍尽量输出 success/failure detail，任务不能标记完成。

## Success Criteria

- 68个独立JVM全部返回semantic success。
- 每个scope的5次warm-up和25次formal sample topology稳定；4次非CHA`none`control只验收语义。
- `expected-results.tsv`中18组`scope + model + algorithm + k_obj_depth` baseline全部通过。
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
