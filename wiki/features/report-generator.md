---
title: "Report Generator"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "status、partial result 与 publication contract"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Path、Structural Reference Path、SSA 与代码 evidence"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "Overall 与三个 Module pages 的持续完整性校验"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/report/PerModuleHtmlReportGenerator.java"
    desc: "impact HTML Index 与 Module pages"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/AnalysisRunResult.java"
    desc: "run status、selected algorithm、试验性semantic comparison配置与metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleAnalysisResult.java"
    desc: "Module detail result"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleCallGraphSnapshot.java"
    desc: "不引用WALA session的report-safe Call Graph metrics"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/SnapshotQueryNode.java"
    desc: "stable method/Context/node/sentinel path node"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleChangedPathSelection.java"
    desc: "requested/actual scope、fallback 与全部 dependency paths"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/callgraph/DependencyBodyBoundaryMetadata.java"
    desc: "external artifact/method policy counts 与 boundary evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "独立 tree HTML renderer"
---

# Feature: Report Generator

## Summary

`impact`只生成英文offline HTML。Renderer面向`Writer`逐段输出document header、navigation、section和footer，不构造完整页面字符串。Production path node与Call Graph metrics在Report前已脱离WALA session；Report投影effective algorithm/JDK model、Reflection applied状态、terminal Evidence kind/mechanism、requested/actual dependency scope、changed dependency paths、artifact-level method-body policy、type-level ancestor exception与coverage limitation。`tree`使用相同Writer原则，但保持独立repository/reactor contract。

## Design Decisions

- Report只投影typed run/module/query结果；Algorithm、WALA ReflectionOptions、access decision与coverage reason不从Diagnostic/summary string反向解析。
- `ACCESS_REMAINS_VALID`是用户可见的非影响结果：进入Dependency Changes，但不伪造Affected Call Chain。
- `POTENTIALLY_INACCESSIBLE`与Structural Reference只描述potential compatibility risk，不宣称一定发生linkage error。
- Existing Java caller继续使用`generate(AnalysisRunResult, ...)`；内部与production共用同一streaming rendering path。Compatibility adapter允许测试或直接caller保留in-memory domain result。
- 完整HTML只存在于output同filesystem的staging file。小型row/card fragment可使用局部builder，但document级body不返回完整`String`。

## Actors / Entrypoints

- `impact` command在全部Module analysis、可选试验性SSA filtering与code comparison完成后触发原子HTML publication。
- 用户从Overall Index进入每个Module的三页视图。

## Behavior Contract

- Overall technical details展示effective Algorithm、JDK Method Model、WALA ReflectionOptions applied/not-applied状态及试验性Bytecode semantic comparison的enabled/disabled状态。
- Overall汇总 changed-paths/full/fallback Module 数量、real-IR/no-op external artifact 数量、no-op/factory method node、dangerous transfer与 `INCONCLUSIVE` 比例。
- CHA Module同时展示ancestor-retained external type/method node与pruned external method target计数。Artifact-level `REAL_IR/NO_OP`列表不因type-level ancestor exception被误报为整个JAR使用真实IR。
- Access narrowing member展示old/new access、typed decision/reason及代表性caller/reference evidence。
- 全部coverage limitation保留；Module单一reason使用typed precedence。

## Index

顶部提供 `How to read this report`、`Analysis scope and limitations` 与 `Terminology`。主视图面向中级 Java 程序员，用 plain-language 说明可能的调用关系、coverage limitation、direct/transitive impact；WALA、Call Graph、RTA/ZeroCFA/optimized 0-1-CFA、conservative/false-positive、Context、SSA equivalence、Reflection、ServiceLoader 在 Terminology 中解释。

Index 记录：

- Overall mode/status、JDK/Maven version、`Maven Dependency Plugin: embedded 3.6.1`。
- Configured/actual analysis parallelism、Module/JAR diff/decompile workers；试验性比较关闭时SSA worker显示`0 (disabled)`，启用时显示`1 (experimental)`。
- Baseline dependency、target build、front preparation、target dependency、JAR diff、Module analysis、decompile elapsed；只有启用试验性比较时展示SSA elapsed。
- Dependency changes、raw ChangePoints、candidate/equivalent-filtered/final paths、duplicate conflict/shadowed ChangePoint与SSA status counts；试验性比较关闭时SSA counts显示`not run`。
- 每 Module status/reason/link、candidate/filtered/final、direct/transitive、affected methods/classes、Structural Reference Paths、entrypoint selector/matching、scope、CG nodes/edges/contexts、SSA/limitation counts。
- Preflight 与 Diagnostics 整体默认折叠。
- Algorithm、JDK model与WALA ReflectionOptions读取command-wide`AnalysisRunResult`：默认显示`cha`、`none`与`not applied by cha`；非CHA显示实际Reflection选项。
- Dependency analysis scope同时展示command requested mode与每个Module actual mode；默认requested为`changed-paths`，graph/path planning failure的Module显示`full`和fallback reason。

## Module Pages

