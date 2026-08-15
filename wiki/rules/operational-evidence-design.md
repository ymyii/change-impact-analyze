---
title: "Operational Evidence Design"
type: rule
relations:
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "日志级别、关联上下文与Runtime Metrics的现有行为契约"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "功能Stage、并发执行单元与性能瓶颈的主要分析边界"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticLog.java"
    desc: "日志级别、retained/transient event与阶段计时基础设施"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/LogVerbosity.java"
    desc: "INFO、DEBUG与TRACE的可见性判断入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/diagnostic/DiagnosticContext.java"
    desc: "stage、substage、可选phase与stable identity上下文"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/metrics/RuntimeMetricsSession.java"
    desc: "资源采样、周期snapshot与final peak summary基础设施"
---

# Rule: Operational Evidence Design

## Summary

所有新建或实质修改的功能设计必须同时包含指标监控、进度跟踪和审计日志设计，并区分不同信息级别的输出内容。指标、进度事件和日志统一构成功能的运行证据；监控、审计和性能诊断是证据用途。三类能力可以复用相同事件、字段与输出基础设施，但任何一类都不得省略或标记为不适用。

## Rules

### Mandatory Design Coverage

- 指标监控必须定义能够解释主要执行成本的工作量、完成量、阶段耗时、并发或队列状态、资源使用量及异常计数；指标必须绑定明确的Stage与稳定业务identity，不能只提供缺少上下文的全局数值。
- 进度跟踪必须覆盖开始、处理中、完成和失败状态，并提供稳定业务identity、Stage、完成量、总量、耗时与结果。无法预知总量时必须输出已完成量和当前Phase，不能伪造百分比。
- 审计日志必须记录关键状态转换、影响执行路径或成本的决策、执行结果和异常，使同一次执行的Stage顺序、状态变化及耗时能够被还原。
- 证据载体与用途不需要一一对应：日志可以承载审计和进度证据，指标可以用于监控和性能诊断，同一事件也可以支持多个用途。不得为了形式完整而建立内容重复且无法关联的独立输出系统。
- 三类能力必须在功能设计中说明数据来源、输出时机、关联字段、用途和验收方式；实现时复用统一的诊断上下文与计时语义，不能形成无法关联的日志或指标孤岛。

### Information Levels and Output Cost

- `INFO`、`WARN`、`ERROR` 是默认可见级别，只能输出已经存在或能够以有界低成本生成的阶段、进度、结果、能力限制及异常摘要。
- `DEBUG` 只增加低频、有界成本的运行决策、中间计数、阶段耗时和异常详情；不能借助 `DEBUG` 无条件执行高频或可能显著影响性能的证据生成。
- `TRACE` 承载高频指标、资源采样、细粒度证据，以及可能显著影响性能的查询、遍历、聚合、序列化或格式化结果。这些工作只能在 `TRACE` 已启用后执行，并应避免改变被观测功能的性能特征。
- `WARN`和`ERROR`保留足以识别业务identity、Stage、状态与原因的低成本摘要；高成本诊断详情必须拆分到已启用的`DEBUG`或`TRACE`。
- verbosity 门禁必须位于数据采集和 message 构造之前。使用现有 `DiagnosticLog` 时，调用方必须先通过 `getVerbosity().includes(...)` 判断相应的 `LogVerbosity.DEBUG` 或 `LogVerbosity.TRACE`，不能先完成昂贵计算再依赖日志方法丢弃输出。
- 更高 verbosity 可以包含低级别事件，但每条新增信息必须按上述用途和生成成本选择级别；不得仅为提高可见性而在多个级别重复输出同一事件。

### Correlation and Safety

- 不同级别的事件必须共享可关联的业务identity、`stage`、`substage`、时间与状态语义；内部算法事件可额外携带可选`phase`。耗时使用monotonic timing，时间戳用于事件排序与跨输出关联。
- 并发执行单元必须使用稳定业务identity，不能依赖thread name区分；进度、指标、审计事件和异常必须能够精确归属到同一执行单元。
- message承载工作量、耗时、状态、结果和指标值；日志prefix只承载可选Phase与稳定身份字段，遵守既有`DiagnosticContext`与`DiagnosticLog`格式契约。Phase不得进入Stage计时stable key。
- 任何信息级别都不得输出 credential、访问令牌、settings 内容或未过滤的完整用户参数；性能分析需要的路径、参数或异常内容必须先进行安全过滤。

## Applies To

- 所有新功能设计，以及改变既有功能执行阶段、并发模型、数据规模、外部调用、缓存、资源使用或失败处理的实质修改。
- CLI command、分析pipeline、并发或后台执行、外部process、缓存处理和Report生成等存在阶段性执行成本的路径。
- 对指标、进度或日志的新增与调整，包括仅改变 verbosity、采样频率、关联字段、证据用途或审计事件的修改。

## Verification

- 功能设计评审必须逐项确认指标监控、进度跟踪和审计日志均有明确设计；缺少任一项时不得通过，复用同一证据载体时必须能够说明每项能力如何满足。
- 检查每项输出都具有业务identity和Stage关联信息，能够从开始、处理中、完成或失败事件还原一次执行，并计算主要Stage耗时。
- 检查 `INFO`、`WARN`、`ERROR` 只生成低成本摘要，`DEBUG` 只增加低频有界详情，高频或可能显著影响性能的工作只在 `TRACE` 已启用后执行。
- 检查 verbosity 判断发生在高成本采集、计算和 message 构造之前，禁用对应级别时不产生这些额外开销。
- 检查异常、降级和性能指标能够共同指向瓶颈阶段，且所有输出均通过敏感信息审查。

## Reference Files

- `DiagnosticLog` 提供统一的 verbosity、retention、输出和 monotonic timing façade。
- `LogVerbosity` 提供 `INFO`、`DEBUG` 与 `TRACE` 的前置可见性判断。
- `DiagnosticContext`提供Stage、可选Phase与稳定identity上下文。
- `RuntimeMetricsSession` 提供 `TRACE` 下的资源观测、周期snapshot和最终峰值摘要。
