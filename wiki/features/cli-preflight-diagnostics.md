---
title: "CLI Preflight and Diagnostics"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "impact/tree pipeline 与 exit code"
  - path: "wiki/features/report-generator.md"
    desc: "Preflight 与 diagnostics 输出"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "spring-backend options、validation、exit code"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/preflight/PreflightRunner.java"
    desc: "check DAG"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticContext.java"
    desc: "并发任务 stable context"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLog.java"
    desc: "retained/transient event、verbosity、stderr 与 monotonic timing façade"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLogFormatter.java"
    desc: "Console/HTML 共用的五段 prefix 与 attribute canonicalization"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/RuntimeMetricsSession.java"
    desc: "TRACE-only heap/thread-pool 异步采样生命周期"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/ManagedExecutorRegistry.java"
    desc: "Analyzer-owned fixed pool factory 与 registry"
---

# Feature: CLI Preflight and Diagnostics

## Summary

CLI 在昂贵分析前执行结构化 Preflight。`DiagnosticLog` 是 Analyzer 唯一日志 façade；`impact`/`tree` 的业务日志统一写入 stderr，并使用稳定五段 prefix。全局 verbosity 默认为 `INFO`，`-v` 选择 `DEBUG`，`-vv` 选择 `TRACE`；只有 `TRACE` 启动异步 Runtime Metrics。

## Design Decisions

- handled uncertainty 与 hard failure 分离：能够继续完成 Call Graph 的 coverage limitation 使用 `INCONCLUSIVE` 和 exit code `0`；无法建立可信 Module 结果的错误使用 `FAILED`/`PARTIAL_SUCCESS` 和 exit code `2`。
- 外部 dependency 的 excluded JDK reference 使用 artifact-level `WARN`，而不是因为 JAR 内可能不可达的 class 阻断整个 Module；当前项目和 Reactor code 仍保持严格边界。

## Global Verbosity

- 未传 `-v`：`INFO`，输出稳定的 stage、progress、warning 和 error；Maven subprocess 只透传 warning/error。
- `-v` 或一个 `--verbose`：`DEBUG`，增加 analysis option/decision、完整 Maven subprocess output；command failure 同时输出 stack trace。
- `-vv` 或两个 `--verbose`：`TRACE`，增加 normalized path、ref、scope 等细粒度 evidence，并在 command dispatch 后立即输出 Runtime Metrics，之后每 10 秒采样。
- `-v` 是 inherited global option，可位于 subcommand 前或后。`DEBUG`/`TRACE` event 只有相应级别启用时才进入 console 与 `impact` HTML Diagnostics；默认 Report 不携带被过滤的详细 event。
- Analyzer 运行日志只写 stderr；stdout 不承载 Analyzer 日志。Picocli help/usage、参数解析错误和绕过 Analyzer logging 的第三方库 stderr 不受五段 prefix contract 约束。
- `impact`/`tree` 的 Maven subprocess output 逐行分类后写入 Console，不生成 build/dependency `.log` 文件。默认只显示 warning/error，`-v/-vv` 显示完整输出；Maven `[INFO]` 保持外层 `INFO` level。该 transient output 不进入 HTML Diagnostics；failure 只在内存保留 bounded tail。

## Impact Options

- `--baseline <ref>` required；`--target <ref>` 默认 current checkout。
- `--path <path>`：reactor root 或 leaf Module。
- `--analysis-target spring-backend`：默认且唯一 target。
- `--analysis-parallelism <N>`：默认 `2`，必须 `>=1`；统一控制 Module analysis、JAR diff、反编译 pool，不按 CPU 数静默截断，超过 CPU 输出 warning。
- `--entrypoint-include '<class-path-pattern>'` 与 `--entrypoint-exclude ...`：可重复；直接匹配 slash-separated JVM internal class path，include 取并集，exclude 优先。普通 segment支持 `*`、`?`；`**` 只能作为最后一个完整 segment。Colon/dot旧语法、leading/trailing slash、空 segment与嵌入式 `**` 在 CLI validation阶段 exit `1`。
- `--call-graph-timeout-seconds <N>`：默认 `0`；按 Module、从实际 WALA build 开始计时。
- `--format html`：唯一有效格式；`md` fail fast。
- `--java-home`：必须是完整 JDK 8；analyzer JVM 可为 Java 17。

## Exit Codes

- `SUCCESS`、`INCONCLUSIVE`：`0`。
- `PARTIAL_SUCCESS`、`FAILED`：`2`。
- Argument validation、Preflight、global preparation failure：`1`。

