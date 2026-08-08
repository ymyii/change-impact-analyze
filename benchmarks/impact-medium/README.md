# Impact Medium Benchmark

本 benchmark 持续验证打包后的 `dependency-analyzer impact`：构造一个 Java 8 Maven application、42 个 compile-scope external dependencies 和 9 类 bytecode changes，执行分析并采集阶段耗时、CPU、内存与报告完整性。

## 目录

- `fixtures/application/baseline/`：`impact-baseline` snapshot。
- `fixtures/application/target-overlay/`：生成 `impact-target` snapshot 的 source overlay。
- `fixtures/artifacts/`：`scenario-api`、`legacy-impact-bridge` 和 vendor artifact source。
- `scripts/prepare-fixture.sh`：编译并安装 fixture artifacts，生成临时 Git project。
- `scripts/invoke-impact.sh`：唯一的 `impact` CLI 参数入口。
- `scripts/verify-report.sh`：校验 exit code、42 个 dependencies、raw changes、final/filtered call chains、Structural Reference Path 和反编译代码证据。
- `run-benchmark.sh`：准备 fixture、采样资源、执行分析和校验报告。

所有生成物默认位于 `tmp-files/impact-medium-benchmark/<label>/`。

## Prerequisites

- Java 17：启动 Analyzer。
- 完整 JDK 8：fixture 编译与 `impact --java-home`。
- Maven 3.6.3–3.x、Git、POSIX shell。
- macOS `/usr/bin/time -lp` 或 GNU `/usr/bin/time -v`。
- 已打包的 `target/dependency-analyzer.jar`。
- Maven local repository 已缓存 `maven-compiler-plugin:3.13.0`；正常执行一次本工程 `mvn package` 即可。benchmark 本身以 offline Maven 运行，避免网络波动进入计时。

先打包 Analyzer：

```sh
mvn package
```

## 执行

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  BENCHMARK_CALL_GRAPH_ALGORITHM=zero-cfa \
  benchmarks/impact-medium/run-benchmark.sh
```

指定稳定 label 便于对比：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  BENCHMARK_CALL_GRAPH_ALGORITHM=rta \
  benchmarks/impact-medium/run-benchmark.sh candidate-01
```

同名结果目录已存在时脚本拒绝覆盖。可通过环境变量替换 runtime：

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `ANALYZER_JAR` | `target/dependency-analyzer.jar` | 待测 shaded JAR |
| `ANALYZER_JAVA` | PATH 中的 `java` | 启动 Analyzer 的 Java 17 executable |
| `MAVEN_BIN` | PATH 中的 `mvn` | 传给 CLI 的 Maven executable |
| `JAVA8_HOME` | 无 | 必填，target JDK 8 home |
| `BENCHMARK_CALL_GRAPH_ALGORITHM` | 无 | 必填；仅接受 `rta`、`zero-cfa`、`optimized-0-1-cfa` |
| `BENCHMARK_WALA_REFLECTION_OPTIONS` | `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD` | 传给 `--wala-reflection-options`；接受 WALA `ReflectionOptions` enum name |
| `BENCHMARK_CALIBRATION` | `0` | 仅 `1` 可临时跳过未来尚未锁定 algorithm 的 candidate/final count；其余报告断言仍执行 |
| `BENCHMARK_MAVEN_REPO` | `$HOME/.m2/repository` | fixture artifacts 安装位置和 Maven local repository |
| `BENCHMARK_RUNTIME_ROOT` | `tmp-files/impact-medium-benchmark` | report、log、config 和临时 Git project 根目录 |

如果使用全新的 `BENCHMARK_MAVEN_REPO`，须先将 Maven lifecycle plugin 及其依赖预热到该 repository；fixture 自有的 42 个 artifacts 会由准备脚本生成。

## 固定场景

`scenario-api:1.0.0 -> 2.0.0` 必须产生以下 9 个 raw changes：

1. `CLASS_ADDED`
2. `CLASS_REMOVED`
3. `METHOD_ADDED`
4. `METHOD_REMOVED`
5. `METHOD_DESCRIPTOR_CHANGED`
6. `METHOD_BODY_CHANGED`
7. `FIELD_ADDED`
8. `FIELD_REMOVED`
9. `FIELD_DESCRIPTOR_CHANGED`

`legacy-impact-bridge` 固定以 API v1 编译，使 target application 在升级到 API v2 后仍保留 removed method、old descriptor、removed field 和 removed class reference。40 个 vendor JAR 均包含一个 class，用于稳定构造中型 direct dependency set。

