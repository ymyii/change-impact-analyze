---
title: "Impact Benchmark"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "benchmark 持续验证打包后的 impact pipeline"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "fixture 固定产生 9 类 raw ChangePoint"
  - path: "wiki/features/impact-tracing.md"
    desc: "fixture 验证 Call Graph path、Structural Impact 与 SSA filtering"
  - path: "wiki/features/report-generator.md"
    desc: "报告完整性校验依赖 Overall 与三个 Module pages contract"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "benchmark 使用已打包的 shaded Analyzer JAR"
code_refs:
  - path: "benchmarks/impact-medium/README.md"
    desc: "benchmark 用户手册、环境变量和 measurement contract"
  - path: "benchmarks/impact-medium/run-benchmark.sh"
    desc: "fixture preparation、资源采样、impact 执行和校验总入口"
  - path: "benchmarks/impact-medium/scripts/prepare-fixture.sh"
    desc: "JAR source compilation、Maven local repository 安装和临时 Git refs 生成"
  - path: "benchmarks/impact-medium/scripts/invoke-impact.sh"
    desc: "固定 benchmark CLI 参数"
  - path: "benchmarks/impact-medium/scripts/verify-report.sh"
    desc: "dependency、ChangePoint、Call Chain 与 HTML pages 验收"
  - path: "benchmarks/impact-medium/fixtures/application/baseline/pom.xml"
    desc: "42 个 compile-scope direct dependencies"
  - path: "benchmarks/impact-medium/fixtures/artifacts/legacy-impact-bridge/com/acme/impact/bridge/LegacyImpactBridge.java"
    desc: "target 中保留 API v1 binary reference 的 transitive bridge"
  - path: "docs/user-manual.md"
    desc: "产品用户手册中的持续 benchmark 入口"
---

# Runbook: Impact Benchmark

## Summary

本 runbook 用 Git 管理的 source fixture 持续验证打包后的 `impact` command。运行入口生成独立临时 Git project、42 个 external JAR、Overall + Module HTML reports，并采集 Wall time、CPU、Analyzer RSS 和包含 Maven/Javac 的 process-tree resources。

## Prerequisites

- Java 17 用于启动 `target/dependency-analyzer.jar`。
- `JAVA8_HOME` 指向包含 `java`、`javac`、`jar` 和 `rt.jar` 的完整 JDK 8。
- Maven 3.6.3–3.x、Git 与 POSIX shell。
- Maven local repository 已缓存 `maven-compiler-plugin:3.13.0`；benchmark 使用 offline Maven，fixture 自有 artifacts 由脚本生成。
- macOS `/usr/bin/time -lp` 或 GNU `/usr/bin/time -v`。

## Commands

打包 Analyzer：

```sh
mvn package
```

执行一次独立 benchmark：

```sh
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-benchmark.sh candidate-01
```

默认结果目录为 `tmp-files/impact-medium-benchmark/candidate-01/`。同名目录存在时拒绝覆盖；不传 label 时使用时间戳。`ANALYZER_JAR`、`ANALYZER_JAVA`、`MAVEN_BIN`、`BENCHMARK_MAVEN_REPO` 和 `BENCHMARK_RUNTIME_ROOT` 可覆盖默认 runtime。

## Fixture Contract

- application POM 固定包含 42 个 compile-scope direct dependencies：40 个 class-bearing vendor JAR、`scenario-api`、`legacy-impact-bridge`。
- preparation 先提交/tag `impact-baseline`，再应用 target source overlay 并提交/tag `impact-target`；commit identity 和 timestamp 固定。
- `scenario-api:1.0.0 -> 2.0.0` 固定产生 `CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- `legacy-impact-bridge` 以 API v1 编译，target application 通过 bridge 保留 removed/old binary references；bridge field descriptor 提供 removed class Structural Impact。
- Analyzer 以 `module-parallelism=2`、120 秒 per-Module Call Graph timeout、offline Maven 和全部 9 类 `include-change-kinds` 执行。

## Success Criteria

- Analyzer exit code 为 `0`；Overall status 为 `Completed`。
- POM direct dependency count 为 `42`；raw changed member count 为 `9`。
- Dependency Changes 页面包含全部 9 个 `Raw ChangePointKind`。
- Candidate / final call chains 为 `6 / 6`；Affected Call Chains 页面存在 class structure reference。
- Overall、Module Index、Affected Call Chains、Dependency Changes 四个 HTML 页面全部存在。
- `logs/verification.txt` 第一行为 `status=SUCCESS`，总入口返回 `0`。

## Measurement Contract

- `logs/time.txt` 保存 Wall/User/System time 与当前 OS 的 process resource summary。
- `logs/process-tree.csv` 每 250 ms 采样 Analyzer 及 Maven/Javac descendants 的 aggregate RSS KiB 和 CPU percent。
- macOS `/usr/bin/time` maximum resident set size 单位是 byte；GNU time 单位是 KiB，跨 OS 对比前必须换算。
- `front-parallel` 包含并发的 `baseline-dependency` 与 `target-build`，阶段值不可全部相加推导 Wall time。
- 性能回归结论至少使用三次相同环境 warm run 的中位数；固定 Analyzer JAR SHA-256、JDK 8、Maven、local repository cache、CPU 核数和并发参数。

## Failure Entrypoints

- `logs/stderr.log`：Preflight、JDK、Maven runtime 与 CLI failure。
- `logs/stdout.log`：workspace、dependency、JAR diff、Call Graph、impact query 和 SSA diagnostics。
- `logs/verification.txt`：fixture 或 report contract failure。
- `logs/time.txt`、`logs/process-tree.csv`：timeout、resource spike 与 process-tree evidence。
- `fixture/fixture-metadata.txt`：生成的 project、refs、dependency count 和 Maven repository。
- Offline plugin resolution failure：使用同一 `BENCHMARK_MAVEN_REPO` 先执行正常 Maven build 预热。
