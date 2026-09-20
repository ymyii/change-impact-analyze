> 适用于 `wiki/use-case-realizations/<system>-<actor-goal>.md`，并使用 `type: use-case-realization`。


---
name: "<Canonical English Actor Goal>"
type: use-case-realization
---

## Realized Goal and Scope

<说明实现的 Actor 目标，以及本 Realization 的起止边界。>

## Primary Actor and Supporting Actors

- Primary Actor: [<Actor>](../c4/actors/<actor>.md)
- Supporting Actors: <引用参与的 Actor 或 Software System；没有时写“无”。>

## Participating C4 Elements

- [<C4 element>](../c4/<type>/<element>.md)：<说明该 element 在本 Realization 中承担的稳定职责。>

## Preconditions

- <触发前必须成立，并得到来源支持的条件。>

## Trigger

<说明启动本 Realization 的外部事件。>

## Main Success Scenario

1. <Actor 或 C4 element 执行最常见成功路径的第一步。>
2. <下一步。>
3. <Actor 可观察的成功结果。>

## Alternative Flows

- 主流程步骤 `2`，分支 `a`：<仍能实现目标的变化条件。>
  1. <进入该分支后的第一个处理动作。>
  2. <说明重新汇入的主流程步骤或独立成功终点。>

## Exception Flows

- 主流程步骤 `2`，分支 `a`：<错误、拒绝或中断条件。>
  1. <说明系统检测和处理异常的动作。>
  2. <说明 Actor 可观察结果。>
  3. <说明重试、重新汇入或终止位置。>

## Minimal Guarantee

<说明成功、失败或中断后的任何终止路径都保持的状态。>

## Success Guarantee

<说明成功结束后对外保证的状态和结果。>

> **填写说明**
>
> - `Realized Goal and Scope` 说明上游 Actor 目标和本页面覆盖的实现边界，不改写需求。
> - `Participating C4 Elements` 只列出除 Actor 外直接参与本 Realization 的 C4 element，并说明各自职责。
> - `Main Success Scenario` 只记录最常见的完整成功路径。
> - `Alternative Flows` 只记录仍能实现目标的受支持变化。
> - `Exception Flows` 只记录错误、拒绝、中断和恢复路径。
> - Alternative Flow 和 Exception Flow 使用主流程步骤编号指出分支发生位置。
> - 同一主流程步骤的多个分支按 `a`、`b`、`c` 区分。
> - 每个 Alternative Flow 明确重新汇入的步骤或独立成功终点。
> - 每个 Exception Flow 明确异常条件、系统处理、Actor 可观察结果，以及重试、重新汇入或终止位置。
> - 每个处理步骤只表达一个 Actor 或 C4 element 的动作。
> - `Minimal Guarantee` 适用于任何终止路径。`Success Guarantee` 只适用于成功路径。
> - 场景在语义发生处使用相对 Markdown 链接引用 Actor、Software System、Container 或 Component。
> - User Story 和 Acceptance Criteria 可以作为行为证据，但不复制为页面章节或 Given-When-Then 结构。
> - 具体运行命令和排障步骤进入 Runbook。
>
> 权威来源没有说明 Alternative Flow 或 Exception Flow 时，将对应章节内容完整替换为：
>
> ```markdown
> - Not specified.
> ```
>
> 删除所有占位符和不适用的示例项。不要从当前实现推导需求或补写缺少的流程。