## 成功条件

脚本仅在下列条件全部满足时返回 `0`：

- Analyzer exit code 为 `0`，Overall status 为 `Completed`。
- Overall technical details 中 Algorithm 必须与显式 `BENCHMARK_CALL_GRAPH_ALGORITHM` 一致。
- Overall technical details 中 WALA ReflectionOptions 必须与 `BENCHMARK_WALA_REFLECTION_OPTIONS` 一致。
- application POM 包含 42 个 direct dependencies。
- Overall technical details 中 raw changed members 为 `9`；Changes 页面仅展示有 candidate/final/Structural Reference Path 的变更，并隐藏三类 added change。
- Candidate / final call chains 必须匹配 versioned `expected-results.tsv` 中该 algorithm 的已锁定值。`rta`、`zero-cfa`、`optimized-0-1-cfa` 经关键路径审查后均锁定为 `6 / 5`；新增 algorithm 首轮只允许用 `BENCHMARK_CALIBRATION=1` 运行并人工审查。
- Affected Call Chains 页面包含 Structural Reference Path；Changes 页面包含 final、filtered、structural badge 与反编译 Unified diff。
- Overall、Module Index、Affected Call Chains、Dependency Changes 四个 HTML 页面均存在。

## 结果目录

```text
tmp-files/impact-medium-benchmark/<label>/
├── config/                 # impact config/cache/workspace
├── fixture/project/        # 带 impact-baseline/impact-target tags 的临时 Git project
├── logs/
│   ├── exit-code.txt
│   ├── process-tree.csv
│   ├── metrics.tsv
│   ├── run-metadata.txt
│   ├── stderr.log
│   ├── stdout.log
│   ├── time.txt
│   └── verification.txt
└── reports/
    ├── impact-report.html
    └── impact-report-modules/
```

`time.txt` 的 `real/user/sys` 是总耗时与 CPU time。`process-tree.csv` 每 250 ms 采样 Analyzer 及 Maven/Javac 子进程的 aggregate RSS/CPU。`metrics.tsv` 以 machine-readable TSV 固定记录 algorithm、WALA ReflectionOptions、Call Graph nodes/edges、Wall time 与 process-tree peak RSS KiB。macOS `time` 的 maximum resident set size 单位为 byte，GNU `time` 为 KiB。

每个 algorithm 以同一环境至少成功运行三次；使用 `scripts/summarize-runs.sh` 读取三个或更多 run directory，并输出 Wall time 与 process-tree peak RSS 的中位数。脚本拒绝混合 algorithm、calibration run 或不同 JAR SHA-256、JVM、JDK 8、Maven repository、fixture、CPU、`analysis-parallelism` 的结果。不同 algorithm 的性能比较只读取该 summary 输出。`front-parallel` 包含并发的 `baseline-dependency` 与 `target-build`，阶段时间不能全部相加推导 Wall time。

```sh
benchmarks/impact-medium/scripts/summarize-runs.sh \
  tmp-files/impact-medium-benchmark/rta-01 \
  tmp-files/impact-medium-benchmark/rta-02 \
  tmp-files/impact-medium-benchmark/rta-03 \
  > tmp-files/impact-medium-benchmark/rta-summary.tsv

benchmarks/impact-medium/scripts/compare-summaries.sh \
  tmp-files/impact-medium-benchmark/rta-summary.tsv \
  tmp-files/impact-medium-benchmark/zero-cfa-summary.tsv
```

`performance-thresholds.tsv` 锁定 RTA 相对 `zero-cfa` 的中位数门槛：Wall time 不超过 `3.50x`，process-tree peak RSS 不超过 `1.75x`。`compare-summaries.sh` 只接收两个 summary，要求相同 WALA ReflectionOptions，并同时执行两项门禁。

## Failure Entrypoints

- `logs/stderr.log`：Preflight 与 CLI failure。
- `logs/stdout.log`：各 pipeline task、Call Graph 和 JAR diff diagnostics。
- `logs/verification.txt`：场景或报告完整性 failure。
- `logs/time.txt`：进程退出及资源统计。
- `fixture/project/pom.xml`：42 个 dependency coordinates 和 target API version。
- Maven offline plugin resolution failure：先对 `BENCHMARK_MAVEN_REPO` 执行正常 Maven build 预热。
