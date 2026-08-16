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
    desc: "Overall、Module、Affected Paths 与规范化内嵌数据"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/affected-paths.js"
    desc: "Impact/Structural path筛选、分页与Java diff展开"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/changed-members.js"
    desc: "全部changed member指标排序、筛选与分页"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Impact/Structural-only concurrent code comparison选择与去重"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/bytecode/SsaComparisonEvidence.java"
    desc: "ChangePoint收集期SSA审计证据"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/CodeComparisonBuilder.java"
    desc: "decompiled Java unified diff生成"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/HtmlReportUsabilityVerifier.java"
    desc: "最终artifact导航、Schema、数据表与离线资源门禁"
---

# Feature: Report Generator

## Summary

`impact`生成英文offline HTML：一个Overall Index，以及每个非`SKIPPED` Module的Module Index和Affected Paths。Report只消费detached immutable result，不读取live WALA对象、exact Context、graph node ID或terminal evidence。

Affected Paths只展示Impact与Structural记录。一行是唯一`(impactPath, changedMember)`关系；path、member、method、dependency upgrade与code diff按确定性整数ID规范化，避免`rows × payload`重复。浏览器只连接并渲染当前页数据。

## 页面契约

### Overall Index

- 保留status、mode、runtime、configured/actual workers、dependency scope、Impact/Structural汇总、affected methods、duplicate、shadowed、Preflight与coverage信息。
- 显示去重后的SSA matched/different/unknown总数与逐方法ChangePoint collection evidence；即使全部effective ChangePoint被抑制、Module因此`SKIPPED_NO_RELEVANT_CHANGE`，证据仍可在Overall审计。
- 不包含Diagnostics section或Diagnostics目录入口。

### Module Index

- Summary保留Module级Impact与Structural总数。
- Changed members表包含本Module全部effective changed members，包括Impact和Structural均为`0`的member。
- 列固定为Changed dependency、`ChangePointKind`、Changed member/class、Impact、Structural、Impact total。
- `Impact total = Impact + Structural`。
- 默认排序依次为Impact total、Impact、Structural降序，再按member stable ID升序。
- Technical details展示本Module关联的SSA evidence；不把evidence附着到path。
- 支持dependency/member搜索、精确`ChangePointKind`筛选和20/50/100分页；默认20。
- Coverage limitations、Module status/reason、Preflight、dependency boundary、duplicate resolution及其他非Path/Diff Technical details继续保留。
- 不包含Diagnostics section或Diagnostic event内容。

### Affected Paths

- `View type`只提供Impact、Structural、All；默认Impact。
- 表列固定为Type、Impact、Affected application methods、Changed dependency、`ChangePointKind`、Changed member/class、Impact path、Code diff。
- Affected application methods从完整Root Impact Path中的全部PROJECT `MethodId`生成，按路径顺序去重。
- 删除Details列、path/member Technical details与evidence drawer。
- Structural记录只展示可读structural path、changed member和精确`ChangePointKind`，不展示raw evidence。
- Code diff只展开decompiled Java unified diff。Java text identical或unavailable只显示简短状态，不暴露failure reason。

## 规范化内嵌数据

Affected Paths使用以下关系型Schema，所有entity按stable key排序后分配整数ID：

- `dependencyUpgrades(id, oldArtifact, newArtifact, scope)`：每个升级一次。
- `changedMembers(id, dependencyUpgradeId, changePointKind, owner, name, codeDiffId)`：每个changed member一次。
- `methods(id, label, project)`：每个可展示`MethodId`一次。
- `paths(id, classification, rootKind, cycle)`：Impact call path entity。
- `structuralPaths(id, classification, applicationMember, relation, changedClass)`：独立Structural path entity。
- `pathSteps(pathId, ordinal, methodId)`：method sequence关系。
- `codeDiffs(id, status, unifiedDiff)`：每个changed member最多一次。
- `pathMemberRows(rowId, pathId, changedMemberId)`：唯一path-member关系，只含ID和外键。

Module Index独立内嵌`dependencyUpgrades`、`changedMembers`与`memberMetrics(memberId, impact, structural)`。SSA evidence位于HTML technical table及显式diagnostics JSON，不进入浏览器path relation数据。

Affected Paths内嵌JSON禁止保存exact WALA Context、graph node ID、terminal mechanism/location/detail、descriptor/hash/access、SSA reason、observation、ASM text或raw comparison reason。ChangePoint collection SSA的descriptor/hash/reason只在专用审计表和显式Schema 10 diagnostics中展示。JSON通过Jackson script-safe escaping写入，解析后删除data script节点。

## Code Comparison

- 全部Module Impact Query完成并snapshot后，只为Impact或Structural path关联的changed member创建comparison request。
- 无路径member不启动decompilation。
- 同一member被多个path引用时只执行、保存一次。
- comparison通过唯一command-wide `common`pool并发执行；滚动提交上限为`--analysis-parallelism`，并在front preparation、JAR diff和Impact Query之后复用同一pool。
- 状态固定为`AVAILABLE`、`JAVA_TEXT_IDENTICAL`、`UNAVAILABLE`。
- 不生成ASM fallback；failure reason只进入Console。

## 前端与样式

- Path搜索先计算匹配`pathId`集合，再过滤`pathMemberRows`；不预构造全部joined row object。
- 翻页、搜索、筛选均通过`DocumentFragment`和`replaceChildren`替换`tbody`，旧DOM不保留。
- Java diff展开时只增加当前行的diff DOM；切换分页或筛选即清理。
- 全报告使用CSS variables、轻量card、圆角横向滚动容器、sticky header、列分隔、紧凑行高、zebra stripe、hover highlight和统一focus ring。
- 数字列右对齐并使用tabular numerals；method/member/path使用等宽字体；状态和`ChangePointKind`使用带文本的高对比度badge。
- 小屏幕保持横向滚动；`aria-live`播报结果，表头和控件保持键盘可访问；状态不只依赖颜色。

## Publication与验证

- Java Renderer通过UTF-8 `Writer`写入同filesystem staging，再原子替换command-owned output。
- 页面不使用CDN、网络请求、外部asset或浏览器持久化存储。
- `HtmlReportUsabilityVerifier`递归验证页面导航、本地资源、table ID、JSON Schema、`noscript`、sticky header、wrapper、badge与focus CSS。
- Dense fixture应验证HTML按unique entities和ID relations增长，2,500+ members首次只渲染20行。

## Acceptance Criteria

- Given一条path关联多个changed members；When生成Report；Then path和method sequence只存一次，每个member与diff只存一次，关系表有多行外键。
- GivenSSA `MATCHED`抑制了全部body ChangePoint；WhenModule因无effective change而跳过；ThenOverall仍展示该pair的方法、class version、hash、status、reason与timing。
- GivenDiagnostics包含敏感或大量event；When生成Report；ThenOverall和Module HTML均不包含Diagnostics标题、目录或event内容。
- GivenJava diff unavailable；When打开Affected Paths；Then只显示`Unavailable`，HTML不包含raw reason或ASM instruction。
