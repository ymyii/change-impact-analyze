---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "冻结结果、ReportCache 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Root Impact Path、Structural Reference Path 与 affected methods"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "Console-only Diagnostic 与显式 topology JSON 边界"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "Tree Reactor Schema v2、全量依赖与Module按需渲染"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "离线HTML的Maven静态门禁与Playwright浏览器门禁"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "Overall、Module、Affected Paths 与Schema 5 manifest"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/affected-paths.js"
    desc: "Impact/Structural path分片加载、全局搜索、分页与Java diff展开"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/AffectedPathReportDataWriter.java"
    desc: "Schema 5 relation/range projection、4 MiB分片与manifest生成"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/offline/OfflineShardWriter.java"
    desc: "Impact与Tree共享的4 MiB callback shard writer和descriptor"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/report-common.js"
    desc: "可配置callback loader、range规范化与求交"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportDataWriter.java"
    desc: "Tree Reactor Schema v2、catalog/range与payload shard投影"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/tree/tree-report.js"
    desc: "Tree全量依赖和唯一活动Module的动态浏览器入口"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/changed-members.js"
    desc: "全部changed member指标排序、筛选与分页"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Impact/Structural-only concurrent code comparison选择与去重"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/SsaComparisonEvidence.java"
    desc: "ChangePoint收集期SSA审计证据"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/DecompileComparisonSummary.java"
    desc: "不含源码的decompiled Java三态与抑制原因"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/ImpactPathPruningSummary.java"
    desc: "固定CHA extension状态、指标与bounded evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "decompiled Java unified diff生成"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/HtmlReportUsabilityVerifier.java"
    desc: "最终artifact导航、Schema、数据表与离线资源门禁"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/report/ReportBrowserFixtureIT.java"
    desc: "为真实浏览器门禁发布确定性Schema 5离线Report"
  - path: "playwright.config.ts"
    desc: "Chromium桌面与小屏幕测试project、artifact和失败诊断配置"
  - path: "tests/report-ui/affected-paths.spec.ts"
    desc: "Affected Paths搜索、筛选、懒加载与shard恢复浏览器合同"
  - path: "tests/report-ui/module-summary.spec.ts"
    desc: "Changed members分页、AND筛选与Java diff浏览器合同"
---

# Feature: Report Generator

## Summary

`impact`生成英文offline HTML：一个Overall Index，以及每个非`SKIPPED` Module的Module Index和Affected Paths。`tree`生成Repository Index与按Reactor拆分的中文离线页面。两种Report只消费detached immutable result，并共享file-local callback shard writer、descriptor和可配置loader。

Affected Paths只展示Impact与Structural记录。一行是唯一`(impactPath, changedMember)`关系；path、member、method、dependency upgrade与code diff按确定性整数ID规范化，避免`rows × payload`重复。Impact主HTML继续使用Schema 5 manifest、原文件名和`window.__CIA_AFFECTED_PATH_SHARD__`。Tree Reactor主HTML使用独立Schema v2 manifest与`window.__CIA_TREE_REPORT_SHARD__`。两者均按需加载本地JavaScript分片并只保留、渲染当前页重数据。

## Design Decisions

- Report交付边界是可直接通过`file://`打开的完整离线目录；真实浏览器验收不得用HTTP server改变origin、加载或路径语义。
- `OfflineShardWriter`统一script-safe JSON callback、descriptor、UTF-8字节上限、单record超限和确定性文件名。调用方配置Schema、callback、record cap与逻辑边界；Impact Schema 5的文件名和callback行为保持不变。
- `report-common.js`的loader以callback name参数隔离Impact与Tree；每个payload验证Schema、kind、shard ID、record数量与连续ID，失败后允许Retry。
- Maven静态/Schema门禁与Playwright浏览器门禁独立必跑。`mvn clean verify`发布确定性Report夹具，Playwright只消费该夹具，不在Node.js流程中重复执行分析。
- 浏览器门禁固定使用Chromium桌面与小屏幕viewports，以语义、可访问性、计算样式和几何断言作为稳定合同；不维护像素截图baseline。

