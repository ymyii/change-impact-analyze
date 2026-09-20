# Coding Wiki Ingest 设计决策

## 背景

`coding-wiki-ingest` 维护面向 coding agent 的项目 Wiki。Wiki 需要建立稳定的架构心智模型，同时避免复制能够直接从代码读取的实现细节。

## 当前决策

### 独立 Skill

`coding-wiki-ingest` 独立负责 coding-agent 项目 Wiki 的创建和维护。

独立 Skill 可以集中维护 C4 taxonomy、Glossary、准入门槛、页面 Schema、模板和代码注释规则，避免普通文档任务误用这套模型。

### 代码与 Wiki 使用互补职责

代码是实现细节的权威来源。Wiki 解释架构边界、契约、场景、决策、约束和操作。

这种分工让 Agent 先建立工程心智模型，再按需进入代码。Wiki 不同步类、方法、字段、调用方和依赖文件的完整变化。

### C4 作为架构底座

Wiki 使用 Actor、Software System、Container、Component 和 Code element 表达架构。

Actor、Software System、Container 和 Component 使用独立页面。Code 层按 Component 聚合，每个组件最多对应一份页面，集中解释内部代码组织，避免单个代码元素分别成页造成过度拆分。

Level 1（L1）和 Level 2（L2）是每个 in-scope Software System 的必备底座。

Level 3（L3）和 Level 4（L4）通过价值门槛与准入测试控制深度，避免把目录树或源码索引包装成架构文档。

