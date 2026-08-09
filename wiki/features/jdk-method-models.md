---
title: "JDK Method Models"
type: feature
relations:
  - path: "wiki/runbooks/jdk-models-build-test.md"
    desc: "公共 engine 与 JDK 8 model 的独立构建、验收和 failure entrypoint"
  - path: "wiki/rules/release-versioning.md"
    desc: "两个 model artifact 的独立 SemVer 与发布约束"
code_refs:
  - path: "models/jdk/pom.xml"
    desc: "公共 engine artifact、SemVer gate 与 flattened consumer POM"
  - path: "models/jdk8/pom.xml"
    desc: "JDK 8 model artifact、公共 engine dependency 与独立 SemVer gate"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModels.java"
    desc: "definition 驱动的公共 install/supports API"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelDefinition.java"
    desc: "model ID 与版本专属 catalog resource contract"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelSession.java"
    desc: "per-hierarchy target hit recorder 与 deterministic snapshot"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelMetadata.java"
    desc: "immutable catalog/available/unavailable/hit metadata"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelException.java"
    desc: "catalog、contract与Synthetic IR blocking failure"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelCatalog.java"
    desc: "definition-owned TSV parsing与duplicate detection"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/CatalogContractValidator.java"
    desc: "resolved method、callback与WALA native-summary conflict验证"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkSummaryBuilder.java"
    desc: "declarative、resource 与 serialization Synthetic IR builder"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/ModelStateClass.java"
    desc: "model-specific synthetic global reference state"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/ModelSyntheticTypes.java"
    desc: "interface/abstract return的Synthetic placeholder registry"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/RecordingBypassMethodTargetSelector.java"
    desc: "exact available-target bypass、fallback delegation 与 hit recording"
  - path: "models/jdk8/src/main/java/io/github/dependencyanalysis/models/jdk8/Jdk8Models.java"
    desc: "JDK 8 model definition 与安装 façade"
  - path: "models/jdk8/src/main/resources/io/github/dependencyanalysis/models/jdk8/jdk8-models.tsv"
    desc: "384 个精确 JDK 8 public method contracts"
  - path: "models/jdk8/src/test/java/io/github/dependencyanalysis/models/jdk8/Jdk8ModelFixedPointAcceptanceTest.java"
    desc: "三种 Call Graph algorithm 的 direct WALA acceptance"
---

# Feature: JDK Method Models

## Summary

JDK Method Models 由两个不依赖 Analyzer 的普通 JAR 组成。`models/jdk` 提供可复用的 WALA Synthetic Intermediate Representation（Synthetic IR）engine/API；`models/jdk8` 提供精确 JDK 8 catalog 与安装 façade。模型保留 application receiver、points-to、容器元素、callback 和 serialization hook，同时避免 Call Graph 为高频 JDK API 深入大量内部实现。

该能力尚未加入 root Maven reactor，也尚未接入 `impact`、Call Graph strategy、CLI、Diagnostic、Report 或 pipeline。主流程接入属于第二部分。

## Design Decisions

- 公共 engine 与版本专属 catalog 分离。未来 JDK 版本通过新的 model module 引用 `models/jdk`，不在公共 JAR 中混合多个版本的 target。
- JDK 8 只约束被分析的 public API。两个 model JAR 按项目 Java 17 runtime 编译和执行，不承诺在 Java 8 JVM 运行。
- 两个 artifact 独立使用 Semantic Versioning（SemVer）；公共 API 与 JDK 8 coverage 可独立演进和发布。
- 使用 exact method target 和 conservative global family state。允许 points-to over-approximation，不使用 package ignore、whole-JDK exclusion 或通用 no-op fallback 丢失 application method。
- JDK 8 reference hierarchy 必须完整解析 catalog；公共 engine 仍保留 unavailable/delegation，以支持不完整 hierarchy 和未来版本 module。

## Actors / Entrypoints

- 版本专属 module 通过 `JdkModelDefinition` 提供稳定 model ID、resource anchor 与 absolute catalog resource。
- Call Graph builder owner 在配置 WALA default selector/native bypass 后调用 `Jdk8Models.install(AnalysisOptions, IClassHierarchy)`。
- 构图完成后调用 `JdkModelSession.snapshot()` 获取 catalog、available、unavailable 和 hit metadata。

公共 engine 的低层入口如下；业务调用方应优先使用版本 façade：

```java
JdkModelDefinition definition = new JdkModelDefinition(
        "jdk8", Jdk8Models.class,
        "/io/github/dependencyanalysis/models/jdk8/jdk8-models.tsv");
JdkModelSession session = JdkModels.install(
        options, hierarchy, definition);
```

## Behavior Contract

