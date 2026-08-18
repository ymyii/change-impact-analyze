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
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorInventoryBuilder.java"
    desc: "入口POM、active module graph与祖先aggregator解析"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/MavenActivationContext.java"
    desc: "Maven JVM、OS、property与settings profile activation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorDescriptor.java"
    desc: "FULL_REACTOR、SINGLE_MODULE与STANDALONE不可变scope"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDependencyCollector.java"
    desc: "单次 Reactor compile、dependency tree 与 classpath evidence collection"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ClasspathEvidenceJsonParser.java"
    desc: "Classpath Evidence Schema v1 严格解析与路径校验"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/ModuleClassConflictAnalyzer.java"
    desc: "Module effective classpath 扫描、风险分类与反编译"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/classpath/ClassOwnershipIndex.java"
    desc: "impact/tree 共享的 class winner 与冲突模型"
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

`TreeCommand`只负责Picocli option与command metrics session；`TreeExecutionEngine`执行Preflight、单入口scope processing、cache spill/publish/discard和Report终态。`--path`或current directory必须直接包含readable `pom.xml`。共享reactor resolver只读取入口POM的active module graph与文件系统祖先aggregator，不枚举Git repository中的其他POM。入口aggregator分析其active subtree；owned leaf从最外层匹配祖先执行`-pl/-am`，但Report只展示入口Module；没有匹配祖先时按standalone project执行。一次Maven session依次执行`compile`、verbose dependency tree goal和Classpath Evidence goal。

安全POM inventory解析本地`parent.relativePath`链并继承父POM properties，因此`${revision}`等CI-friendly version会在Module identity校验前解析；空`relativePath`或repository外父POM不读取。

## Design Decisions

- Current checkout 文件集合为 Git tracked + non-ignored untracked；local ref 使用 detached worktree，不隐式 fetch。
- `-p, --path` 同时确定 Git root 与 Git-root-relative analysis path；`-r, --ref` 将同一 relative path 映射到 detached snapshot。
- 只有本次Maven语义下active的normal/profile module declaration参与ownership和递归；inactive profile module不成为独立reactor。
- Scope固定为`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE`。入口POM有active child时优先为`FULL_REACTOR`且不向祖先扩大；leaf只检查到Git root的祖先POM并选择最外层active closure owner。
- `SINGLE_MODULE`使用aggregator-relative path执行`-pl <module> -am`。上游reactor module进入Maven session和classpath evidence，但不生成额外Module tab。`STANDALONE`从入口POM直接执行，不附加`-pl/-am`。
- Git root只用于snapshot映射、路径边界、ignored/submodule eligibility和metadata；repository-wide POM discovery不属于分析流程。
- Maven conflict key 固定为 `groupId + artifactId + type + classifier`，reactor module occurrence 保留并标记。
- Module 多版本问题比较实际 dependency path requested version 与实际应用的 dependencyManagement effective version；全部来源去重后至少为 2 时成立。`duplicate` 只作为 omitted evidence，不独立构成冲突。
- 跨 module analyzer 用 selected occurrence 判定 resolved version 差异。Module mediation 保留在各自 Module tab 的 Internal conflicts table；跨 Module resolved version 差异进入独立的 Reactor-level Cross-module conflicts section。
- JAR 内置 `maven-dependency-plugin:3.6.1` 和完整 plugin dependency repository，默认分析不依赖远程 plugin download。
- Dependency tree 是 Maven-style verbose `<pre class="dependency-tree">` 静态证据；页面只为 conflict table 提供 component-scoped 交互，避免大型 tree 的额外状态与操作成本。
- 每个command复用`CommandRunDirectory`的UUID temporary root，在`report-cache`内写versioned JSON Lines。Reactor发布后立即删除其fragment；cache不进入最终artifact，也不用于断点续跑。
- Module internal与cross-module grouping采用external sort：batch最多10,000条或约8 MiB payload，任一阈值先到即spill；merge fan-in最多32。Internal只归并当前Module，cross-module只归并selected occurrence摘要。
- 冲突类边界是单个Module的实际classpath。同一binary name存在两个及以上有效定义即成立；全部SHA-256相同为`LOW`，存在不同摘要为`HIGH`。风险不改变Module、Reactor status或exit code。
- winner固定为`PROJECT > REACTOR_DEPENDENCY > DEPENDENCY`，同一层按Maven classpath顺序选择。Tree不扫描JDK；`impact`在其JDK 8 scope中保留更高的JDK precedence，并使用相同`LOW`/`HIGH`定义。
- Multi-Release JAR（多版本JAR）按Classpath Evidence记录的Maven JVM major选择实际可见entry；`module-info.class`不参与扫描。物理路径只用于分析，不进入Report。
- 只有已识别冲突的class bytes进入Vineflower；同一Module内按SHA-256去重，完整Module classpath作为library context。反编译失败保留finding，源码位置显示`Unavailable`，不改变status或exit code。

