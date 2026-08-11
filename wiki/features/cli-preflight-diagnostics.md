---
title: "CLI Preflight and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact/tree pipeline 与 exit code"
  - path: "wiki/features/report-generator.md"
    desc: "Preflight 与 diagnostics 输出"
  - path: "wiki/features/jdk-method-models.md"
    desc: "--jdk-model默认值、关闭语义与严格失败"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "JAR pair failure isolation与异常诊断"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "spring-backend options、validation、exit code"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/JdkModelSelectionConverter.java"
    desc: "jdk8/none精确标识符parse与CLI error"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/JdkModelSelection.java"
    desc: "public selection与defaultSelection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/preflight/PreflightRunner.java"
    desc: "check DAG"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticContext.java"
    desc: "并发任务 stable context"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLog.java"
    desc: "retained/transient event、verbosity、stderr 与 monotonic timing façade"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLogFormatter.java"
    desc: "Console/HTML 共用的五段 prefix 与 attribute canonicalization"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/RuntimeMetricsSession.java"
    desc: "TRACE-only 100 ms heap observation、10 s snapshot 与 final peak summary"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/ManagedExecutorRegistry.java"
    desc: "Analyzer-owned fixed pool factory 与 registry"
---

# Feature: CLI Preflight and Diagnostics

## Summary

CLI 在昂贵分析前执行结构化 Preflight。`DiagnosticLog` 是 Analyzer 唯一日志 façade；`impact`/`tree` 的业务日志统一写入 stderr，并使用稳定五段 prefix。全局 verbosity 默认为 `INFO`，`-v` 选择 `DEBUG`，`-vv` 选择 `TRACE`；只有 `TRACE` 启动异步 Runtime Metrics。

## Design Decisions

- handled uncertainty 与 hard failure 分离：能够继续完成 Call Graph 的 coverage limitation 使用 `INCONCLUSIVE` 和 exit code `0`；无法建立可信 Module 结果的错误使用 `FAILED`/`PARTIAL_SUCCESS` 和 exit code `2`。
- 外部 dependency 的 excluded JDK reference 使用 artifact-level `WARN`，而不是因为 JAR 内可能不可达的 class 阻断整个 Module；当前项目和 Reactor code 仍保持严格边界。
- 五段 prefix 的第五段只承载当前 `stage/substage` 无法唯一表达的阶段实例或日志分类 identity；结果、观测值和其他实际日志信息使用 message 中的 `key=value`。
- 并行JAR pair failure以单个原子日志操作输出retained WARN与可选transient stack，避免不同pair的message和stack交叉。

## Actors / Entrypoints

- `dependency-analyzer impact`和`tree`通过root verbosity选择Console可见性；Preflight、Maven subprocess、JAR diff、Module analysis与Report共享同一`DiagnosticLog`。

## Behavior Contract

- INFO保留稳定进度、warning和error；DEBUG增加分析决策、完整Maven output与异常stack；TRACE再增加细粒度evidence和Runtime Metrics。
- Retained event可进入HTML Diagnostics；transient output只进入Console。

## Core Flow

1. CLI根据`-v`次数创建command-scoped`DiagnosticLog`。
2. 各stage使用immutable`DiagnosticContext`输出retained或transient line。
3. Formatter为每个物理行生成同一五段prefix；Report只消费verbosity已允许的retained snapshot。

## Global Verbosity

- 未传 `-v`：`INFO`，输出稳定的 stage、progress、warning 和 error；Maven subprocess 只透传 warning/error。
- `-v` 或一个 `--verbose`：`DEBUG`，增加 analysis option/decision、完整 Maven subprocess output；command failure和隔离的JAR pair failure同时输出完整stack trace与cause chain。
- `-vv` 或两个 `--verbose`：`TRACE`，增加 normalized path、ref、scope 等细粒度 evidence，并在 command dispatch 后立即输出 Runtime Metrics snapshot，之后每10 s输出一次；heap observation独立按100 ms执行。
- `-v` 是 inherited global option，可位于 subcommand 前或后。`DEBUG`/`TRACE` event 只有相应级别启用时才进入 console 与 `impact` HTML Diagnostics；默认 Report 不携带被过滤的详细 event。
- Analyzer 运行日志只写 stderr；stdout 不承载 Analyzer 日志。Picocli help/usage、参数解析错误和绕过 Analyzer logging 的第三方库 stderr 不受五段 prefix contract 约束。
- `impact`/`tree` 的 Maven subprocess output 逐行分类后写入 Console，不生成 build/dependency `.log` 文件。默认只显示 warning/error，`-v/-vv` 显示完整输出；Maven `[INFO]` 保持外层 `INFO` level。该 transient output 不进入 HTML Diagnostics；failure 只在内存保留 bounded tail。

## Impact Options

