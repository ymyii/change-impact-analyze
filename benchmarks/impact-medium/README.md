# Impact Medium CallGraph Benchmark

本 benchmark 对打包后的 `dependency-analyzer impact` 执行四种 Call Graph algorithm、两种 dependency analysis scope 和`jdk8`/`none` JDK Method Model semantic control。Fixture 固定包含42个direct dependencies、9类bytecode change，以及经`Stream.map` private `Function` callback调用changed dependency的model路径；target 以direct `scenario-api:2.0.0`作为winner，同时两条transitive path继续请求`1.0.0`，用于覆盖Maven mediation loser path；每个scope/model/algorithm的candidate/final call chains由`expected-results.tsv`锁定。

## Canonical suite

先打包 Analyzer，再运行唯一 canonical matrix 入口：

```sh
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

可选 positional argument 是 matrix label；同名 raw/candidate 目录存在时拒绝覆盖。Matrix 显式执行 `changed-paths` 与 `full`，每个 scope 固定执行：

- `rta`、`zero-cfa`、`optimized-0-1-cfa`、`1-object-1-call-site`各1次warm-up，共4个独立Java Virtual Machine（JVM）进程。Warm-up预热fixture、Maven与文件缓存，并通过`--call-graph-diagnostics-output`采集CGNode topology、source与IR。
- 5个round，每个round各运行四种algorithm，共20个正式样本和20个独立JVM进程。
- 每种algorithm额外执行1个`--jdk-model none` semantic control，共4个独立JVM进程；warm-up和正式样本省略该option以验收默认`jdk8`。
- 正式样本按round交错；algorithm列表在每个round循环左移一位，第5个round回到原始顺序。
- 正式样本不设置 diagnostics option，不执行 CGNode ranking、IMethod 子榜、shortest path、IR capture 或反编译。

每个scope使用28个独立Java Virtual Machine（JVM），双scope合计56个，其中48个默认`jdk8` warm-up/formal run和8个`none` control。`run-suite.sh`是单scope内部入口，要求显式设置`BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE`；canonical验收必须使用`run-scope-matrix.sh`。

`BENCHMARK_WALA_REFLECTION_OPTIONS` 默认且正式验收要求为 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。

## Prerequisites

- Java 17：启动 Analyzer。
- 完整 JDK 8：fixture 编译与 `impact --java-home`。
- Maven 3.6.3–3.x、Git、Python 3、POSIX shell。
- macOS `/usr/bin/time -lp` 或 GNU `/usr/bin/time -v`。
- Maven local repository 已缓存 `maven-compiler-plugin:3.13.0`；suite 使用 offline Maven。

可覆盖的 runtime 环境变量：

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `ANALYZER_JAR` | `target/dependency-analyzer.jar` | 待测 shaded JAR |
| `ANALYZER_JAVA` | PATH 中的 `java` | 启动 Analyzer 的 Java 17 executable |
| `MAVEN_BIN` | PATH 中的 `mvn` | 传给 CLI 的 Maven executable |
| `JAVA8_HOME` | 无 | 必填，完整 target JDK 8 home |
| `BENCHMARK_WALA_REFLECTION_OPTIONS` | `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD` | command-wide WALA ReflectionOptions |
| `BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE` | 无 | `run-benchmark.sh` 与 `run-suite.sh` 必填：`changed-paths` 或 `full` |
| `BENCHMARK_JDK_MODEL` | 无 | `run-benchmark.sh` 必填：`jdk8`或`none`；suite自动设置。 |
| `BENCHMARK_MAVEN_REPO` | `$HOME/.m2/repository` | fixture artifact 与 offline Maven repository |
| `BENCHMARK_RUNTIME_ROOT` | `tmp-files/impact-medium-benchmark` | raw run、candidate、topology JSON 与 HTML 根目录 |

`run-benchmark.sh` 是 suite 使用的单进程底层入口。手工诊断时必须显式设置 `BENCHMARK_CALL_GRAPH_ALGORITHM`；`BENCHMARK_CAPTURE_TOPOLOGY=1` 仅用于 warm-up。

## Outputs

固定用户报告：

```text
tmp-files/impact-medium-benchmark/benchmark-report-changed-paths.html
tmp-files/impact-medium-benchmark/benchmark-report-full.html
```

HTML 自包含 CSS，不使用 JavaScript 或外部 asset。每份报告包含本 scope 的四种 algorithm、五个正式样本、Min/median/max、Call Graph topology、dependency path evidence 和 method-body boundary 计数。两份报告均包含 changed-paths 相对 full 的 absolute change 与 ratio；不设置未经实测的固定提速阈值。

Git 管理的最近一次完整成功快照：

```text
benchmarks/impact-medium/results/changed-paths/samples.tsv
benchmarks/impact-medium/results/changed-paths/summary.tsv
benchmarks/impact-medium/results/changed-paths/topology.tsv
benchmarks/impact-medium/results/full/samples.tsv
benchmarks/impact-medium/results/full/summary.tsv
benchmarks/impact-medium/results/full/topology.tsv
```

- `samples.tsv`：20个默认`jdk8`正式样本；包含model、wall、Call Graph、heap、RSS、graph、method-body boundary、status与环境identity。
- `summary.tsv`：每种algorithm一行；包含scope、model、资源Min/median/max、稳定topology、boundary node计数和相对`zero-cfa` ratio。
- `topology.tsv`：只发布默认`jdk8` warm-up；除`RANKED_CGNODE`、`RELATED_IMETHOD`、`REACHABILITY_PATH`外，使用`DEPENDENCY_SCOPE`与`DEPENDENCY_PATH`保存model、requested/actual scope、fallback、artifact/method policy计数和全部changed dependency path evidence。

只有两个scope的56个run全部通过semantic verification，且各scope内默认`jdk8` warm-up与五个正式样本的Entrypoint、CGNode、CGEdge、artifact policy和boundary node计数完全一致时，才原子替换两组tracked snapshot。`none` control只参加语义验收，不进入performance Report或tracked snapshot。任一scope出现`FAILED`或`TOPOLOGY_DRIFT`时，旧tracked snapshot整体不变；两份failure HTML、candidate TSV、raw run、topology JSON与`failure.txt`保留在`tmp-files/impact-medium-benchmark/`。

## Metrics

每个正式样本记录：

- Total wall time、CallGraph stage time。
- 每 100 ms 观察得到的 Peak Heap Used、Peak Heap Committed、Heap Max 与 sample count；TRACE snapshot 仍只在启动和每 10 s 输出，command 关闭前强制 final observation。
- 每 250 ms 采样的 process-tree peak Resident Set Size（RSS），作为辅助指标。
- Entrypoint、CGNode、CGEdge、execution status、exit code。
- real-IR/no-op external artifact、real-IR/no-op/factory method node 和 dangerous transfer 数量。
- Analyzer SHA-256、Git commit/dirty、OS、architecture、Analyzer Java、target JDK、Maven identity。

全图CGNode/CGEdge totals、排行榜和path均包含WALA fake root/fake world-clinit及其incident edge；sentinel使用typed role和badge显式标识。父榜不合并Context，以精确CGNode为单位，先按related CGNode count降序，再按distinct related IMethod、raw CGEdge与稳定CGNode identity排序。每个父CGNode下的子榜按IMethod聚合，以该IMethod代表的related CGNode count、raw CGEdge和稳定Method identity排序；展开项保留deterministic前10个具体CGNode/Context example与omitted count，从而定位同一Method的Context或points-to膨胀，同时避免单个高膨胀Method令HTML/TSV不可浏览。项目不输出独立points-to set排行榜。

## Semantic success criteria

每个 run 必须同时满足：

- Analyzer exit code 为 `0`；`changed-paths` 为 `Completed with coverage limitations`，`full` 为 `Completed`。
- 实际 Algorithm 等于请求值；实际 WALA ReflectionOptions 等于 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。
- Overall `JDK method model`等于请求值；默认run为`jdk8`，control为`none`。
- requested/actual dependency analysis scope 均等于当前 matrix scope，不允许 fixture fallback。
- POM 有 42 个 direct dependencies；两个 Module 合计 18 个 raw changed members。
- `expected-results.tsv`中存在该scope/model/algorithm的已校准candidate/final call chains。
- 默认`jdk8`的Affected Call Chains包含`JdkModelUseCase → JdkModelUseCase$ChangedMapper.apply → ScenarioApi.bodyChanged`路径；`none`按algorithm锁定真实JDK bytecode语义下的candidate/final baseline。
- `changed-paths` 包含三条到 seed 的 path evidence、no-op sibling/downstream、dangerous transfer 和 flow-to-cast factory evidence；`full` 不产生 no-op/factory/boundary evidence。
- Affected Call Chains 页面包含 Structural Reference Path 与 filtered candidate。
- Dependency Changes 页面包含 final、filtered、structural badge 与反编译代码 evidence。
- Overall、Module Index、Affected Call Chains、Dependency Changes 四页均存在。

## Historical comparison

保存任意两个版本的 `summary.tsv`，执行：

```sh
benchmarks/impact-medium/scripts/compare-summaries.sh \
  /path/to/baseline-summary.tsv \
  /path/to/candidate-summary.tsv
```

输出各 algorithm 的 median wall、CallGraph、Peak Heap Used、RSS、CGNode、CGEdge 的 baseline、candidate、absolute change 与 ratio。没有固定 threshold，也不输出 PASS/FAIL。

## Failure entrypoints

- `<run>/logs/stderr.log`：Preflight、CLI、Runtime Metrics 与 pipeline failure。
- `<run>/logs/verification.txt`：42 dependencies、18 raw changes、scope semantic baseline、Structural Reference Path、Algorithm/ReflectionOptions failure。
- `<run>/logs/metrics.tsv`：即使 run failure 也尽量保留的单样本指标。
- `<run>/topology.json`：默认`jdk8` warm-up Schema v5 CGNode topology、model selection、dependency path、body policy、sentinel role、declared entrypoint/WALA sentinel reachability path、IMethod子榜、shortest chain、source与IR。
- `<suite>-candidate-results/failure.txt`：suite Schema、环境或 topology drift failure。
- `benchmark-report-changed-paths.html`、`benchmark-report-full.html`：成功或失败均更新；失败时明确说明 tracked TSV 未发布。
