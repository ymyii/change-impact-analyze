---
title: "Dependency Analysis Pipelines"
type: architecture
relations:
  - path: "wiki/features/call-graph-engine.md"
    desc: "per-Module Call Graph input、strategy 与 metadata"
  - path: "wiki/features/impact-tracing.md"
    desc: "构图后 evidence、QueryNode reverse query 与固定CHA调用边裁剪"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "Schema v3 dependency evidence 与 logical artifact binding"
  - path: "wiki/features/maven-build-runner.md"
    desc: "共享reactor scope到Maven compile的执行合同"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree analyze单侧入口scope与报告边界"
  - path: "wiki/features/repository-dependency-tree-diff.md"
    desc: "tree diff双侧workspace、结构配对与增量报告边界"
  - path: "wiki/features/report-generator.md"
    desc: "冻结结果到 HTML 的消费边界"
  - path: "wiki/rules/package-boundaries.md"
    desc: "pipeline 的 package 依赖方向"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "impact CLI 与 command-wide configuration"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactExecutionEngine.java"
    desc: "command-level Impact 执行边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "pair diff、构图、evidence、query、code comparison和snapshot编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorInventoryBuilder.java"
    desc: "tree/impact共享的入口POM与祖先aggregator resolver"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/MavenActivationContext.java"
    desc: "与实际Maven执行对齐的profile activation context"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleScopePlanner.java"
    desc: "共享scope到impact Module模型的适配"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyArtifactSelection.java"
    desc: "JAR Diff前的changed dependency source边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffResult.java"
    desc: "ChangePoint收集期SSA/decompiled Java filtering结果与证据"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/MethodBodyComparisonCache.java"
    desc: "command-owned方法体源码fragment与缓存diff读取边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleCallGraphInputAdapter.java"
    desc: "业务 domain 到 Call Graph input 的投影"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/engine/ModuleCallGraphEngine.java"
    desc: "独立 Call Graph engine"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CallGraphCoverageMapper.java"
    desc: "Call Graph typed finding 到业务 reason 的转换"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeExecutionEngine.java"
    desc: "Tree Analyze preflight、Reactor processing与发布执行边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffExecutionEngine.java"
    desc: "Tree Diff双侧scope、逐Reactor采集、diff与发布执行边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffEngine.java"
    desc: "Tree Module结构校验与occurrence-aware依赖差异领域边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/cha/ChaImpactPathPruningEngine.java"
    desc: "固定顺序、fail-open的CHA Impact Path裁剪引擎"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/ImpactPathPruningSummary.java"
    desc: "Module级统一extension指标与bounded evidence"
---

# Architecture: Dependency Analysis Pipelines

## Summary

Root CLI 分发 `impact` 与`tree`父命令，后者再分派`tree analyze`和`tree diff`。`impact`只编译target，并为每个relevant Module构建一张selected Call Graph；baseline提供dependency evidence与old artifact。默认组合为`cha + changed-paths + jdk-model none`，Static Single Assignment（SSA，静态单赋值）与decompiled Java equivalence固定启用；CHA固定执行caller-local `cha-local-receiver-inference` Impact Path裁剪extension。`k-obj`是显式选择的实验性algorithm。

三个分析入口都通过中立`reactor` package把入口POM解析为`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE`。Resolver只读取入口active module graph和文件系统祖先aggregator，不依赖任一命令package，也不执行repository-wide discovery。`ImpactCommand`分别对baseline和target workspace规划scope；`TreeAnalyzeCommand`对current/ref snapshot规划一个scope；`TreeDiffCommand`对两侧分别规划scope，再按ReactorKey与ModuleKey验证结构并比较。后续Call Graph、dependency tree与Report pipeline消费同一边界模型。

## Key Terms

- Execution Engine：命令级业务编排边界。Impact以接口表达，Tree以独立执行器表达；都不负责Picocli option定义。
- Evidence analysis：Call Graph fixed point完成后的单线程阶段，包含Structural metadata scan、唯一Call Graph node scan与统一binding。
- Reverse BFS binding：`QueryNode -> ChangePointTerminal`辅助索引；只为Reverse BFS导航，不替代`BoundChangePoint` resolution事实。
- Finalization：把coverage、query和stage metrics组装成Module结果，并在Report cache前移除live WALA对象。
- Reactor scope：入口POM、active module closure、execution root、requested Module和scope mode组成的不可变边界。Git root只提供映射与eligibility，不代表分析范围。
- Tree side：Tree Diff中的baseline或target workspace及其独立inventory、collection和ReportCache namespace；side不是跨运行缓存身份。