- `--baseline <ref>` required；`--target <ref>` 默认 current checkout。
- `--path <path>`：reactor root 或 leaf Module。
- `--analysis-target spring-backend`：默认且唯一 target。
- `--analysis-parallelism <N>`：默认 `2`，必须 `>=1`；统一控制 Module analysis、JAR diff、反编译 pool，不按 CPU 数静默截断，超过 CPU 输出 warning。
- `--call-graph-algorithm <rta|zero-cfa|optimized-0-1-cfa|k-obj>`：默认`rta`，command-wide应用到全部Module；大小写不敏感，不接受alias或自动fallback；旧`1-object-1-call-site`标识直接拒绝。
- `--k-obj-depth <正整数>`：只可与`k-obj`同时使用，默认`1`，不设置人为上限；零值、负值及与其他算法组合均在Preflight前作为参数错误返回。
- `--jdk-model <jdk8|none>`：默认`jdk8`，command-wide应用到全部Module；大小写不敏感，只接受精确标识符。`none`跳过JDK model catalog与selector，保留真实JDK bytecode分析。非法值由Picocli在Preflight前以exit code`1`拒绝。
- `--wala-reflection-options <enum-name>`：默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`，接受WALA `ReflectionOptions` enum name；`--reflection-options`为alias。
- `--entrypoint-include '<class-path-pattern>'` 与 `--entrypoint-exclude ...`：可重复；直接匹配 slash-separated JVM internal class path，include 取并集，exclude 优先。普通 segment支持 `*`、`?`；`**` 只能作为最后一个完整 segment。Colon/dot旧语法、leading/trailing slash、空 segment与嵌入式 `**` 在 CLI validation阶段 exit `1`。
- `--call-graph-timeout-seconds <N>`：默认 `0`；按 Module、从实际 WALA build 开始计时。
- `--call-graph-diagnostics-output <json>`：可选benchmark-only只读输出；未设置时不执行CGNode ranking、IMethod子榜、shortest path、IR capture或decompilation。路径不得与`--output`相同。
- `--format html`：唯一有效格式；`md` fail fast。
- `--java-home`：必须是完整 JDK 8；analyzer JVM 可为 Java 17。

## Exit Codes

- `SUCCESS`、`INCONCLUSIVE`：`0`。
- `PARTIAL_SUCCESS`、`FAILED`：`2`。
- Argument validation、Preflight、global preparation failure：`1`。

`INCONCLUSIVE`表示analysis在公开model内完成，但存在JAR diff、`invokedynamic`、MethodHandle、ServiceLoader、SSA或外部dependency excluded JDK reference uncertainty；它不是hard failure。仅由最后一类scope gap触发时，Module reason为`INCONCLUSIVE_SCOPE_VALIDATION`。

## Preflight Boundary

- 校验 path/Git/root POM/output/JDK 8/Maven version/Maven arguments、内嵌 Dependency Plugin `3.6.1` 与 Dependency Evidence Plugin `3.0.0` runtime、workspace/structured evidence capability。
- Preflight failure 不启动 pipeline，不触碰旧 Report。
- Reactor/leaf mode、Module coordinate collision、physical artifact ambiguity 属于 preparation failure。
- entrypoint selector 使用当前 Module `target/classes` 的 immutable index；interface、annotation、private nested class与private method/constructor不进入root范围。Filtered门禁与Call Graph roots复用同一index；privacy过滤不从scope删除class/method。Relevant Module无可执行root时为`SKIPPED_USER_ENTRYPOINT_SCOPE`；所有relevant Module均无匹配时command exit `1`，不替换旧Report。
- `PROJECT`/`REACTOR_DEPENDENCY` excluded JDK reference、scope I/O/scanner failure 与 Call Graph failure 属于 handled failed Module result，可产生 partial Report。外部 `DEPENDENCY` reference 只产生 `INCONCLUSIVE` warning。
- 默认`jdk8`的Synthetic loader、安装或catalog completeness failure属于Call Graph failed Module；不转换为coverage limitation，也不fallback到`none`。zero model hit不失败。

## Diagnostics

- `DiagnosticContext` 是 immutable prefix identity，包含 `stage`、`substage` 和 ordered attributes；不使用 thread name。当前生产日志只使用 `check`、`reactor`、`module`、`artifact`、`pool` identity。
- 每个物理行固定为 `[时间][日志级别][阶段][子阶段][额外信息] message`。时间使用带 offset、毫秒精度的 ISO 8601；level 始终显式为 `TRACE/DEBUG/INFO/WARN/ERROR`；缺失段使用 `[-]`。
- 第五段只在 identity 必要时使用 `key=value` 与 `;` 分隔，canonical 顺序为 `check, reactor, module, artifact, pool`。`command`、`side`、path、scope、progress、status、decision、elapsed、计数、result 与 metrics value 禁止进入第五段；这些实际日志信息追加到 message。
- `\\`、`;`、`=`、`[`、`]` 在 prefix 中统一转义。多行 message 和 stack trace 拆成独立物理行，每行重新添加完整 prefix。
- `stageStarts` 以完整 identity stable key 计时，同一 stage 的并发任务不会覆盖 elapsed；完成或失败 event 独立保存 elapsed，并以 `elapsedMs=...` 输出到 message。
- `INFO` 输出 front branch、Module task start/end；`DEBUG` 输出每个 logical coordinate JAR pair start/end；`TRACE` 输出筛选后的 command/path evidence，不输出 credential、settings 内容或完整 user arguments。
- JAR pair failure在`INFO`以WARN输出异常类型和完整message；`DEBUG`/`TRACE`紧接输出同context的完整stack与cause chain。WARN为retained event，stack为Console-only transient lines。
- 外部 dependency scope warning 使用 `[scope-validation][module][module=…][artifact=…]` context；每个 artifact 一条，warning text 同时进入 Module `Coverage limitations`。
- Analyzer Diagnostic event 默认 retained，可进入 `impact` HTML Diagnostics。Preflight evidence/fallback、Maven output、exception stack trace 与 Runtime Metrics 是 transient，只进入 Console。
- Console 与 HTML Report 对 retained event 共用 `DiagnosticLogFormatter`，包含同一 event timestamp 和 prefix；Module Diagnostics 只按 `DiagnosticEvent.module` 精确归属。
- Call Graph completion message包含`jdkModel=jdk8|none`，并仅在`k-obj`时包含实际`kObjDepth`。HTML遵循同一条件展示；Schema v6 diagnostics JSON在`k-obj`时输出正整数`kObjDepth`，其他算法输出`null`。用户输出不展示model available/hit count或target列表。

示例：

```text
[2026-08-05T14:30:01.123+08:00][INFO][analysis][reactor][reactor=root] Maven collection completed; progress=1/2; status=SUCCESS; modules=8
```

## Runtime Metrics

- 生命周期从 Picocli 成功 dispatch 到 `impact`/`tree` 的 `call()` 开始，到 command 返回结束。Help、usage 和参数解析失败不启动采样。
- `INFO`/`DEBUG` 使用 no-op session；不创建 scheduler、不读取 heap、不输出 metrics。
- `TRACE` 使用 command-scoped daemon scheduler：启动时立即观察并输出snapshot，之后每100 ms fixed-delay观察heap，但只按10 s cadence输出heap/thread-pool snapshot。所有normal return、early return和exception path都通过`close()`停止。
- Heap 行使用 `stage=runtime-metrics, substage=heap` 和空第五段；`sample/elapsedMs/heapUsedMiB/heapCommittedMiB/heapMaxMiB` 位于 message，MiB 保留 1 位小数。
- 当前注册的每个 Analyzer-owned pool 单独使用 `stage=runtime-metrics, substage=thread-pool`，第五段只保留 `pool` identity；sample、elapsed、pool size、task count 和 lifecycle value 位于 message。Registry 只包含 `front-preparation`、`jar-diff`、`module-analysis`、`code-comparison`；scheduler、process-output pump、JVM common pool、WALA internal thread 和 Maven external process 不注册。
- 单次采样异常使用 `stage=runtime-metrics, substage=sampler` 和空第五段；sample、elapsed 与 error 位于 TRACE transient message，不会改变 command status、Report 或 exit code。
- `close()`在设置closed flag前强制一次final heap observation，然后使用`stage=runtime-metrics, substage=summary`输出`sample count`、`peakHeapUsedMiB`、`peakHeapCommittedMiB`与`heapMaxMiB`。该summary是benchmark heap主指标来源；process-tree RSS继续由外部runner采集。

## Acceptance Criteria

### Functional

- Given单个JAR pair抛出`BytecodeDiffException`；When使用INFO；Then同pair WARN包含异常类型与完整message，其他pair继续执行。
- Given同一failure使用DEBUG或TRACE；When输出诊断；Then完整stack与cause chain逐行携带同一pair prefix，且不进入retained events。

### Non-Functional

- [ ] 并行failure的WARN与对应stack原子输出，不与其他pair的异常块交叉。
- [ ] 日志不输出credential、settings内容或未过滤的完整user arguments。

## Edge Cases

- Exception message为`null`时使用显式placeholder；不影响pair isolation或Module outcome。
- 多行message、Windows换行和nested cause均规范化为独立prefixed physical line。

## Implementation Boundaries

- `DiagnosticLog`负责visibility、retention与physical-line格式；业务stage负责提供完整、可行动的message和稳定context。
- Stack trace只用于Console诊断，不写入HTML或benchmark diagnostics JSON。
