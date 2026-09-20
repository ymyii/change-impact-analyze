> 适用于 `wiki/adr/NNNN-<decision>.md`，并使用 `type: adr`。


---
name: "<Canonical English Decision Name>"
type: adr
id: "ADR-NNNN"
date: "YYYY-MM-DD"
status: accepted
---

## Context

<说明促成决策的稳定问题、约束和相关 C4 element。>

## Decision Criteria

- <实际用于判断方案的条件。>

## Considered Alternatives

### <真实考虑过的方案 A>

<中立说明方案及其相对条件。>

### <真实考虑过的方案 B>

<中立说明方案及其相对条件。>

## Decision

<说明已经接受的选择，以及它如何影响相关 C4 element。>

## Consequences

- <已知正面、负面或中性后果。>

> 旧 ADR 被替代时，只将其 front matter 更新为：

```yaml
status: superseded
superseded_by: "[[adr/NNNN-replacement-decision]]"
```

> **填写说明**
>
> - 只记录影响结构、质量属性、重要依赖、接口或构造方式的重要决策。
> - 只有可靠来源能够证明决策已接受时才创建 ADR。
> - `Considered Alternatives` 只记录真实考虑过的方案。
> - `Context`、`Decision` 和 `Consequences` 在语义发生处使用相对 Markdown 链接引用相关 C4 element。
> - 已接受 ADR 的正文保持不变。
> - 新决策替代旧决策时创建新 ADR。
> - 旧 ADR 只更新 `status` 和 `superseded_by`。
> - `status` 只使用 `accepted` 或 `superseded`。
> - 不维护通用“相关页面”清单。
>
> 删除所有占位符和不适用的示例项。不要补写假想方案。
