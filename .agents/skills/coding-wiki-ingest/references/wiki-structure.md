# Coding Wiki 公共结构

本文定义 coding-agent 项目 Wiki 的目录、page type、front matter、命名、链接、关系、写作和代码注释规则。

Architecture Decision Record（ADR）用于记录已接受的重要架构决策。页面正文结构由 `templates/` 下的对应模板定义。

## 目录结构

```text
wiki/
├── index.md
├── c4/
│   ├── system-arch.md
│   ├── actors/
│   ├── software-systems/
│   ├── containers/
│   ├── components/
│   └── code/
├── use-case-realizations/
├── adr/
├── rules/
├── runbooks/
└── glossary/
```

## Page type

| `type` | 目录 | 职责 |
| --- | --- | --- |
| `index` | `wiki/index.md` | 解释 Wiki 结构，并进入主要 in-scope Software System。 |
| `system-context` | `wiki/c4/system-arch.md` | 使用 Mermaid C4 System Context 图展示 Wiki 范围内的系统上下文。 |
| `actor` | `wiki/c4/actors/` | 表达与系统直接交互的稳定角色。 |
| `software-system` | `wiki/c4/software-systems/` | 表达独立提供价值的软件系统。 |
| `container` | `wiki/c4/containers/` | 表达 application、data store 或 batch process 等运行边界。 |
| `component` | `wiki/c4/components/` | 表达 Container 内由稳定接口封装的内聚功能。 |
| `code` | `wiki/c4/code/` | 表达一个 Component 内部关键代码元素的组织、职责和结构关系。 |
| `use-case-realization` | `wiki/use-case-realizations/` | 表达 C4 element 如何协作实现稳定的 Actor 目标，包括成功路径、替代路径、异常路径和结束保证。 |
| `adr` | `wiki/adr/` | 表达已接受的重要架构决策。 |
| `rule` | `wiki/rules/` | 表达可复用约束、适用范围和验证方式。 |
| `runbook` | `wiki/runbooks/` | 表达可执行操作、成功标准和失败入口。 |
| `glossary` | `wiki/glossary/` | 定义重要、稳定且容易产生歧义的项目术语。 |

### 核心约束

- **必须** 只使用表中声明的目录和 `type`。
- **必须** 让 `wiki/` 同时作为 Obsidian vault 根。
- **必须** 让每个 Glossary 页面只定义一个 canonical term。
- **禁止** 创建 Project、Architecture、Feature、Implementation、Concept、Use Case、User Story 或 Acceptance Criteria 页面。
- **禁止** 创建 `wiki/log.md`。git commit 保存 Wiki 演进。

## C4 element front matter

System Context 使用：

```yaml
---
name: "Project System Context"
type: system-context
children:
  - target: "[[c4/software-systems/order-platform]]"
---
```

Actor 和 Software System 使用：

```yaml
---
name: "Customer"
type: actor
relations:
  - target: "[[c4/software-systems/order-platform]]"
    description: "提交并管理订单。"
---
```

Container、Component 和 Code 使用：

```yaml
---
name: "Order API"
type: container
children:
  - target: "[[c4/components/order-platform-order-api-order-service]]"
relations:
  - target: "[[c4/containers/order-platform-order-database]]"
    description: "读取并写入订单状态。"
    mechanism: "SQL over PostgreSQL wire protocol/TCP"
---
```

### 核心约束

- **必须** 让 C4 element 顶层字段只包含 `name`、`type`、`children` 和 `relations`。
- **必须** 固定已有字段顺序为 `name`、`type`、`children`、`relations`。
- **必须** 让 `children` 只出现在拥有下一层 C4 element 的父页面。
- **必须** 让每项 `children` 只包含 `target`，并使用 vault-root Wikilink。
- **必须** 让 Actor、Code 和没有下级页面的 C4 element 省略 `children`。
- **必须** 始终提供 `relations`。没有出站关系时使用 `relations: []`。
- **必须** 让每项 relation 只包含 `target`、`description` 和可选 `mechanism`。
- **必须** 让 `description` 使用中文动作句说明 source 对 target 做什么。
- **必须** 让 `mechanism` 只描述交互协议或机制。
- **禁止** 将 framework、runtime、数据库产品或版本写入 `mechanism`。
- **禁止** 在 `relations` 中重复 `children` 包含关系。
- **禁止** 在正文重复 `children` 或 `relations`。

