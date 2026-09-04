---
name: "Code and Concept Reuse"
type: rule
---

## Overview

本规则要求 [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md) 对同一领域含义使用唯一概念，对同一职责与不变量使用 canonical implementation，并避免未被当前需求支持的抽象。

## Scope

- Production/test API、domain model、状态、原因码、Schema、Report、Diagnostic、metrics 与 Wiki terminology。
- 新增共享 helper、contract、adapter、fallback 或跨 package abstraction 的修改。

## Rules

- **必须**先识别语义最接近的现有概念与 owner；含义、值域和失败语义一致时直接复用。
- **必须**让 CLI、domain、Report、Diagnostic、tests 与 Wiki 对同一概念使用一致名称和值域。
- **必须**让规范化、校验、状态转换和序列化只有一个 canonical owner。
- **必须**只为当前可到达场景建立类型、分支和 extension point；新概念需有不同的所有权、生命周期、不变量或失败语义。
- **必须**在合并重复概念时同步迁移消费者，并删除旧 alias、wrapper、双写状态与过期文档。
- **禁止**以复用为由破坏 [Package Boundaries](package-boundaries.md)，或删除 CLI、filesystem、Git、Maven、Schema 等真实外部边界校验。
- **禁止**仅因语法形状相似抽取无稳定语义的通用 abstraction。

## Verification

- Review 新状态是否对应当前可达场景，新 abstraction 是否已有稳定调用方。
- Review 同一事实是否存在重复 enum、reason、normalization、Schema field 或 writable source。
- 执行受影响 package tests 与架构测试，确认复用未引入反向依赖。

## Non-Goals

- 不要求所有相似代码共享实现。
- 不把未经 owner 与 contract 保证的假设视为领域不变量。