- Module Index：status/reason、scope、entrypoint selection、affected method/class/path/dependency/member counts、stage/worker/entry/call metrics、coverage limitations和sibling links。Dependency body section展示requested/actual mode、fallback、每个changed dependency的全部到达路径、real-IR/no-op artifact列表和数量、real/no-op/factory method node数量、ancestor-retained type/method node、pruned external target、dangerous transfer表与flow-to-cast factory evidence。`Duplicate class resolution`表展示binary name、winner origin/logical source、shadowed logical source与precedence reason。
- Affected Call Chains：一级按 changed JAR，二级按 changed member 与 affected application method；最终链展示 Java method sequence 及 `Direct dependency impact`/`Transitive dependency impact`。SSA-equivalent candidate chains 独立默认折叠。Structural Reference Chains 显示 PROJECT boundary 到 changed class 的完整关系；raw Context/edge evidence 位于 `Technical details`。Changed member 链接到 Dependency Changes anchor。
- Dependency Changes：展示至少关联candidate/final Impact Path、Structural Reference Path，或disposition为`SHADOWED_BY_DUPLICATE`/`ACCESS_REMAINS_VALID`的changed member，按Maven coordinate/JAR分组。Access member展示`PUBLIC->PROTECTED`等transition、`ACCESSIBLE`/`INACCESSIBLE`/`POTENTIALLY_INACCESSIBLE` decision与representative evidence。其他raw changes只保留总数和未展示数；JAR diff failure转移到Module limitations/Diagnostics。
- 每个相关 member 默认折叠，并使用 `Affected`、`Equivalent (filtered)`、`Structural impact`、`Shadowed by duplicate` badge。Shadowed member 明确说明未生成 Impact Path 的原因、actual winner 与 precedence；dependency winner 使用 logical coordinate，不展示 physical path。其他 member 的 `View code changes` 展示由 dependency bytecode 生成的 old/new Unified diff，明确标记为 `Decompiled Java representation`；反编译失败或文本相同时展示 ASM fallback/unavailable reason。
- 默认展开区只显示 Maven/Module coordinate、scope/version、Java package/class/member、影响链和核心 metrics。Dependency JAR physical path 在所有区域均不输出；Workspace、classpath、Maven executable、JDK/config/temp/output 等其他 filesystem path 进入 `Technical details`。
- Call chain 空态固定为 `No affected call chain was found within the documented analysis scope.`，不声明确定性 no impact。
- `changed-paths` 的 `SUCCESS` 页面必须明确说明：只代表 selected dependency path 与 modeled boundary 内未发现 Impact Path，不代表 no-op dependency 内部不存在影响。

所有页面共享 top breadcrumbs、Module sibling navigation、sticky side TOC；窄屏下 TOC 回到正文顶部。Navigation 使用纯 HTML/CSS，无 JavaScript、CDN 或外部 asset。Dynamic text、href 和 anchor attribute 均 HTML escaping；anchor 使用 stable hash。

## Publication

- Staging 中先写每个非-skip Module 的三页，再写 Overall Index。
- 每页通过UTF-8 `Writer`直接追加并关闭；一个path、change、occurrence或conflict完成escaping后立即写入staging，不把完整页面装入`StringBuilder`。
- 最后 atomic move command-owned Module directory 和 Index；不支持 filesystem atomic move 时使用同 filesystem replace fallback。
- Handled Module failure 仍发布 partial/all-failed Report。
- Global preparation 或 report publication failure 不主动替换旧 Report；Module directory replace failure尝试恢复 backup。
- 原 Module detail URL 的 base filename 保留为 Module Index；新增 `-impact`、`-changes` sibling。Module filename 由 sanitized coordinate + stable SHA-256 prefix 生成；owned directory 整体 replacement 会清理 stale page。
- Atomic publication成功后立即清理task cache；analysis、render或publish failure同样清理。Cache cleanup失败作为command错误，外层owned run directory在close时再次回收。

## Format Contract

- `--format html` 接受。
- `--format md` 保留 parser compatibility，但在 pipeline 前 fail fast，提示 Markdown 已移除。

## Core Flow

1. 从immutable AnalysisRunResult或production detached snapshot归并Overall、Module、path、disposition、limitation与code evidence view。
2. 依次打开staging page Writer，逐段写每个非skip Module的三页，再写Overall。
3. 完整escaping、navigation与owned-file校验后关闭全部Writer，原子替换旧Report并清理cache。

## Acceptance Criteria

### Functional

- Given default command configuration；When发布Report；Then technical details显示`cha`、`none`与`not applied by cha`，terminal显示Evidence kind/mechanism。
- Given未启用试验性bytecode semantic comparison；When发布Report；ThenOverall与Module technical details显示`disabled (experimental)`，SSA worker为`0 (disabled)`且counts为`not run`。
- Given显式启用试验性bytecode semantic comparison；When发布Report；Then显示`enabled (experimental)`、单一SSA worker与实际equivalent/different/unknown counts。
- Given access reference全部仍合法；When发布Dependency Changes；Then显示`ACCESS_REMAINS_VALID`与old/new access，Affected Call Chains中不存在虚假path。
- Given potential access reference；When发布Report；Then明确标注`Potential access incompatibility`且不改变Module status。
- Given dangerous transfer或flow-to-cast factory；When发布Report；ThenModule reason为`INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`并展示caller、callee、artifact、PC、typed proof/type与dependency path evidence。
- Given occurrence graph recovery failure；When发布Report；Thenrequested mode保持`changed-paths`、actual mode显示`full`并展示stable fallback reason。
- Given changed-paths CHA保留external祖先type；When发布Module Index；Thenartifact仍显示`NO_OP`，type-level exception与retained/pruned计数单独展示。

### Non-Functional

- [ ] 所有页面offline、无JavaScript/CDN，dynamic text/href/anchor均escaping。
- [ ] Publication使用staging与command-owned atomic replacement，handled Module failure仍可发布partial Report。
- [ ] Renderer按单向顺序写入Writer；production report path不持有WALA `CGNode`、class hierarchy、analysis cache或session。

## Edge Cases

- Empty path文案只表示declared analysis scope内未发现路径。
- Code comparison unavailable只影响evidence，不改写Impact或Module status。

## Implementation Boundaries

- Report不计算Call Graph、access legality或coverage precedence；只消费上游typed result。