## Actors / Entrypoints

- `impact`用户从Overall打开Module Index，再进入Affected Paths进行搜索、筛选、分页和Java diff审查。
- Java Report tests验证projection、Schema、escaping与静态HTML合同；Playwright验证`file://`下的真实DOM事件、异步shard和响应式布局。

## Core Flow

1. Pipeline冻结每个Module的changed members、Impact/Structural paths和关联code comparison。
2. Renderer发布Overall、Module Index、Affected Paths与Schema 5本地shards。
3. 浏览器初始页只加载当前页需要的实体，搜索与精确筛选按需加载轻量catalog。
4. 用户提交Scope、搜索或筛选后，页面合并并相交ranges，再替换当前页DOM。
5. 用户展开Java diff时才加载diff shard；失败保留最近一次成功DOM并提供Retry。

## 页面契约

### Overall Index

- 保留status、mode、runtime、configured/actual workers、dependency scope、Impact/Structural汇总、affected methods、duplicate、shadowed、Preflight与coverage信息。
- 显示去重后的decompiled Java identical/different/unknown，以及SSA matched/different/unknown/skipped总数和两类逐方法ChangePoint collection evidence；即使全部effective ChangePoint被抑制、Module因此`SKIPPED_NO_RELEVANT_CHANGE`，无源码证据仍可在Overall审计。
- Technical details固定显示`Decompiled Java first; normalized SSA on miss`的求值顺序，以及`cha-local-receiver-inference`的status与checked/pruned/unknown edge计数；不显示已删除的adjacent extension或result refinement selection。CHA页面同时声明裁剪只使用caller-local receiver facts，跨方法receiver flow可能保留保守路径且不改变Module status。
- 固定声明CHA不把JDK声明的virtual/interface dispatch扩展到非JDK实现，可能漏报callback、Service Provider Interface（SPI，服务提供者接口）、lambda、collection implementation及应用`Thread`/`Runnable`链；该范围不改变Module status。
- 不包含Diagnostics section或Diagnostics目录入口。

### Module Index

- Summary保留Module级Impact与Structural总数。
- Changed members表包含本Module全部effective changed members，包括Impact和Structural均为`0`的member。
- 列固定为Changed dependency、`ChangePointKind`、Changed member/class、Impact、Structural、Impact total。
- `Impact total = Impact + Structural`。
- 每行按该行`BoundChangePoint`独立过滤Impact与Structural path；不得复用Module aggregate，因此不同changed member可以显示不同计数。
- 默认排序依次为Impact total、Impact、Structural降序，再按member stable ID升序。
- Technical details展示本Module关联的SSA/decompiled Java evidence、固定extension指标与JDK声明分派裁剪计数；不把evidence附着到path，也不展示decompiled源码。
- 只有一个大小写不敏感的搜索框，对Changed dependency显示文本、target `groupId:artifactId`与完整changed member JVM签名执行子串匹配。
- Dependency scope使用独立Include/Exclude Glob表单和`Apply scope`；不与搜索、普通筛选或分页按钮混排。Glob输入以逗号分隔，忽略首尾空白和空项；exclude优先，浏览器只能缩小CLI已分析的数据。
- 支持target `groupId:artifactId`、完整changed member JVM签名和Impact chain精确单选筛选；Impact chain值为All、`Impact total > 0`与`Impact total = 0`。保留精确`ChangePointKind`筛选和20/50/100分页；Scope、搜索和全部筛选使用AND组合，默认20。
- 新增Code diff列。只对`Impact total > 0`的member复用pipeline已生成的comparison并按需在当前行下展开；零链路member固定显示`Not generated — no impact path`，不扩大code comparison范围。同一时间最多展开一个diff。
- Coverage limitations、Module status/reason、Preflight、dependency boundary、duplicate resolution及其他非Path/Diff Technical details继续保留。
- 不包含Diagnostics section或Diagnostic event内容。

### Affected Paths

