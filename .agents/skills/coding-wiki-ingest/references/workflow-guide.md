# Coding Wiki Ingest Workflow

你应按以下顺序创建或更新 coding-agent 项目 Wiki。本文使用 Level 1（L1）到 Level 4（L4）表示 C4 下钻层级。

Architecture Decision Record（ADR）表示已接受的重要架构决策。各阶段只处理已通过证据与准入检查的候选。

## 目录

- [阶段一：检索现有新式页面](#阶段一检索现有新式页面)
- [阶段二：读取权威来源与交叉证据](#阶段二读取权威来源与交叉证据)
- [阶段三：建立 L1 Actor 与 Software System](#阶段三建立-l1-actor-与-software-system)
- [阶段四：建立必备 L2 Container](#阶段四建立必备-l2-container)
- [阶段五：执行 L3 门槛并提取 Component](#阶段五执行-l3-门槛并提取-component)
- [阶段六：执行 L4 门槛并提取 Code](#阶段六执行-l4-门槛并提取-code)
- [阶段七：提取 children 与架构显著 relations](#阶段七提取-children-与架构显著-relations)
- [阶段八：选择并维护上层页面](#阶段八选择并维护上层页面)
- [阶段九：提取并维护 Glossary](#阶段九提取并维护-glossary)
- [阶段十：说明并执行 Wiki 更新](#阶段十说明并执行-wiki-更新)
- [阶段十一：更新高价值行级代码注释](#阶段十一更新高价值行级代码注释)
- [阶段十二：更新精简 Index 并完成检查](#阶段十二更新精简-index-并完成检查)

## 阶段一：检索现有新式页面

1. 读取 `wiki/index.md`。
2. 检查 `c4/`、`use-case-realizations/`、`adr/`、`rules/`、`runbooks/` 和 `glossary/`。
3. 按 `name`、文件名、`children`、`relations`、Glossary Aliases 和正文引用检索语义相近页面。
4. 搜索 YAML Wikilink、正文相对 Markdown 链接和行级 Wiki 注释中指向候选路径的引用。
5. 同一稳定主题已经存在时，优先更新现有页面。

旧目录或旧 page type 不进入本 Workflow。发现旧式 Wiki 时，你应说明迁移属于独立任务，并继续处理能够独立落地的新式页面。

## 阶段二：读取权威来源与交叉证据

你应读取与候选边界直接相关的来源：

- 当前对话中的明确需求、确认和决策。
- 项目文档、用户文档和公共入口说明。
- 源代码、接口、测试和身份模型。
- 构建定义、runtime entrypoint 和部署配置。
- 数据库 Schema、queue、bucket、cache 和协议定义。

明确需求、部署配置、入口定义、Schema 或用户确认可以单独支持 element。普通实现代码需要由测试、配置、接口或多处一致结构交叉佐证。

你应只提取脱离当前任务后仍然成立，并能帮助 Agent 正确理解或修改项目的知识。临时实现笔记、一次性命令、失效方案和通用编程建议不进入 Wiki。

无法证明层级、直接子页面、稳定职责或关系方向时，你应暂停对应候选。说明缺少的证据，并请求用户补充关键边界。

## 阶段三：建立 L1 Actor 与 Software System

### Actor 准入

Actor 候选只有同时满足以下条件才准入：

- 表示稳定角色、用户类别或 persona，而不是具体个人。
- 与 in-scope Software System 直接交互。
- 拥有区别于其他 Actor 的目标或交互方式。
- 得到需求、公共入口、身份模型、测试、项目文档或用户确认支持。

以下对象不准入 Actor：

- 不直接交互的决策者或利益相关方。
- 开发团队、Feature team 和临时项目角色。
- 具体人员姓名。
- 自动化外部系统。此类对象按 Software System 判断。

### Software System 准入

Software System 候选只有满足以下条件才准入：

- 独立向 Actor 或其他 Software System 提供价值。
- 具有可辨识的责任、所有权或修改边界。
- in-scope Software System 的内部实现由当前团队负责或可见。
- external Software System 与 in-scope Software System 存在直接交互。

以下对象不准入 Software System：

- Repository、团队、业务能力、领域或 bounded context。
- library、package、module 或单个 runtime service。
- 没有直接交互的供应商产品或组织。

System Context 只包含 Actor、in-scope Software System、直接相关的 external Software System 和它们的关系。内部 Container、技术选择和部署设施留在后续层级。

每个 in-scope Software System 都需要 L1 页面。Context 超过约 15–20 个 element 时，你应重新检查系统范围。该数量是异常信号，不是硬限制。

完成准入后，更新唯一的 `wiki/c4/system-arch.md`。使用 Mermaid `C4Context` 绘制 Actor、in-scope Software System、直接相关的 external Software System 和直接关系。图中节点必须能回到对应页面；关系方向和文字与 source 页面 `relations[].target`、`description` 保持一致。

## 阶段四：建立必备 L2 Container

Container 候选只有同时满足以下条件才准入：

- 属于一个 in-scope Software System。
- 是系统运行所需的 application 或 data store。
- 拥有独立执行边界或数据生命周期。
- 能够明确说明自身责任和当前技术。
- 得到 runtime entrypoint、构建定义、部署配置、数据库 Schema、queue、bucket 或其他稳定来源支持。

以下对象不准入 Container：

- package、module、Java Archive（JAR）、library 和普通代码目录。
- Docker image、Pod、虚拟机（VM）、replica、load balancer 和其他部署节点。
- 不受当前系统拥有或管理的第三方服务。此类对象按 external Software System 判断。
- 仅存在于部署配置，但没有独立运行或数据责任的资源。

当前团队拥有并管理的数据库 Schema、bucket、queue 或 cache 可以作为 Container，即使它们托管在外部云服务中。

每个 in-scope Software System 都需要完整的 L2 页面。你应先完成 L2，再判断是否需要 L3。

每个 Software System 页面必须在 `Container Diagram` 中使用 Mermaid `C4Container` 展示所属 Container 和直接关系。

## 阶段五：执行 L3 门槛并提取 Component

你应先对所属 Container 执行价值门槛。以下任一条件成立，才允许下钻：

- 内部结构复杂。
- 采用需要明确维护的刻意架构。
- 多个团队共同修改。
- 新成员长期难以定位职责和修改入口。

没有条件成立时，当前分支停在 L2。

Component 候选还需同时满足以下条件：

- 完全位于一个 Container 内。
- 表示由稳定接口封装的内聚功能分组。
- 通常由多个 code element 实现。
- 拥有独立、可说明的架构责任。
- 能提供目录结构和局部代码之外的解释增量。

以下对象不准入 Component：

- 仅因存在同名 package 或目录形成的分组。
- 单个类、函数或接口。
- helper、utility、Data Transfer Object（DTO）或普通 framework wiring。
- 直白的创建、读取、更新和删除（CRUD）、薄 Adapter 和薄 Controller。
- 第三方 Container 的内部结构。

单个 Container 超过约 20 个 Component 时，你应重新检查 Component 粒度或 Container 边界。该数量是异常信号，不是硬限制。

你应在通过准入的 Component 页面中始终提供主要接口和入口的精简 Mermaid Code diagram。图中使用真实代码标识符，并说明必要职责；定位存在歧义时补充路径或限定名称。此代码图不要求创建 Code 页面。

## 阶段六：执行 L4 门槛并提取 Code

你应先对所属 Component 执行价值门槛。以下任一条件成立，才允许下钻：

- 实现复杂算法或非直觉设计。
- 属于合规或审计范围。
- 涉及关键安全边界。
- 是长期遗留代码的主要入职障碍。
- 暴露重要公共契约。
- 实现结构本身体现关键设计模式。

没有条件成立时，当前分支停在 L3。

你应按 Component 判断 Code 页面候选。候选还需同时满足以下条件：

- 范围限定为一个已准入的 Component。
- 关键代码元素及结构关系能够由当前代码或 Schema 验证。
- 内部组织具有组件页精简 Code diagram 无法充分表达的解释价值。
- 能说明关键元素的分组、职责和关系如何实现组件职责。

仅罗列符号、复述目录结构或展示普通样板代码的候选不准入。Contract 和 Invariant 不作为必备条件。

你应让每个 Component 最多对应一份 Code 页面。通过结构图和文字集中解释内部代码组织，不设置覆盖率目标，也不为单个代码元素另建页面。

每个 Container 页面必须在 `Component Diagram` 中使用 Mermaid `C4Component` 展示所属 Component 和直接关系。每个 Component 页面必须在 `Code Diagram` 中保留精简代码视图；只有通过 L4 门槛时才创建 Code 页面，详细说明优先使用 Mermaid `flowchart` 配合文字。

## 阶段七：提取 children 与架构显著 relations

1. 在 System Context、Software System、Container 和 Component 页面登记直接下一层页面的 `children[].target`。
2. 从当前 source 识别直接出站交互。
3. 只保留影响边界、数据、协议、失败面或关键协作的关系。
4. 使用中文动作句写 `description`。
5. 仅在证据明确时写交互 `mechanism`。
6. 删除包含关系、反向导航、传递依赖和偶然调用。

关系方向存在歧义时，你应暂停该 relation。不要用推测补齐发起方、发送方或协议。

## 阶段八：选择并维护上层页面

你应只创建具有独立工程价值和可靠来源的上层页面。

| 页面 | 准入条件 |
| --- | --- |
| Use Case Realization | 存在稳定 Actor 目标、受支持入口、直接参与的 C4 element、主成功路径和结束保证。 |
| ADR | 存在已接受且影响结构、质量属性、重要依赖、接口或构造方式的决策。 |
| Rule | 约束适用于后续同类修改，并拥有稳定项目来源。 |
| Runbook | 操作能够说明前置条件、命令、成功标准和失败入口。 |

Use Case Realization 在流程发生处使用相对 Markdown 链接引用参与的 C4 element。上游 Use Case、User Story 和 Acceptance Criteria 可以作为行为证据，但不复制到 coding Wiki。具体运行命令和排障步骤进入 Runbook。

**必须** 让 ADR 的备选方案来自真实讨论或记录。无法证明决策已接受时，不创建 ADR。

新决策替代旧决策时创建新 ADR，只修改旧 ADR 的 `status` 和 `superseded_by`。

## 阶段九：提取并维护 Glossary

你应先把候选正文改写为自然中文。删除不再需要的陌生词和中英混合名词链，再收集仍不可避免的项目专有术语。

Glossary 统一词义，不能代替局部解释。

Glossary 候选必须同时满足以下条件：

- 拥有可靠来源支持的稳定项目含义。
- 目标新成员可能不了解，或常见含义不足以表达项目中的特定语义。
- 定义能够脱离单个代码符号或单段实现独立成立。
- 定义能够帮助 Agent 判断架构边界、Contract、Invariant、生命周期或失败语义。
- 至少出现在两个独立页面；或者只出现一次，但误解会导致关键边界、Contract、Invariant、资源生命周期或失败语义被错误修改。
- 现有 C4、Rule、ADR 或 Code 页面没有承担同一概念的 canonical 解释职责。

以下候选不准入 Glossary：

- Java、JSON、HTTP、WALA 等标准语言、协议、framework 或 library 名称。
- 类、接口、方法、字段和配置项等代码标识符。
- Actor、Software System、Container、Component 和 Code element 名称。
- `immutable`、`canonical`、`bounded`、`session` 和 `snapshot` 等通用形容词或孤立技术词。
- 只在一个页面出现，并能用自然中文直接解释的局部实现概念。
- 操作步骤、架构决策、约束或完整机制说明。
- 只为缩短正文而提取的术语。
- 缺少稳定定义，或不同来源仍在使用冲突含义的术语。

你应按以下顺序维护候选：

1. 搜索现有 Glossary 的 `name`、`Aliases` 和 `Definition`。
2. 同一概念已经存在时，更新 canonical 页面，不创建重复页面。
3. 执行全部准入条件。
4. 无法确认 canonical name、定义或同义词关系时，暂停该术语并请求用户确认。
5. 通过准入后创建或更新 Glossary 页面。
6. 在每个正文页面首次出现处添加指向 Glossary 页面的相对 Markdown 链接，后续出现不重复链接。
7. 检查包含 Glossary Markdown 链接的句子在不打开链接时仍能表达基本含义。

例如 `session close` 应改写为“构图结束或失败时关闭会话并释放 WALA 资源；已冻结结果仍可供下游读取”。该表达不创建 `Session Close` 页面。

代码标识符 `ModuleCallGraphInput` 和技术名称 WALA 也不进入 Glossary。

Glossary 总量超过约 30 项时，你应复查重复、常识和局部术语。该数量是异常信号，不是硬限制。

## 阶段十：说明并执行 Wiki 更新

你应在编辑前给出简短更新说明：

| 字段 | 内容 |
| --- | --- |
| Page path | 目标 Wiki 路径。 |
| Action | `create`、`update`、`rename` 或 `remove`。 |
| Type | 目标 page type。 |
| Intent | 页面提供的一句解释价值。 |
| Admission evidence | 对应层级或页面类型的准入证据。 |
| Parent | 直接父级；不适用时写 `N/A`。 |
| Relations | 方向、目标、描述、可选机制和证据。 |
| C4 references | 上层页面在语义发生处引用的 C4 element。 |
| Realization flows | Main Success Scenario、Alternative Flows、Exception Flows、结束保证及其证据。 |
| Glossary | 术语的准入依据、canonical name、Aliases 和正文首次引用位置。 |
| Code comments | 需要新增、更新或移除的高价值注释。 |
| Index | 主要 Software System 入口是否变化。 |

**必须** 为 Code 页面逐项给出 Component 价值门槛证据，以及组件内部结构的全部准入证据。证据不足时，从说明中移除该页面候选。

完成说明后，你应加载目标类型模板。按 [wiki-structure.md](wiki-structure.md) 的公共规则和模板边界更新页面。

Use Case Realization 的 Main Success Scenario 只描述最常见的完整成功路径。Alternative Flows 描述仍能实现目标的受支持变化。Exception Flows 描述错误、拒绝、中断和恢复路径。

Alternative Flow 和 Exception Flow 使用主流程步骤编号与分支字母定位。替代路径明确重新汇入的步骤或独立成功终点。异常路径明确系统处理、Actor 可观察结果，以及重试、重新汇入或终止位置。

Minimal Guarantee 适用于任何终止路径。Success Guarantee 只适用于成功路径。权威来源没有说明 Alternative Flow 或 Exception Flow 时，对应章节使用 `- Not specified.`，不能根据当前实现推导需求。

## 阶段十一：更新高价值行级代码注释

你应使用 `Wiki: <wiki-path> - <reason>` 维护以下入口：

- 公共 Use Case Realization 入口。
- C4 边界。
- 关键 Code element。
- canonical Rule 实现。

已有注释仍然有效时保留。页面职责或路径变化时同步更新。代码块不再承担页面主题时移除对应注释。

## 阶段十二：更新精简 Index 并完成检查

你应让 `wiki/index.md` 只解释 Wiki 结构，并链接主要 in-scope Software System。存在 Glossary 页面时说明 `glossary/` 的职责。

不要枚举全部 C4 element、Use Case Realization、ADR、Rule、Runbook 或 Glossary term。

### 首次读者检查

你应只按 Software System、Container、Component 和 Code 的父子路径阅读目标分支，不查看源码。检查每页能否说明自身职责、输入输出、生命周期和边界。

遇到陌生概念时，先确认当前句子已经提供基本含义。项目专有术语需要跨页面统一时，再确认首次出现处链接已准入的 Glossary 页面。

代码标识符不能作为唯一解释。

### 完成检查

- [ ] 每个 in-scope Software System 都有 L1 和完整 L2 底座。
- [ ] 每个 L3 和 L4 页面都通过价值门槛与全部准入条件。
- [ ] 简单 Container 停在 L2；复杂 Container 只有通过门槛后进入 L3。
- [ ] 普通 Component 停在 L3；关键 Component 只有通过门槛后进入 L4。
- [ ] Code 页面提供内部组织的解释价值，而非符号清单、目录复述或普通样板代码。
- [ ] 每个 Component 最多对应一份 Code 页面，范围限定为该组件。
- [ ] 页面路径与允许的 `type` 匹配。
- [ ] 页面路径符合命名规则；Code 的 `name` 为 `<Component Name> Code`，basename 继承父 Component。
- [ ] C4 front matter 只包含允许字段。
- [ ] `children` 只登记直接下一层页面；没有下级页面的 C4 element 省略 `children`。
- [ ] 所有 C4 element 都包含 `relations`，空关系使用 `relations: []`。
- [ ] relation 只包含 `target`、`description` 和可选 `mechanism`。
- [ ] relation `description` 使用中文动作句。
- [ ] relation 没有 element technology、通用反向关系、传递依赖或消费者清单。
- [ ] element 技术和可靠版本只出现在正文 `Technology`。
- [ ] `Technology` 每条 `-` 列表项只描述一种技术。
- [ ] `children[].target`、`target` 和 `superseded_by` 使用 vault-root YAML Wikilink。
- [ ] 正文内部引用使用相对当前页面、带 `.md` 的标准 Markdown 链接。
- [ ] 正文没有 Obsidian Wikilink。
- [ ] C4 正文没有重复 `children` 或 `relations`。
- [ ] 五类 C4 页面符合固定章节、条件章节和内容边界。
- [ ] Component 和 Code 沿父级阅读时，无需源码即可理解职责、输入输出、生命周期和边界。
- [ ] 生命周期描述包含触发时机、释放或保留的状态，以及事件后仍可使用的结果。
- [ ] 每个 C4 `Boundaries` 包含适用范围和明确排除项。
- [ ] Use Case Realization 包含固定的十个章节。
- [ ] Main Success Scenario 描述最常见的完整成功路径。
- [ ] Alternative Flows 与 Exception Flows 分开记录，并映射主流程步骤。
- [ ] Alternative Flow 明确重新汇入的步骤或独立成功终点。
- [ ] Exception Flow 明确异常条件、系统处理、Actor 可观察结果，以及重试、重新汇入或终止位置。
- [ ] Minimal Guarantee 适用于任何终止路径，Success Guarantee 只适用于成功路径。
- [ ] 来源没有说明的 Alternative Flows 或 Exception Flows 使用 `Not specified.`。
- [ ] Use Case Realization 没有复制 User Story、Acceptance Criteria 或 Given-When-Then 验收结构。
- [ ] ADR 只使用 `accepted` 或 `superseded`，替代关系符合不可变正文规则。
- [ ] Rule 和 Runbook 在语义发生处引用相关 C4 element。
- [ ] 每个 Glossary 页面都通过全部准入条件，并且只定义一个 canonical term。
- [ ] Aliases 没有形成重复 Glossary 页面。
- [ ] C4 `relations` 没有指向 Glossary 页面。
- [ ] 每个页面首次使用已准入术语时提供相对 Markdown 链接，后续出现不重复链接。
- [ ] 删除 Glossary Markdown 链接后，原句仍能表达基本含义。
- [ ] 页面没有 Project、Architecture、Feature、Implementation、Concept、Use Case、User Story 或 Acceptance Criteria 类型。
- [ ] `wiki/c4/system-arch.md` 存在唯一的 Mermaid `C4Context` System Context diagram。
- [ ] 每个 Software System 页面包含 Mermaid `C4Container` Container diagram。
- [ ] 每个 Container 页面包含 Mermaid `C4Component` Component diagram。
- [ ] 每个 Component 页面包含精简 Mermaid Code diagram；存在 Code 页面时提供下钻链接。
- [ ] Code 页面使用 Mermaid `flowchart` 或必要的 `erDiagram`，并解释关键代码元素的分组、职责和关系。
- [ ] 图中元素和关系有代码或 Schema 支持，没有复制页面之间的架构关系。
- [ ] Code 固定章节为 `Overview`、`Code Structure` 和 `Boundaries`，可选章节只在解释结构设计确有需要时出现。
- [ ] 高价值代码入口按需维护行级 Wiki 注释。
- [ ] Index 没有形成全页面导航中心。
- [ ] Index 没有枚举 Glossary term。
- [ ] 内容使用中文电报风格，并只描述当前终态。
