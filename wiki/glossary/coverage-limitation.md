---
name: "Coverage Limitation"
type: glossary
---

## Definition

Coverage Limitation 是分析已产生可用结果，但静态模型、classpath、协议处理或范围选择无法证明覆盖完整时保存的类型化限制。

## Usage

该术语帮助 Agent 区分“没有发现路径”和“当前证据不足以覆盖全部路径”；存在 Coverage Limitation 的 Module 或命令可以保留结果，但必须呈现为 `INCONCLUSIVE` 或受限状态。

## Distinctions

- Failure：表示必要分析结果无法可信地产生；Coverage Limitation 仍允许保留已证明的结果。