- `View type`只提供Impact、Structural、All；默认Impact。
- 表列固定为Type、Impact、Affected application methods、Changed dependency、`ChangePointKind`、Changed member/class、Impact path、Code diff。
- Affected application methods从完整Root Impact Path中的全部PROJECT `MethodId`生成，按路径顺序去重。
- 方法固定显示为`dotted.owner#name(JVM descriptor)`；field显示为`dotted.owner#name:JVM descriptor`；class显示完整dotted binary name。Descriptor变化同时显示完整old/new签名。
- 删除Details列、path/member Technical details与evidence drawer。
- Structural记录只展示可读structural path、changed member和精确`ChangePointKind`，不展示raw evidence。
- Impact Path将method sequence与末端`Changed member`作为节点数组安全渲染；每两个节点换行，换行前保留`→`。Structural Path不强制两节点分组，但允许超长class、method与descriptor软换行。
- Code diff只展开decompiled Java unified diff。进入Report的retained method body不会是Java text identical；缓存反编译不可用只显示`Unavailable`，不暴露failure reason。
- 只有一个全局搜索框，对Affected application methods、Changed dependency、Changed member/class和Impact path执行大小写不敏感子串匹配；任一列命中即形成search range。
- 支持target `groupId:artifactId`、完整changed member JVM签名与单个Affected application method完整签名的可输入精确单选筛选。清空表示All；非候选完整值报错并保留最近一次成功结果。一行的Affected application methods以数组存储，选择数组任一值均命中该行。
- Dependency scope独立提交；Scope、全局搜索、三个精确筛选和View type按AND组合。

## 规范化分片数据

Impact与Tree共用分片传输机制但不共用业务Schema。共享层不理解path、dependency、Module或class source，只负责callback文件、record边界、descriptor与安全序列化。

Affected Paths使用以下关系型Schema，所有entity按stable key排序后分配整数ID：

- `index(pathId, type, searchText, affectedMethods, rowStart, rowCount)`：完整path搜索文本、结构化affected method值与row range定位。
- `rows(rowId, pathId, changedMemberId)`：唯一path-member关系，只含ID和外键。
- `dependencies(id, oldArtifact, newArtifact, scope, source)`：每个dependency upgrade一次；`source`是target `groupId:artifactId`。
- `members(id, dependencyUpgradeId, changePointKind, signature, codeDiffStatus, codeDiffId, rowRanges)`：每个effective changed member一次；无path member的`rowRanges`为空。
- `methods(id, label, project)`：每个可展示`MethodId`一次。
- `paths(id, type, classification, rootKind, cycle, applicationMember, relation, changedClass, methodIds)`：统一Impact与Structural path entity；`methodIds`保持path顺序。
- `diffs(id, unifiedDiff)`：只保存可展开的Java unified diff，每个可用changed member最多一次。

Schema 5 manifest内嵌于`*-impact.html`，保存Impact/Structural/All row range、按字典序排列的changed-member source catalog和分片descriptor。`source-index(id, rowStart, rowCount)`把每个source映射到连续row ranges；member自身保存其relation row ranges。相邻`<module-base>-impact-data/`按index、source-index、rows、paths、methods、members、dependencies、diffs分类。每个普通分片的UTF-8目标上限为4 MiB，单记录超过上限时独占分片。分片通过预注册callback与本地`script src`加载，兼容直接`file://`打开，不依赖`fetch`、backend或network。Module Index独立内嵌带显式`source`、完整签名与diff ID的`dependencyUpgrades`、`changedMembers`及`memberMetrics(memberId, impact, structural)`；diff payload仍只位于共享diff shard。SSA与decompiled Java compact evidence位于HTML technical table及显式diagnostics JSON，不进入浏览器path relation数据。

Tree Reactor Schema v2在相邻`<reactor-base>-data/`保存dependency ranges/index/rows、Module Dependency catalog、class conflicts、class sources与dependency trees。Module内dependency表复用全局row/index并以Module range约束；Internal conflicts只保留metadata汇总，不生成专用payload shard。其业务合同、页面交互和性能边界由[Repository Dependency Tree Report](repository-dependency-tree-report.md)定义。共享writer不改变Impact Schema 5的字段、文件前缀、callback或浏览器行为。