## Actors / Entrypoints

- 用户执行 `dependency-analyzer tree [-p|--path <dir>] [-r|--ref <local-ref>] -o|--output <dir> ...`。
- CI 归档 `<output>/index.html` 和 `<output>/dependency-report/`。

## Behavior Contract

- Analysis directory必须直接包含readable `pom.xml`；只包含多个子项目的容器目录在Preflight阻断，旧Report不被替换。
- Git ignored POM、Git submodule内POM、symlink逃逸和repository外module不允许进入scope；current checkout接受tracked与non-ignored untracked POM。
- 入口POM包含active module时，只递归该入口subtree。即使入口本身被外层reactor聚合，也不向外层扩大。
- Leaf模式只检查入口目录到Git root之间的祖先`pom.xml`。多个祖先active closure包含入口时选择最外层，以便sibling reactor dependency进入同一Maven session。
- 非祖先aggregator不形成ownership；没有祖先owner时按`STANDALONE`执行入口POM。同reactor dependency可能转为Maven repository解析，或因artifact不可用导致dependency resolution failure。
- Packaging 为`pom`且存在active child的纯aggregator（包括嵌套aggregator）不进入Module result；无active child的单POM project，以及packaging为`jar`、`war`等且同时聚合children的project仍进入分析。
- 默认 scope 为 `compile,runtime,provided,test,system`；filter 同时作用于dependency tree、版本冲突、external/Reactor dependency冲突类扫描。当前Module主类始终纳入。
- 每个入口scope只启动一次Maven session，goal顺序固定为`compile`、dependency plugin text output、Classpath Evidence。`SINGLE_MODULE`使用`-pl/-am`；其他mode不附加project selector。不执行`test-compile`或`package`。
- Reactor classifier只能由`test-compile`或`package`产生时，不读取旧产物；Classpath Evidence记录不完整原因，Reactor标记为`DEGRADED`。Compile或evidence command失败时当前Reactor为`FAILED`。
- Dependency tree Parser持有`BufferedReader`并逐行消费UTF-8 output，不用`Files.readString`或完整文本`split`。
- 默认 plugin 使用全限定 `org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree` goal；`-d` override 必须在 Command Preflight 通过完整 evidence capability check。
- `dependencyManagement` 只通过实际 occurrence 的 Maven verbose annotation 参与分析；未使用的 managed entry、完整 imported BOM 清单和 management 来源文件不在分析范围内。
- Scope failure形成当前Reactor issue；全局终态为`SUCCESS`、`COMPLETED_WITH_ISSUES`或`FAILED`。
- Index 的 Metadata 与 Summary 都使用table；Reactors table分别统计Internal conflicts、Cross-module conflicts、Class conflicts和High-risk class conflicts。同一class在不同Module按Module-class relation分别计数。
- Reactor page 先展示 Reactor metadata、Module metadata 和“问题”，再展示独立的 Cross-module conflicts section 与 Module tabs。Module metadata 分别统计 Internal/Cross-module conflicts，不展示 role 或纳入原因。
- 每个Module tab先展示固定列Class、Risk、Winner、Shadowed sources、Selection、Decompiled code的冲突类表，再展示Internal conflicts和单个Maven-style verbose `<pre>` dependency tree。Tree使用`+-`、`\-`与缩进呈现完整path，annotation顺序固定为version managed、scope managed、optional、omitted reason、reactor module；Tree本身没有button、link、`<details>`、tooltip或click行为。
- 冲突类表支持全字段大小写不敏感search、`LOW`/`HIGH` filter、Class/Risk/Winner sort与10/50/100 pagination；默认`HIGH`优先再按binary name。空表保留header但不渲染controls。
- 点击“查看反编译代码”在当前row下展开，默认winner。Winner与Shadowed按钮使用`aria-pressed`单选状态；active按钮持续显示不同背景、边框、文字和font weight，键盘focus保持可见，切换后源码与状态同步。页面同时最多保留一个展开row与一个payload；shard schema保持v1。
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
- Command Preflight 成功后重建工具拥有的输出，立即发布assets、空reactors directory与`RUNNING 0/1` Index；output root其他文件保留。
- Console 通过统一五段 Diagnostic prefix 按 `Preflight → Analysis → Summary` 输出。Reactor start/result 使用 `stage=analysis, substage=reactor`；`SUCCESS` 为 `INFO`，degraded/issue 为 `WARN`，failed 为 `ERROR`。
- `compile`、dependency/classpath collection、Module class scan、conflict decompile与Reactor publish通过`INFO` stage生命周期输出开始、完成、失败与耗时。classpath evidence或class scan不完整时输出Reactor级`WARN`摘要；反编译不可用按Module最多输出20条明细，其余合并为一条suppressed count，不改变Reactor status或exit code。
- `DEBUG`输出classpath entry、conflict risk、candidate digest/effective entry、反编译缓存命中与shard数量。逐artifact、逐class与逐shard进度只在`TRACE`启用后遍历和生成，避免默认运行承担高频证据成本。
- Maven collection 每个非空输出行按 level 转发；默认只显示 warning/error，`-v/-vv` 显示完整 output。Failure evidence 只保留 bounded 100-line tail。
- 入口scope执行Maven collection并将ordered tree record、normalized occurrence、selected reactor dependency摘要与Module metadata写入command cache；module/version/cross-module analysis通过bounded external grouping聚合issue。
- Reactor page以UTF-8 Writer顺序写metadata、conflict、Module tab与verbose tree；完整关闭后原子发布，再用Writer原子刷新Index。Index只链接已完整发布的page。
- Reactor page和Index checkpoint都成功后删除该Reactor cache fragment；只保留`ReactorReportSummary`和Command Preflight，释放完整`ReactorTreeResult`、occurrence与path数据。
- Scope处理结束写`SUCCESS 1/1`或`COMPLETED_WITH_ISSUES`；pipeline/report failure写`FAILED`并保留已完整发布page。

