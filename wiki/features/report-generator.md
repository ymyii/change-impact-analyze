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
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "Overall、Module、Affected Paths 与Schema 4 manifest"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/affected-paths.js"
    desc: "Impact/Structural path分片加载、全局搜索、分页与Java diff展开"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/AffectedPathReportDataWriter.java"
    desc: "Schema 4 relation/source-range projection、4 MiB分片与manifest生成"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/changed-members.js"
    desc: "全部changed member指标排序、筛选与分页"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Impact/Structural-only concurrent code comparison选择与去重"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/SsaComparisonEvidence.java"
    desc: "ChangePoint收集期SSA审计证据"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/ImpactPathPruningSummary.java"
    desc: "固定CHA extension状态、指标与bounded evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "decompiled Java unified diff生成"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/HtmlReportUsabilityVerifier.java"
    desc: "最终artifact导航、Schema、数据表与离线资源门禁"
---

# Feature: Report Generator

## Summary

`impact`生成英文offline HTML：一个Overall Index，以及每个非`SKIPPED` Module的Module Index和Affected Paths。Report只消费detached immutable result，不读取live WALA对象、exact Context、graph node ID或terminal evidence。

Affected Paths只展示Impact与Structural记录。一行是唯一`(impactPath, changedMember)`关系；path、member、method、dependency upgrade与code diff按确定性整数ID规范化，避免`rows × payload`重复。主HTML只保存Schema 4 manifest与source catalog，浏览器按需加载本地JavaScript分片并只连接、保留和渲染当前页数据。

## 页面契约

### Overall Index

- 保留status、mode、runtime、configured/actual workers、dependency scope、Impact/Structural汇总、affected methods、duplicate、shadowed、Preflight与coverage信息。
- 显示去重后的SSA matched/different/unknown总数与逐方法ChangePoint collection evidence；即使全部effective ChangePoint被抑制、Module因此`SKIPPED_NO_RELEVANT_CHANGE`，证据仍可在Overall审计。
- Technical details固定显示SSA equivalence enabled状态，以及`cha-local-receiver-inference`的status与checked/pruned/unknown edge计数；不显示已删除的adjacent extension或result refinement selection。CHA页面同时声明裁剪只使用caller-local receiver facts，跨方法receiver flow可能保留保守路径且不改变Module status。
- 固定声明CHA不把JDK声明的virtual/interface dispatch扩展到非JDK实现，可能漏报callback、Service Provider Interface（SPI，服务提供者接口）、lambda、collection implementation及应用`Thread`/`Runnable`链；该范围不改变Module status。
- 不包含Diagnostics section或Diagnostics目录入口。

### Module Index

- Summary保留Module级Impact与Structural总数。
- Changed members表包含本Module全部effective changed members，包括Impact和Structural均为`0`的member。
- 列固定为Changed dependency、`ChangePointKind`、Changed member/class、Impact、Structural、Impact total。
- `Impact total = Impact + Structural`。
- 每行按该行`BoundChangePoint`独立过滤Impact与Structural path；不得复用Module aggregate，因此不同changed member可以显示不同计数。
- 默认排序依次为Impact total、Impact、Structural降序，再按member stable ID升序。
- Technical details展示本Module关联的SSA evidence、固定extension指标与JDK声明分派裁剪计数；不把evidence附着到path。
- 支持dependency/member搜索、changed-member target `groupId:artifactId`的Include/Exclude Glob、精确`ChangePointKind`筛选和20/50/100分页；各条件使用AND组合，默认20。Glob输入以逗号分隔，忽略首尾空白和空项；exclude优先，浏览器只能缩小CLI已分析的数据。
- Coverage limitations、Module status/reason、Preflight、dependency boundary、duplicate resolution及其他非Path/Diff Technical details继续保留。
- 不包含Diagnostics section或Diagnostic event内容。

### Affected Paths

- `View type`只提供Impact、Structural、All；默认Impact。
- 表列固定为Type、Impact、Affected application methods、Changed dependency、`ChangePointKind`、Changed member/class、Impact path、Code diff。
- Affected application methods从完整Root Impact Path中的全部PROJECT `MethodId`生成，按路径顺序去重。
- 删除Details列、path/member Technical details与evidence drawer。
- Structural记录只展示可读structural path、changed member和精确`ChangePointKind`，不展示raw evidence。
- Code diff只展开decompiled Java unified diff。Java text identical或unavailable只显示简短状态，不暴露failure reason。

## 规范化分片数据

Affected Paths使用以下关系型Schema，所有entity按stable key排序后分配整数ID：

- `index(pathId, type, searchText, rowStart, rowCount)`：轻量全局搜索与row range定位。
- `rows(rowId, pathId, changedMemberId)`：唯一path-member关系，只含ID和外键。
- `dependencies(id, oldArtifact, newArtifact, scope, source)`：每个dependency upgrade一次；`source`是target `groupId:artifactId`。
- `members(id, dependencyUpgradeId, changePointKind, owner, name, codeDiffStatus, codeDiffId)`：每个path关联changed member一次。
- `methods(id, label, project)`：每个可展示`MethodId`一次。
- `paths(id, type, classification, rootKind, cycle, applicationMember, relation, changedClass, methodIds)`：统一Impact与Structural path entity；`methodIds`保持path顺序。
- `diffs(id, unifiedDiff)`：只保存可展开的Java unified diff，每个可用changed member最多一次。