`mechanism` 可以写 `HTTPS/JSON`、`AMQP 0-9-1`、`gRPC/HTTP2` 或 `SQL over PostgreSQL wire protocol/TCP`。`PostgreSQL 17` 等 element 技术进入正文 `Technology`。

## 其他页面 front matter

Index、Use Case Realization、Rule、Runbook 和 Glossary 只使用 `name` 和 `type`：

```yaml
---
name: "Place an Order"
type: use-case-realization
---
```

ADR 使用：

```yaml
---
name: "Use PostgreSQL as the Order System of Record"
type: adr
id: "ADR-0001"
date: "YYYY-MM-DD"
status: accepted
---
```

被替代的 ADR 增加：

```yaml
status: superseded
superseded_by: "[[adr/0002-replacement-decision]]"
```

### 核心约束

- **必须** 使用双引号包裹 `name`、ADR `id`、`date` 和全部 YAML Wikilink。
- **必须** 让 Index、Use Case Realization、Rule、Runbook 和 Glossary 顶层字段只包含 `name` 和 `type`。
- **必须** 让 ADR 顶层字段只包含 `name`、`type`、`id`、`date`、`status` 和可选 `superseded_by`。
- **必须** 固定 ADR 字段顺序为 `name`、`type`、`id`、`date`、`status`、`superseded_by`。
- **必须** 让 ADR `date` 使用 `YYYY-MM-DD`。
- **必须** 让 ADR `status` 只使用 `accepted` 或 `superseded`。
- **必须** 仅在 `status: superseded` 时提供 `superseded_by`。
- **禁止** 为非 C4 页面添加通用关系或代码清单字段。

## 文件命名

页面使用稳定、语义明确的 lowercase English `kebab-case`。

| 页面 | 路径 |
| --- | --- |
| Actor | `wiki/c4/actors/<actor>.md` |
| Software System | `wiki/c4/software-systems/<system>.md` |
| Container | `wiki/c4/containers/<system>-<container>.md` |
| Component | `wiki/c4/components/<system>-<container>-<component>.md` |
| Code | `wiki/c4/code/<system>-<container>-<component>.md` |
| Use Case Realization | `wiki/use-case-realizations/<system>-<actor-goal>.md` |
| ADR | `wiki/adr/NNNN-<decision>.md` |
| Rule | `wiki/rules/<rule>.md` |
| Runbook | `wiki/runbooks/<operation>.md` |
| Glossary | `wiki/glossary/<term>.md` |

`wiki/index.md` 使用固定文件名。Code 页面继承父 Component 的 basename。ADR 文件编号与 `id` 数字一致。

`name` 是页面 canonical name。你应按上表生成路径：Code 继承父 Component 的 basename，其他页面将 canonical English `name` 转换为 lowercase English `kebab-case`，生成页面自身路径片段。

Actor、Software System、Rule、Runbook 和 Glossary 的 basename 直接来自 `name`。

Container 和 Component 的 basename 由父页面 basename 加自身 `name` 片段组成。

Code 页面的 `name` 使用 `<Component Name> Code`，basename 直接继承父 Component。每个 Component 最多对应一份 Code 页面，内部代码元素不另建页面。

Use Case Realization 使用 Software System 片段加自身 `name` 片段。ADR 使用编号加自身 `name` 片段。

例如 `name: "Call Graph Engine"` 的 Component 若父页面为 `dependency-analyzer-cli.md`，路径必须为 `wiki/c4/components/dependency-analyzer-cli-call-graph-engine.md`。

## YAML Wikilink、正文 Markdown 链接与关系

你应让 `children[].target`、relation `target` 和 ADR `superseded_by` 使用从 `wiki/` vault 根开始、不带 `.md` 的 Obsidian Wikilink。

```yaml
children:
  - target: "[[c4/components/order-platform-order-api-order-service]]"
relations:
  - target: "[[c4/software-systems/payment-provider]]"
    description: "请求支付授权。"
    mechanism: "HTTPS/JSON"
```

正文内部引用使用标准 Markdown 链接。链接目标相对当前页面计算，并显式包含 `.md`。

以下示例假设当前页面位于 `wiki/use-case-realizations/`：

```markdown
[Customer](../c4/actors/customer.md) 通过
[Order Platform](../c4/software-systems/order-platform.md) 提交订单。
```

