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
    desc: "Tree Reactor Schema v2投影、range index、shard与版本汇总"
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

`tree`从current checkout或local ref中的单个入口POM解析`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE` scope。一次Maven session依次执行`compile`、verbose dependency tree和Classpath Evidence collection。Report为可直接通过`file://`打开的离线目录；Reactor HTML只内嵌轻量catalog与Tree Reactor Report Data Schema v2 manifest，dependency occurrence、当前Module候选、源码和dependency tree按需从相邻`<reactor-base>-data/`加载。

“跨模块依赖分析”逐行展示Reactor范围内全部selected与omitted occurrence，不去重；“模块内部依赖分析”使用相同occurrence语义并限制在当前Module。Dependency与Module使用带展开标志的可输入单选combobox，并在宽屏保持紧凑双行布局。Dependency候选同时显示selected occurrence的resolved-version数量和全部occurrence的original/resolved unique-version数量；前者继续驱动`Multi-version dependencies`，后者只提供观察范围。

## Design Decisions

- `--path`必须直接包含readable `pom.xml`；入口aggregator只分析active subtree，owned leaf从最外层匹配祖先执行`-pl/-am`，无owner时按standalone执行。
- 默认scope为`compile,runtime,provided,system`；`test`仅在显式`--scopes`中纳入。Scope同时约束dependency tree、version mediation与class conflict扫描。
- Maven conflict key固定为`groupId + artifactId + type + classifier`。每个occurrence保留requested/effective/selected version、effective/managed scope、selection、omitted reason、完整path和reactor module标记。
- Module内部多版本问题仍由实际dependency path与Maven明确给出的dependency management evidence形成；相同版本的`omitted for duplicate`不独立形成问题。
- Reactor级multi-version判定只看selected occurrence：同一DependencyKey的distinct非空`selectedVersion`数量大于1。Reactor计数为满足条件的DependencyKey数量；Module计数为该Module中出现并selected的对应DependencyKey数量。
- `uniqueVersionCount`只合并非空`requestedVersion`与`selectedVersion`；相同字符串去重，intermediate `effectiveVersion`不会因仅出现在Resolution detail而计入，也不改变multi-version判定。
- 全量依赖表不以“问题”为边界。selected与omitted occurrence均保留一行，因此可直接核对requested/resolved version与完整chain。
- Resolution source由occurrence已保存的management与omission evidence确定，展示Direct selection、Dependency management和Maven mediation步骤及版本转换；不推断具体POM/BOM或冲突winner path。
- 冲突类边界是单个Module的effective classpath。全部候选反编译成功时比较规范化换行后的完整文本；任一候选证据不完整时回退SHA-256。Winner固定为`PROJECT > REACTOR_DEPENDENCY > DEPENDENCY`，同层按Maven classpath顺序。
- 主HTML不创建隐藏的全量row、Module panel或源码DOM。每张表只创建当前页；Module切换直接替换唯一活动panel并释放上一Module重payload。
- 全量依赖筛选区使用独立响应式布局：宽屏为有最大列宽的双行表单，中屏将检索独占一行，小屏按单列排列。筛选控件不随宽表或viewport无限拉伸。
- 普通shard UTF-8目标上限为4 MiB，单条超限record独占shard。Dependency row按最小页大小和Module边界切分；dependency index按较大批次渐进扫描；Module Dependency catalog按Module边界切分；每个Module dependency tree独占shard。
- 所有动态内容通过`textContent`、`DocumentFragment`和DOM API构造，不把Report数据解释为HTML。
- Impact与Tree共享`OfflineShardWriter`及可配置callback loader；Impact继续使用Schema 5、原文件名和`window.__CIA_AFFECTED_PATH_SHARD__`，Tree使用Schema v2和`window.__CIA_TREE_REPORT_SHARD__`。
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

- 固定列为`Dependency`、`Scope`、`Module`、`Dependency chain`、`Original version`、`Resolved version`、`Resolution source`。
- `Dependency`为完整DependencyKey；`Module`为完整coordinate；`Dependency chain`使用` → `连接完整path。
- `Original version`取`requestedVersion`；`Resolved version`取`selectedVersion`；`Resolution source`展示来源分类和版本转换详情；空值显示明确占位。
- Dependency、Module与Scope为精确筛选；全字段检索在七列间执行大小写不敏感OR匹配；四类条件按AND组合。
- Dependency与Module combobox支持输入过滤、Enter、Escape、Arrow Up/Down、Home和End。输入框右侧展开按钮支持鼠标及Enter/Space，chevron方向、按钮可读名称和`aria-expanded`与候选列表同步。焦点在输入框与展开按钮间移动时不关闭列表。清空可选筛选恢复“全部”。每次最多渲染前50个候选，并提示剩余候选需继续输入。
- 宽屏第一行依次为检索、Dependency和Module，第二行为Scope、每页及操作按钮；中屏和小屏按可用宽度重排。document不因筛选控件或七列表格产生横向滚动。
- 每个Dependency候选均显示两个徽标：红色数字为Reactor内selected occurrence的distinct非空resolved version数量；中性色`N unique`为全部selected与omitted occurrence的original/resolved非空版本并集。两个徽标包括值`0`与`1`并提供可读文本，不只依赖颜色。
- 表格支持七列排序及10/50/100分页。默认无搜索和自定义排序时不加载dependency index；搜索或排序逐片扫描index、显示进度并用generation token忽略过期请求。
- Dependency、Module与Scope精确筛选从预计算row ranges求交，不先加载全量row payload。

