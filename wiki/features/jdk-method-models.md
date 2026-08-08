---
title: "JDK Method Models"
type: feature
relations:
  - path: "wiki/runbooks/jdk-models-build-test.md"
    desc: "独立 module 的构建、跨版本验证与 failure entrypoint"
code_refs:
  - path: "models/jdk/pom.xml"
    desc: "独立 Maven module、WALA/test dependencies 与 Checkstyle gate"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModels.java"
    desc: "公共 install/supports API 与 per-hierarchy 安装边界"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelSession.java"
    desc: "per-builder target hit recorder 与 deterministic snapshot"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelMetadata.java"
    desc: "immutable catalog/available/unavailable/hit metadata"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelException.java"
    desc: "catalog、contract 与 Synthetic IR blocking failure"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkModelCatalog.java"
    desc: "committed TSV catalog parser 与 duplicate detection"
  - path: "models/jdk/src/main/resources/io/github/dependencyanalysis/models/jdk/jdk-models.tsv"
    desc: "精确 owner/name/descriptor/static/template/state/callback catalog"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/CatalogContractValidator.java"
    desc: "resolved method、callback 与 WALA native summary conflict 验证"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/JdkSummaryBuilder.java"
    desc: "declarative、resource 与 serialization Synthetic IR builder"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/ModelStateClass.java"
    desc: "per-hierarchy conservative global reference state"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/ModelSyntheticTypes.java"
    desc: "interface/abstract return 的 Synthetic placeholder registry"
  - path: "models/jdk/src/main/java/io/github/dependencyanalysis/models/jdk/RecordingBypassMethodTargetSelector.java"
    desc: "exact available-target bypass、fallback delegation 与 hit recording"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkSummaryTemplateTest.java"
    desc: "全部 template 的 JDK 8 IR generation gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkRuntimeCompatibilityTest.java"
    desc: "host JDK jrt:/ layout compatibility gate"
  - path: "models/jdk/src/test/java/io/github/dependencyanalysis/models/jdk/JdkModelFixedPointAcceptanceTest.java"
    desc: "RTA、ZeroCFA 与 optimized ZeroX direct WALA acceptance"
---

# Feature: JDK Method Models

## Summary

`models/jdk` 是不依赖 Analyzer 的普通 JAR，以 WALA `MethodSummary` / `SummarizedMethod` 为稳定 public JDK method contract 生成 conservative Synthetic Intermediate Representation（Synthetic IR）。目标是保留 application receiver、points-to、容器元素、callback 和 serialization hook，同时避免 Call Graph 为高频 JDK API 深入大量内部实现。

本页只描述独立 module。它尚未加入 root Maven reactor，也尚未接入 `impact`、Call Graph strategy、CLI、Diagnostic、Report 或 pipeline。主流程接入属于第二部分。

## Artifact and Public API

- Maven coordinate：`io.github.dependencyanalysis:dependency-analyzer-jdk-models:${revision}`。
- Java package：`io.github.dependencyanalysis.models.jdk`。
- model ID：`jdk`。
- `JdkModels.install(AnalysisOptions, IClassHierarchy)` 在现有 selector 外安装模型，返回新的 `JdkModelSession`。
- `JdkModelSession.snapshot()` 返回 deterministic immutable `JdkModelMetadata`，包含 catalog、available、unavailable 和实际 hit target。
- `JdkModelException` 表达 duplicate、resolved contract mismatch、callback/native conflict、serialization contract 与 Synthetic IR generation failure。

安装前 `AnalysisOptions` 必须已有 WALA default selector/native bypass。安装后不得调用会重装 selector 的 convenience builder factory；例如 RTA 应直接构造 `BasicRTABuilder`。module 不持有 Analyzer 类型，也不跨 hierarchy、builder 或线程复用 session。

```java
Util.addDefaultSelectors(options, hierarchy);
Util.addDefaultBypassLogic(
        options, Util.class.getClassLoader(), hierarchy);
JdkModelSession session = JdkModels.install(options, hierarchy);
```

## Catalog and Capability Detection

committed TSV catalog 使用精确 JVM owner、method name、descriptor、static flag、semantic template、state slot 与 callback parameter 描述 target。当前 catalog 包含 385 个 target。

安装时逐项执行：

1. duplicate catalog identity 直接失败。
2. 当前 Class Hierarchy Analysis（CHA）可解析且 static contract 一致的 target 进入 available set并生成 summary。
3. 当前 runtime 不存在的 target 进入 unavailable set；selector继续委托原实现，不阻塞其他模型。
4. callback owner/method/descriptor/dispatch、argument index 与 WALA `natives.xml` conflict fail-fast。
5. 只有 available exact target会被 bypass；未建模 method继续使用真实 JDK IR。

