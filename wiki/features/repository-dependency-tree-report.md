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
  - path: "wiki/features/git-workspace-management.md"
    desc: "Current checkout 和 local-ref snapshot"
  - path: "wiki/features/report-generator.md"
    desc: "Offline HTML、escaping 和受控替换"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git/Maven process 执行约束"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "Public tree CLI option 与metrics session入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeExecutionEngine.java"
    desc: "Preflight、串行Reactor processing、cache与Report生命周期"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiagnosticEmitter.java"
    desc: "通过 DiagnosticLog 输出 Preflight、Analysis、Summary 三阶段语义"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/GitSnapshotProvider.java"
    desc: "Current checkout/local-ref snapshot"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ReactorInventoryBuilder.java"
    desc: "Git file set、POM ownership 和 active module inventory"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDependencyCollector.java"
    desc: "Full-reactor 或 bounded-module Maven text collection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntimeManager.java"
    desc: "内置 Dependency Plugin repository、settings overlay 和 capability boundary"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/DependencyTextParser.java"
    desc: "Reader逐行DependencyOccurrence parser"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/DependencyOccurrence.java"
    desc: "Version、scope、selection、path 和 reactor module evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/VersionPath.java"
    desc: "Occurrence-level path、resolved version、scope 与 version source evidence"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/VersionEvidence.java"
    desc: "Dependency path 与 dependencyManagement version source"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ModuleVersionAnalyzer.java"
    desc: "Module 内 version mediation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/CrossModuleVersionAnalyzer.java"
    desc: "跨 module resolved version 差异"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeExternalOccurrenceSorter.java"
    desc: "bounded batch与最多32路external merge grouping"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportCacheSpiller.java"
    desc: "per-Reactor ordered/normalized/selected JSON Lines fragment"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/ReportCache.java"
    desc: "UUID command-owned cache manifest、complete marker与cleanup"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportRenderer.java"
    desc: "Repository/reactor static HTML 与 atomic publish"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeReportSession.java"
    desc: "Incremental page/Index checkpoint 生命周期"
---

# Feature: Repository Dependency Tree Report

## Summary

`TreeCommand`只负责Picocli option与command metrics session；`TreeExecutionEngine`执行Preflight、串行Reactor processing、cache spill/publish/discard和Report终态。分析扫描Git repository内所有eligible `pom.xml`，按Maven `<modules>` ownership识别reactor root。Path命中root时收集完整reactor；只命中child module时收集requested module与同reactor dependency closure。Maven output通过`Reader`逐行解析；conflict grouping使用command cache external sort；HTML通过Writer逐段写入。Packaging为`pom`且存在active child的纯aggregator root保留execution context，但不生成Module result。

## Design Decisions

- Current checkout 文件集合为 Git tracked + non-ignored untracked；local ref 使用 detached worktree，不隐式 fetch。
- `-p, --path` 同时确定 Git root 与 Git-root-relative analysis path；`-r, --ref` 将同一 relative path 映射到 detached snapshot。
- 所有 normal/profile module declaration 参与 ownership；本次 active profile module 才生成 module section。
- Inventory 读取完整 repository POM；`activePoms` 保留完整 active reactor，`requestedPoms` 保留 analysis path 下的 direct matches，`rootSelected` 决定 full-reactor 或 bounded-module mode。
- Root-selected reactor 使用全部 `activePoms` 建立 Maven execution scope；child-only reactor 使用内部 `-pl/-am`。Report 仅保留产生 dependency analysis result 的 module；packaging 为 `pom` 且存在 active child 的纯 aggregator root 即使参与 execution 也不进入 Module result。
- Maven conflict key 固定为 `groupId + artifactId + type + classifier`，reactor module occurrence 保留并标记。
- Module 多版本问题比较实际 dependency path requested version 与实际应用的 dependencyManagement effective version；全部来源去重后至少为 2 时成立。`duplicate` 只作为 omitted evidence，不独立构成冲突。
- 跨 module analyzer 用 selected occurrence 判定 resolved version 差异。Module mediation 保留在各自 Module tab 的 Internal conflicts table；跨 Module resolved version 差异进入独立的 Reactor-level Cross-module conflicts section。
- JAR 内置 `maven-dependency-plugin:3.6.1` 和完整 plugin dependency repository，默认分析不依赖远程 plugin download。
- Dependency tree 是 Maven-style verbose `<pre class="dependency-tree">` 静态证据；页面只为 conflict table 提供 component-scoped 交互，避免大型 tree 的额外状态与操作成本。
- 每个command复用`CommandRunDirectory`的UUID temporary root，在`report-cache`内写versioned JSON Lines。Reactor发布后立即删除其fragment；cache不进入最终artifact，也不用于断点续跑。
- Module internal与cross-module grouping采用external sort：batch最多10,000条或约8 MiB payload，任一阈值先到即spill；merge fan-in最多32。Internal只归并当前Module，cross-module只归并selected occurrence摘要。

