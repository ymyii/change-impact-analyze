---
title: "Call Graph Engine"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "per-Module pipeline 与并发边界"
  - path: "wiki/features/impact-tracing.md"
    desc: "direct WALA query 消费 live session"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphEngine.java"
    desc: "per-Module Vanilla 0-1-CFA builder"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleCallGraphSession.java"
    desc: "live graph、CHA、cache、ownership 与 metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DeterministicSubtypesEntrypoint.java"
    desc: "PROJECT all-method entrypoint 参数候选"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointSelection.java"
    desc: "用户 include/exclude selector 与默认 all-method boundary"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/EntrypointClassScanner.java"
    desc: "WALA 前轻量 target PROJECT class index"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ClassOwnershipIndex.java"
    desc: "binary-name ownership 与 duplicate validation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/ModuleScopeValidator.java"
    desc: "excluded JDK reference validation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleServiceLoaderEnricher.java"
    desc: "ServiceLoader conservative overlay"
---

# Feature: Call Graph Engine

## Summary

每个 relevant target Module 构建一个独立 WALA Vanilla 0-1-CFA Call Graph。Builder 与 query 在 Module 内单线程；不同 Module 可并发。没有 CHA pre-graph、seed pre-scan、class-reference closure 或 full predecessor snapshot。

## Scope and Ownership

- `PROJECT`：当前 Module `target/classes`。
- `REACTOR_DEPENDENCY`：当前 Module resolved reactor dependency closure 的 `target/classes`。
- `DEPENDENCY`：Maven resolved external JAR absolute path。
- `JDK`：显式 `--java-home` 的 JDK 8 boot/ext JAR。
- `SYNTHETIC`：ServiceLoader overlay 与 ChangePoint terminal。
- Scope load 前建立 binary-name ownership index。byte-identical duplicate 允许去重；内容不同的 duplicate class 使当前 Module fail，禁止 WALA first-wins。为避免对 JDK 8 全量 class 重复 hash，JDK JAR 只对已在 Application scope index 中出现的 binary name 读取 bytecode 并校验 duplicate；byte-identical JDK duplicate 按 bootstrap loader precedence 归属 `JDK`。

## Entrypoints

- 未配置 selector 时，当前 `PROJECT` 中全部 non-abstract declared methods成为 entrypoints，包括 interface default/static 与 concrete bridge/synthetic methods。
- `--entrypoint-include '<package-pattern>:<class-pattern>'`/`--entrypoint-exclude ...` 可重复；include 取并集，exclude 优先。Package 只支持精确匹配与尾部 `**` 递归匹配；class pattern 匹配 simple binary class name，`*` 为 wildcard，nested class 使用 `$`。
- 命中 class 的全部 non-abstract declared methods成为 entrypoints；不自动加入 inherited method 或 subclass。Selector 只限制 `PROJECT` roots，不裁剪 Module scope、CHA、Reflection、ServiceLoader 或 Reference 参数 subtype candidates。
- 轻量 ASM class index 在 WALA 前计算匹配 metrics；无匹配的 relevant Module 为 `SKIPPED_USER_ENTRYPOINT_SCOPE`。所有 relevant Module 均无匹配时 command fail，不发布新 Report。
- Reference 参数枚举 scope 内全部 concrete assignable types，按 type name 排序；primitive 保留 declared type；constructor receiver 保留 declaring type。
- `REACTOR_DEPENDENCY`、`DEPENDENCY`、`JDK` 不作为 entrypoint，只由 reachability 进入。
- 零 `PROJECT` entrypoint 使当前 Module fail。

## WALA Builder

```java
AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
options.setReflectionOptions(AnalysisOptions.ReflectionOptions.FULL);
SSAPropagationCallGraphBuilder builder =
        Util.makeVanillaZeroOneCFABuilder(
                Language.JAVA, options, cache, hierarchy);
MethodHandles.analyzeMethodHandles(options, builder);
```

- Factory 已建立 selector/bypass 配置，不重复设置。
- `AnalysisCacheImpl` 显式使用 `SSAOptions.defaultOptions()`。
- `--call-graph-timeout-seconds` 从实际 Module build 开始计时，不含 pool queue time。
- Timeout 只失败当前 Module；WALA fixed-point 不输出 partial graph。

## JDK Exclusions

- Whole-JAR：`jfxrt.jar`、`deploy.jar`、`javaws.jar`、`plugin.jar`。
- Class：Swing 与 Applet package 的最小规则。
- `scope-validation` 扫描 `PROJECT`、`REACTOR_DEPENDENCY`、`DEPENDENCY` 对 excluded class 的显式 bytecode reference；命中、unreadable class、scanner failure 均 fail 当前 Module。
- Validation 不发现 seed，不参与 Call Graph gate。

## Reflection and ServiceLoader

- Reflection 只采用 WALA `ReflectionOptions.FULL` 与 MethodHandle extension；不实现通用 Reflection target inference 或 completeness classifier。
- ServiceLoader 只处理 reachable `load/loadInstalled` 且 service type 可解析的 callsite。
- Provider 来自 scope 内 `META-INF/services/*`；必须可解析、assignable、public 且具有 public zero-arg constructor。
- Overlay 包含 load caller→provider constructor，以及当前 Module graph 内兼容 interface invoke→registered implementation。
- 缺少 WALA `CGNode` 时使用 `OverlayMethodNode`；edge 标记 `SERVICE_LOADER`、`CONSERVATIVE` evidence。
- Unresolved service/provider 使 Module `INCONCLUSIVE`。

## Acceptance

- Vanilla 0-1-CFA、`FULL`、MethodHandle extension 可由 fixture 验证。
- 不同 Module 的 classpath/version、CHA、graph、cache 相互隔离。
- WALA build/query 单线程；Module pool 并发受 `--analysis-parallelism` 限制。
- Report 记录 selector boundary、matched classes、scope、exclusions、entrypoints、parameter candidates、nodes、edges、contexts、elapsed。
