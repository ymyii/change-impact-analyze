---
title: "Repository Dependency Tree Report"
type: feature
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "tree subcommand 的产品范围"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Repository 到 occurrence/report 的数据边界"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "Command Preflight、Analysis issue 和 exit code"
  - path: "wiki/features/maven-runtime.md"
    desc: "Dependency collection 使用的 Maven runtime"
  - path: "wiki/features/maven-build-runner.md"
    desc: "共享reactor scope与Maven compile mode"
  - path: "wiki/features/git-workspace-management.md"
    desc: "Current checkout 和 local-ref snapshot"
  - path: "wiki/features/report-generator.md"
    desc: "Impact/Tree共享的offline shard writer、loader与浏览器门禁"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git/Maven process 执行约束"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "Public tree CLI option 与metrics session入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeExecutionEngine.java"
    desc: "Preflight、串行Reactor processing、cache与Report生命周期"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorInventoryBuilder.java"
    desc: "入口POM、active module graph与祖先aggregator解析"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDependencyCollector.java"
    desc: "单次Reactor compile、dependency tree与classpath evidence collection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/DependencyOccurrence.java"
    desc: "Version、scope、selection、path和reactor module evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ModuleVersionAnalyzer.java"
    desc: "单个Module内的version mediation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ModuleClassConflictAnalyzer.java"
    desc: "Module effective classpath扫描、风险分类与反编译"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeExternalOccurrenceSorter.java"
    desc: "bounded batch与最多32路external merge grouping"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportDataWriter.java"
    desc: "Tree Reactor Schema v1投影、range index、shard与multi-version汇总"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportManifest.java"
    desc: "Module/dependency/scope catalog与shard descriptor"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "Repository Index、Reactor shell与atomic publish"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportSession.java"
    desc: "Incremental page/Index checkpoint生命周期"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/offline/OfflineShardWriter.java"
    desc: "Impact/Tree共享的file-local callback shard writer"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/report-common.js"
    desc: "可配置callback loader、range规范化与求交"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/tree/tree-report.js"
    desc: "Tree筛选、分页、Module切换、懒加载与Retry入口"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/tree/TreeReportBrowserFixtureIT.java"
    desc: "101个Module与2,626行dependency的浏览器夹具"
  - path: "tests/report-ui/tree-dependencies.spec.ts"
    desc: "Tree全量依赖、combobox、stale request与Retry浏览器合同"
  - path: "tests/report-ui/tree-class-conflicts.spec.ts"
    desc: "冲突类分页、Module切换与源码懒加载浏览器合同"
---

# Feature: Repository Dependency Tree Report

## Summary

`tree`从current checkout或local ref中的单个入口POM解析`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE` scope。一次Maven session依次执行`compile`、verbose dependency tree和Classpath Evidence collection。Report为可直接通过`file://`打开的离线目录；Reactor HTML只内嵌轻量catalog与Tree Reactor Report Data Schema v1 manifest，dependency occurrence、冲突、源码和dependency tree按需从相邻`<reactor-base>-data/`加载。

“跨模块依赖分析”逐行展示Reactor范围内全部selected与omitted occurrence，不去重。Dependency与Module使用带展开标志的可输入单选combobox，并在宽屏保持紧凑双行布局；Dependency候选的红色数字徽标表示整个Reactor中selected occurrence的distinct非空resolved version数量。原“跨模块依赖冲突”由该徽标及`Multi-version dependencies`汇总取代。

## Design Decisions

- `--path`必须直接包含readable `pom.xml`；入口aggregator只分析active subtree，owned leaf从最外层匹配祖先执行`-pl/-am`，无owner时按standalone执行。
- 默认scope为`compile,runtime,provided,system`；`test`仅在显式`--scopes`中纳入。Scope同时约束dependency tree、version mediation与class conflict扫描。
- Maven conflict key固定为`groupId + artifactId + type + classifier`。每个occurrence保留requested/effective/selected version、effective/managed scope、selection、omitted reason、完整path和reactor module标记。
- Module内部多版本问题仍由实际dependency path与Maven明确给出的dependency management evidence形成；相同版本的`omitted for duplicate`不独立形成问题。
- Reactor级multi-version判定只看selected occurrence：同一DependencyKey的distinct非空`selectedVersion`数量大于1。Reactor计数为满足条件的DependencyKey数量；Module计数为该Module中出现并selected的对应DependencyKey数量。
- 全量依赖表不以“问题”为边界。selected与omitted occurrence均保留一行，因此可直接核对requested/resolved version与完整chain。
- 冲突类边界是单个Module的effective classpath。全部候选反编译成功时比较规范化换行后的完整文本；任一候选证据不完整时回退SHA-256。Winner固定为`PROJECT > REACTOR_DEPENDENCY > DEPENDENCY`，同层按Maven classpath顺序。
- 主HTML不创建隐藏的全量row、Module panel或源码DOM。每张表只创建当前页；Module切换直接替换唯一活动panel并释放上一Module重payload。
- 全量依赖筛选区使用独立响应式布局：宽屏为有最大列宽的双行表单，中屏将检索独占一行，小屏按单列排列。筛选控件不随宽表或viewport无限拉伸。
- 普通shard UTF-8目标上限为4 MiB，单条超限record独占shard。Dependency row按最小页大小和Module边界切分；dependency index按较大批次渐进扫描；每个Module dependency tree独占shard。
- 所有动态内容通过`textContent`、`DocumentFragment`和DOM API构造，不把Report数据解释为HTML。
- Impact与Tree共享`OfflineShardWriter`及可配置callback loader；Impact继续使用Schema 5、原文件名和`window.__CIA_AFFECTED_PATH_SHARD__`，Tree使用Schema v1和`window.__CIA_TREE_REPORT_SHARD__`。
- 命令cache仍位于UUID command-owned目录，不进入最终artifact；Reactor page和Index checkpoint成功后释放完整Reactor结果。