`wiki/index.md` 引用 Software System 时使用 `[Order Platform](c4/software-systems/order-platform.md)`。`wiki/c4/software-systems/` 下的页面引用 Actor 时使用 `[Customer](../actors/customer.md)`。

### 链接约束

- **必须** 只在 YAML `children[].target`、relation `target` 和 ADR `superseded_by` 中使用 Obsidian Wikilink。
- **必须** 让正文内部引用使用 `[显示名称](相对路径.md)`。
- **必须** 从当前页面所在目录计算正文链接目标。
- **必须** 让正文内部链接显式包含 `.md`。
- **禁止** 在正文使用 Obsidian Wikilink。

### 关系准入

- **必须** 只记录影响边界、数据、协议、失败面或关键协作的直接关系。
- **必须** 让关系方向遵循交互发起方或信息发送方。
- **必须** 让关系得到当前、可定位的证据支持。
- **禁止** 记录传递依赖、偶然调用或普通内部协作。
- **禁止** 为导航维护反向关系或通用消费者清单。
- **禁止** 因目标页面链接 source 就推断目标主动返回关系。

同一交互可以按 C4 zoom 分别出现在 Level 1（L1）、Level 2（L2）和 Level 3（L3）。每一层仍需独立说明该层边界内的直接交互。

`children[].target` 必须作为 children 项下的字符串字段保存，并使用 vault-root Wikilink。该字段表达父页到直接子页的包含关系。`relations[].target` 仍作为 relation 项下的字符串 Wikilink，表达 source 主动发起或发送的架构交互。两者都不携带显示名称或图形样式，Mermaid 图由页面正文明确绘制。

Glossary Markdown 链接只表达语义导航。C4 `relations` 不指向 Glossary 页面。

## 代码与 Wiki 的内容边界

- **必须** 让代码负责类、接口、字段、方法和局部控制流。
- **必须** 让 Wiki 负责稳定背景、架构边界、契约、决策、场景、约束和操作。
- **必须** 只记录脱离当前任务后仍然成立的工程知识。
- **禁止** 按源码顺序复述调用过程。
- **禁止** 罗列全部类、方法、异常、配置项、依赖、调用方或消费者。
- **禁止** 记录临时探索命令、一次性检查清单或变更历史。

你应让 Code 页面集中解释所属 Component 内关键代码元素的分组、职责和结构关系，并说明这些组织方式如何实现组件职责。Contract 和 Invariant 仅在解释结构设计确有需要时补充。

## C4 层级词汇边界

- **必须** 让 System Context 和 Software System 的正文优先使用通用术语、相邻 C4 层级概念和已准入 Glossary 术语。
- **必须** 让 Software System 的 `Responsibilities`、`Boundaries` 和 `Overview` 说明系统价值、责任和范围，不直接引用类名、方法名、字段名、package 或源码路径。
- **必须** 让 Container 的正文说明运行边界、职责、接口、技术和数据，不直接使用类级实现信息。
- **必须** 让 Component 只在 `Code Diagram`、接口定位或确有解释增量的说明中使用代码标识符，并先说明其领域作用。
- **必须** 让 Code 页面承担关键类、方法、模块和局部结构的详细解释。
- **禁止** 用低层代码细节替代上层 C4 element 的职责、价值或边界说明。
- **必须** 在跨页面复用且不属于标准技术名称的专有概念通过 Glossary 统一词义。

## 写作与格式

- **必须** 默认使用中文正文和模板规定的英文章节标题。
- **必须** 让 relation `description` 使用中文动作句。
- **必须** 保持专业技术术语、名称、标识符和代码原样。
- **必须** 让 `Overview` 最多包含三句。
- **必须** 让 Component 和 Code 的 `Overview` 说明 element 是什么、处理什么，以及向父级能力提供什么结果。
- **必须** 让 Component 和 Code 只假定读者已经阅读直接父页面。
- **必须** 在项目专有概念首次出现时使用自然中文解释，并按 Glossary 准入结果添加相对 Markdown 链接。
- **必须** 让包含 Glossary Markdown 链接的句子在不打开链接时仍能表达基本含义。
- **必须** 先说明代码标识符的领域作用，再使用标识符补充定位。
- **必须** 让生命周期描述说明触发时机、释放或保留的状态，以及事件后仍可使用的结果。
- **必须** 让 `Technology` 中每条 `-` 列表项只描述一种技术。名称、版本和该技术的架构作用可以位于同一项。
- **必须** 让一个段落或一条列表项只表达一个事实或动作。
- **必须** 将并列责任、边界、接口、技术、流程和约束使用 Markdown 列表表达；代码块、Mermaid 图和连续说明可保留其所需格式。
- **必须** 在模板中使用可见的 Markdown 引用块 `>` 表达填写说明、目的、表达形式和其他约束。
- **必须** 让模板章节标题和顺序直接对应最终生成文件的章节结构；填写完成后删除引用说明、占位内容和不适用章节。
- **必须** 让同一事实只由一个页面负责，并在语义发生处引用负责页面。
- **必须** 省略没有解释增量的可选章节。
- **必须** 让每个 C4 element 的 `Boundaries` 同时说明适用范围和至少一个明确排除项。
- **禁止** 留下空的可选章节或使用 `None` 占位。
- **禁止** 使用模糊、暗示性或缺少失败边界的关键表述。
- **禁止** 在 `Technology` 的同一列表项中合并多种技术。
- **禁止** 使用未解释的中英混合名词链代替自然中文说明。

