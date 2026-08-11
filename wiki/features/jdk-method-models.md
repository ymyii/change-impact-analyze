---
title: "JDK Method Models"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "command-wide selection、per-Module安装与metadata流"
  - path: "wiki/features/call-graph-engine.md"
    desc: "四种 strategy 的统一 model installation point 与fixed-point metadata"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "--jdk-model contract、parse failure 与Console Diagnostic"
  - path: "wiki/project/dependency-analyzer.md"
    desc: "Analyzer与两个独立model reactor的artifact边界"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "model bootstrap、Analyzer packaging与integration quality gate"
  - path: "wiki/runbooks/jdk-models-build-test.md"
    desc: "公共 engine 与 JDK 8 model 的独立构建、验收和 failure entrypoint"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "四个独立artifact的Snapshot与Stable release顺序"
  - path: "wiki/rules/release-versioning.md"
    desc: "两个 model artifact 的独立 SemVer 与发布约束"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer侧JDK 8 model version dependency与SemVer gate"
  - path: "analyzer/pom.xml"
    desc: "JDK 8 model dependency与uber JAR packaging"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/JdkModelSelection.java"
    desc: "public jdk8/none选择与默认值"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/JdkModelInstallation.java"
    desc: "per-graph严格安装、完整catalog验收与metadata snapshot"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphBuildRequest.java"
    desc: "strategy build request中的command-wide model选择"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "--jdk-model CLI入口与pipeline传递"
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
    desc: "三种Call Graph algorithm的direct WALA acceptance"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/callgraph/JdkCallbackReachabilityTest.java"
    desc: "四种Analyzer algorithm默认model与显式none验收"
---

# Feature: JDK Method Models

## Summary

JDK Method Models 由两个独立普通JAR组成。`models/jdk`提供可复用的WALA Synthetic Intermediate Representation（Synthetic IR）engine/API；`models/jdk8`提供精确JDK 8 catalog与安装façade。Analyzer通过dependency将两者打入uber JAR，`impact`与未显式选择model的`ModuleCallGraphEngine`默认在每张per-Module Call Graph启用`jdk8`。模型保留application receiver、points-to、容器元素、callback和serialization hook，同时避免Call Graph为高频JDK API深入大量内部实现。

## Design Decisions

- 公共 engine 与版本专属 catalog 分离。未来 JDK 版本通过新的 model module 引用 `models/jdk`，不在公共 JAR 中混合多个版本的 target。
- JDK 8 只约束被分析的 public API。两个 model JAR 按项目 Java 17 runtime 编译和执行，不承诺在 Java 8 JVM 运行。
- 两个 artifact 独立使用 Semantic Versioning（SemVer）；公共 API 与 JDK 8 coverage 可独立演进和发布。
- 使用 exact method target 和 conservative global family state。允许 points-to over-approximation，不使用 package ignore、whole-JDK exclusion 或通用 no-op fallback 丢失 application method。
- 公共engine仍记录unavailable并对未替换target委托原selector；Analyzer的`jdk8`边界要求完整384-target catalog，任一unavailable均使当前Module失败，不允许降级到`none`。
- 用户输出只保留command-wide selection。catalog/available/unavailable/hit metadata只在per-graph session内部用于验收，不进入Report、Console Diagnostic或diagnostics JSON。

## Actors / Entrypoints

- 版本专属 module 通过 `JdkModelDefinition` 提供稳定 model ID、resource anchor 与 absolute catalog resource。
- `impact --jdk-model jdk8|none`选择command-wide policy；默认`jdk8`，大小写不敏感且不接受alias。`none`完全跳过catalog读取和selector安装。
- Call Graph strategy在配置WALA default selector/native bypass后通过`JdkModelInstallation`映射到`Jdk8Models.install(...)`或no-op。
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
- `impact`与兼容`ModuleCallGraphEngine`constructor默认`jdk8`；显式`none`保留直接分析真实JDK bytecode的既有语义。
- Synthetic loader不支持、model安装异常或完整JDK 8存在unavailable catalog target时，当前Module构图失败；zero hit不是错误。
- duplicate target、resolved static contract mismatch、callback target/dispatch mismatch、WALA `natives.xml` conflict、无法生成 Synthetic IR 和必要 serialization constructor 缺失均抛出 `JdkModelException`。
- unavailable target 不被替换并委托原 selector；只有 available exact target 返回 `SummarizedMethod` 并计入 session hit。
- session 为 per-hierarchy、per-builder mutable recorder；snapshot 为稳定排序的 immutable metadata，不跨 Call Graph 或线程复用。
- synthetic state type 包含 model ID；同一 hierarchy 中不同版本模型不会共享同名 state class。

## Core Flow

1. Strategy创建`AnalysisOptions`，设置ReflectionOptions，再执行`Util.addDefaultSelectors`与`Util.addDefaultBypassLogic`。
2. `JdkModelInstallation`按selection安装一次JDK 8 model或直接返回no-op；`jdk8`立即验收Synthetic loader与384-target availability。
3. engine从版本module的classpath读取TSV，校验identity、descriptor、static flag、callback、state slot和native-summary ownership，并安装recording selector。
4. Strategy随后安装`invokedynamic`、MethodHandle、ServiceLoader和dependency body boundary，再完成builder fixed point。
5. fixed point完成后snapshot immutable metadata；未建模method继续使用真实JDK IR，application callback由summary中的显式invoke进入Call Graph。

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
- Given Analyzer选择`jdk8`且target不存在于当前hierarchy，When安装model，Then该Module构图失败且不fallback；公共engine单独使用时仍记录unavailable并委托原selector。
- Given collection、Stream、Temporal、async、resource或serialization fixture，When四种algorithm完成fixed point，Thenrequired application callback、business receiver与downstream method可达。
- Given lightweight同源fixture，When分别构建models-on和models-off Call Graph，Then models-on reachable application method set包含models-off集合。

### Non-Functional

- [x] 公共与JDK 8 artifact使用独立SemVer和flattened consumer POM。
- [x] model JAR不shade WALA，也不包含Analyzer class。
- [x] host JDK `jrt:/` test验证公共engine没有`rt.jar` layout依赖。
- [x] JDK 8 fixed-point test记录wall time、nodes、edges、JDK nodes和hit count，但不设置性能硬阈值。
- [x] `impact`、四种strategy、CLI、pipeline、Diagnostic、Report和Schema v6已接入；用户输出展示model selection，`k-obj`额外展示实际深度。

## Edge Cases

- 将`Jdk8Models`安装到非JDK 8 hierarchy不属于兼容性承诺；Analyzer严格失败，公共engine直接调用仍按available/unavailable记录。
- global family state可能合并无关实例，属于接受的false positive；不得因此改为instance-blind no-op。
- 缺失原selector、错误安装顺序或重复selector stacking属于调用方配置错误。
- 首个non-serializable superclass缺少可解析zero-argument constructor时安装失败，不静默省略constructor edge。
- catalog resource必须由definition anchor的ClassLoader可见，并使用absolute classpath path。

## Implementation Boundaries

- `models/jdk`只拥有公共definition、parser、validator、summary engine、synthetic state/type、selector与metadata；production JAR不包含版本catalog。
- `models/jdk8`只拥有JDK 8 façade、catalog和版本专属acceptance；通过精确version dependency引用公共engine。
- 两个module继续继承root parent，但不属于root`<modules>`；Analyzer构建前按公共engine、JDK 8 model的顺序安装独立artifact。
- Analyzer只依赖JDK 8 façade，由其传递引入公共engine；Shade输出包含`JdkModels.class`、`Jdk8Models.class`和`jdk8-models.tsv`。