## Actors / Entrypoints

- 用户执行`dependency-analyzer tree [-p|--path <dir>] [-r|--ref <local-ref>] -o|--output <dir> ...`。
- CI归档`<output>/index.html`和`<output>/dependency-report/`。
- 用户从Repository Index进入Reactor page，再通过全量依赖表或Module card定位证据。

## Behavior Contract

### Repository Index与metadata

- Index展示Metadata、Preflight、Summary与已发布Reactor链接。
- Summary和Reactors表统计Module、Dependency occurrence、Internal conflicts、`Multi-version dependencies`、Class conflicts与High-risk class conflicts。
- Reactor metadata和Module metadata使用相同命名；不展示内部Module role或纳入原因。
- Reactor/module failure与降级证据进入“问题”表；无issue时不生成该section。

### 跨模块依赖分析

- 固定列为`Dependency`、`Scope`、`Module`、`Dependency chain`、`Original version`、`Resolved version`。
- `Dependency`为完整DependencyKey；`Module`为完整coordinate；`Dependency chain`使用` → `连接完整path。
- `Original version`取`requestedVersion`；`Resolved version`取`selectedVersion`；空值显示明确占位。
- Dependency、Module与Scope为精确筛选；全字段检索在六列间执行大小写不敏感OR匹配；四类条件按AND组合。
- Dependency与Module combobox支持输入过滤、Enter、Escape、Arrow Up/Down、Home和End。输入框右侧展开按钮支持鼠标及Enter/Space，chevron方向、按钮可读名称和`aria-expanded`与候选列表同步。焦点在输入框与展开按钮间移动时不关闭列表。清空可选筛选恢复“全部”。每次最多渲染前50个候选，并提示剩余候选需继续输入。
- 宽屏第一行依次为检索、Dependency和Module，第二行为Scope、每页及操作按钮；中屏和小屏按可用宽度重排。document不因筛选控件或六列表格产生横向滚动。
- 每个Dependency候选均显示红色resolved-version数量徽标，包括值`1`；徽标同时提供`N resolved versions`可读文本，不只依赖颜色。
- 表格支持六列排序及10/50/100分页。默认无搜索和自定义排序时不加载dependency index；搜索或排序逐片扫描index、显示进度并用generation token忽略过期请求。
- Dependency、Module与Scope精确筛选从预计算row ranges求交，不先加载全量row payload。

### Module分析card

- Module selector与其全部分析内容处于同一卡片。初始选择第一个Module；无Module时显示明确空状态。
- selector为带展开按钮的可搜索单选combobox，使用完整coordinate；同样限制前50个候选、同步展开状态并支持完整键盘操作。宽屏下selector保持有限宽度。
- 唯一活动panel依次展示coordinate、Module failure、冲突类、模块内部依赖冲突和Dependency tree。
- 冲突类与内部冲突只渲染当前页，支持检索、精确filter、排序和10/50/100分页。
- Module切换保存筛选、排序与分页等轻量状态；切回时恢复。展开源码及已加载重payload不跨Module保留。
- 反编译源码只在点击“查看反编译代码”后加载，同一时间最多展开一项。Winner/Shadowed按钮使用`aria-pressed`单选状态，源码Unavailable不改变finding或status。
- Dependency tree按Module加载为单条文本record，只创建一个`<pre>`文本节点；空tree与Module failure使用不同状态。

### 离线失败与安全

- shard缺失、损坏或Schema不匹配时显示Retry，不将load failure伪装为零结果；已成功表格DOM保持可用。
- JavaScript禁用时metadata与汇总仍可读，动态区域显示`noscript`说明。
- 页面不使用backend、network request、CDN、remote font或浏览器持久化存储。
- 桌面和小屏幕的document不产生横向溢出；宽表只在自身`.table-scroll`容器内滚动。

## Dependency Management与内部冲突语义

`dependencyManagement`只通过实际进入resolved tree的occurrence参与分析；工具不枚举完整`dependencyManagement`、imported Bill of Materials（BOM，物料清单）、未使用managed entry或Plugin dependency tree。

Maven verbose annotation中的`version managed from X`使`requestedVersion=X`，node version成为effective version；selected occurrence以effective version作为selected version，omitted conflict从`omitted for conflict with ...`取得selected version。`managedFrom`不表示定义来源POM或BOM。