## Architecture Decision Records

- None.

## Package Dependency Direction

```mermaid
flowchart LR
  CLI["cli / commands"] --> Impact["impact pipeline + domain"]
  CLI --> Reactor["reactor scope resolver"]
  Reactor --> Impact
  Reactor --> TreeAnalyze["tree analyze pipeline"]
  Reactor --> TreeDiff["tree diff pipeline"]
  TreeDiff --> TreeDomain["tree diff domain"]
  TreeDomain --> TreeReport["tree diff report"]
  Impact --> Engine["callgraph.engine"]
  Engine --> Strategy["callgraph.strategy"]
  Strategy --> CHA["strategy.cha"]
  Strategy --> KObj["strategy.kobj"]
  CHA --> Common["scope / entrypoint / protocol / model"]
  KObj --> Common
  Impact --> Pruning["impact.pruning"]
  Impact --> Snapshot["frozen report result"]
  Snapshot --> Report["report"]
```

`reactor`和`classpath`是`impact`与`tree`共同依赖的中立层。前者拥有POM安全解析、profile activation、module graph与aggregator resolution；后者拥有binary-name ownership、winner precedence与`LOW`/`HIGH`冲突分类。

禁止反向边：`callgraph.. -> impact..`、`callgraph.. -> report..`、`report.. -> strategy.cha..|strategy.kobj..`。CHA 与 `k-obj` implementation 不互相引用；公共 protocol 不依赖 strategy 或 engine。

## Impact Flow

```mermaid
flowchart TD
  Scope["entry POM + active graph + ancestor aggregator"] --> Prepare["baseline dependency + target compile"]
  Prepare --> Evidence["Schema v3 dependency evidence"]
  Evidence --> Diff["complete Maven Dependency Diff"]
  Diff --> Selection["dependency-selection: changed JAR Glob boundary"]
  Selection --> JarDiff["selected bytecode + resource diff"]
  JarDiff --> Java["all method body candidates: Vineflower exact text"]
  Java -->|"IDENTICAL: suppress + skip SSA"| Cache["atomic method-body-comparison JSON Lines fragment"]
  Java -->|"DIFFERENT / UNKNOWN"| Semantic["normalized SSA"]
  Semantic -->|"MATCHED: suppress; else retain"| Cache
  Cache --> Effective["effective ChangePoints"]
  Effective --> Bind["BoundChangePoint + compact staged evidence per Module"]
  Bind --> PathPlan["changed-path union or full scope"]
  PathPlan --> Input["ModuleCallGraphInputAdapter"]
  Input --> Validate["Call Graph scope validation"]
  Validate --> Build["ModuleCallGraphEngine"]
  Build --> Metadata["freeze graph + Call Graph metadata"]
  Metadata --> Structural["StructuralImpactScanner"]
  Structural --> Collector["single-pass ChangePointEvidenceCollector"]
  Collector --> Binding["resolution + unified reverseBfsBindings"]
  Binding --> Coverage["CallGraphCoverageMapper"]
  Coverage --> Query["QueryNode reverse Impact Query"]
  Query --> ChaRefine["fixed CHA caller-local pruning registry"]
  ChaRefine --> Snapshot
  Snapshot --> Compare["cached body diff + on-demand non-body comparison"]
  Compare --> Report["stream HTML + atomic publication"]
```

## Tree Flow

```mermaid
flowchart TD
  Path["current checkout path"] --> Scope["Git-relative bounded reactor scope"]
  Scope --> AnalyzeChoice{"tree mode"}
  AnalyzeChoice -->|"analyze"| Snapshot["current checkout or ref snapshot"]
  Snapshot --> AnalyzeCollect["compile + dependency tree + classpath evidence"]
  AnalyzeCollect --> AnalyzeReport["Tree Schema v2 incremental report"]
  AnalyzeChoice -->|"diff"| Workspaces["baseline worktree + target ref/current workspace"]
  Workspaces --> SideScopes["independent side inventories"]
  SideScopes --> Pair["ReactorKey + ModuleKey structure pairing"]
  Pair --> Collect["per-side compile + dependency tree"]
  Collect --> Diff["DependencyKey aggregation + PathKey pairing"]
  Diff --> DiffReport["Tree Diff Schema v1 checkpoint report"]
```