Schema 4 manifest内嵌于`*-impact.html`，保存Impact/Structural/All row range、按字典序排列的changed-member source catalog和分片descriptor。`source-index(id, rowStart, rowCount)`把每个source映射到连续row ranges；相邻`<module-base>-impact-data/`按index、source-index、rows、paths、methods、members、dependencies、diffs分类。每个普通分片的UTF-8目标上限为4 MiB，单记录超过上限时独占分片。分片通过预注册callback与本地`script src`加载，兼容直接`file://`打开，不依赖`fetch`、backend或network。Module Index独立内嵌带显式`source`的`dependencyUpgrades`、`changedMembers`与`memberMetrics(memberId, impact, structural)`。SSA evidence位于HTML technical table及显式diagnostics JSON，不进入浏览器path relation数据。

`PerModuleHtmlReportGenerator`显式接收`DiagnosticLog`。每个Module在DEBUG输出row/path、各kind shard数量、总shard数量、总字节、最大shard和oversized数量；TRACE为每个shard输出kind、`current/total` progress、record数量和UTF-8字节数。Report `publish` Stage继续负责整体started/completed/failed。

Affected Paths分片禁止保存exact WALA Context、graph node ID、terminal mechanism/location/detail、descriptor/hash/access、SSA reason、observation、ASM text或raw comparison reason。ChangePoint collection SSA的descriptor/hash/reason只在专用审计表和显式Schema 12 diagnostics中展示。Manifest与shard payload通过Jackson script-safe escaping写入，manifest解析后删除data script节点。

## Code Comparison

- 全部Module Impact Query完成并snapshot后，只为Impact或Structural path关联的changed member创建comparison request。
- comparison提交前断言changed member target source仍位于CLI `DependencyArtifactSelection`内，防止未选来源越过pipeline边界。
- 无路径member不启动decompilation。
- 同一member被多个path引用时只执行、保存一次。
- comparison通过唯一command-wide `common`pool并发执行；滚动提交上限为`--analysis-parallelism`，并在front preparation、JAR diff和Impact Query之后复用同一pool。
- 状态固定为`AVAILABLE`、`JAVA_TEXT_IDENTICAL`、`UNAVAILABLE`。
- 不生成ASM fallback；failure reason只进入Console。

## 前端与样式

- Affected method与dependency Include/Exclude是draft条件；只有Search按钮或任一输入框Enter提交。输入期间不加载或扫描index shard。提交使用generation token取消过期查询；非法Glob内联报错并保留最近一次成功DOM。
- 空affected method根据manifest row range定位；dependency Glob先匹配小型source catalog并只加载允许source的`source-index` ranges，非空affected method再逐片扫描轻量index。两类range求交后保存为与View type无关的基础结果；Impact、Structural、All、分页与Rows per page只在该结果上本地计算，不重新扫描index。
- 当前页先加载rows，再按ID加载所需path、method、member与dependency分片；切页或筛选后释放旧payload。Java diff只在展开一行时加载对应diff分片，同一时间最多保留一个展开diff。
- 分片缺失、损坏或Schema不匹配时显示包含文件名的retry error，保留最近一次成功DOM，不把load failure展示成零结果。
- 翻页、搜索、筛选均通过`DocumentFragment`和`replaceChildren`替换`tbody`，旧DOM不保留。
- Java diff展开时只增加当前行的diff DOM；切换分页或筛选即清理。
- 全报告使用CSS variables、轻量card、圆角横向滚动容器、sticky header、列分隔、紧凑行高、zebra stripe、hover highlight和统一focus ring。
- `.layout`使用完整viewport宽度、响应式水平padding与有界gap；桌面目录栏稳定为240px，主内容以`minmax(0,1fr)`占满余量，不设置`1440px`上限。800px以下目录堆叠，表格继续由`.table-scroll`隔离横向溢出。
- 数字列右对齐并使用tabular numerals；method/member/path使用等宽字体；状态和`ChangePointKind`使用带文本的高对比度badge。
- 小屏幕保持横向滚动；`aria-live`播报结果，表头和控件保持键盘可访问；状态不只依赖颜色。

## Publication与验证

- Java Renderer通过UTF-8 `Writer`写入同filesystem staging，再原子替换command-owned output。
- 页面不使用CDN、网络请求、外部asset或浏览器持久化存储。
- `HtmlReportUsabilityVerifier`递归验证页面导航、本地资源、table ID、JSON Schema、`noscript`、sticky header、wrapper、badge与focus CSS。
- Dense fixture应验证HTML按unique entities和ID relations增长，2,500+ members首次只渲染20行。

## Acceptance Criteria

- Given一条path关联多个changed members；When生成Report；Then path和method sequence只存一次，每个member与diff只存一次，关系表有多行外键。
- Given同一Module有多个changed member且关联path数量不同；When生成Changed members数据；Then每行Impact、Structural与Impact total仅来自该member，不得显示相同Module aggregate。
- GivenSSA `MATCHED`抑制了全部body ChangePoint；WhenModule因无effective change而跳过；ThenOverall仍展示该pair的方法、class version、hash、status、reason与timing。
- GivenDiagnostics包含敏感或大量event；When生成Report；ThenOverall和Module HTML均不包含Diagnostics标题、目录或event内容。
- GivenJava diff unavailable；When打开Affected Paths；Then只显示`Unavailable`，HTML不包含raw reason或ASM instruction。
- Given超大Affected Paths；When发布Report；Then主HTML只含Schema 4 manifest与source catalog，各普通分片不超过4 MiB，单记录oversize可审计，来源筛选不预加载member/dependency/path/diff，分页与diff展开只加载当前所需分片。
- Given非空affected method查询；When扫描多个index shard；Then页面显示进度、取消旧查询并返回与原大小写不敏感子串语义一致的全局结果。Given本地shard失败；Then展示可重试错误且不伪造空结果。
