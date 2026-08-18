---
title: "CLI Preflight and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact/tree pipeline 与 exit code"
  - path: "wiki/features/report-generator.md"
    desc: "Preflight保留、HTML Diagnostics删除与显式topology JSON边界"
  - path: "wiki/features/jdk-method-models.md"
    desc: "algorithm相关--jdk-model默认值、关闭语义与严格失败"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "JAR pair failure isolation与异常诊断"
  - path: "wiki/rules/operational-evidence-design.md"
    desc: "功能设计必须遵守的运行证据、信息级别与输出成本规则"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "spring-backend options、validation、exit code"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/MavenArtifactPattern.java"
    desc: "groupId:artifactId Glob validation与segment matcher"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyArtifactSelection.java"
    desc: "include union、exclude precedence与稳定去重"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CallGraphDiagnosticsExporter.java"
    desc: "Schema 13 Java-first方法体过滤、JDK dispatch与caller-local Impact Path pruning evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/ImpactPathPruningSummary.java"
    desc: "Module级extension状态、metrics与bounded examples"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/JdkModelSelectionConverter.java"
    desc: "jdk8/none精确标识符parse与CLI error"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/jdk/JdkModelSelection.java"
    desc: "public selection与defaultSelection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/strategy/CallGraphPolicy.java"
    desc: "CLI、pipeline与Java API共享的algorithm/model policy"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/preflight/PreflightRunner.java"
    desc: "check DAG"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticContext.java"
    desc: "stage、substage、可选phase与stable identity context"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticEvent.java"
    desc: "单个physical Console line的immutable格式化值"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLog.java"
    desc: "Console-only verbosity、stderr与monotonic timing façade"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLogFormatter.java"
    desc: "Console/HTML 共用的五段 prefix 与 attribute canonicalization"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/RuntimeMetricsSession.java"
    desc: "TRACE-only 100 ms heap observation、10 s snapshot 与 final peak summary"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/ManagedExecutorRegistry.java"
    desc: "Analyzer-owned fixed pool factory 与 registry"
---

# Feature: CLI Preflight and Diagnostics

## Summary

CLI 在昂贵分析前执行结构化 Preflight。`DiagnosticLog` 是 Analyzer 唯一日志 façade；`impact`/`tree` 的业务日志统一写入 stderr，并使用稳定五段 prefix。控制流程只使用`stage`和`phase`：Stage是具有开始、完成、失败与耗时的执行边界；Phase是Stage内可选的算法活动。全局 verbosity 默认为 `INFO`，`-v` 选择 `DEBUG`，`-vv` 选择 `TRACE`；只有 `TRACE` 启动异步 Runtime Metrics。

## Design Decisions

- handled uncertainty 与 hard failure 分离：能够继续完成 Call Graph 的 coverage limitation 使用 `INCONCLUSIVE` 和 exit code `0`；无法建立可信 Module 结果的错误使用 `FAILED`/`PARTIAL_SUCCESS` 和 exit code `2`。
- 外部 dependency 的 excluded JDK reference 使用 artifact-level `WARN`，而不是因为 JAR 内可能不可达的 class 阻断整个 Module；当前项目和 Reactor code 仍保持严格边界。
- 五段 prefix 的第五段承载可选Phase与当前`stage/substage`无法唯一表达的阶段实例或日志分类identity；Phase固定在identity之前。结果、观测值和其他实际日志信息使用message中的`key=value`。
- 不为没有明确内部算法步骤的Stage生成Phase；`event`和`status`仍是普通日志字段，不形成新的控制流程层级。
- 并行JAR pair failure以单个原子日志操作输出WARN与可选DEBUG stack，避免不同pair的message和stack交叉。

## Actors / Entrypoints

- `dependency-analyzer impact`和`tree`通过root verbosity选择Console可见性；Preflight、Maven subprocess、JAR diff、Module analysis与Report共享同一`DiagnosticLog`。

## Behavior Contract

- INFO保留稳定进度、warning和error；DEBUG增加分析决策、完整Maven output与异常stack；TRACE再增加细粒度evidence和Runtime Metrics。
- 全部Diagnostic line只进入Console；HTML不保留event snapshot，也不渲染Diagnostics section。

