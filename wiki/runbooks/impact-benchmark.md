---
title: "Impact Benchmark"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "benchmark 持续验证打包后的 impact pipeline"
  - path: "wiki/features/call-graph-engine.md"
    desc: "只读CGNode topology、IMethod子榜、shortest path、source与IR capture"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "100 ms heap observation 与 command final peak summary"
  - path: "wiki/features/impact-tracing.md"
    desc: "semantic verifier 校验 Call Graph path、Structural Reference Path 与 SSA filtering"
  - path: "wiki/features/report-generator.md"
    desc: "fixture semantic contract 依赖 Overall 与三个 Module pages"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "suite 使用已打包的 shaded Analyzer JAR"
code_refs:
  - path: "benchmarks/impact-medium/README.md"
    desc: "用户手册、环境变量、Schema 与 failure entrypoint"
  - path: "benchmarks/impact-medium/run-suite.sh"
    desc: "canonical 3 warm-up + 15 formal suite 与 tracked TSV 原子发布"
  - path: "benchmarks/impact-medium/run-benchmark.sh"
    desc: "单 JVM fixture preparation、资源采样、impact 执行与校验"
  - path: "benchmarks/impact-medium/scripts/invoke-impact.sh"
    desc: "固定 CLI 参数与可选 diagnostics output"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "42 dependencies、9 raw changes、6/5 chains 与 HTML pages 验收"
  - path: "benchmarks/impact-medium/scripts/generate-report.py"
    desc: "HTML、TSV Schema、topology drift 与 comparison 生成"
  - path: "benchmarks/impact-medium/scripts/publish-results.sh"
    desc: "完整候选集预校验与 tracked TSV 逐文件原子替换"
  - path: "benchmarks/impact-medium/scripts/compare-summaries.sh"
    desc: "任意两个历史 summary 的 absolute change 与 ratio"
  - path: "benchmarks/impact-medium/expected-results.tsv"
    desc: "versioned per-algorithm candidate/final semantic baseline"
  - path: "benchmarks/impact-medium/results/samples.tsv"
    desc: "最近一次成功 suite 的 15 个正式样本"
  - path: "benchmarks/impact-medium/results/summary.tsv"
    desc: "最近一次成功 suite 的三种 algorithm 汇总"
  - path: "benchmarks/impact-medium/results/topology.tsv"
    desc: "最近一次成功warm-up的CGNode父榜、IMethod子榜与entrypoint path snapshot"
  - path: "benchmarks/impact-medium/tests/test_generate_report.py"
    desc: "HTML/TSV、escaping、topology drift、原子发布与历史 comparison 测试"
  - path: "docs/user-manual.md"
    desc: "产品用户手册中的 canonical suite 入口"
---

# Runbook: Impact Benchmark

## Summary

Canonical suite在同一Analyzer JAR与环境下对`rta`、`zero-cfa`、`optimized-0-1-cfa`分别执行1次warm-up topology capture和5次正式样本，共18个独立Java Virtual Machine（JVM）进程。用户入口是固定的self-contained HTML，三种algorithm与comparison各占一个CSS-only tab；最近一次完整成功的15个正式样本、summary与CGNode topology由Git管理。

## Prerequisites

- Java 17用于启动`target/dependency-analyzer.jar`。
- `JAVA8_HOME`指向包含`java`、`javac`、`jar`和`rt.jar`的完整JDK 8。
- Maven 3.6.3–3.x、Git、Python 3与POSIX shell。
- Maven local repository已缓存`maven-compiler-plugin:3.13.0`；fixture以offline Maven运行。
- macOS`/usr/bin/time -lp`或GNU`/usr/bin/time -v`。

## Commands

```sh
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-suite.sh
```

可选suite label只接受字母、数字、dot、underscore与hyphen；同名raw/candidate结果存在时拒绝覆盖。`BENCHMARK_WALA_REFLECTION_OPTIONS`默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`，正常suite同时将其作为成功条件。

手工执行一个底层run仅用于diagnosis：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  BENCHMARK_CALL_GRAPH_ALGORITHM=zero-cfa \
  BENCHMARK_RUN_KIND=formal \
  BENCHMARK_CAPTURE_TOPOLOGY=0 \
  benchmarks/impact-medium/run-benchmark.sh diagnosis-01
```

## Suite Schedule

- Warm-up：每种algorithm一次；设置`--call-graph-diagnostics-output`，预热Maven/fixture/filesystem cache并采集唯一已完成Call Graph的CGNode topology、source与IR。
- Formal：5个round；每个round三种algorithm各一次。顺序在`rta → zero-cfa → optimized-0-1-cfa`、`zero-cfa → optimized-0-1-cfa → rta`、`optimized-0-1-cfa → rta → zero-cfa`间轮换。
- 每个run启动全新JVM。Formal不设置diagnostics option，因此不执行CGNode ranking、IMethod子榜、shortest path、IR capture或decompilation。

## Fixture and Semantic Contract

