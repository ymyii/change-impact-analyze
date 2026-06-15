---
title: "Coding Principles"
type: rule
relations: []
code_refs: []
---

# Rule: Coding Principles

## Summary

项目级编码基本原则，约束所有编码决策和实现方式。

## Rules

- **KISS**：优先选择简单、直接、可读的实现。不要为了显得通用而引入复杂抽象、额外状态或难以追踪的控制流。
- **YAGNI**：不要为未确认的未来需求提前设计扩展点、配置项、fallback 或框架层。只有当前需求和稳定边界需要时才添加能力。
- **系统性修复**：遇到 bug 或异常行为时，先理解根因、数据流、调用边界和失败条件，再修改拥有该职责的模块。
- **避免 hack 和 ad-hoc fix**：不要通过硬编码特殊分支、绕过验证、吞掉异常、隐藏错误、伪造状态或只修表象的方式解决问题。

## Applies To

- 所有编码任务和代码变更。
- 所有模块和文件类型。

## Verification

- Code review 时检查是否违反以上原则。
- 发现 hack 或过度设计时，要求按原则修正。

## Reference Files

- 无特定文件；原则适用于整个项目。

## Non-Goals

- 不列举所有遵循这些原则的代码位置。