层级语义遵循 C4 对 [Software System](https://c4model.com/abstractions/software-system) 和 [Container](https://c4model.com/abstractions/container) 的定义。

下钻边界遵循 C4 对 [Component](https://c4model.com/abstractions/component) 和 [Code](https://c4model.com/abstractions/code) 的定义。

### 精简 C4 front matter

C4 element 顶层只使用 `name`、`type`、`children` 和 `relations`。`children` 只出现在拥有下一层 C4 element 的父页面。

每项 relation 只使用 `target`、`description` 和可选 `mechanism`。结构字段保持最小，可以降低维护成本，也便于 Obsidian Graph 读取有向关系。

`children` 只表达父页面到直接子页面的包含关系。`relations` 只表达 source 主动发起或发送的架构显著交互。两者不重复。使用父页面的 `children[].target` 登记下一层页面，避免让每个子页面产生指向父页的 Obsidian 关系箭头。

### C4 图示与关系来源

Wiki 使用唯一的 `c4/system-arch.md` 绘制 System Context diagram。每个 Software System 页面绘制所属系统的 Container diagram，每个 Container 页面绘制 Component diagram，每个 Component 页面承载精简 Code diagram。这样读者可以沿页面层级逐层下钻，图示也与页面职责保持一致。

`relations[].target` 保持为 relation 项下的字符串 Wikilink。它为 Obsidian Graph 提供稳定的页面关系入口；Mermaid 图显式展示 C4 关系，不依赖 Obsidian 从 YAML 自动生成图。将 target 改成对象会增加 Properties 解析差异，也不能替代图示正文。

### Canonical name 决定页面路径

所有页面使用 `name` 表达 canonical English name。除继承父 Component basename 的 Code 页面外，页面自身路径片段由 `name` 转换为 lowercase English `kebab-case`。

Container 和 Component 在父页面 basename 后追加自身名称片段。Code 页面的 `name` 使用 `<Component Name> Code`，basename 直接继承父 Component。Use Case Realization 和 Architecture Decision Record（ADR）按各自路径规则增加 Software System 或编号前缀。

统一命名源可以避免页面显示名称、文件名和链接漂移。

### Relation 使用 mechanism

relation 的 `mechanism` 只描述交互协议或机制。framework、runtime、数据库产品和版本属于 element 自身，进入正文 `Technology`。

这种划分区分“怎样交互”和“element 由什么实现”。例如 `SQL over PostgreSQL wire protocol/TCP` 是机制，`PostgreSQL 17` 是 Container 技术。

relation 的 `description` 使用中文动作句。技术名称、代码标识符和 `mechanism` 保留原文，使结构说明与 Wiki 正文语言一致。

### YAML Wikilink 与正文 Markdown 链接分工

`children[].target`、relation `target` 和 Architecture Decision Record（ADR）`superseded_by` 使用从 `wiki/` vault 根开始的 Obsidian Wikilink。

YAML `children` 管理页面包含关系，`relations` 管理页面之间的架构交互。Obsidian [Properties](https://obsidian.md/help/properties) 保存该结构。

### C4 词汇与模板表达

Software System 和 Container 页面优先使用通用术语、相邻 C4 层级概念和 Glossary 术语。低层代码标识符只在 Component 的 Code diagram 或 Code 页面中出现。这样可以保持上层架构说明独立于实现细节，让新开发人员先理解价值、责任和边界。

模板的章节结构直接决定目标页面结构。模板中的约束与填写提示使用可见的 Markdown 引用块 `>`，并以列表表达并列要求。填写完成后删除引用说明、占位内容和不适用章节，保留模板章节作为最终页面结构。连续说明仍可使用段落，避免机械拆分破坏语义。

[Graph view](https://obsidian.md/help/plugins/graph) 只承担人工审查，不要求通用反向关系、消费者清单或正文关系副本。

正文内部引用使用相对当前页面、带 `.md` 的标准 Markdown 链接。该格式可以被通用 Markdown 渲染器和 coding agent 稳定识别，不依赖 Obsidian 解析。

正文不使用 Obsidian Wikilink。YAML 结构字段与正文导航承担不同职责，避免为兼容渲染器破坏现有 Graph 结构。

### C4 正文按层级控制抽象

五类 C4 页面使用独立正文模板。越靠近 L1，内容越强调价值与边界；L4 集中说明一个 Component 内关键代码元素的分组、职责和结构关系。

Code 页面的固定章节为 `Overview`、`Code Structure` 和 `Boundaries`。Contract、Invariant、技术和状态说明仅在有助于解释结构设计时补充，不作为准入的必备条件。

所有 C4 页面都保留 `Boundaries`。该章节同时说明适用范围和明确排除项，用于阻止责任在相邻 element 间漂移。

可选 `Technology` 或 `State and Data` 只在有解释增量时出现。可靠来源包含具体版本时，`Technology` 保留版本；来源不足时不猜测。

`Technology` 每条 Markdown 列表项只描述一种技术。名称、版本和架构作用可以位于同一项。单项单技术便于 Agent 区分技术本身与算法、模式或业务能力。

Component 和 Code 只假定读者已经阅读直接父页面。`Overview` 需要说明 element 是什么、处理什么，以及向父级能力提供什么结果。

项目专有概念首次出现时先用自然中文解释。

生命周期说明同时交代触发时机、释放或保留的状态，以及事件后仍可使用的结果，避免局部实现术语阻断 C4 下钻阅读。

### Glossary 统一受控项目词汇

Glossary 使用 `wiki/glossary/<term>.md`，每个页面只定义一个 canonical term。页面只使用 `name` 和 `type`。

正文提供 Definition、Usage，以及有证据时使用的 Aliases 和 Distinctions。

Glossary 只接收拥有稳定项目含义、对新成员可能陌生或容易歧义，并且能够独立于单个代码符号成立的术语。

术语还需跨至少两个页面复用，或关系到关键边界、Contract、Invariant、生命周期或失败语义。

标准技术名称、代码标识符、C4 element 名称、通用形容词、局部实现概念、步骤、决策和约束由各自权威来源解释。Glossary 不能成为晦涩正文的替代品。

正文先改写为自然中文，再提取仍不可避免的术语。每个页面首次使用已准入术语时提供 Glossary Markdown 链接，并让原句在不打开链接时仍能表达基本含义。

Glossary Markdown 链接只用于语义导航，不进入 C4 `relations`。Glossary 总量超过约 30 项时重新检查重复、常识和局部术语，避免把它扩展为百科全书。

该设计沿用 [arc42 Glossary](https://docs.arc42.org/section-12/) 对重要业务与技术术语的定位，以及[保持词表精简](https://docs.arc42.org/tips/12-5/)的建议。

### Use Case Realization 表达实现协作

Use Case Realization 描述 C4 element 如何协作实现稳定的 Actor 目标。它位于需求与架构实现之间，并在流程发生处引用参与的 C4 element。

该定位遵循 [Unified Modeling Language（UML）1.5](https://www.omg.org/spec/UML/1.5/PDF/) 对 Use Case Realization 的说明：一个或一组 Collaboration 描述 Use Case 如何被实现。页面不建立独立 Use Case 模型，也不承担需求定义职责。

User Story 说明用户需要什么以及原因。Acceptance Criteria 定义需求是否被接受。两者由 Product Requirements Document（PRD）等需求文档负责，不作为 coding Wiki page type，也不复制到 Use Case Realization。

上游 Use Case、User Story 和 Acceptance Criteria 可以作为行为证据。Use Case Realization 只提取有来源支持的实现协作、流程和结束保证，不保留 Given-When-Then 或验收分类结构。

Main Success Scenario 描述最常见的完整成功路径。Alternative Flows 描述仍然实现目标的受支持变化，并明确重新汇入的步骤或独立成功终点。

Exception Flows 描述错误、拒绝、中断和恢复路径。每个分支说明异常条件、系统处理、Actor 可观察结果，以及重试、重新汇入或终止位置。

Minimal Guarantee 说明任何终止路径都保持的状态。Success Guarantee 说明成功结束后成立的结果。

Alternative Flows 和 Exception Flows 使用主流程步骤编号与分支字母定位。来源没有说明某类流程时保留对应章节，并使用 `- Not specified.` 明确证据缺口。

### Architecture Decision Record 保存已接受的重要决策

ADR 只记录已经接受，并影响结构、质量属性、重要依赖、接口或构造方式的决策。

ADR 正文在接受后保持不变。新决策替代旧决策时创建新 ADR，旧 ADR 只更新 `status` 和 `superseded_by`，从而保留可靠的决策链。

ADR 只记录真实考虑过的备选方案。缺少接受证据或真实备选方案时，不创建或补写推测内容。

正文结构采用 [arc42 Architecture Decisions](https://docs.arc42.org/section-9/) 扩展的 Nygard 模型，并补充明确的 Decision Criteria 与 Considered Alternatives。

### Rule 与 Runbook 保留独立职责

Rule 保存可复用约束、适用范围、验证方式和 Non-Goals。Runbook 保存前置条件、命令、成功标准和失败入口。

两类页面都在语义发生处引用相关 C4 element。Use Case Realization 不复制运行命令，C4 element 不承担操作手册职责。

### Summary-only Index

`wiki/index.md` 只解释 Wiki 结构，并链接主要 in-scope Software System。

存在 Glossary 页面时，Index 说明 `glossary/` 的职责，但不枚举术语。

Index 不枚举全部 C4 element 和上层页面，避免 Obsidian Graph 出现没有架构语义的中心节点。

### 组件 Code diagram 与 Code 结构图

Component 页面始终使用精简 Mermaid Code diagram 展示主要接口和入口。面向对象实现可以使用 `classDiagram`，非面向对象实现使用 `flowchart` 或 `erDiagram`。多数组件没有 Code 页面，这种映射让读者仍能将组件职责对应到真实代码。已有 Code 页面时，组件页保留精简图并提供下钻链接。

Code 页面按 [C4 Code diagram](https://c4model.com/diagrams/code) 的单组件范围解释代码组织。详细代码说明优先使用 `flowchart` 配合文字；数据库表结构使用 `erDiagram`。Code 页面不承担 Software System、Container 或 Component 页面已经表达的层级图。

图示基于真实代码或 Schema，只保留具有解释价值的元素和关系。Code 页面不设源码定位表，定位存在歧义时在相关说明中补充路径或限定名称。

页面之间的架构关系由 YAML 管理，组件内部代码元素关系由结构图表达。两者职责分开，避免重复维护同一份关系。

### 高价值代码注释

源码使用 `Wiki: <wiki-path> - <reason>` 行级注释链接 Wiki。

注释只标记公共 Use Case Realization 入口、C4 边界、关键 Code element 和 canonical Rule 实现。普通辅助代码和 Data Transfer Object（DTO）不标注。

### 渐进加载模板

公共 Schema、命名、链接和写作规则集中在 `wiki-structure.md`。准入条件和执行顺序集中在 `workflow-guide.md`。

每种页面类型使用独立模板，包括 Glossary。你只加载当前任务需要的模板，避免无关页面结构占用上下文。

### 旧 Wiki 迁移保持独立

本 Skill 的常规 Workflow 不发现、映射、重定向或迁移旧式 Wiki。

旧 Wiki 的范围和语义映射需要单独确认。将迁移拆为独立任务，可以避免常规维护隐式删除或错误解释旧内容。

### Git 记录历史

Wiki 只描述当前终态和当前理由。git commit 保存 Wiki 演进。

双重历史会增加维护成本，也会让 Agent 把已失效状态误认为当前规则。

## 维护规则

**必须** 在 purpose、taxonomy、Workflow、metadata、模板或 bundled references 变化时同步更新本文件。只记录当前决策及其必要理由。