## Core Flow

1. CLI根据`-v`次数创建command-scoped`DiagnosticLog`。
2. 各Stage使用immutable`DiagnosticContext`输出line；内部算法事件按需通过`withPhase`创建context。
3. Formatter为每个物理行生成同一五段prefix；`DiagnosticLog`不保存全量event list。

## Global Verbosity

- 未传 `-v`：`INFO`，输出稳定的 stage、progress、warning 和 error；Maven subprocess 只透传 warning/error。
- `-v` 或一个 `--verbose`：`DEBUG`，增加 analysis option/decision、完整 Maven subprocess output；command failure和隔离的JAR pair failure同时输出完整stack trace与cause chain。
- `-vv` 或两个 `--verbose`：`TRACE`，增加 normalized path、ref、scope 等细粒度 evidence，并在 command dispatch 后立即输出 Runtime Metrics snapshot，之后每10 s输出一次；heap observation独立按100 ms执行。
- `-v` 是 inherited global option，可位于 subcommand 前或后。`DEBUG`/`TRACE` line只有相应级别启用时才进入Console；Report不携带任何Diagnostic event。
- Analyzer 运行日志只写 stderr；stdout 不承载 Analyzer 日志。Picocli help/usage、参数解析错误和绕过 Analyzer logging 的第三方库 stderr 不受五段 prefix contract 约束。
- `impact`/`tree` 的 Maven subprocess output逐行分类后写入Console，不生成build/dependency `.log`文件。默认只显示warning/error，`-v/-vv`显示完整输出；failure只在内存保留bounded tail。

## Impact Options