## Actors / Entrypoints

- 用户执行 `dependency-analyzer tree [-p|--path <dir>] [-r|--ref <local-ref>] -o|--output <dir> ...`。
- CI 归档 `<output>/index.html` 和 `<output>/dependency-report/`。

## Behavior Contract

- 未被其他 POM `<modules>` 引用的 POM 是 reactor root；纯物理嵌套不建立 ownership。
- Git ignored 内容、Git submodule 内文件、repository 外部 module path 不纳入分析。
- 只有 `requestedPoms` 非空的 reactor 执行。一个 path 命中多个 independent reactor root 时分别执行 full-reactor mode；root 与 child 同时命中时 root rule 优先。
- Bounded-module mode 按稳定 `groupId:artifactId` selector 执行 `-pl <requested> -am`。Dependency closure 来自 requested occurrence，要求 selected、scope 命中、GAV 匹配且属于同 reactor；无关 sibling、root aggregator 和 support project 不进入 Report。
- Full-reactor mode 即使 active module 位于 reactor root directory 外，只要仍在 Git repository 内也进入 execution。Packaging 为 `pom` 且存在 active child 的纯 aggregator root 不进入 Module result；无 active child 的 `pom` project，以及 packaging 为 `jar`、`war` 等且同时聚合 children 的 root 仍进入分析。Independent reactor dependency 不跨 reactor 追踪。
- 默认 scope 为 `compile,runtime,provided,test,system`；filter 同时作用于 tree、统计和 version analysis。
- 每个active module独立调用dependency plugin text output、standard tokens、verbose；不执行compile/package。Parser持有`BufferedReader`并逐行消费UTF-8 output，不用`Files.readString`或完整文本`split`。
- 默认 plugin 使用全限定 `org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree` goal；`-d` override 必须在 Command Preflight 通过完整 evidence capability check。
- `dependencyManagement` 只通过实际 occurrence 的 Maven verbose annotation 参与分析；未使用的 managed entry、完整 imported BOM 清单和 management 来源文件不在分析范围内。
- Reactor failure 不阻止其他 reactor report；全局终态为 `SUCCESS`、`COMPLETED_WITH_ISSUES` 或 `FAILED`。
- Index 的 Metadata 与 Summary 都使用 table；Reactors table 分别统计 Internal conflicts 与 Cross-module conflicts，不输出 `dependency=60` 一类 key/value 文本。
- Reactor page 先展示 Reactor metadata、Module metadata 和“问题”，再展示独立的 Cross-module conflicts section 与 Module tabs。Module metadata 分别统计 Internal/Cross-module conflicts，不展示 role 或纳入原因。
- 每个 Module tab 包含该 Module 的 Internal conflicts table 和单个 Maven-style verbose `<pre>` dependency tree。Tree 使用 `+-`、`\-` 与缩进呈现完整 path，annotation 顺序固定为 version managed、scope managed、optional、omitted reason、reactor module；Tree 内没有 button、link、`<details>`、tooltip 或 click 行为。
- Internal 与 Cross-module conflict table 分别维护 search/filter/sort/page state。两类表都有全字段 search、Scope filter、sortable columns 和 10/50/100 pagination，Cross-module table 额外提供 Module filter；空表保留 header，但不渲染 controls。
- Reactor 和 Module metadata 使用表格汇总；module role 仅用于内部选择与排序，Report 不展示 role 或纳入原因。
- Reactor/module failure 和降级证据统一转换为“问题”表行；无 issue 时不生成该 section。

## DependencyOccurrence Schema

每个 occurrence 保存完整 resolution evidence：

- Identity：`groupId`、`artifactId`、`type`、`classifier`，共同构成 Maven conflict key。
- Version：`requestedVersion`、`managedFromVersion`、`effectiveVersion`、`selectedVersion`。
- Scope：`effectiveScope`、`managedFromScope`。
- State：`optional` 或 `unknown`、`selected`/`omitted`、`omittedReason`（`conflict`、`duplicate`、`cycle` 或 raw reason）。
- Context：从 module root 到 occurrence 的完整 `path`，以及 `reactorModule` 标记。