### Module分析card

- Module selector与其全部分析内容处于同一卡片。初始选择第一个Module；无Module时显示明确空状态。
- selector为带展开按钮的可搜索单选combobox，使用完整coordinate；同样限制前50个候选、同步展开状态并支持完整键盘操作。宽屏下selector保持有限宽度。
- 唯一活动panel依次展示coordinate、Module failure、冲突类、模块内部依赖分析和Dependency tree。
- 模块内部依赖分析展示当前Module的全部selected与omitted occurrence，固定列与跨模块表一致但移除`Module`。Dependency combobox只列出当前Module实际出现的DependencyKey，并显示Module范围的resolved/unique双徽标。
- 冲突类与模块内部依赖分析只渲染当前页；后者支持Dependency、Scope、六字段检索、六列排序、清空和10/50/100分页，精确筛选使用全局range与当前Module dependency range求交。
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

Resolution source将这些字段投影为稳定步骤：无management的selected occurrence为`Direct selection`；managed occurrence增加`Dependency management`；conflict、duplicate、cycle和其他omission分别增加对应Maven mediation步骤。同一occurrence的步骤按顺序组合并展示requested、effective与selected转换，但不构造缺失的定义位置或winner chain。

单个Module内，每个occurrence产生`DEPENDENCY_PATH`版本来源；存在managed version时再产生`DEPENDENCY_MANAGEMENT`来源。全部来源至少有两个distinct version才形成Internal conflict。Management evidence没有来源chain，Evidence中保持空cell，不伪造POM/BOM位置。

## Core Flow

1. `TreeExecutionEngine`完成Command Preflight、snapshot、Maven runtime与入口scope准备；失败不改动旧Report。
2. 每个Reactor执行一次Maven collection，逐行解析DependencyOccurrence并收集Classpath Evidence。
3. Module version与class conflict分析完成后，`TreeReportDataWriter`投影Reactor/Module catalog、ranges、occurrence rows、sources与trees；Internal conflict只保留汇总计数，不生成专用展示row。
4. 共享writer在staging目录写script-safe callback shards；`TreeReportRenderer`写轻量HTML shell与manifest。
5. Reactor data directory和page完整发布后，Index使用轻量`ReactorReportSummary`刷新checkpoint。
6. Scope完成后写`SUCCESS`或`COMPLETED_WITH_ISSUES`；pipeline/report failure写`FAILED`并保留此前完整page。

## Acceptance Criteria

### Functional

- Given同一Reactor含selected与omitted occurrence；When生成Report；Then每个occurrence各占一行，七列准确投影requested/resolved version、Scope、Module、完整chain和Resolution source。
- Given同一DependencyKey的selected occurrence解析到两个非空版本；When生成catalog；Then徽标为`2`，Reactor与相关Module的`Multi-version dependencies`各按DependencyKey去重计数。
- Given同一DependencyKey的original/resolved非空版本并集为三个值；When打开Dependency combobox；Then红色徽标保持selected resolved数量，中性色徽标显示`3 unique`，且intermediate effective-only值不计入。
- Given只有一个resolved version或没有selected occurrence；When打开Dependency combobox；Then仍分别显示红色`1`或`0`及对应可读文本。
- GivenDependency或Module候选超过50；When聚焦combobox；ThenDOM最多50个option并提示继续输入；键盘可筛选、选中、关闭和清空。
- Given任一自定义combobox处于关闭状态；When用户点击展开按钮或聚焦后使用键盘；Then候选列表打开，chevron、可读名称及输入框和按钮的`aria-expanded`同步；再次触发按钮时列表关闭。
- Given报告位于1920px、1280px或390px viewport；When打开跨模块依赖分析；Then筛选区分别保持有界双行、响应式重排或单列布局，document无横向溢出且宽表只在自身容器滚动。
- Given同时设置Dependency、Module、Scope和全字段检索；When提交；Then精确range取交集，七字段检索取并集后再按AND组合。
- Given当前Module包含selected与omitted occurrence；When打开模块内部依赖分析；Then六列表格展示全部occurrence，Dependency候选及双徽标只按当前Module计算，Internal conflicts metadata仍保持issue计数。
- Given2,500+ dependency rows与100+ Modules；When首屏；Thendependency、class conflict和Module dependency表各自DOM不超过当前页，未选择Module的catalog与tree shard不加载。
- Given快速连续切换Module；When旧请求晚于新请求完成；Then页面只展示最后选择Module的全部内容。
- Given切换后返回原Module；When重新渲染；Then轻量筛选/分页恢复，源码展开状态不恢复。
- GivenModule catalog、源码或row shard缺失、损坏或Schema不匹配；When加载；Then对应组件显示Retry并保留其他成功DOM；恢复文件后Retry成功且Report数据不作为HTML执行。
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
- Tree Reactor Report Data Schema v2只用于新生成Report；旧Schema v1 artifact无需迁移，重新执行`tree`生成完整v2目录。
- Renderer不执行Git、Maven或dependency analysis；browser不回调Analyzer。
- Java tests负责Schema、projection、range、escaping与publication合同；Playwright负责真实`file://`事件、加载时序、ARIA和布局合同。