- `--baseline <ref>` required；`--target <ref>` 默认 current checkout。
- `--path <path>`：reactor root 或 leaf Module。
- `--analysis-target spring-backend`：默认且唯一 target。
- `--analysis-parallelism <N>`：未传值时运行期取`max(1, Runtime.getRuntime().availableProcessors() / 2)`，向下取整；显式值必须`>=1`。作为唯一`common`pool大小，全局限制front preparation、JAR diff、Impact Query与code comparison；值为`1`时front preparation串行。显式值超过可用CPU时输出warning，不截断。
- `--call-graph-algorithm <cha|k-obj>`：默认`cha`，command-wide应用到全部Module；`k-obj`标记为`experimental`。大小写不敏感，不接受alias或自动fallback；其他标识在参数解析阶段失败。
- `--k-obj-depth <正整数>`：只可与`k-obj`同时使用，默认`1`，不设置人为上限；零值、负值及与其他算法组合均在Preflight前作为参数错误返回。
- `--jdk-model <jdk8|none>`：默认依algorithm解析。未指定algorithm/model或显式`cha`但未指定model时为`none`；其他algorithm未指定model时为`jdk8`。显式`cha + jdk8`在Preflight前exit code`1`；其他algorithm仍可显式`none`。
- `--wala-reflection-options <enum-name>`：默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`，接受WALA `ReflectionOptions` enum name；`--reflection-options`为alias。CHA保留配置值但不应用，Report显示`not applied by cha`。
- `--result-refinement-algorithms`已删除；传入旧option由Picocli作为未知参数返回exit code `1`。SSA与decompiled Java equivalence固定启用；CHA固定执行experimental `cha-local-receiver-inference` Impact Path pruning extension，`k-obj`不执行路径裁剪。
- `--entrypoint-include '<class-path-pattern>'` 与 `--entrypoint-exclude ...`：可重复；直接匹配 slash-separated JVM internal class path，include 取并集，exclude 优先。普通 segment支持 `*`、`?`；`**` 只能作为最后一个完整 segment。Colon/dot旧语法、leading/trailing slash、空 segment与嵌入式 `**` 在 CLI validation阶段 exit `1`。
- `--dependency-include '<groupPattern>:<artifactPattern>'`与`--dependency-exclude ...`：可重复并按首次出现去重；include取并集，未传include表示全部，exclude始终优先。两段均非空且不含空白，只允许一个`:`；`*`和`?`分别匹配当前段任意长度与单字符，`**`没有特殊语义，匹配区分大小写。只比较target `groupId:artifactId`，忽略version、type和classifier。格式错误exit `1`；显式selector整体未选中任何`VERSION_CHANGED` JAR pair时在JAR Diff前exit `1`，不替换旧Report或diagnostics output。
- `--call-graph-timeout-seconds <N>`：默认 `0`；按 Module、从实际 WALA build 开始计时。
- `--call-graph-diagnostics-output <json>`：可选benchmark-only只读输出；未设置时不执行CGNode ranking、IMethod子榜、shortest path、IR capture或decompilation。路径不得与`--output`相同。
- `--format html`：唯一有效格式；`md` fail fast。
- `--java-home`：必须是完整 JDK 8；analyzer JVM 可为 Java 17。

## Exit Codes

- `SUCCESS`、`INCONCLUSIVE`：`0`。
- `PARTIAL_SUCCESS`、`FAILED`：`2`。
- Argument validation、Preflight、global preparation failure：`1`。

`INCONCLUSIVE`表示analysis在公开model内完成，但存在JAR diff、`invokedynamic`、MethodHandle、ServiceLoader或外部dependency excluded JDK reference uncertainty；它不是hard failure。ChangePoint收集期decompiled Java `UNKNOWN`会继续执行SSA；SSA `UNKNOWN`采用fail-open并保留变化。`UNKNOWN`本身不降级Module。仅由最后一类scope gap触发时，Module reason为`INCONCLUSIVE_SCOPE_VALIDATION`。

## Preflight Boundary

- 校验path/Git/root POM/output/JDK 8/Maven version/Maven arguments、内嵌Dependency Plugin`3.6.1`与Dependency Evidence Plugin`3.1.0-SNAPSHOT` runtime、workspace/structured evidence capability。
- Preflight failure 不启动 pipeline，不触碰旧 Report。
- 完整Dependency Diff后的`dependency-selection` Stage输出candidate/selected/excluded pair、include/exclude数量与耗时；DEBUG逐pattern输出匹配数。JAR Diff生命周期只统计selected pair。单个pattern未命中不是failure，只要整体仍有selected pair即继续。
- Reactor/leaf mode、Module coordinate collision、physical artifact ambiguity 属于 preparation failure。
- entrypoint selector 使用当前 Module `target/classes` 的 immutable index；interface、annotation、private nested class与private method/constructor不进入root范围。Filtered门禁与Call Graph roots复用同一index；privacy过滤不从scope删除class/method。Relevant Module无可执行root时为`SKIPPED_USER_ENTRYPOINT_SCOPE`；所有relevant Module均无匹配时command exit `1`，不替换旧Report。
- `PROJECT`/`REACTOR_DEPENDENCY` excluded JDK reference、scope I/O/scanner failure 与 Call Graph failure 属于 handled failed Module result，可产生 partial Report。外部 `DEPENDENCY` reference 只产生 `INCONCLUSIVE` warning。
- 非CHA默认`jdk8`的Synthetic loader、安装或catalog completeness failure属于Call Graph failed Module；不转换为coverage limitation，也不fallback到`none`。CHA固定跳过安装。

## Diagnostics

- `DiagnosticContext`是immutable prefix context，包含`stage`、`substage`、独立可选`phase`和ordered attributes；不使用thread name。当前生产日志只使用`check`、`reactor`、`module`、`artifact`、`pool` identity。
- 每个物理行固定为 `[时间][日志级别][阶段][子阶段][额外信息] message`。时间使用带 offset、毫秒精度的 ISO 8601；level 始终显式为 `TRACE/DEBUG/INFO/WARN/ERROR`；缺失段使用 `[-]`。
- 第五段格式为可选`phase`加identity，使用`key=value`与`;`分隔；canonical顺序固定为`phase, check, reactor, module, artifact, pool`。无Phase且无identity时为`[-]`；只有Phase时例如`[phase=REVERSE_BFS]`；二者并存时例如`[phase=REVERSE_BFS;module=g:a:1]`。`command`、`side`、path、scope、progress、status、decision、elapsed、计数、result与metrics value禁止进入第五段；这些实际日志信息追加到message。
- `\\`、`;`、`=`、`[`、`]` 在 prefix 中统一转义。多行 message 和 stack trace 拆成独立物理行，每行重新添加完整 prefix。
- `stageStarts`以完整identity stable key计时；Phase不进入attributes或stable key，内部Phase切换不会破坏同一Stage的elapsed匹配。完成或失败line以`elapsedMs=...`输出到message。
- `DiagnosticLog`统一生成Stage生命周期正文：`started`、`completed; elapsedMs=...`、`failed; reason=...; elapsedMs=...`；调用方只追加详情，不自行拼接生命周期词。
- `INFO`输出front branch、Module Stage start/end；`DEBUG`输出每个logical coordinate JAR pair start/end；`TRACE`输出筛选后的command/path evidence，不输出credential、settings内容或完整user arguments。
- JAR pair failure在`INFO`以WARN输出异常类型和完整message；`DEBUG`/`TRACE`紧接输出同context的完整stack与cause chain。
- 外部 dependency scope warning 使用 `[scope-validation][module][module=…][artifact=…]` context；每个 artifact 一条，warning text 同时进入 Module `Coverage limitations`。
- `DiagnosticLog`不保留event list或`getEvents()` snapshot；Overall和Module HTML不包含Diagnostics section、目录入口或event formatter。
- 显式`--call-graph-diagnostics-output`是用户主动请求的Schema 13 topology JSON，独立于HTML。根对象保存固定`ssaEquivalence.enabled/fixed`与只包含`cha-local-receiver-inference`的`impactPathPruningExtensions`，不再保存`resultRefinementAlgorithms`。Module保存algorithm状态、topology、Context、IR snapshot、JDK声明分派裁剪计数与最多10条caller/callee edge example、Impact Path edge metrics。`changePointCollection.decompiledJavaEquivalence`声明`evaluationOrder=1/shortCircuitWhen=IDENTICAL`；`ssaEquivalence`声明`evaluationOrder=2/shortCircuitedBy=DECOMPILED_JAVA_TEXT_IDENTICAL`及eligible/executed/skipped、三态结果、reason、version、hash和timing。只保存单一suppression reason，不写decompiled source。query-time裁剪不改写topology node/edge。
- Call Graph completion message包含effective`algorithm`、`jdkModel=jdk8|none`与`jdkDeclaredDispatchPrunedTargets`，并仅在`k-obj`时包含实际`kObjDepth`。HTML仍展示CHA Reflection not-applied，但不复制Diagnostic line。
- `impact-query` INFO completion输出visited nodes、edge checks与pruned edges；`-vv`额外输出最多10条稳定caller/callee edge example。Receiver unknown仍保留原CHA edge，不改变Module status或生成coverage limitation。
- 每个Module的`evidence-analysis` INFO覆盖Structural metadata scan与唯一Call Graph node scan；start包含`changes/graphNodes`，completion包含`structuralReferences/evidence/queryNodes/bindings`。`-vv` Evidence进度在collector同一线程按5秒门限输出，不创建scheduler。
- 每个Module的`impact-query` INFO start在planning前输出并包含`changes/evidenceBindings`；planning完成后的DEBUG包含`seeds/queryNodes/workers`。该Stage的开始、完成与失败不携带Phase。`-vv` QueryNode事件使用`query-node-started|progress|completed`，第五段按当前算法活动携带`REVERSE_BFS`、`PATH_MATERIALIZATION`或`REPRESENTATIVE_SELECTION` Phase；message携带stable ordinal、Evidence seed数、elapsed、recent node，以及QueryNode-local `visited`、`edgeChecks`和`prunedEdges`，不重复`phase=`。heartbeat使用fixed-delay，不补发暂停期间的过期事件。
- JAR diff aggregate INFO completion包含成功logical pair的唯一`changes`总数、logical `pairs`、`failedPairs`、实际`workers`，以及`eligible`、`ssaExecuted/ssaSkipped`、SSA matched/different/unknown、Java identical/different/unknown、`unionSuppressed`、`retained`和两类elapsed milliseconds；空diff全部计数为`0`。
- 每个logical JAR pair的DEBUG completion输出`rawChanges/changes/semanticEligible/ssaExecuted/ssaSkipped/ssaMatchedSuppressed/ssaDifferent/ssaUnknown/ssaElapsedMillis/javaIdentical/javaDifferent/javaUnknown/semanticSuppressed/semanticRetained/decompileElapsedMillis`；同一pair绑定多个Module不重复比较或重复计入pair日志。
- `-vv`对实际执行且结果为SSA `DIFFERENT`与`UNKNOWN`的候选输出原子多行审计块：Stage为`jar-diff`、substage为`ssa-equivalence-audit`，第五段携带artifact与可搜索method identity；正文固定包含candidate label、status、reason、hash、class version、耗时及old/new bytecode、原始IR、normalized IR六段。Java `IDENTICAL`候选短路，不生成SSA审计。`MATCHED`、INFO与DEBUG不构建该文本。

示例：

```text
[2026-08-05T14:30:01.123+08:00][INFO][analysis][reactor][reactor=root] Maven collection completed; progress=1/2; status=SUCCESS; modules=8
```

## Runtime Metrics

- 生命周期从 Picocli 成功 dispatch 到 `impact`/`tree` 的 `call()` 开始，到 command 返回结束。Help、usage 和参数解析失败不启动采样。
- `INFO`/`DEBUG` 使用 no-op session；不创建 scheduler、不读取 heap、不输出 metrics。
- `TRACE` 使用 command-scoped daemon scheduler：启动时立即观察并输出snapshot，之后每100 ms fixed-delay观察heap，但只按10 s cadence输出heap/thread-pool snapshot。所有normal return、early return和exception path都通过`close()`停止。
- Heap 行使用 `stage=runtime-metrics, substage=heap` 和空第五段；`sample/elapsedMs/heapUsedMiB/heapCommittedMiB/heapMaxMiB` 位于 message，MiB 保留 1 位小数。
- 当前注册的Analyzer-owned pool使用`stage=runtime-metrics, substage=thread-pool`，第五段只保留`pool=common`；sample、elapsed、pool size、`queued/completed/submitted` count和lifecycle value位于message。`common`顺序承载front preparation、JAR diff、Impact Query与code comparison。Scheduler、process-output pump、JVM ForkJoin common pool、WALA internal thread和Maven external process不注册。
- 单次采样异常使用 `stage=runtime-metrics, substage=sampler` 和空第五段；sample、elapsed 与 error 位于 TRACE transient message，不会改变 command status、Report 或 exit code。
- `close()`在设置closed flag前强制一次final heap observation，然后使用`stage=runtime-metrics, substage=summary`输出`sample count`、`peakHeapUsedMiB`、`peakHeapCommittedMiB`与`heapMaxMiB`。该summary是benchmark heap主指标来源；process-tree RSS继续由外部runner采集。

## Acceptance Criteria

### Functional

- Given单个JAR pair抛出`BytecodeDiffException`；When使用INFO；Then同pair WARN包含异常类型与完整message，其他pair继续执行。
- Given同一failure使用DEBUG或TRACE；When输出诊断；Then完整stack与cause chain逐行携带同一pair prefix，且HTML不包含该内容。
- Given未传旧result refinement option；When解析Impact CLI；Then固定SSA与CHA extension状态不依赖selection。Given传入旧option；Then在Preflight前作为unknown option返回exit code `1`。
- Given SSA结果为`DIFFERENT`或`UNKNOWN`且verbosity为TRACE；When输出审计；Then一个method的全部物理行具有相同artifact/method identity且不与其他并发method块交错。Given任一审计section不可用；Then输出稳定unavailable reason且不改变ChangePoint。

### Non-Functional

- [ ] 并行failure的WARN与对应stack原子输出，不与其他pair的异常块交叉。
- [ ] 日志不输出credential、settings内容或未过滤的完整user arguments。
- [ ] 全仓术语门禁仅允许WALA和Java强制外部API保留既有名称；项目自有控制流程只使用Stage与Phase。

## Edge Cases

- Exception message为`null`时使用显式placeholder；不影响pair isolation或Module outcome。
- 多行message、Windows换行和nested cause均规范化为独立prefixed physical line。

## Implementation Boundaries

- `DiagnosticLog`负责visibility、physical-line格式与Stage计时；业务stage负责提供完整、可行动的message和稳定context。
- Stack trace只用于Console诊断，不写入HTML。显式topology JSON只由`--call-graph-diagnostics-output`控制。
