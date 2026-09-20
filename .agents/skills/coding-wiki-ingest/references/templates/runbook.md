> 适用于 `wiki/runbooks/<operation>.md`，并使用 `type: runbook`。


---
name: "<Canonical English Runbook Name>"
type: runbook
---

## Purpose and Scope

<说明操作目的、适用环境和涉及的 C4 element。>

## Prerequisites

- <权限、工具、输入、备份或环境条件。>

## Procedure

1. <单一操作。>

   ```sh
   <可复制执行的命令>
   ```

2. <下一项单一操作。>

## Success Criteria

- <命令输出、状态、指标或外部行为等成功证据。>

## Failure Entry Points

- <失败信号>：<停止条件、诊断入口或安全恢复动作。>

> **填写说明**
>
> - `Purpose and Scope` 在操作语境中使用相对 Markdown 链接引用相关 C4 element。
> - `Prerequisites` 在执行步骤前完整声明。
> - 每个步骤只执行一个动作。
> - 命令使用 repository-relative path 或明确工作目录。
> - `Success Criteria` 使用可观察结果，不使用“正常”等模糊词。
> - `Failure Entry Points` 说明停止条件和下一诊断入口。
> - 系统行为和结束保证进入 Use Case Realization。
> - User Story 和 Acceptance Criteria 由 PRD 等需求文档负责。
> - 长期工程约束进入 Rule。
> - 不维护通用“相关页面”清单。
>
> 删除所有占位符和不适用的示例项。