- application POM固定42个compile-scope direct dependencies：40个vendor JAR、`scenario-api`、`legacy-impact-bridge`。
- `scenario-api:1.0.0 -> 2.0.0`固定产生9类raw change：`CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- 三种algorithm在`expected-results.tsv`中均锁定`6 / 5`条candidate/final call chains。
- 每个run同时验证Structural Reference Path、filtered candidate、反编译code evidence、四页impact Report、实际algorithm和默认WALA ReflectionOptions。

## Metrics Contract

单run的`logs/metrics.tsv`即使failure也尽量输出一行，包含：

- Total wall、CallGraph stage time。
- Peak Heap Used、Peak Heap Committed、Heap Max、heap sample count。
- Process-tree peak Resident Set Size（RSS）。
- Entrypoint、CGNode、CGEdge、status、exit code。
- Analyzer SHA-256、Git commit/dirty、OS、architecture、Analyzer Java、target JDK、Maven identity。

Runtime Metrics每100 ms观察heap，启动和每10 s才输出TRACE snapshot；command关闭前强制final observation并输出`Runtime metrics summary`。Process tree每250 ms采样RSS，作为heap主指标之外的辅助数据。

## Topology Contract

- 父榜以精确CGNode为单位，不合并WALA Context；identity包含Method、Context、graph node id与`walaSynthetic`。
- Caller按related callee CGNode count降序；Callee按related caller CGNode count降序。次级排序依次是distinct related IMethod、raw CGEdge与稳定CGNode identity；每个direction取Top 10。
- 每个父CGNode下按IMethod聚合related CGNode并取Top 10；排序依次是related CGNode count、raw CGEdge与稳定Method identity。子项保存完整count、deterministic前10个exact CGNode/Context example与omitted count，用于定位Context或points-to传播造成的节点膨胀并限制报告体积；没有独立points-to set排行榜。
- Ranking排除WALA fake root、fake world-clinit及其incident edge；全图CGNode/CGEdge totals继续包含sentinel。
- Caller与Callee两个父榜的每个CGNode都固定展示`Declared entrypoint shortest CGNode chains`；列出所有可达declared entrypoint CGNode，每个entrypoint输出一条deterministic shortest CGNode chain。Reverse breadth-first search（BFS）使用visited set，路径按稳定CGNode identity解tie。
- Self-loop与多CGNode strongly connected component（SCC）统一标记`CYCLE`；没有declared entrypoint path时输出`UNREACHABLE_FROM_DECLARED_ENTRYPOINTS`。
- 每个父榜CGNode展示WALA IR。Capture使用`IMethod.isWalaSynthetic()`标记WALA generated/summary Method。Source依次支持PROJECT/reactor classes directory、dependency JAR和JDK 8 boot/ext JAR。Vineflower失败使用ASM instructions；synthetic/WALA summary无bytecode时source为`UNAVAILABLE`，可以仅展示IR。
- Warm-up与5个Formal的Entrypoint/CGNode/CGEdge必须完全一致，否则Formal标记`TOPOLOGY_DRIFT`。

## Outputs and Publication

固定HTML：

```text
tmp-files/impact-medium-benchmark/benchmark-report.html
```

Raw run、topology JSON、candidate TSV与failure detail保留在`tmp-files/impact-medium-benchmark/`，由`.gitignore`排除。

Tracked snapshot：

```text
benchmarks/impact-medium/results/samples.tsv
benchmarks/impact-medium/results/summary.tsv
benchmarks/impact-medium/results/topology.tsv
```

- `samples.tsv`每个Formal一行。
- `summary.tsv`每个algorithm一行，包含Min/median/max、稳定graph totals、successful sample数及相对`zero-cfa`ratio。
- `topology.tsv`使用`RANKED_CGNODE`、`RELATED_IMETHOD`、`ENTRYPOINT_PATH`三种record。Multiline source与IR只进入HTML；TSV保存各自status与SHA-256。

18个run和Schema/topology validation全部成功后，suite才原子替换三个canonical TSV。任何failure都保留旧snapshot，只更新failure HTML与tmp candidate。

## Historical Comparison

```sh
benchmarks/impact-medium/scripts/compare-summaries.sh \
  /path/to/baseline-summary.tsv \
  /path/to/candidate-summary.tsv
```

输出三种algorithm的median wall、CallGraph、Peak Heap Used、RSS、CGNode、CGEdge的baseline/candidate、absolute change与ratio。不存在固定Wall/RSS threshold，不输出PASS/FAIL，也不推导algorithm优劣。

## Failure Entrypoints

- `<run>/logs/stderr.log`：Preflight、CLI、Runtime Metrics与pipeline failure。
- `<run>/logs/verification.txt`：semantic contract failure。
- `<run>/logs/metrics.tsv`：单样本status与可恢复指标。
- `<run>/topology.json`：warm-up Schema、CGNode/IMethod关系、source、IR或path failure。
- `<suite>-candidate-results/failure.txt`：environment、sample count、Schema或`TOPOLOGY_DRIFT`。
- `benchmark-report.html`：success/failure统一用户入口；failure明确说明tracked TSV未发布。