Tree Analyze的classpath evidence、version mediation和class conflict enrichment不进入Tree Diff。Tree Diff复用dependency-only collector与共享Maven runtime，避免执行完整Analyze collector后丢弃证据，也不通过mode flag合并不同职责。

## Call Graph Boundary

- `ModuleCallGraphInput` 只携带 module label、class directory、artifact coordinate、scope/body policy、change selector 与 protocol fact。
- Call Graph engine 不接收 `ModuleAnalysisUnit`、`BoundChangePoint` 或 `ModuleChangedPathSelection`。
- `CallGraphBuildContext` 保存公共 WALA 构建数据；`ChaCallGraphRequest` 与 `KObjCallGraphRequest` 保存各自配置。
- Engine 只输出 graph/session、stats、capability、typed protocol limitation、scope warning、boundary finding 与 optional topology。
- `PerModuleImpactPipeline`实现`ImpactExecutionEngine`。Call Graph完成后，`evidence-analysis`先全量扫描effective class metadata，再在同一线程遍历最终Call Graph一次；不建立node/IR snapshot，不创建Evidence线程池。
- `ChangePointEvidenceIndex`以每个`BoundChangePoint`唯一resolution为事实主索引，以exact `QueryNode -> ChangePointTerminal`为Reverse BFS辅助索引。Structural与普通Evidence共用该binding；Query不再按Structural Reference重复扫描Call Graph。
- `CallGraphCoverageMapper`是Call Graph finding到业务coverage reason的唯一转换点。
- Report 只消费 detached immutable snapshot，不读取 live graph、hierarchy、cache 或 concrete strategy。

## Module Contract

- Reactor root：target reactor 执行一次 `mvn compile`；active Module 独立分析。
- Leaf Module：从所属 reactor root执行 `-pl <path> -am compile`，只报告当前 Module。
- Nested aggregator入口：只分析该入口管理的active subtree，不扩大到外层reactor。
- Standalone：入口POM没有active child且不属于任何祖先active closure时，从入口目录直接执行，不附加`-pl/-am`。
- Baseline和target分别解析scope；`allModules`与reactor coordinates继续用于artifact ownership、`REACTOR_DEPENDENCY` classpath和上游`target/classes`，Module增删保留`BASELINE_ONLY`/`TARGET_ONLY`。
- 当前 Module classes 为 `PROJECT`；上游 reactor Module 为 `REACTOR_DEPENDENCY`；外部 selected artifact 为 `DEPENDENCY`；target JDK 为 `JDK`。
- Entrypoint class 只来自当前 Module classes index。Scope、hierarchy、cache 和 Call Graph 均为 per-Module。
- Requested dependency scope 与 actual scope 分开保存。无法稳定恢复完整 changed path 时，只对当前 Module fallback 到 `full`，并生成 typed warning。
- `DependencyArtifactSelection`只裁剪`VERSION_CHANGED` JAR logical pair产生的Bytecode Diff、ServiceLoader Diff、SSA/decompiled Java比较、ChangePoint、Evidence、Impact Query与code comparison。完整Dependency Diff和target/baseline classpath不裁剪；`changed-paths`仍从选中seed反向保留全部中间依赖，路径外sibling继续使用既有no-op policy。

## Algorithm and Pruning Contract

