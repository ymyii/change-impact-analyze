---
title: "Code and Concept Reuse"
type: rule
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "Repository-wide编码规范与规则检索入口"
  - path: "wiki/rules/package-boundaries.md"
    desc: "共享抽象的职责归属与依赖方向约束"
code_refs:
  - path: "AGENTS.md"
    desc: "编码工作遵循Wiki规范的全局入口"
---

# Rule: Code and Concept Reuse

## Summary

同一领域含义必须复用统一概念，同一职责与不变量必须复用canonical implementation。复用用于减少用户需要理解的术语、状态和行为分支，同时控制重复代码、平行模型与同步修改成本；新增抽象必须具有现有概念无法表达的明确语义边界和稳定所有者。

## Rules

### Reuse Existing Concepts First

- 新增类型、API、helper、常量、状态、原因码或Schema字段前，必须先识别现有的术语、contract、domain model和实现入口。现有概念的含义与约束一致时，直接复用，不创建同义名称或平行模型。
- 同一领域含义在CLI、domain、Report、日志、指标、测试和文档中使用一致的名称、类型、值域与字段语义。展示格式可以因载体不同而变化，但不能改变概念含义或另建一套术语。
- 面向用户的名称、状态和原因必须映射到既有domain概念；不得仅因新的调用方或输出位置而复制enum、reason、metadata或Schema结构。
- 概念的校验、规范化、序列化和状态转换规则由其canonical owner统一定义。调用方消费统一结果，不各自重建相同规则。

### Reuse Behavior by Ownership

- 具有相同职责、不变量和失败语义的行为必须归并到单一canonical implementation；优先组合、参数化或扩展既有能力，不复制实现后分别演进。
- 共享能力放在拥有该概念或contract的稳定模块与package中。不得为了缩短局部代码，把行为堆入无明确所有者的通用utility、全局helper或跨层base class。
- 复用必须遵守既有package职责和单向依赖。不得通过反向依赖、跨层访问内部状态或把业务语义下沉到通用层来实现表面复用。
- 多个调用方只共享执行机制时，提取算法无关的contract或机制；调用方特有的业务决策、状态与evidence仍由其所属层负责。

### Boundaries for New Concepts

- 所有权、生命周期、不变量、状态转换、失败语义或安全边界不同的能力可以保持独立；仅有代码形状相似不能作为强制复用依据。
- 新概念必须能够说明与最接近现有概念的语义差异、稳定所有者以及二者的转换或协作边界。无法说明差异时，复用现有概念。
- 扩展现有抽象会引入互斥flag、调用顺序约束、无关依赖或模糊命名时，应建立职责明确的相邻抽象，不把不同概念压入一个通用入口。
- 不得为未来假设场景提前建立抽象。共享抽象必须表达当前稳定概念或已经存在的相同行为约束。

### Consolidate Completely

- 合并重复概念或实现时，同步迁移production与test调用方、配置、Schema、Report、日志和Wiki，使所有消费者指向同一事实来源。
- 迁移完成后删除被替代的alias、wrapper、重复常量、重复模型和专用分支；不得保留两套可写状态或需要人工同步的映射表。

## Applies To

- 新增或修改production code、test support、公共或内部API、domain model、状态、原因码、配置、Schema、Report、日志、指标与Wiki术语。
- 提取共享helper、utility、base class、service、adapter、contract或跨package能力的重构。
- 发现重复实现、同义命名、平行状态或多个事实来源时的修复。

## Verification

Design review和Code review必须确认：

- 已识别语义最接近的现有概念、contract和canonical implementation，并说明复用方式。
- 新概念确有不同的所有权、生命周期、不变量、状态或失败语义，且名称与边界能够表达该差异。
- 共享行为只有一个稳定所有者和事实来源，没有复制业务规则、双写状态或需要人工同步的平行模型。
- CLI、domain、Report、日志、指标、测试和Wiki对同一概念使用一致术语与字段语义。
- 合并后的旧alias、wrapper、重复模型、无效分支和过期文档已经删除。
- 复用没有破坏package职责与依赖方向；相关自动化边界检查按适用runbook执行，但不能替代语义复用评审。

## Reference Files

- `AGENTS.md` - 编码工作检索并遵循Wiki规范的全局入口。

## Non-Goals

- 不因偶发语法重复或代码形状相似而强制提取共享抽象。
- 不要求不同所有权、生命周期、不变量或失败语义的能力共用同一domain model或实现。
- 不在本规则中枚举所有遵循规则的代码位置；具体架构和Feature边界由对应Wiki页面维护。