短标识符、字段、路径和短命令使用行内代码。多行源码、命令、Schema 和配置使用带语言标识的 fenced code block。

只有最小代码片段比自然语言更准确地表达稳定契约或格式时，才展示代码。代码片段必须服务于解释，不能代替解释。

## 图示

你应使用图示解释 C4 层级和组件内部代码结构。`wiki/c4/system-arch.md` 绘制 System Context；Software System 页面绘制所属系统的 Container diagram；Container 页面绘制 Component diagram；Component 页面保留精简 Code diagram。YAML `children` 管理页面包含关系，`relations` 管理页面之间的架构关系，Obsidian Graph 提供人工审查视图。

1. 在 `wiki/c4/system-arch.md` 使用 Mermaid `C4Context` 展示 Actor、in-scope Software System、直接相关的 external Software System 和关系。
2. 在每个 Software System 页面的 `Container Diagram` 使用 Mermaid `C4Container` 展示该系统的 Container 及其直接关系。
3. 在每个 Container 页面的 `Component Diagram` 使用 Mermaid `C4Component` 展示该 Container 的 Component 及其直接关系。
4. 在每个 Component 页面的 `Code Diagram` 使用 Mermaid `classDiagram`、`erDiagram` 或 `flowchart` 展示主要接口、入口和必要实现类型。非面向对象实现标注实际 module 或 function，不虚构类或继承关系。
5. 在 Code 页面的 `Code Structure` 使用 Mermaid `flowchart` 配合文字说明需要特别详细解释的代码组织。面向对象实现可以在 flowchart 节点中标注类和方法；数据库结构仍可使用 `erDiagram`。
6. 使用真实代码标识符，只保留具有解释价值的元素、属性和方法。配套文字说明职责、组织方式和必要理由。
7. 图示基于可验证的代码或 Schema。证据不足时说明缺口，暂停补写无法确认的部分。

### 图示约束

- **必须** 让 Component Code diagram 保持精简；存在 Code 页面时仍保留精简图，并提供下钻链接。
- **必须** 让每个 Software System 和 Container 页面分别提供 Container diagram 和 Component diagram。
- **必须** 让 Component 页面承载 Code diagram；Code 页面只在通过 L4 门槛时补充详细代码解释。
- **必须** 让 Code 结构图限定在所属 Component 内。
- **禁止** 在代码结构图中复制页面之间的架构关系或逐步复述源码控制流。
- **禁止** 虚构代码元素、属性、方法或关系。

## 行级代码注释

高价值代码入口使用以下格式链接 Wiki：

```text
Wiki: <wiki-path> - <reason>
```

TypeScript 示例：

```ts
// Wiki: wiki/use-case-realizations/order-platform-place-order.md - Public Use Case Realization entrypoint
async function placeOrder(input: PlaceOrderInput) {
  // ...
}
```

### 核心约束

- **必须** 使用宿主语言的普通行注释语法。
- **必须** 让 `<wiki-path>` 相对 repository root，并以 `wiki/` 开头。
- **必须** 让 `<reason>` 说明当前代码块与页面的稳定关系。
- **必须** 一行只保存一个 Wiki 引用。
- **必须** 只标注公共 Use Case Realization 入口、C4 边界、关键 Code element 和 canonical Rule 实现。
- **禁止** 添加时间戳。
- **禁止** 标注普通辅助方法、Data Transfer Object（DTO）、局部工具或偶然相关代码。
