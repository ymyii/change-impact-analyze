---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "status、partial result 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Path、Structural Reference Path、SSA 与代码 evidence"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "Overall 与 Module pages 的持续完整性校验"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "impact HTML Index、Module pages 与规范化内嵌数据"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/report/affected-paths.js"
    desc: "Affected Paths 客户端筛选、分页、单例详情与 Diff 着色"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "path-associated code comparison 选择与去重"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AnalysisRunResult.java"
    desc: "run status、selected algorithm、result refinement selection 与 metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisResult.java"
    desc: "Module detail、Impact Paths 与 code comparison evidence"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/HtmlReportUsabilityVerifier.java"
    desc: "最终artifact HTML结构、本地资源与多页面可达性验证"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "shaded JAR生成真实Impact/Tree Report的黑盒gate"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleCallGraphSnapshot.java"
    desc: "不引用 WALA session 的 report-safe Call Graph metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleChangedPathSelection.java"
    desc: "requested/actual scope、fallback 与 dependency paths"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "独立 tree HTML renderer"
---

# Feature: Report Generator

## Summary

`impact`生成英文offline HTML：一个Overall Index，以及每个非`SKIPPED` Module的Module Index和Affected Paths。Java Renderer通过UTF-8 `Writer`流式写入页面；Affected Paths把规范化`members + paths` JSON安全内嵌到页面，浏览器只动态渲染当前页记录和一个当前详情。Production path node与Call Graph metrics在Report前已脱离WALA session。`tree`保持独立的repository/reactor report contract。

## Design Decisions

- Report只投影typed run/module/query结果；Algorithm、WALA ReflectionOptions、access decision与coverage reason不从Diagnostic或summary string反向解析。
- Affected Paths按changed member规范化数据；member metadata与code diff只存一份，多个Final、Equivalent filtered或Structural path通过整数ID引用。
- 分页控制DOM规模，而不是裁剪evidence：完整路径和全部关联member evidence保留在浏览器内存中，当前页最多渲染所选10、20或100条记录。
- 详情使用单例行内`<tr>`并移动到当前记录下方；切换记录会替换旧详情和Diff DOM。
- 无Impact Path的changed member不进入浏览器数据，也不触发code comparison；Overall与Module Index继续保留raw change、duplicate、shadowed等审计汇总。
- 完整HTML只存在于output同filesystem的staging file；页面无CDN、网络请求或外部asset，Affected Paths脚本从JAR resource内联。

## Actors / Entrypoints

- `impact` command在全部Module analysis、可选SSA filtering与path-associated code comparison完成后触发原子HTML publication。
- 用户从Overall Index进入Module Index，再进入Affected Paths搜索、分页和审阅evidence。

## Behavior Contract

- Overall与Module Index展示effective algorithm/JDK model、Result refinement、scope、workers、metrics、coverage limitation、raw change、duplicate与shadowed汇总。
- Affected Paths只有一张表，列为Type、Impact、Affected application method/member、Changed dependency、Changed member/class、Impact path和Details。
- `View type`提供Final、Equivalent filtered、Structural和All；默认Final。All固定按三类顺序拼接，各类内部使用deterministic comparator。
- affected method搜索对完整`package.Class#method`执行忽略大小写substring匹配；直接Structural class/member reference使用application class/member参与匹配。
- 页长提供10、20、100，默认10；First、Previous、当前页、Next、Last不按总页数生成按钮。类型、搜索或页长变化回到第一页并关闭详情。
- Path单元格以单个文本节点显示完整`→`序列。Details在当前行下方展示path evidence、changed member metadata、observations、SSA结果与code comparison。
- Unified diff在详情打开时按行生成DOM：old/new file header、hunk、addition、deletion和context使用不同CSS；Decompiled Java与Git-style ASM fallback共用同一渲染器。
- JavaScript只通过DOM API与`textContent`创建动态内容；内嵌JSON转义HTML-sensitive字符、控制字符和Unicode行分隔符。解析完成后删除JSON script节点，状态只保存在当前页面内存。
- `ACCESS_REMAINS_VALID`、`SHADOWED_BY_DUPLICATE`等无路径结果不产生可浏览member明细；其汇总与Module诊断仍可用于解释分析覆盖。

## Index

- Overall记录mode/status、runtime、configured/actual workers、dependency scope、candidate/final path、affected method/class、raw change、duplicate、shadowed、SSA和coverage metrics。
- Module Index记录status/reason、scope、entrypoint selection、affected path counts、dependency body boundary、duplicate resolution、runtime metrics、limitations和Diagnostics。
- Empty Final文案固定为`No affected call chain was found within the documented analysis scope.`，不声明确定性no impact。