## Dependency Management 与冲突语义

`dependencyManagement` 的分析边界是“实际使用”：只有进入 resolved project dependency tree 的 occurrence 才可能携带 management evidence。工具不枚举 `dependencyManagement` section、imported BOM 或未使用的 managed entry，也不分析 plugin dependency management。

Maven verbose text 出现 `version managed from X` 或 `scope managed from Y` 时：

- `requestedVersion`/Report `originalVersion` 使用 management 前的 `X`；没有 annotation 时使用 occurrence node version。
- `managedFromVersion`/`managedFromScope` 保存 annotation 中的 management 前值；空值表示 Maven 没有提供该类 evidence。
- `effectiveVersion`/`effectiveScope` 是 management 应用后的 node 值。
- `selectedVersion`/Report `resolvedVersion` 是 conflict resolution 的最终值；omitted conflict 从 `omitted for conflict with ...` 读取，selected occurrence 使用 effective version。
- `managedFrom` 不表示 management 来源位置。Report 不推断定义该值的 parent POM、BOM 或具体 `dependencyManagement` entry。

`MODULE_MEDIATION` 在单个 Module 内按 Maven conflict key 分组。每条 occurrence 生成 `DEPENDENCY_PATH` 来源；存在 `managedFromVersion` 时额外生成 `DEPENDENCY_MANAGEMENT` 来源，版本为 management 应用后的 effective version。全部来源至少出现两个不同 version 才生成一行，因此单条 path 从 `1.0` 被管理为 `2.0` 也会报告。冲突行位于对应 Module tab，Evidence 是最后一列，其子表固定为 Source、Dependency chain、Original version、Scope。`DEPENDENCY_PATH` 行保留完整 occurrence chain；`DEPENDENCY_MANAGEMENT` 行的 Dependency chain 是真正的空单元格，因为 Maven annotation 不提供 management 来源 chain。相同 version 的不同 path 仍分别展示。`omitted for duplicate` 保留在 verbose tree annotation 中，但同版本 duplicate 不会单独生成冲突。

`CROSS_MODULE_RESOLUTION` 在 Reactor 内通过 selected occurrence 判定。同一 conflict key 至少有两个不同 `selectedVersion` 才生成一行；冲突行位于独立 Cross-module conflicts section，Evidence 是最后一列，其子表固定为 Source、Module、Dependency chain、Original version、Scope。`DEPENDENCY_PATH` 行保留完整 occurrence chain；`DEPENDENCY_MANAGEMENT` 行的 chain 为空。Module、Scope、Resolved version 展示涉及值集合。

## Core Flow

- `TreeExecutionEngine`拥有Command Preflight、analysis与publication失败边界；`TreeCommand`不读取或传递弱类型Preflight artifact。Preflight准备repository snapshot、Maven runtime和完整inventory；failure不改动旧Report。
- Command Preflight 成功后重建工具拥有的输出，立即发布 assets、空 reactors directory 与 `RUNNING 0/N` Index；output root 其他文件保留。
- Console 通过统一五段 Diagnostic prefix 按 `Preflight → Analysis → Summary` 输出。Reactor start/result 使用 `stage=analysis, substage=reactor`；`SUCCESS` 为 `INFO`，degraded/issue 为 `WARN`，failed 为 `ERROR`。
- Maven collection 每个非空输出行按 level 转发；默认只显示 warning/error，`-v/-vv` 显示完整 output。Failure evidence 只保留 bounded 100-line tail。
- 每个reactor顺序执行Maven collection并将ordered tree record、normalized occurrence、selected reactor dependency摘要与Module metadata写入command cache；module/version/cross-module analysis通过bounded external grouping聚合issue。
- Reactor page以UTF-8 Writer顺序写metadata、conflict、Module tab与verbose tree；完整关闭后原子发布，再用Writer原子刷新Index。Index只链接已完整发布的page。
- Reactor page和Index checkpoint都成功后删除该Reactor cache fragment；只保留`ReactorReportSummary`和Command Preflight，释放完整`ReactorTreeResult`、occurrence与path数据。
- 全部处理结束写 `SUCCESS N/N` 或 `COMPLETED_WITH_ISSUES`；pipeline/report failure 写 `FAILED x/N` 并保留已发布 page；hard interruption 保留最后一个 `RUNNING x/N` checkpoint。

