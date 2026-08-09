# Impact Medium CallGraph Benchmark

本 benchmark 对打包后的 `dependency-analyzer impact` 执行三种 Call Graph algorithm 的可复现对比。Fixture 固定包含 42 个 compile-scope external dependencies 和 9 类 bytecode change；semantic contract 继续由 `expected-results.tsv` 锁定为每种 algorithm `6 / 5` 条 candidate/final call chains。

## Canonical suite

先打包 Analyzer，再运行唯一 suite 入口：

```sh
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-suite.sh
```

可选 positional argument 是 suite label；同名 raw/candidate 目录存在时拒绝覆盖。Suite 固定执行：

- `rta`、`zero-cfa`、`optimized-0-1-cfa` 各 1 次 warm-up，共 3 个独立 Java Virtual Machine（JVM）进程。Warm-up 预热 fixture、Maven 与文件缓存，并通过 `--call-graph-diagnostics-output` 采集 CGNode topology、source 与 IR。
- 5 个 round，每个 round 各运行三种 algorithm，共 15 个正式样本和 15 个独立 JVM 进程。
- 正式样本按 round 交错，algorithm 顺序在 `rta → zero-cfa → optimized-0-1-cfa`、`zero-cfa → optimized-0-1-cfa → rta`、`optimized-0-1-cfa → rta → zero-cfa` 之间轮换。
- 正式样本不设置 diagnostics option，不执行 CGNode ranking、IMethod 子榜、shortest path、IR capture 或反编译。

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
| `BENCHMARK_MAVEN_REPO` | `$HOME/.m2/repository` | fixture artifact 与 offline Maven repository |
| `BENCHMARK_RUNTIME_ROOT` | `tmp-files/impact-medium-benchmark` | raw run、candidate、topology JSON 与 HTML 根目录 |

`run-benchmark.sh` 是 suite 使用的单进程底层入口。手工诊断时必须显式设置 `BENCHMARK_CALL_GRAPH_ALGORITHM`；`BENCHMARK_CAPTURE_TOPOLOGY=1` 仅用于 warm-up。

## Outputs

固定用户报告：

```text
tmp-files/impact-medium-benchmark/benchmark-report.html
```

HTML 自包含 CSS，不使用 JavaScript 或外部 asset。`rta`、`zero-cfa`、`optimized-0-1-cfa`与comparison各占一个CSS-only tab。每个algorithm tab包含五个正式样本、Min/median/max、Top 10 caller CGNode、Top 10 callee CGNode、每个父CGNode下按IMethod聚合的Top 10相关节点、每个可达declared entrypoint的deterministic shortest CGNode chain、cycle、decompiled source和WALA IR。Generated Method没有bytecode source时允许仅展示IR。Comparison以`zero-cfa`为ratio baseline；ratio仅描述数据，不自动判定algorithm优劣。

Git 管理的最近一次完整成功快照：

```text
benchmarks/impact-medium/results/samples.tsv
benchmarks/impact-medium/results/summary.tsv
benchmarks/impact-medium/results/topology.tsv
```

- `samples.tsv`：15 个正式样本；包含 wall、CallGraph、heap、RSS、graph、status 与环境 identity。
- `summary.tsv`：每种 algorithm 一行；包含资源 Min/median/max、稳定 topology 和相对 `zero-cfa` ratio。
- `topology.tsv`：使用`RANKED_CGNODE`、`RELATED_IMETHOD`、`ENTRYPOINT_PATH`三种record；保存父CGNode、`wala_synthetic`、子榜IMethod、related CGNode count、最多10个deterministic Context example、omitted count和shortest CGNode path。Multiline source/IR只进入HTML；TSV保存各自status与SHA-256。

Suite 只有在 18 个 run 全部通过 semantic verification，且 warm-up 与五个正式样本的 Entrypoint/CGNode/CGEdge 完全一致时，才逐文件原子替换 tracked TSV。出现 `FAILED` 或 `TOPOLOGY_DRIFT` 时，旧 tracked snapshot 不变；failure HTML、candidate TSV、raw run、topology JSON 与 `failure.txt` 保留在 `tmp-files/impact-medium-benchmark/`。

## Metrics

每个正式样本记录：

- Total wall time、CallGraph stage time。
- 每 100 ms 观察得到的 Peak Heap Used、Peak Heap Committed、Heap Max 与 sample count；TRACE snapshot 仍只在启动和每 10 s 输出，command 关闭前强制 final observation。
- 每 250 ms 采样的 process-tree peak Resident Set Size（RSS），作为辅助指标。
- Entrypoint、CGNode、CGEdge、execution status、exit code。
- Analyzer SHA-256、Git commit/dirty、OS、architecture、Analyzer Java、target JDK、Maven identity。

全图CGNode/CGEdge totals包含WALA fake root/world-clinit；排行榜排除两个sentinel及其incident edge。父榜不合并Context，以精确CGNode为单位，先按related CGNode count降序，再按distinct related IMethod、raw CGEdge与稳定CGNode identity排序。每个父CGNode下的子榜按IMethod聚合，以该IMethod代表的related CGNode count、raw CGEdge和稳定Method identity排序；展开项保留deterministic前10个具体CGNode/Context example与omitted count，从而定位同一Method的Context或points-to膨胀，同时避免单个高膨胀Method令HTML/TSV不可浏览。项目不输出独立points-to set排行榜。

## Semantic success criteria

每个 run 必须同时满足：

- Analyzer exit code 为 `0`，Overall status 为 `Completed`。
- 实际 Algorithm 等于请求值；实际 WALA ReflectionOptions 等于 `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。
- POM 有 42 个 direct dependencies；Overall 有 9 个 raw changed members。
- `expected-results.tsv` 中该 algorithm 的 candidate/final call chains 为 `6 / 5`。
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
- `<run>/logs/verification.txt`：42 dependencies、9 raw changes、`6 / 5` chains、Structural Reference Path、Algorithm/ReflectionOptions failure。
- `<run>/logs/metrics.tsv`：即使 run failure 也尽量保留的单样本指标。
- `<run>/topology.json`：warm-up Schema v2 CGNode topology、IMethod子榜、shortest chain、source与IR。
- `<suite>-candidate-results/failure.txt`：suite Schema、环境或 topology drift failure。
- `benchmark-report.html`：成功或失败均更新的用户入口；失败时明确说明 tracked TSV 未发布。
