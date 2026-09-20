> 适用于 `wiki/c4/actors/<actor>.md`，并使用 `type: actor`。


---
name: "<Canonical English Actor Name>"
type: actor
relations:
  - target: "[[c4/software-systems/<system>]]"
    description: "<该角色对系统执行的直接交互>"
    mechanism: "<可选交互协议或机制>"
---

## Overview

<说明稳定角色、身份和基本交互语境。>

## Goals

- <该角色希望通过系统实现的稳定结果。>

## Boundaries

<说明角色适用范围。明确至少一个容易混淆但不属于该角色的范围。>

> **填写说明**
>
> - `Overview` 说明角色类别，不描述具体个人。
> - `Goals` 说明稳定结果，不写操作步骤。
> - `Boundaries` 同时说明适用范围和明确排除项。
> - 具体实现协作与流程进入 Use Case Realization。
> - 权限矩阵和系统实现责任不进入 Actor 页面。
> - 没有出站关系时，将 front matter 写为 `relations: []`。
>
> 只有证据明确时才保留 `mechanism`。删除所有占位符和不适用的示例项。
