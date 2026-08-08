---
title: "JDK Models Build and Test"
type: runbook
relations:
  - path: "wiki/features/jdk-method-models.md"
    desc: "public API、catalog、conservative semantics与acceptance contract"
code_refs:
  - path: "models/jdk/pom.xml"
    desc: "独立 build、test JVM environment 与 Checkstyle配置"
  - path: "models/jdk/src/main/resources/io/github/dependencyanalysis/models/jdk/jdk-models.tsv"
    desc: "committed exact-method catalog"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/TestHierarchies.java"
    desc: "JDK 8 rt.jar、host jrt:/ 与Java 8 fixture compiler setup"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkModelsTest.java"
    desc: "install、metadata、selector与cross-version fallback gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkSummaryTemplateTest.java"
    desc: "catalog contract与全部template IR gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkRuntimeCompatibilityTest.java"
    desc: "JDK 17 jrt:/ compatibility gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkModelFixedPointAcceptanceTest.java"
    desc: "三种algorithm的on/off、reachability与metric gate"
  - path: "models/jdk/src/test/resources/fixtures/JdkModelFixture.java"
    desc: "Java 8 compiled coverage与lightweight comparison fixture"
---

# Runbook: JDK Models Build and Test

## Summary

本 runbook只构建和验收独立 `models/jdk` module。命令不会把 module加入 root reactor，不修改或验证 `impact` 主流程接入。

## Prerequisites

- Maven JVM使用 Java 17 JDK。
- Maven 3.x。
- `test.jdk8.home` 指向完整 JDK 8，必须包含 `bin/java`、`bin/javac` 与 `jre/lib/rt.jar`。
- 默认路径继承 root POM。其他环境通过 `-Dtest.jdk8.home=/absolute/path/to/jdk8` 覆盖。
- Maven test JVM本身提供 JDK 17 `jrt:/` image和`javax.tools.JavaCompiler`。
- 所有 command从repository root执行。

## Commands

完整独立 quality gate：

```sh
mvn -f models/jdk/pom.xml clean verify
```

覆盖 JDK 8路径：

```sh
mvn -f models/jdk/pom.xml \
  -Dtest.jdk8.home=/absolute/path/to/jdk8 \
  clean verify
```

只运行快速 API/catalog/runtime tests：

```sh
mvn -f models/jdk/pom.xml \
  -Dtest=JdkModelCatalogTest,JdkModelsTest,JdkRuntimeCompatibilityTest \
  test
```

只运行全部 template IR gate：

```sh
mvn -f models/jdk/pom.xml -Dtest=JdkSummaryTemplateTest test
```

只运行 direct WALA fixed-point acceptance：

```sh
mvn -f models/jdk/pom.xml \
  -Dtest=JdkModelFixedPointAcceptanceTest \
  test
```

检查普通 JAR内容：

```sh
jar tf models/jdk/target/dependency-analyzer-jdk-models-*.jar
```

## Test Matrix

| Gate | Runtime/scope | 核心断言 |
|---|---|---|
| catalog/API | JDK 8 `rt.jar` | duplicate、descriptor/static、native conflict、metadata、selector delegation |
| runtime compatibility | test JVM JDK 17 `jrt:/` | 无 JDK 8 layout假设；available/unavailable能力检测 |
| template IR | JDK 8 `rt.jar` | 每个template生成非空合法 Synthetic IR；state put/get与callback invoke |
| fixed point | JDK 8 `rt.jar` + `--release 8` fixture | Basic RTA、ZeroCFA、optimized ZeroX callback与business reachability |
| packaging | Maven JAR | public API/catalog存在；无Analyzer class；WALA未shade |

fixed-point test使用同一 source生成两类 entrypoint：lightweight fixture执行model on/off application method set比较；coverage fixture只用于完整 conservative callback与serialization gate，避免model-off深入整个JDK导致unit gate失控。

## Metrics

fixed-point test在以下路径记录非门禁指标：

```text
models/jdk/target/jdk-model-acceptance.tsv
```

每行依次为algorithm、phase、models-enabled、elapsed nanoseconds、nodes、edges、JDK method count与hit count。`clean`会删除旧文件；性能值只用于同环境观察，不设置硬阈值。

## Success Criteria

- Maven输出`0 Checkstyle violations`。
- unit、runtime、template与fixed-point tests全部通过且`Skipped: 0`。
- JDK 8 catalog除显式跨版本`Stream.toList()`外均为available；JDK 17将该target识别为available。
- 三种algorithm均命中model target并产生`SummarizedMethod`；required application callback与downstream method可达。
- lightweight model-on application method set包含model-off set。
- 输出JAR包含`JdkModels`、`JdkModelSession`、`JdkModelMetadata`、`JdkModelException`与`jdk-models.tsv`。
- 输出JAR不含`io/github/dependencyanalysis/callgraph/`、`impact/`、`report/`或`com/ibm/wala/`class。
- root `pom.xml`、`analyzer/pom.xml`与Analyzer source/tests未因本 module验收发生修改。

## Failure Entrypoints

- `test.jdk8.home must point to a complete JDK 8`：确认路径包含完整 JDK 8，或传入absolute `-Dtest.jdk8.home`。
- `JDK model catalog is unavailable`：检查`jdk-models.tsv` resource path及JAR内容。
- `Duplicate catalog target`：按owner/name/descriptor删除duplicate；overload descriptor必须不同。
- `Catalog static contract mismatch`：以当前 public JDK API校正static flag或descriptor。
- `Catalog callback target is unresolved`：校正callback owner/name/descriptor/dispatch；不得改为no-op掩盖。
- `Catalog conflicts with WALA native summaries`：删除overlap或先明确WALA native model ownership，禁止selector顺序覆盖。
- `Serializable type has no resolvable first non-serializable constructor`：确认fixture/application hierarchy符合Java serialization constructor contract。
- unit failure report：`models/jdk/target/surefire-reports/`。
- Checkstyle failure：Maven Console中的file/line/check名称。
- fixed-point reachability failure：先检查`JdkModelFixedPointAcceptanceTest`的hit target，再检查summary callback receiver、state slot与builder安装顺序。

## Isolation Boundary

第一部分完成后仍然不能从`impact`启用模型。root reactor、Analyzer dependency、CLI option、Call Graph安装顺序、Diagnostic与Report metadata全部留给第二部分；不要在本 runbook的修复中顺带修改这些边界。
