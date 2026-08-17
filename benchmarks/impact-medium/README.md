# Impact Medium CHA Benchmark

本 benchmark 对打包后的 `dependency-analyzer impact` 执行 Class Hierarchy Analysis（CHA，类层次分析）canonical matrix。覆盖 `changed-paths` 与 `full` 两种 dependency analysis scope、`--dependency-include 'com.acme.impact:scenario-api'` changed-member来源边界、固定启用的 Static Single Assignment（SSA，静态单赋值）equivalence，以及固定的 CHA Impact Path pruning extensions。Fixture 固定包含 40 个 direct dependencies、10 类 change、external ancestor retention、JDK declared dispatch pruning 和真实/虚假 receiver path；semantic verification继续要求`path-a → path-c → scenario-api`与`path-x → path-y → scenario-api`中间JAR路径存在。

## Canonical suite

先打包 Analyzer，再运行唯一 matrix 入口：

```sh
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

每个 scope 固定执行：

- 1 次 `cha + none` warm-up，采集Schema 12 topology。
- 5 次 `cha + none` formal，每次使用全新 Java Virtual Machine（JVM）进程。

双 scope 合计 12 个 JVM 进程。`run-suite.sh` 是单 scope 内部入口，canonical 验收使用 `run-scope-matrix.sh`。Runner 固定传入 `--analysis-parallelism 2`；CHA 固定 `jdk-model=none`，WALA Reflection 配置不应用；SSA equivalence和`cha-local-receiver-inference`不可关闭。

本次收敛后的两个Impact call-chain baseline位于`expected-results.tsv`，在明确授权calibration前均保持`PENDING`。不得把`k-obj`historical数据写入CHA canonical baseline。

## Prerequisites

- Java 17：启动 Analyzer。
- 完整 JDK 8：编译 fixture，并传给 `impact --java-home`。
- Maven 3.6.3–3.x、Git、Python 3、POSIX shell。
- macOS `/usr/bin/time -lp` 或 GNU `/usr/bin/time -v`。
- Maven local repository 已缓存 `maven-compiler-plugin:3.13.0`；suite 使用 offline Maven。

主要 runtime 环境变量：

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `ANALYZER_JAR` | `target/dependency-analyzer.jar` | 待测 shaded JAR |
| `ANALYZER_JAVA` | PATH 中的 `java` | 启动 Analyzer 的 Java 17 executable |
| `MAVEN_BIN` | PATH 中的 `mvn` | 传给 CLI 的 Maven executable |
| `JAVA8_HOME` | 无 | 必填，完整 target JDK 8 home |
| `BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE` | 无 | `changed-paths` 或 `full` |
| `BENCHMARK_JDK_MODEL` | 无 | 底层入口必须为 `none`；suite 自动设置 |
| `BENCHMARK_CALIBRATION` | `0` | `1` 允许 `PENDING` baseline，且禁止发布 tracked snapshot |
| `BENCHMARK_RUNTIME_ROOT` | `tmp-files/impact-medium-benchmark` | raw run、candidate、topology JSON 与 HTML 根目录 |

`run-benchmark.sh` 是单进程底层入口。手工诊断时必须显式设置 `BENCHMARK_CALL_GRAPH_ALGORITHM=cha`；`BENCHMARK_CAPTURE_TOPOLOGY=1` 仅用于 warm-up。

## Outputs

固定 HTML：

```text
tmp-files/impact-medium-benchmark/benchmark-report-changed-paths.html
tmp-files/impact-medium-benchmark/benchmark-report-full.html
```

每份报告包含 5 个正式样本、min/median/max、Call Graph topology、dependency path evidence 与 external-target pruning 计数。两份报告增加 changed-paths 相对 full 的 absolute change 与 ratio；不包含 algorithm comparison tab。

成功 calibration 后，每个 scope 发布：

- `samples.tsv`：5 个 formal 样本及资源、graph、状态和环境 identity。
- `summary.tsv`：1 行 CHA 聚合结果；不含 `k_obj_depth` 或相对 ZeroCFA ratio。
- `topology.tsv`：CHA warm-up 的 topology、scope、path、ancestor retention 和 pruning evidence。

当前不保留历史 tracked TSV；只有两个 scope 的 12 个进程全部通过且 topology 稳定时，publisher 才原子写入新快照。`BENCHMARK_CALIBRATION=1` 只生成 candidate 与 HTML。

## Semantic success criteria

每个 run 必须满足：

- Analyzer exit code 为 `0`，状态为 `Completed with coverage limitations`。
- Algorithm 为 `cha`，JDK Method Model 为 `none`，Reflection 显示 `not applied by cha`。
- requested/actual dependency analysis scope 等于当前 matrix scope。
- fixture POM 有 40 个 direct dependencies；两个 Module 合计 22 个 raw changed members。
- `scope-conflict-marker` 不进入报告，fixture repository 不泄漏 dependency evidence 临时文件。
- `expected-results.tsv` 存在当前 scope baseline；`PENDING` 只允许显式 calibration。
- Affected Call Chains 保留递归、structural reference、external ancestor 和 dependency path evidence。
- CHA 裁剪无关 external target，不为被裁剪调用生成 dependency boundary limitation。
- 固定 local receiver extension 保留 `ChangedReceiver`，删除 `unrelatedReceiverPath`，并输出 extension 指标。
- Report包含JDK声明分派限制和非零裁剪指标；Schema 12保存固定SSA状态、单local extension注册表和模块裁剪证据。
- Overall、Module Index、Affected Call Chains、Dependency Changes 页面完整。

## Contract verification

合同测试不运行 benchmark：

```sh
python3 -m unittest discover \
  -s benchmarks/impact-medium/tests -p 'test_*.py'

for script in benchmarks/impact-medium/*.sh \
    benchmarks/impact-medium/scripts/*.sh; do
  sh -n "$script"
done
```

保存任意两个 CHA `summary.tsv` 后，可执行 `scripts/compare-summaries.sh` 对比 median wall、CallGraph、heap、Resident Set Size（RSS）、CGNode 与 CGEdge。没有固定 threshold。