## Publication

- Staging中先写每个非`SKIPPED` Module的Module Index与`-impact.html` Affected Paths，再写Overall Index。
- 每页通过Writer顺序追加；JSON按member/path record流式写出，不构造完整document或完整data字符串。
- 最后atomic move command-owned Module directory和Index；fallback replace仍保持同filesystem。Owned directory replacement清理旧`-changes.html`及其他stale page。
- Handled Module failure仍发布partial/all-failed Report；global preparation或publication failure不主动替换旧Report。

## Final Artifact Usability Gate

- `PackagedJarCliIT`必须调用最终`target/dependency-analyzer.jar`生成真实Impact和Tree Report，验证publisher而非测试内直接Renderer。
- `HtmlReportUsabilityVerifier`检查每个可达HTML文件可读、UTF-8内容非空，并包含doctype、html、head、非空title、body及closing tag。
- 所有非HTTP、非data、非fragment的`href/src`都按当前页面解析；规范化结果必须留在Report root内，目标必须是可读regular file。
- Tree从`index.html`递归验证本地HTML链接，并要求至少可到达一个Reactor page；Impact从Overall entry递归验证Module与Affected Paths页面。
- verifier拒绝`{{`/`}}`未展开模板标记。该gate只验证交付可打开性和资源闭包，不在测试中重新计算分析结论。

## Core Flow

1. Impact pipeline从Candidate与Structural Paths收集唯一changed member，生成并按logical identity去重code comparison。
2. Renderer归并Overall、Module、path、member、limitation与code evidence，流式输出两级Module页面和script-safe规范化JSON。
3. 浏览器加载Affected Paths后解析数据到内存，按type、search、page size和page选择渲染table slice。
4. 用户打开Details时，脚本在当前行后移动单例详情并按需渲染Diff；切换或重新筛选时销毁详情DOM。
5. 全部staging文件关闭后原子发布并清理command-owned cache。

## Acceptance Criteria

### Functional

- Given超过100条路径；When首次打开Affected Paths；Then默认只生成10条table row，切换20/100或翻页后DOM row不超过当前页长加一个详情行。
- GivenFinal、Equivalent filtered与Structural数据；When切换View type或All；Then记录类型、顺序、结果计数和页码稳定，搜索只匹配affected application method/member。
- Given多条路径关联同一changed member；When生成Report；Then内嵌member与raw diff只有一份，各path引用同一member ID。
- GivenUnified diff；When打开详情；Thenfile header、hunk、addition和deletion获得对应样式，关闭或切换详情后不保留旧Diff DOM。
- Given只有`ACCESS_REMAINS_VALID`等无路径change；When完成analysis与Report；Thencode comparison worker不因该change启动，Affected Paths不包含该member，raw汇总仍保留。
- GivenJavaScript禁用；When打开Affected Paths；Then页面显示明确`noscript`提示，不一次性回退渲染全部路径。
- Given最终shaded JAR成功生成Impact或Tree Report；When执行HTML usability gate；Thenentry、所有可达本地页面和asset均可读，链接不能逃逸Report root，Tree Index至少链接一个完整Reactor page。

### Non-Functional

- [ ] Report完全offline，不使用CDN、网络请求、外部asset、`localStorage`或其他持久化。
- [ ] Dynamic content使用DOM API与`textContent`；内嵌JSON不能通过`</script>`、控制字符或Unicode行分隔符突破script边界。
- [ ] Renderer保持Writer单向输出，production report path不持有WALA `CGNode`、class hierarchy、analysis cache或session。
- [ ] Publication使用staging与command-owned atomic replacement，handled Module failure仍可发布partial Report。
- [ ] 最终artifact的Impact单文件入口与Tree多页面入口通过HTML结构和本地资源闭包验证。

## Edge Cases

- 当前type或搜索无结果时使用对应保守空态；page input超界时clamp到有效范围。
- Structural直接class/member reference没有affected method时，以application class/member作为展示和搜索值。
- Code comparison缺失或unavailable只改变详情文案，不改写Impact、Module status或path。
- ASM fallback不是Git-style Unified diff时保持普通preformatted文本，不错误分类行。

## Implementation Boundaries

- Report不计算Call Graph、access legality、SSA等价性或coverage precedence；只消费上游typed result。
- Code comparison选择由Impact pipeline负责；Renderer只序列化已绑定到可展示path member的evidence。
- Affected Paths前端只负责内存筛选、分页、DOM materialization与Diff presentation，不改变分析结论。