单个Module内，每个occurrence产生`DEPENDENCY_PATH`版本来源；存在managed version时再产生`DEPENDENCY_MANAGEMENT`来源。全部来源至少有两个distinct version才形成Internal conflict。Management evidence没有来源chain，Evidence中保持空cell，不伪造POM/BOM位置。

## Core Flow

1. `TreeExecutionEngine`完成Command Preflight、snapshot、Maven runtime与入口scope准备；失败不改动旧Report。
2. 每个Reactor执行一次Maven collection，逐行解析DependencyOccurrence并收集Classpath Evidence。
3. Module version与class conflict分析完成后，`TreeReportDataWriter`投影catalog、ranges、rows、conflicts、sources与trees。
4. 共享writer在staging目录写script-safe callback shards；`TreeReportRenderer`写轻量HTML shell与manifest。
5. Reactor data directory和page完整发布后，Index使用轻量`ReactorReportSummary`刷新checkpoint。
6. Scope完成后写`SUCCESS`或`COMPLETED_WITH_ISSUES`；pipeline/report failure写`FAILED`并保留此前完整page。

## Acceptance Criteria

### Functional

- Given同一Reactor含selected与omitted occurrence；When生成Report；Then每个occurrence各占一行，六列准确投影requested/resolved version、Scope、Module和完整chain。
- Given同一DependencyKey的selected occurrence解析到两个非空版本；When生成catalog；Then徽标为`2`，Reactor与相关Module的`Multi-version dependencies`各按DependencyKey去重计数。
- Given只有一个resolved version；When打开Dependency combobox；Then仍显示红色`1`徽标及`1 resolved versions`可读文本。
- GivenDependency或Module候选超过50；When聚焦combobox；ThenDOM最多50个option并提示继续输入；键盘可筛选、选中、关闭和清空。
- Given任一自定义combobox处于关闭状态；When用户点击展开按钮或聚焦后使用键盘；Then候选列表打开，chevron、可读名称及输入框和按钮的`aria-expanded`同步；再次触发按钮时列表关闭。
- Given报告位于1920px、1280px或390px viewport；When打开跨模块依赖分析；Then筛选区分别保持有界双行、响应式重排或单列布局，document无横向溢出且宽表只在自身容器滚动。
- Given同时设置Dependency、Module、Scope和全字段检索；When提交；Then精确range取交集，六字段检索取并集后再按AND组合。
- Given2,500+ dependency rows与100+ Modules；When首屏；Thendependency、class conflict和internal conflict表各自DOM不超过当前页，未选择Module的tree shard不加载。
- Given快速连续切换Module；When旧请求晚于新请求完成；Then页面只展示最后选择Module的全部内容。
- Given切换后返回原Module；When重新渲染；Then轻量筛选/分页恢复，源码展开状态不恢复。
- Given源码或row shard缺失、损坏或Schema不匹配；When加载；Then显示Retry；恢复文件后Retry成功且Report数据不作为HTML执行。
- Given没有Module、没有dependency、Module failure或JavaScript禁用；When打开Report；Then分别显示明确状态。
- Given同版本duplicate occurrence；When分析Internal conflict；Thentree保留duplicate annotation但不生成新问题。
- Givenoutput root含其他文件；When重复生成；Then只替换工具拥有的Index与dependency-report目录。

### Non-Functional

- [ ] Reactor、Module、Dependency、range、filename和link deterministic。
- [ ] 每个普通shard不超过4 MiB；单record超限时独占且descriptor准确。
- [ ] Report在无backend、无network的`file://`环境完成筛选、分页、Module切换、源码加载与Retry。
- [ ] 所有动态数据使用DOM文本API；无脚本注入，状态不只依赖颜色。
- [ ] 桌面与小屏幕无document级横向溢出，表格保留独立横向滚动。
- [ ] Impact Schema 5、文件名和callback行为保持不变。

## Edge Cases

- Path不存在、不是directory、无法解析Git root、未直接包含POM或目标ref缺少相同路径时为command-level block；旧Report保留。
- Missing active module、module逃逸Git root、active graph cycle、重复或不可解析coordinate为preparation failure；不回退repository扫描。
- Maven部分Module failure时已解析Module保留，Reactor为`FAILED`；反编译不可用只显示`Unavailable`。
- 只能由`test-compile`或`package`产生的Reactor classifier不读取旧产物；Classpath Evidence记录不完整原因并将Reactor标记为`DEGRADED`。
- 单条dependency tree或源码record超过4 MiB时独占shard，不截断内容。

## Implementation Boundaries

- `tree`CLI、DependencyOccurrence domain、scope、exit code与incremental publication contract不因浏览器Schema改变。
- Tree Reactor Report Data Schema v1只用于新生成Report；旧自包含HTML无需迁移。
- Renderer不执行Git、Maven或dependency analysis；browser不回调Analyzer。
- Java tests负责Schema、projection、range、escaping与publication合同；Playwright负责真实`file://`事件、加载时序、ARIA和布局合同。
