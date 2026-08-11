---
title: "JDK Models Build and Test"
type: runbook
relations:
  - path: "wiki/features/jdk-method-models.md"
    desc: "公共 engine、JDK 8 catalog、保守语义与 acceptance contract"
  - path: "wiki/rules/release-versioning.md"
    desc: "两个 model artifact 的独立 SemVer 与 Stable release gate"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "model bootstrap后的Analyzer packaging与impact integration gate"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer消费的jdk8-models.version与SemVer gate"
  - path: "analyzer/pom.xml"
    desc: "JDK 8 façade dependency与uber JAR packaging"
  - path: "models/jdk/pom.xml"
    desc: "公共 engine build、SemVer、flatten与Packaging gate"
  - path: "models/jdk8/pom.xml"
    desc: "JDK 8 model dependency、JDK 8 test environment与Packaging gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkModelsTest.java"
    desc: "definition、metadata、selector、unavailable与native conflict gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkSummaryTemplateTest.java"
    desc: "全部template的host jrt Synthetic IR gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkPackagingIT.java"
    desc: "公共普通JAR与无版本catalog验收"
  - path: "models/jdk8/src/test/java/io/github/dependencyanalysis/models/jdk8/TestHierarchies.java"
    desc: "JDK 8 rt.jar hierarchy与Java 8 fixture compiler setup"
  - path: "models/jdk8/src/test/java/io/github/dependencyanalysis/models/jdk8/Jdk8ModelsTest.java"
    desc: "384-target exact JDK 8 contract gate"
  - path: "models/jdk8/src/test/java/io/github/dependencyanalysis/models/jdk8/Jdk8ModelFixedPointAcceptanceTest.java"
    desc: "三种algorithm的on/off、reachability、serialization与metrics gate"
  - path: "models/jdk8/src/test/java/io/github/dependencyanalysis/models/jdk8/Jdk8PackagingIT.java"
    desc: "JDK 8 façade/catalog普通JAR验收"
---

# Runbook: JDK Models Build and Test

## Summary

本runbook按dependency顺序独立构建`models/jdk`公共engine与`models/jdk8`版本module。两个module不加入root reactor；完成install后，root Analyzer quality gate验证`impact`默认接入与uber JAR packaging。

## Prerequisites

- Maven JVM使用Java 17 JDK。
- Maven 3.x。
- `test.jdk8.home`指向完整JDK 8，必须包含`bin/java`、`bin/javac`与`jre/lib/rt.jar`。
- 默认JDK 8路径继承root POM；其他环境使用`-Dtest.jdk8.home=/absolute/path/to/jdk8`覆盖。
- 所有command从repository root执行。

## Commands

完整quality gate先安装公共engine，再验收JDK 8 module：

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
mvn clean verify
```

覆盖JDK 8路径时，两条命令都传入相同property，因为两个POM继承root Enforcer：

```sh
mvn -f models/jdk/pom.xml \
  -Dtest.jdk8.home=/absolute/path/to/jdk8 \
  clean install
mvn -f models/jdk8/pom.xml \
  -Dtest.jdk8.home=/absolute/path/to/jdk8 \
  clean verify
```

只验收公共engine：

```sh
mvn -f models/jdk/pom.xml clean verify
```

只运行公共API/catalog/layout tests：

```sh
mvn -f models/jdk/pom.xml \
  -Dtest=JdkModelDefinitionTest,JdkModelCatalogTest,JdkModelsTest,JdkRuntimeCompatibilityTest \
  test
```

只运行全部Synthetic IR template gate：

```sh
mvn -f models/jdk/pom.xml -Dtest=JdkSummaryTemplateTest test
```

公共engine已安装后，只运行JDK 8 fixed-point acceptance：

```sh
mvn -f models/jdk8/pom.xml \
  -Dtest=Jdk8ModelFixedPointAcceptanceTest \
  test