## Acceptance Criteria

### Functional

- Given tracked、eligible untracked、ignored 和 submodule POM；When inventory；Then 仅前两类进入结果。
- Given profile module；When `-P` 激活；Then module 进入本次 section，未激活时不作为独立 reactor 重复分析。
- Given reactor root 被 path 命中；When collection；Then全部 active module 进入 Maven execution scope，包括 repository 内但 root directory 外的 declared module；纯 aggregator root 不生成 Module result。
- Given `module-b` 依赖 `module-a` 且另有无关 `module-c`；When path 只命中 `module-b`；Then Report 只包含 `module-b/module-a`。
- Given requested module 有多级同 reactor dependency；When scope 命中；Then完整 upstream closure 进入 Report；scope 不命中时不进入。
- Given 一个 dependency occurrence 从 `1.0` 被 `dependencyManagement` 管理为 `2.0`；When Maven 提供 verbose annotation；Then `DEPENDENCY_PATH=1.0` 与 `DEPENDENCY_MANAGEMENT=2.0` 构成 `MODULE_MEDIATION`，但 Report 不声称知道 management 来源 POM/BOM。
- Given dependency path 或实际 management 来源至少包含两个版本；When mediation；Then issue 进入对应 Module tab 的 Internal conflicts table，Evidence 位于最后一列。
- Given只有相同 requested version 的 duplicate occurrence；When mediation；Then verbose tree 保留 duplicate annotation，但不生成 `MODULE_MEDIATION`。
- Given不同 module selected version 不同；When reactor report；Then以 `CROSS_MODULE_RESOLUTION` 进入独立 Cross-module conflicts section，不复制到 Module tab。
- Given conflict Evidence 来自 `DEPENDENCY_MANAGEMENT`；When renderer 输出 Evidence 子表；Then Dependency chain cell 为空，不显示 occurrence path 或伪造 management source。
- Given 冲突数超过 10；When 检索、Module/Scope filter、排序或分页；Then 页面离线更新，page size 可选 10/50/100。
- Given output root 有其他文件；When重复生成；Then其他文件保持不变，旧 reactor 页面消失。
- Given 第二个 reactor 尚未结束；When process 被硬终止；Then第一个 page 与 `RUNNING 1/N` Index 可通过 `file://` 打开。
- Given occurrence超过batch阈值且产生超过32个spill；When执行internal/cross-module analysis；Then多轮merge保持dependency key、Module和occurrence稳定顺序，且同时打开的输入不超过32。
- Given Maven output远大于heap；When解析并render verbose tree；Thenparser与renderer均单向逐行/逐段处理，不构造完整input text或HTML page字符串。

### Non-Functional

- [ ] Reactor、module、dependency、path、filename 和 link deterministic。
- [ ] 所有动态 HTML 内容 escaping；assets 不引用 CDN/remote API/font。
- [ ] 大型 verbose tree 不设置 path 截断上限。
- [ ] 每张 conflict table 的交互状态彼此隔离，且不改变或截断底层 occurrence path。
- [ ] Current branch、index 和 tracked 文件不被修改。
- [ ] Success、analysis failure、render failure与publish failure均删除当前UUID下`report-cache`；并发command cache互不读取或删除。
- [ ] 最终shaded JAR生成的Index及其本地资源全部可读，Index至少可达一个完整Reactor page，链接不能逃逸Report root。

## Edge Cases

- 空 repository 或无 eligible reactor 为 command-level block，不生成 HTML。
- Path 不存在、无法解析 Git root、在目标 ref 中不存在，或没有匹配 active POM 时为 command-level block；旧 Report 保留。
- 某 reactor 外部 module/malformed POM/model failure 记录 Analysis issue；可执行 reactor 继续。
- Maven 部分 module failure 时，已解析 module 保留，reactor 状态为 `FAILED`。
- 空 dependency tree 与 `FAILED` 使用不同文案和视觉状态；不完整 plugin capability 在 Command Preflight 阻断。

## Implementation Boundaries

- 只分析 project `dependencies`，不分析 plugin dependency tree 或未使用的完整 `dependencyManagement` 清单。
- Parser不依赖ANSI color或Maven console prefix；canonical input是UTF-8 output file并通过`Reader`消费。
- Incremental writer 只持有 metadata、轻量 Command Preflight 与 `ReactorReportSummary`，不长期持有已完成 reactor 的完整 domain result。
- Renderer不执行Git、Maven或dependency analysis，不返回完整HTML `String`。