- 公共 coordinate：`io.github.dependencyanalysis:dependency-analyzer-jdk-models:0.1.0-SNAPSHOT`；package 为 `io.github.dependencyanalysis.models.jdk`。
- JDK 8 coordinate：`io.github.dependencyanalysis:dependency-analyzer-jdk8-models:0.1.0-SNAPSHOT`；package 为 `io.github.dependencyanalysis.models.jdk8`；model ID 固定为 `jdk8`。
- JDK 8 catalog 包含 384 个 JDK 8 public targets，不包含 Java 16 引入的 `Stream.toList()`。
- duplicate target、resolved static contract mismatch、callback target/dispatch mismatch、WALA `natives.xml` conflict、无法生成 Synthetic IR 和必要 serialization constructor 缺失均抛出 `JdkModelException`。
- unavailable target 不被替换并委托原 selector；只有 available exact target 返回 `SummarizedMethod` 并计入 session hit。
- session 为 per-hierarchy、per-builder mutable recorder；snapshot 为稳定排序的 immutable metadata，不跨 Call Graph 或线程复用。
- synthetic state type 包含 model ID；同一 hierarchy 中不同版本模型不会共享同名 state class。

## Core Flow

1. `Jdk8Models` 创建固定 `jdk8` definition并委托公共 engine。
2. engine 从版本 module 的 classpath读取 TSV，校验 identity、descriptor、static flag、callback、state slot 和 native-summary ownership。
3. engine为当前 hierarchy注册 model-specific synthetic state与interface/abstract return placeholder。
4. available target生成`MethodSummary`；unavailable target只写入metadata。
5. recording `BypassMethodTargetSelector`包装原 selector，仅替换exact available target并记录实际hit。
6. WALA builder继续fixed point；未建模method使用真实JDK IR，application callback由summary中的显式invoke进入Call Graph。

## Coverage

- Collection：Iterable、Iterator、Collection/List/Set/Map/Entry、Queue/Deque、sorted/navigable variant、常见 concrete/legacy/concurrent collection、`Collections` 与 `Arrays`。
- Stream/Optional：object/primitive Stream、BaseStream、object/primitive Optional、factory、intermediate/terminal operation 与 functional interface callback。
- Text/value：String、builder/buffer、StringJoiner、Formatter、Scanner、Pattern/Matcher、Objects、UUID、wrapper、BigInteger/BigDecimal、Random、Locale/Currency/ResourceBundle 与 Base64。
- Time：`java.time`、DateTimeFormatter、legacy Date/Calendar/TimeZone/DateFormat，以及 TemporalQuery/TemporalAdjuster callback。
- Concurrency：Lock/Condition/synchronizer、Atomic、ThreadLocal、Executor、Future/ForkJoin 与 CompletableFuture callback。
- Resource：I/O、NIO、Path/Files、Buffer/Channel、Charset、URI/URL、ZIP/GZIP/JAR、FileVisitor 与 DirectoryStream Filter。
- Serialization：concrete Serializable/Externalizable application type、private read/write hook、readResolve/writeReplace、ObjectInputValidation、stream subclass hook及首个non-serializable superclass zero-argument constructor。

Class/Reflection、Proxy、ClassLoader、ServiceLoader、MethodHandle 与 `invokedynamic` 不属于这两个 module。

## Acceptance Criteria

### Functional

- Given 配置的完整 JDK 8 `rt.jar`，When 安装 `Jdk8Models`，Then catalog/available 均为 384、unavailable 为 0，且没有 post-JDK 8 target。
- Given 已安装 WALA default selector，When available target 被解析，Then 返回 `SummarizedMethod`、记录 deterministic hit，并保留 application points-to 与 callback edge。
- Given target 不存在于当前 hierarchy，When selector查询该 target，Then session记录 unavailable 且调用委托到原 selector。
- Given collection、Stream、Temporal、async、resource 或 serialization fixture，When RTA、ZeroCFA、optimized ZeroX 完成 fixed point，Then required application callback、business receiver 与 downstream method 可达。
- Given lightweight同源fixture，When分别构建models-on和models-off Call Graph，Then models-on reachable application method set包含models-off集合。

### Non-Functional

- [x] 公共与JDK 8 artifact使用独立SemVer和flattened consumer POM。
- [x] model JAR不shade WALA，也不包含Analyzer class。
- [x] host JDK `jrt:/` test验证公共engine没有`rt.jar` layout依赖。
- [x] JDK 8 fixed-point test记录wall time、nodes、edges、JDK nodes和hit count，但不设置性能硬阈值。
- [ ] `impact`接入、CLI toggle、Diagnostic与Report metadata留待第二部分。

## Edge Cases

- 将`Jdk8Models`安装到非JDK 8 hierarchy不属于兼容性承诺；公共engine仍会按available/unavailable安全退化。
- global family state可能合并无关实例，属于接受的false positive；不得因此改为instance-blind no-op。
- 缺失原selector、错误安装顺序或重复selector stacking属于调用方配置错误。
- 首个non-serializable superclass缺少可解析zero-argument constructor时安装失败，不静默省略constructor edge。
- catalog resource必须由definition anchor的ClassLoader可见，并使用absolute classpath path。

## Implementation Boundaries

- `models/jdk`只拥有公共definition、parser、validator、summary engine、synthetic state/type、selector与metadata；production JAR不包含版本catalog。
- `models/jdk8`只拥有JDK 8 façade、catalog和版本专属acceptance；通过精确version dependency引用公共engine。
- 两个module继续继承root parent，但不属于root `<modules>`；独立build不会触发Analyzer或`impact` tests。
- 第二部分才允许修改root/analyzer POM、Call Graph安装顺序、CLI、pipeline、Diagnostic、Report与benchmark。
