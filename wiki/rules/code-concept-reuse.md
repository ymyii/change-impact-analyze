---
title: "Code and Concept Reuse"
type: rule
---

# Rule: Code and Concept Reuse

## Summary

设计与实现必须同时遵循Keep It Simple（KISS）、You Aren't Gonna Need It（YAGNI）和Don't Repeat Yourself（DRY）。同一领域含义复用统一概念，同一职责与不变量复用canonical implementation；只实现当前已确认且可到达的状态，不为明确领域不变量已经排除的理论情况增加防御分支、回退模型或扩展抽象。

## Rules

### Keep the Domain Model Simple

- 领域模型直接表达已经确认的业务不变量和值域，优先使用能够完整表达当前语义的最小类型、状态和算法。
- 当领域不变量保证一个值是标量、一个身份只有一条匹配记录或一个状态不可到达时，直接按该不变量建模；不得为理论上的多值、重复或异常状态改用collection、fallback或任意选择逻辑。
- 一个业务判断只保留一条清晰路径。不得通过防御性分支、兼容层、隐式降级或多套状态模型掩盖不成立的输入假设。
- 命名必须准确表达实际比较维度和状态范围；不得使用比领域语义更宽的名称，使局部未变化被误解为整体未变化。

### Avoid Speculative Work

- 只实现当前需求、已确认领域语义和现有调用方所需能力；不得为假设中的未来拓扑、额外状态、第二种实现或潜在扩展点提前建立抽象。
- 不为领域不变量排除的理论情况编写防御性编码、恢复策略、兼容分支或专用测试。
- 若未来需求改变既有不变量，应先更新canonical领域定义、调用方合同和Wiki，再以新需求实现对应能力；不得预先保留未启用的代码路径。
- 数据协议、指标、日志和Report只携带当前功能需要消费或审计的事实，不因上游对象存在更多字段而完整复制无关evidence。

### Reuse Without Duplication

- Don't Repeat Yourself（DRY）约束语义与职责重复，不要求对偶发的语法相似进行抽象。
- 相同概念、规范化规则、状态转换、校验和序列化必须只有一个canonical owner；调用方复用该实现，不各自维护近似副本。
- 复用不得破坏Keep It Simple（KISS）和You Aren't Gonna Need It（YAGNI）。如果抽取会引入无当前调用方需要的通用参数、扩展点或生命周期，应保留职责清晰的局部实现。

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

### Validate Real Boundaries

- “不为不可能状态防御”只适用于由明确领域不变量和canonical owner保证的内部状态，不得用来跳过真实外部边界校验。
- CLI输入、文件系统、Git/Maven等外部进程、网络或repository数据、跨版本Schema、反序列化内容和安全边界仍必须按其失败合同校验。
- 外部事实进入领域模型时应在边界完成规范化和校验；领域内部消费已经成立的不变量，不重复增加相同防御。
- 发现实际输入违反既有领域不变量时，应重新审查不变量或边界所有权，不在下游散布临时fallback。

### Consolidate Completely

- 合并重复概念或实现时，同步迁移production与test调用方、配置、Schema、Report、日志和Wiki，使所有消费者指向同一事实来源。
- 迁移完成后删除被替代的alias、wrapper、重复常量、重复模型和专用分支；不得保留两套可写状态或需要人工同步的映射表。

## Applies To

- 新增或修改production code、test support、公共或内部API、domain model、状态、原因码、配置、Schema、Report、日志、指标与Wiki术语。
- 提取共享helper、utility、base class、service、adapter、contract或跨package能力的重构。
- 发现重复实现、同义命名、平行状态或多个事实来源时的修复。
- 设计新领域状态、异常分支、fallback、兼容层、扩展点、通用容器或未来能力时的必要性判断。

## Verification

Design review和Code review必须确认：

- 每个领域状态和分支都对应当前可到达场景；不存在只服务于理论情况的防御逻辑。
- 类型和值域遵循已确认不变量，没有把标量、唯一映射或确定性流程扩张为不需要的collection、多值模型或fallback。
- 新抽象至少有当前稳定语义或实际调用方，不为未来假设预留通用参数、实现注册点或兼容路径。
- 已识别语义最接近的现有概念、contract和canonical implementation，并说明复用方式。
- 新概念确有不同的所有权、生命周期、不变量、状态或失败语义，且名称与边界能够表达该差异。
- 共享行为只有一个稳定所有者和事实来源，没有复制业务规则、双写状态或需要人工同步的平行模型。
- CLI、domain、Report、日志、指标、测试和Wiki对同一概念使用一致术语与字段语义。
- 合并后的旧alias、wrapper、重复模型、无效分支和过期文档已经删除。
- 复用没有破坏package职责与依赖方向；相关自动化边界检查按适用runbook执行，但不能替代语义复用评审。
- 用户输入、外部系统、持久化数据、Schema和安全边界的真实失败校验没有被错误删除。

## Non-Goals

- 不因偶发语法重复或代码形状相似而强制提取共享抽象。
- 不要求不同所有权、生命周期、不变量或失败语义的能力共用同一domain model或实现。
- 不把未经定义或未经owner保证的假设称为领域不变量；“理论上不会发生”必须有明确语义合同支撑。
- 不以Keep It Simple（KISS）或You Aren't Gonna Need It（YAGNI）为由删除外部边界、数据完整性或安全校验。
- 不在本规则中枚举所有遵循规则的代码位置；具体架构和Feature边界由对应Wiki页面维护。