- `--call-graph-algorithm` 只接受 `cha`、`k-obj`；默认 `cha`，不自动 fallback。
- CHA 固定 `jdk-model=none`，不应用 Reflection。`k-obj` 默认 `jdk-model=jdk8`，允许显式 `none`，并应用 Reflection。
- `--k-obj-depth` 只对 `k-obj` 合法，默认 `1`。
- `--result-refinement-algorithms`已删除，旧参数作为未知option返回exit code `1`。方法体equivalence在ChangePoint收集期固定运行，不按class major version门禁。全部候选先比较decompiled Java；`IDENTICAL`立即抑制并短路。只有Java `DIFFERENT/UNKNOWN`执行normalized SSA，`MATCHED`抑制，其余fail-open保留。
- CHA固定执行代码内`cha-local-receiver-inference` extension registry；无CLI、`ServiceLoader`或外部Plugin注册。extension只验证已有caller-to-callee predecessor edge，不发现terminal evidence、不补边、不构建第二张graph。`k-obj`不执行Impact Path裁剪。
- Reverse BFS以exact `QueryNode`为状态，在加入前驱前执行caller-local receiver裁剪；只有`PROVEN_INFEASIBLE`才跳过调用边，unknown、不适用与非中断异常均fail-open。bridge参数、factory返回值和其他跨方法receiver flow不推导，可能保留保守路径。

## Concurrency

- Module 严格串行，避免同时持有多张 WALA graph。
- scope planning后创建唯一command-wide managed `common`固定线程池，大小严格等于`--analysis-parallelism`。front preparation、JAR diff、Impact Query与code comparison顺序复用；显式值为`1`时baseline dependency与target build串行。
- 各阶段保留独立worker计数与Diagnostic Stage，但不拥有线程池。工作线程统一使用`dependency-analyzer-common-*`；全局异常取消已提交任务并关闭common pool。
- 当前Module snapshot无条件把path node转换为`SnapshotQueryNode`，把method Evidence anchor转换为stable-only anchor，清空只供Reverse BFS使用的binding并释放live WALA state；该生命周期不依赖`ReportCache`是否存在。
- `impact`无条件创建当前run的`ReportCache`。每个selected logical JAR pair原子发布一个稳定排序的`method-body-comparison` JSON Lines fragment；即使eligible为`0`也保留空fragment。记录logical artifact、method identity、hash、class major version、decompile状态与耗时、`ssaExecuted`、SSA三态或`NOT_EXECUTED/JAVA_TEXT_IDENTICAL_SHORT_CIRCUIT`、old/new源码或不可用原因、抑制原因，不保存physical JAR path。
- filtered和retained候选都进入cache。`ModuleChangeSet`与`ModuleAnalysisUnit`只保存不含源码的compact evidence；完整源码仅存在于cache。
- Impact/Structural path关联的retained `METHOD_BODY_CHANGED`从cache生成Unified diff；`UNKNOWN`直接生成`Unavailable`，禁止重新调用Vineflower。其他ChangePoint kind沿用按需Code comparison。
- Cache fragment写入、读取、complete marker、record count、JSON与identity/schema校验失败终止command。单侧或双侧反编译失败只形成`UNKNOWN`，不把JAR pair或Module标记为失败。
- HTML只读取已冻结Module result；显式Schema 13 topology JSON从diagnostic fragment输出，并保存固定SSA状态、JDK声明分派裁剪指标、caller-local Impact Path edge裁剪证据，以及`changePointCollection.ssaEquivalence`和`changePointCollection.decompiledJavaEquivalence`的无源码审计证据。
- Report完整写入同filesystem staging后原子替换；发布成功或失败后关闭并删除当前run的`report-cache`，不跨command复用。

## Failure Boundaries

- 入口POM缺失、active module缺失或越界、cycle、重复/不可解析coordinate和settings parse failure属于全局preparation failure，不回退repository扫描。Call Graph、Evidence、Query或finalization失败转换为该Module的typed failure，后续Module继续。
- 方法体反编译不可用是候选级fail-open；command-owned cache基础设施或完整性失败是全局fail-fast。
- `ChangePointEvidenceIndex`在binding与resolution不一致时fail-fast，禁止生成缺少terminal事实的路径。
- Tree Analyze command-level preflight失败不替换旧Report；Reactor collection失败记录issue并继续；renderer/publisher失败由`TreeExecutionEngine`关闭Report session并返回失败状态。
- Tree Diff preflight失败不替换旧Report。单侧采集失败形成`UNAVAILABLE`，结构单侧缺失形成`STRUCTURE_MISMATCH`；至少一个Module可比较时继续后续Reactor并最终为`COMPLETED_WITH_ISSUES`，没有可比较Module或pipeline/publication失败时为`FAILED`。两类非成功终态都保留此前原子发布的页面。
- 线程中断继续传播；不把partial Evidence index发布为completed Module结果。