```

Packaging gate由Failsafe在`verify`阶段自动运行。人工检查使用：

```sh
jar tf models/jdk/target/dependency-analyzer-jdk-models-0.1.0-SNAPSHOT.jar
jar tf models/jdk8/target/dependency-analyzer-jdk8-models-0.1.0-SNAPSHOT.jar
```

## Test Matrix

| Gate | Module/runtime | 核心断言 |
|---|---|---|
| definition/catalog/API | `models/jdk`, host JDK 17 | model ID、duplicate、descriptor/static、native conflict、metadata、selector delegation |
| layout compatibility | `models/jdk`, JDK 17 `jrt:/` | 公共engine不依赖`rt.jar` layout |
| template IR | `models/jdk`, JDK 17 `jrt:/` | 每个template生成合法Synthetic IR；state put/get与callback invoke |
| exact contract | `models/jdk8`, JDK 8 `rt.jar` | catalog=384、available=384、unavailable=0、无post-JDK 8 target |
| fixed point | `models/jdk8`, JDK 8 `rt.jar` + `--release 8` fixture | Basic RTA、ZeroCFA、optimized ZeroX callback、serialization与business reachability |
| packaging | 两个普通JAR | artifact职责分离；无Analyzer class；WALA未shade；flattened consumer POM可解析 |

fixed-point test使用同一source中的lightweight entrypoint比较models-on/off application method set；coverage entrypoint验证完整conservative callback、I/O与serialization行为，避免models-off展开整个JDK使unit gate失控。

## Metrics

JDK 8 fixed-point test记录非门禁metrics：

```text
models/jdk8/target/jdk8-model-acceptance.tsv
```

每行依次为algorithm、phase、models-enabled、elapsed nanoseconds、nodes、edges、JDK method count与hit count。`clean`删除旧文件；只比较同一环境结果，不设置性能硬阈值。

## Success Criteria

- 两个module均输出`0 Checkstyle violations`，且所有test为`Failures: 0, Errors: 0, Skipped: 0`。
- 公共gate运行16个unit tests与1个Packaging integration test；JDK 8 gate运行5个unit/fixed-point tests与1个Packaging integration test。
- JDK 8 metadata为`modelId=jdk8`、catalog=384、available=384、unavailable=0。
- 三种algorithm均命中model target并产生`SummarizedMethod`；required application callback、serialization hook与downstream method可达。
- lightweight models-on application method set包含models-off set；Stream、AbstractQueuedSynchronizer、ObjectInputStream内部实现与URL protocol implementation不被继续展开。
- 公共JAR包含`JdkModels`、`JdkModelDefinition`、session/metadata/exception与engine，不包含production catalog。
- JDK 8 JAR包含`Jdk8Models`与`jdk8-models.tsv`，不包含公共engine class副本。
- Analyzer uber JAR通过JDK 8 façade的transitive dependency包含公共engine、façade与catalog；packaged `impact --help`包含`--jdk-model`，默认Report显示`jdk8`。
- 两个JAR均不含Analyzer或`com/ibm/wala/`class；local repository中的flattened POM不依赖root parent解析。
- root`pom.xml`固定`jdk8-models.version`并执行默认/release SemVer gate；Analyzer POM只直接依赖JDK 8 façade。

## Failure Entrypoints

- `test.jdk8.home must point to a complete JDK 8`：确认路径含完整JDK 8，或传入absolute`-Dtest.jdk8.home`。
- 无法解析`dependency-analyzer-jdk-models`：先执行公共module的`clean install`，并确认`models/jdk8/pom.xml`中的`jdk-models.version`一致。
- consumer尝试解析`${revision}` parent：确认公共module启用了Flatten Maven Plugin，并检查`models/jdk/target/flattened-pom.xml`。
- `JDK model catalog is unavailable`：检查definition的absolute resource path、resource anchor与JAR内容。
- `Duplicate catalog target`：按owner/name/descriptor删除duplicate；overload descriptor必须不同。
- `Catalog static contract mismatch`：以JDK 8 public API校正static flag或descriptor。
- `Catalog callback target is unresolved`：校正callback owner/name/descriptor/dispatch；不得改成no-op掩盖。
- `Catalog conflicts with WALA native summaries`：删除overlap或明确native model ownership，禁止用selector顺序覆盖。
- `Serializable type has no resolvable first non-serializable constructor`：确认fixture/application hierarchy符合Java serialization constructor contract。
- unit/fixed-point failure：分别检查`models/jdk/target/surefire-reports/`和`models/jdk8/target/surefire-reports/`；Packaging failure检查对应`failsafe-reports/`。

## Configuration

- 公共artifact version由`models/jdk/pom.xml`的`project.version`维护。
- JDK 8 artifact version由`models/jdk8/pom.xml`的`project.version`维护；公共dependency version由同一POM的`jdk-models.version`维护。
- 默认profile接受Stable或Snapshot SemVer；`release` profile只接受Stable SemVer并拒绝Snapshot dependency。

## Integration Boundary

公共engine与JDK 8 model继续独立version、reactor和acceptance。Analyzer只在local artifact已安装后构建；`impact`的selection、严格安装、Diagnostic、Report与benchmark regression由root gate负责，不反向放入model module。