`PerModuleHtmlReportGenerator`显式接收`DiagnosticLog`。每个Module在DEBUG输出row/path、各kind shard数量、总shard数量、总字节、最大shard和oversized数量；TRACE为每个shard输出kind、`current/total` progress、record数量和UTF-8字节数。Report `publish` Stage继续负责整体started/completed/failed。

Affected Paths分片禁止保存exact WALA Context、graph node ID、terminal mechanism/location/detail、descriptor/hash/access、SSA reason、observation、ASM text、decompiled source或raw comparison reason。ChangePoint collection分阶段证据的descriptor/hash/status/reason只在专用审计表和显式Schema 13 diagnostics中展示。Manifest与shard payload通过Jackson script-safe escaping写入，manifest解析后删除data script节点。

## Code Comparison

- 全部Module Impact Query完成并snapshot后，只为Impact或Structural path关联的changed member创建comparison request。
- comparison提交前断言changed member target source仍位于CLI `DependencyArtifactSelection`内，防止未选来源越过pipeline边界。
- 无路径member不生成Code comparison；method body反编译已在ChangePoint收集期完成并缓存。
- 同一member被多个path引用时只执行、保存一次。
- comparison通过唯一command-wide `common`pool并发执行；滚动提交上限为`--analysis-parallelism`，并在front preparation、JAR diff和Impact Query之后复用同一pool。
- Retained `METHOD_BODY_CHANGED`从command cache生成`AVAILABLE`或`UNAVAILABLE`，不再次调用Vineflower；其他kind沿用按需Code comparison。
- 不生成ASM fallback；failure reason只进入Console。

## 前端与样式

- Dependency Include/Exclude是独立Scope draft，只有`Apply scope`或Enter提交；全局搜索只有Search或Enter提交。输入期间不加载或扫描index shard。提交使用generation token取消过期查询；非法Glob或非候选精确值内联报错并保留最近一次成功DOM。
- 空搜索与筛选直接使用manifest all range；dependency Scope/筛选从source catalog加载`source-index` ranges；member搜索/筛选使用延迟加载的member签名与`rowRanges`；affected method筛选和path搜索逐片扫描轻量index。搜索产生的三类range取并集，再与Scope和各精确筛选range求交，保存为与View type无关的基础结果。
- Member与affected method候选catalog只在首次聚焦相应可输入单选控件或提交需要它的搜索时加载；候选去重、稳定排序。Impact、Structural、All、分页与Rows per page只在基础结果上本地计算。
- 当前页先加载rows，再按ID加载所需path、method、member与dependency分片；切页或筛选后释放旧payload。Java diff只在展开一行时加载对应diff分片，同一时间最多保留一个展开diff。
- 分片缺失、损坏或Schema不匹配时显示包含文件名的retry error，保留最近一次成功DOM，不把load failure展示成零结果。
- 翻页、搜索、筛选均通过`DocumentFragment`和`replaceChildren`替换`tbody`，旧DOM不保留。
- Java diff展开时只增加当前行的diff DOM；切换分页或筛选即清理。
- 全报告使用CSS variables、轻量card、圆角横向滚动容器、sticky header、列分隔、紧凑行高、zebra stripe、hover highlight和统一focus ring。
- `.layout`使用完整viewport宽度、响应式水平padding与有界gap；桌面目录栏稳定为240px，主内容以`minmax(0,1fr)`占满余量，不设置`1440px`上限。800px以下目录堆叠，表格继续由`.table-scroll`隔离横向溢出。
- `.path-sequence`使用有界首选宽度、`max-inline-size`、`white-space:normal`与`overflow-wrap:anywhere`；Impact节点通过`createTextNode`与`br`安全构建，不使用`innerHTML`。表格仍保留横向滚动。
- 数字列右对齐并使用tabular numerals；method/member/path使用等宽字体；状态和`ChangePointKind`使用带文本的高对比度badge。
- `.table-action`固定horizontal writing mode、nowrap与最小宽度；`View Java diff`和`Hide Java diff`不得被压缩为竖列。
- 小屏幕的长标题与breadcrumb使用`overflow-wrap:anywhere`留在viewport内，表格横向溢出只由`.table-scroll`承接；`aria-live`播报结果，表头和控件保持键盘可访问；状态不只依赖颜色。