`INCONCLUSIVE` 表示 analysis 在公开 model 内完成，但存在 JAR diff、ServiceLoader、SSA 或外部 dependency excluded JDK reference uncertainty；它不是 hard failure。仅由后者触发时，Module reason 为 `INCONCLUSIVE_SCOPE_VALIDATION`。

## Preflight Boundary

- 校验 path/Git/root POM/output/JDK 8/Maven version/Maven arguments、内嵌 Dependency Plugin `3.6.1` runtime、workspace/GraphML capability。
- Preflight failure 不启动 pipeline，不触碰旧 Report。
- Reactor/leaf mode、Module coordinate collision、physical artifact ambiguity 属于 preparation failure。
- entrypoint selector 使用当前 Module `target/classes` 的 immutable index；interface/annotation不进入选择范围。Filtered门禁与 Call Graph roots复用同一 index。Relevant Module 无匹配时为 `SKIPPED_USER_ENTRYPOINT_SCOPE`；所有 relevant Module 均无匹配时 command exit `1`，不替换旧 Report。
- `PROJECT`/`REACTOR_DEPENDENCY` excluded JDK reference、scope I/O/scanner failure 与 Call Graph failure 属于 handled failed Module result，可产生 partial Report。外部 `DEPENDENCY` reference 只产生 `INCONCLUSIVE` warning。

## Diagnostics

- `DiagnosticContext` 是 immutable identity，包含 `stage`、`substage` 和 ordered attributes。`side/module/artifact/path` 都是 attributes；不使用 thread name。
- 每个物理行固定为 `[时间][日志级别][阶段][子阶段][额外信息] message`。时间使用带 offset、毫秒精度的 ISO 8601；level 始终显式为 `TRACE/DEBUG/INFO/WARN/ERROR`；缺失段使用 `[-]`。
- 第五段使用 `key=value` 与 `;` 分隔。公共字段依次为 `command, side, reactor, module, artifact, path, check, scope, scopeId, progress, status, decision, elapsedMs`；Runtime Metrics 字段随后为 `sample, pool, core, max, size, active, queued, completed, tasks, shutdown, terminated, heapUsedMiB, heapCommittedMiB, heapMaxMiB`；扩展字段按 key 字典序追加。
- `\\`、`;`、`=`、`[`、`]` 在 prefix 中统一转义。多行 message 和 stack trace 拆成独立物理行，每行重新添加完整 prefix。
- `stageStarts` 以完整 context stable key 计时，同一 stage 的并发任务不会覆盖 elapsed。
- `INFO` 输出 front branch、Module task start/end；`DEBUG` 输出每个 logical coordinate JAR pair start/end；`TRACE` 输出筛选后的 command/path evidence，不输出 credential、settings 内容或完整 user arguments。
- 外部 dependency scope warning 使用 `[scope-validation][module][module=…][artifact=…]` context；每个 artifact 一条，warning text 同时进入 Module `Coverage limitations`。
- Analyzer Diagnostic event 默认 retained，可进入 `impact` HTML Diagnostics。Preflight evidence/fallback、Maven output、exception stack trace 与 Runtime Metrics 是 transient，只进入 Console。
- Console 与 HTML Report 对 retained event 共用 `DiagnosticLogFormatter`，包含同一 event timestamp 和 prefix；Module Diagnostics 只按 `DiagnosticEvent.module` 精确归属。

示例：

```text
[2026-08-05T14:30:01.123+08:00][INFO][analysis][reactor][reactor=root;progress=1/2;status=SUCCESS] Maven collection completed
```

## Runtime Metrics

- 生命周期从 Picocli 成功 dispatch 到 `impact`/`tree` 的 `call()` 开始，到 command 返回结束。Help、usage 和参数解析失败不启动采样。
- `INFO`/`DEBUG` 使用 no-op session；不创建 scheduler、不读取 heap、不输出 metrics。
- `TRACE` 使用 command-scoped daemon scheduler：启动时立即采样，之后每 10 秒 fixed-delay 采样；所有 normal return、early return 和 exception path 都通过 `close()` 停止。
- Heap 行使用 `stage=runtime-metrics, substage=heap`，通过 `MemoryMXBean.getHeapMemoryUsage()` 输出 `used/committed/max` MiB，保留 1 位小数。
- 当前注册的每个 Analyzer-owned pool 单独使用 `stage=runtime-metrics, substage=thread-pool` 输出。Registry 只包含 `front-preparation`、`jar-diff`、`module-analysis`、`code-comparison`；scheduler、process-output pump、JVM common pool、WALA internal thread 和 Maven external process 不注册。
- 单次采样异常只输出 TRACE transient sampler event；不会改变 command status、Report 或 exit code。
