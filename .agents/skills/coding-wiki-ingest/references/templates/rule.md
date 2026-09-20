> 适用于 `wiki/rules/<rule>.md`，并使用 `type: rule`。


---
name: "<Canonical English Rule Name>"
type: rule
---

## Overview

<最多三句说明该约束保护的结果，以及适用的 C4 element。>

## Scope

- <说明约束适用的修改、入口或边界。>

## Rules

- **必须** <可执行且可验证的正向约束。>
- **禁止** <后果较高且无法仅用正向规则表达的行为，并给出替代动作。>

## Verification

- <说明验证方式、命令或可观察结果。>

## Non-Goals

- <说明容易混淆但不由本 Rule 约束的范围。>

> **填写说明**
>
> - Rule 只保存适用于后续同类修改的稳定约束。
> - 在约束发生处使用相对 Markdown 链接引用相关 Actor、Software System、Container、Component 或 Code element。
> - `Rules` 默认使用正向描述。
> - 安全、授权、数据完整性或严格格式边界可以使用必要的 `**禁止**`。
> - `Verification` 提供可执行检查或明确观察点。
> - 一次性修复步骤和排障流程进入 Runbook。
> - 不维护通用“相关页面”或实现文件清单。
>
> 删除所有占位符和不适用的示例项。