## Publication与验证

- Java Renderer通过UTF-8 `Writer`写入同filesystem staging，再原子替换command-owned output。
- 页面不使用CDN、网络请求、外部asset或浏览器持久化存储。
- `HtmlReportUsabilityVerifier`递归验证页面导航、本地资源、table ID、JSON Schema、`noscript`、sticky header、wrapper、badge与focus CSS。
- `ReportBrowserFixtureIT`与`TreeReportBrowserFixtureIT`在`mvn clean verify`中把确定性真实Report发布到`target/playwright-report-fixture/`；该build artifact不进入Analyzer JAR或Git。
- Playwright通过`file://`运行Chromium `1280×800`与`390×844`两个project，验证DOM事件、懒加载、Retry、键盘/ARIA和响应式几何；失败截图、Trace与HTML report只写入`target/playwright/`。
- Dense fixture应验证HTML按unique entities和ID relations增长，2,500+ members首次只渲染20行。

## Acceptance Criteria

### Functional

- Given一条path关联多个changed members；When生成Report；Then path和method sequence只存一次，每个member与diff只存一次，关系表有多行外键。
- Given同一Module有多个changed member且关联path数量不同；When生成Changed members数据；Then每行Impact、Structural与Impact total仅来自该member，不得显示相同Module aggregate。
- Givendecompiled Java或其miss后执行的SSA抑制了全部body ChangePoint；WhenModule因无effective change而跳过；ThenOverall仍展示该pair的方法、class version、hash、已执行比较的status/reason、SSA skipped计数、抑制原因与timing。
- Given Impact Path含1至5个展示节点；When渲染节点数组；Then每两个节点插入换行，行末箭头保留，末节点后不追加箭头或空行。Given Structural Path；Then不强制分组但超长节点可软换行。
- GivenDiagnostics包含敏感或大量event；When生成Report；ThenOverall和Module HTML均不包含Diagnostics标题、目录或event内容。
- GivenJava diff unavailable；When打开Affected Paths；Then只显示`Unavailable`，HTML不包含raw reason或ASM instruction。
- Given超大Affected Paths；When发布Report；Then主HTML只含Schema 5 manifest与source catalog，各普通分片不超过4 MiB，单记录oversize可审计，空查询不预加载member/dependency/path/diff，分页与diff展开只加载当前所需重数据。
- Given非空全局查询；When扫描多个index/member/dependency shard；Then页面显示进度、取消旧查询，并对四个指定列返回大小写不敏感的OR结果。Given本地shard失败；Then展示可重试错误且不伪造空结果。
- Given一行含多个Affected application methods；When选择其中任一完整签名；Then该行进入结果。Given输入非候选精确值；Then保留最近一次成功DOM并显示错误。
- Given共享writer用于Impact；When生成Affected Paths；ThenSchema仍为5，文件名与`window.__CIA_AFFECTED_PATH_SHARD__`不变。Given用于Tree；Then使用独立Schema v2 callback且不能相互接收payload。

### Non-Functional

- [ ] Report在无backend、无network request的`file://`环境完成全部交互。
- [ ] 桌面与小屏幕viewport均无document级横向溢出，表格保留自己的横向滚动。
- [ ] 搜索、Scope、精确筛选、分页、Retry与diff按钮支持键盘、focus ring和ARIA状态。

## Implementation Boundaries

- Production负责生成静态HTML、内嵌manifest、本地shard和共享JavaScript；浏览器不回调Analyzer，也不持久化筛选状态。
- Java tests拥有Schema与projection精确合同；Playwright拥有浏览器事件、加载时序、恢复行为和布局合同，两层不得互相替代。
- Playwright夹具只构造detached report input，不改变`impact` CLI参数、分析语义或Schema版本。