## Acceptance Criteria

### Functional

- Given analysis directory没有`pom.xml`；When Preflight；Then command阻断且旧Report保持不变。
- Given入口aggregator被外层reactor聚合；When分析入口；Then只分析入口active subtree，不加入外层sibling。
- Given leaf同时属于多层祖先aggregator；When scope resolution；Then选择最外层匹配祖先并生成正确`-pl/-am`。
- Given `module-b`依赖未安装的sibling `module-a`且另有无关`module-c`；When path只命中`module-b`；ThenMaven通过`-am`构建上游，但Report只有`module-b` Module tab。
- Given非祖先aggregator声明入口；When分析入口；Then按`STANDALONE`执行且不附加`-pl/-am`。
- Giveninactive、显式、activeByDefault、JDK、OS、property、file或settings profile；When解析scope；Thenactive module graph与同参数Maven subprocess一致。
- Given 一个 dependency occurrence 从 `1.0` 被 `dependencyManagement` 管理为 `2.0`；When Maven 提供 verbose annotation；Then `DEPENDENCY_PATH=1.0` 与 `DEPENDENCY_MANAGEMENT=2.0` 构成 `MODULE_MEDIATION`，但 Report 不声称知道 management 来源 POM/BOM。
- Given dependency path 或实际 management 来源至少包含两个版本；When mediation；Then issue 进入对应 Module tab 的 Internal conflicts table，Evidence 位于最后一列。
- Given只有相同 requested version 的 duplicate occurrence；When mediation；Then verbose tree 保留 duplicate annotation，但不生成 `MODULE_MEDIATION`。
- Given不同 module selected version 不同；When reactor report；Then以 `CROSS_MODULE_RESOLUTION` 进入独立 Cross-module conflicts section，不复制到 Module tab。
- Given conflict Evidence 来自 `DEPENDENCY_MANAGEMENT`；When renderer 输出 Evidence 子表；Then Dependency chain cell 为空，不显示 occurrence path 或伪造 management source。
- Given 冲突数超过 10；When 检索、Module/Scope filter、排序或分页；Then 页面离线更新，page size 可选 10/50/100。
- Given output root 有其他文件；When重复生成；Then其他文件保持不变，旧 reactor 页面消失。
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

- Path不存在、不是directory、无法解析Git root、未直接包含POM，或同一路径在目标ref中没有directory/POM时为command-level block；旧Report保留。
- Missing active module、module越过Git root、active graph cycle、重复或不可解析coordinate为preparation failure；不回退到repository扫描。
- Maven 部分 module failure 时，已解析 module 保留，reactor 状态为 `FAILED`。
- 空 dependency tree 与 `FAILED` 使用不同文案和视觉状态；不完整 plugin capability 在 Command Preflight 阻断。

## Implementation Boundaries

- 只分析 project `dependencies`，不分析 plugin dependency tree 或未使用的完整 `dependencyManagement` 清单。
- Parser不依赖ANSI color或Maven console prefix；canonical input是UTF-8 output file并通过`Reader`消费。
- Incremental writer 只持有 metadata、轻量 Command Preflight 与 `ReactorReportSummary`，不长期持有已完成 reactor 的完整 domain result。
- Renderer不执行Git、Maven或dependency analysis，不返回完整HTML `String`。
