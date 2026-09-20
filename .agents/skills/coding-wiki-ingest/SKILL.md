---
name: coding-wiki-ingest
description: >-
  Coding wiki ingest expert. ALWAYS invoke this skill when the user asks to create, update, ingest, or maintain a coding-agent project wiki,
  including C4 actors, software systems, containers, components, code elements, use case realizations, architecture decision records,
  rules, runbooks, glossaries, indexes, Obsidian graph relations, terminology, or code-to-wiki references.
  Do not create or maintain coding-agent wiki content without this skill.
---

# Coding Wiki Ingest

你应维护面向 coding agent 的项目 Wiki。C4 element 提供可导航的架构底座。

Use Case Realization、Architecture Decision Record（ADR）、Rule 和 Runbook 通过引用 C4 element 解释实现协作、决策、约束和操作。Glossary 统一重要且容易产生歧义的项目术语。

## 适用范围

- 创建或更新 coding-agent 项目 Wiki。
- 从代码、项目文档、配置、测试或当前对话提取稳定工程知识。
- 维护 C4 element、Use Case Realization、ADR、Rule、Runbook、Glossary 和精简 Index。
- 在高价值代码入口维护行级 Wiki 引用。

## 不适用范围

- 仅查询现有 Wiki 内容。
- 编写普通 README、用户手册或通用领域知识库。
- 记录应由 git commit 保存的变更历史。
- 迁移旧式 Wiki、创建兼容页或维护重定向。

## 核心模型

| 层次 | 职责 |
| --- | --- |
| 代码 | 实现细节的权威来源。 |
| C4 底座 | 使用 Actor、Software System、Container、Component 和 Code element 表达架构边界。 |
| 上层工程知识 | 使用 Use Case Realization、ADR、Rule 和 Runbook 解释 C4 element 之上的实现协作与约束。 |
| 共享词汇 | 使用 Glossary 统一重要且稳定的项目术语。 |
| Code comment | 从高价值代码入口进入 Wiki 的轻量导航。 |

## 核心约束

- **必须** 为每个 in-scope Software System 建立 Level 1（L1）和 Level 2（L2）底座。
- **必须** 在 `wiki/c4/system-arch.md` 维护唯一的 Mermaid System Context diagram。
- **必须** 让 Level 3（L3）Component 和 Level 4（L4）Code 通过对应价值门槛与准入测试。
- **必须** 让 YAML `children` 和 `relations` 分别管理页面包含关系和页面之间的架构交互。
- **必须** 让父页面使用 `children[].target` 登记直接下一层 C4 页面；子页面不使用 `parent`。
- **必须** 让 YAML 结构字段使用 vault-root Wikilink，并让正文内部引用使用带 `.md` 的相对 Markdown 链接。
- **必须** 让页面 `name` 成为 canonical name，并按公共命名规则生成路径；Code 页面继承父 Component 的 basename。
- **必须** 让 relation `description` 使用中文动作句。
- **必须** 先用自然中文解释陌生概念，再判断是否需要 Glossary。
- **必须** 让 Wiki 提供代码之外的解释增量。
- **必须** 只描述当前终态及仍然有效的理由。
- **禁止** 为追求覆盖率创建 Component 或 Code 页面。
- **必须** 让 Use Case Realization 描述 C4 element 如何协作实现稳定的 Actor 目标。
- **必须** 让 User Story 和 Acceptance Criteria 由 PRD 等需求文档负责。
- **禁止** 在 coding Wiki 中创建或复制 User Story、Acceptance Criteria 或 Given-When-Then 验收结构。
- **必须** 让 Component 页面始终提供主要接口和入口的精简 Mermaid Code diagram。
- **必须** 让 Software System 页面提供 Container diagram，Container 页面提供 Component diagram，Component 页面提供 Code diagram。
- **必须** 按 Component 聚合 Code 内容，每个组件最多对应一份 Code 页面。
- **必须** 让 Code 页面通过 Mermaid flowchart 和文字解释需要特别详细说明的组件内部代码组织。
- **必须** 让 Software System 和 Container 正文优先使用通用术语、相邻 C4 层级概念和 Glossary 术语。
- **禁止** 在 Software System 或 Container 的职责、边界和概述中直接使用类名、方法名、字段名、package 或源码路径。
- **必须** 使用可见的 Markdown 引用块 `>` 表达模板约束，并让约束中的并列事实使用 Markdown 列表。
- **必须** 让模板中的章节标题和顺序直接对应最终生成文件的章节结构。
- **禁止** 枚举全部依赖文件、调用方、消费者、类、方法或 endpoint。
- **禁止** 在本 Workflow 中迁移旧式 Wiki。

## 执行入口

你应在规划 Wiki 更新前完整阅读 [wiki-structure.md](references/wiki-structure.md) 和 [workflow-guide.md](references/workflow-guide.md)。

确定目标页面类型后，只加载对应模板。

| 目标 | 模板 |
| --- | --- |
| Actor | [actor.md](references/templates/actor.md) |
| System Context | [system-arch.md](references/templates/system-arch.md) |
| Software System | [software-system.md](references/templates/software-system.md) |
| Container | [container.md](references/templates/container.md) |
| Component | [component.md](references/templates/component.md) |
| Code | [code.md](references/templates/code.md) |
| Use Case Realization | [use-case-realization.md](references/templates/use-case-realization.md) |
| ADR | [adr.md](references/templates/adr.md) |
| Rule | [rule.md](references/templates/rule.md) |
| Runbook | [runbook.md](references/templates/runbook.md) |
| Glossary | [glossary.md](references/templates/glossary.md) |
| `wiki/index.md` | [index.md](references/templates/index.md) |