该机制不依赖 JDK 8 `rt.jar` layout。测试同时覆盖配置的 JDK 8 `rt.jar` 和 Maven test JVM 的 JDK 17 `jrt:/` module image。跨版本能力来自运行时解析 public contract，不代表任意未来 JDK 的新增 API 自动获得语义；新增 target仍须进入 catalog。

## Conservative State and Return Types

每次安装向当前 hierarchy 的 Synthetic loader注册 module-owned state class。static field按 collection element、map key/value、stream element、optional value、future result、ThreadLocal value、atomic value、byte/char buffer、resource与serialized object分类。

- 写操作把 application reference合并到对应 slot；读操作从 slot返回。
- global family state允许不同实例互相污染，可能增加 false positive，但不丢失 application type。
- concrete return直接分配 declared type；interface/abstract return使用 WALA Synthetic placeholder。
- primitive return使用 conservative default；primitive functional callback仍生成显式 invoke edge。
- 未使用 package ignore、whole-JDK exclusion或通用 no-op fallback。

## Coverage

- Collection：`Iterable`、iterator、Collection/List/Set/Map/Entry、Queue/Deque、sorted/navigable view、常见 concrete collection、legacy collection、ConcurrentMap、concurrent/skip-list/copy-on-write/blocking collection、`Collections` 与 `Arrays` 高频操作。
- Stream/Optional：object/primitive Stream、BaseStream、object/primitive Optional、factory、intermediate/terminal operation与 functional interface callback。
- Text/value：String、StringBuilder/StringBuffer、StringJoiner、Formatter、Scanner、Pattern/Matcher、Objects、UUID、wrapper、BigInteger/BigDecimal、Random、Locale/Currency/ResourceBundle与Base64。
- Time：Clock、Instant、Duration、Period、local/offset/zoned types、Zone、Year/Month、DateTimeFormatter、legacy Date/Calendar/TimeZone/DateFormat；TemporalQuery/TemporalAdjuster显式 callback。
- Concurrency：Lock/ReadWriteLock/Condition/StampedLock、同步器、Atomic/LongAdder、ThreadLocal、Executor/ScheduledExecutor、Future/ForkJoin、CompletionStage/CompletableFuture；Runnable、Callable与 completion callback进入 Call Graph。
- Resource：stream/reader/writer、File/RandomAccessFile、Path/Files、Buffer/Channel、Charset、URI/URL/URLConnection/InetAddress、ZIP/GZIP/JAR；FileVisitor与DirectoryStream Filter显式 callback。
- Serialization：扫描 concrete Serializable/Externalizable application type，保留 private read/write hook、readResolve/writeReplace、Externalizable、ObjectInputValidation、stream subclass hook及首个 non-serializable superclass zero-argument constructor。

Class/Reflection、Proxy、ClassLoader、ServiceLoader、MethodHandle与`invokedynamic`不属于该 module。

## Serialization Contract

`ObjectOutputStream.writeObject` 将输入写入 global serialization state并调用候选 write hook。`ObjectInputStream.readObject` 为全部 concrete application Serializable/Externalizable type分配候选值、调用 read hook、合并返回并调用 resolve hook。

stream subclass覆盖的 annotate/descriptor/header/resolve hook也进入 summary。首个 non-serializable superclass找不到 zero-argument constructor时安装失败，不静默生成不完整模型。`registerValidation` 显式调用传入 `ObjectInputValidation.validateObject()`。

## Acceptance

- catalog parser、duplicate、descriptor/static/callback/native conflict、metadata排序、unavailable fallback和selector delegation通过 unit gate。
- 每个 semantic template都必须在真实 JDK 8 hierarchy生成非空且可构造 IR的 `SummarizedMethod`。
- direct WALA fixture分别运行 `BasicRTABuilder`、class-based ZeroCFA和 optimized ZeroX policy；application callback、business downstream、serialization hook与state propagation必须可达。
- lightweight同源 fixture的 model-on application method set必须包含 model-off set。
- modeled target必须出现 `SummarizedMethod`，且典型 Stream pipeline与AbstractQueuedSynchronizer内部实现不继续展开。
- JAR必须包含 public API和catalog，不包含 Analyzer class，也不 shade WALA。

## Edge Cases

- unavailable target不是 coverage failure；它自动委托真实 JDK method target selector。
- global state可能把无关实例合并，属于接受的 over-approximation。
- runtime class存在但 descriptor/static/callback contract不一致时属于 blocking model failure。
- 重复安装或错误安装顺序可能造成 selector stacking；第二部分接入时必须由唯一 AnalysisOptions factory控制。
- module当前不能从 `impact` CLI启用；没有 `--jdk-models` 或 Report metadata。
