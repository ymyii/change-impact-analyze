---
title: "Repository Dependency Tree Diff"
type: feature
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "tree diff 的产品范围与CLI入口"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "双侧workspace、bounded reactor scope、采集和增量发布边界"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree analyze 的单侧采集语义与视觉基础"
  - path: "wiki/features/git-workspace-management.md"
    desc: "commit-ish worktree与当前工作区target生命周期"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "区分impact去重JAR变更与tree occurrence-aware差异语义"
  - path: "wiki/features/report-generator.md"
    desc: "Tree Diff Schema v1、callback shard与离线浏览器合同"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "Preflight、运行状态、进度、审计日志和退出码"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Git与Maven外部命令执行约束"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "打包JAR、Maven门禁与Tree Diff浏览器验收"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffCommand.java"
    desc: "baseline/target CLI入口与Runtime Metrics生命周期"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommonOptions.java"
    desc: "tree analyze与tree diff共享参数事实来源"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffExecutionEngine.java"
    desc: "双侧workspace、Reactor配对、采集、diff和发布编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/DependencyTreeCollector.java"
    desc: "compile加dependency tree的最小单侧采集边界"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffEngine.java"
    desc: "Module结构校验、依赖聚合、分类与链路配对"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffModuleResult.java"
    desc: "Module双侧状态、指标、依赖差异和树文本结果"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffReportDataWriter.java"
    desc: "Schema v1 catalog、range与callback shard投影"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffReportRenderer.java"
    desc: "Index、Reactor页面、asset和安全增量发布"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeDiffReportSession.java"
    desc: "Reactor checkpoint、终态与已完成页面保留"
  - path: "analyzer/src/main/resources/io/github/dependencyanalysis/tree/tree-diff-report.js"
    desc: "Module切换、range筛选、分页、chain与tree按需加载"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/tree/TreeReportBrowserFixtureIT.java"
    desc: "Tree Diff大型离线浏览器夹具"
  - path: "tests/report-ui/tree-diff.spec.ts"
    desc: "桌面与小屏的懒加载、筛选、chain和响应式合同"
---

# Feature: Repository Dependency Tree Diff

## Summary

`tree diff`比较baseline与target的Maven依赖树，输出可通过`file://`直接打开的离线HTML目录。baseline必须是可peel为commit的本地Git commit-ish；target可为同类commit-ish，省略时直接分析当前工作区。分析沿用入口POM限定的bounded reactor scope，不扫描整个Git仓库。

比较结果以Module和`DependencyKey`为稳定边界。主表回答resolved version、scope与direct dependency在两侧的差异；按需展开的子表回答dependency chain结构与`managedFromVersion` evidence。Reactor或Module结构单侧缺失属于`STRUCTURE_MISMATCH`，不推导为整Module依赖新增或删除。

## Design Decisions

- None.

## Actors / Entrypoints

- 用户执行`dependency-analyzer tree diff --baseline <commit-ish> [--target <commit-ish>] --output <dir>`。
- CI归档`<output>/index.html`及`<output>/tree-diff-report/`。
- 用户从Index进入Reactor page，只通过Module detail中的Module selector选择当前Module，再筛选依赖、展开chain或查看双侧文本树。

## Behavior Contract

### Workspace与范围

- baseline总是detached worktree；显式target也是detached worktree；省略target时使用当前工作区，Analyzer不stash、不restore、不清理用户build output。
- 两侧分别解析相同relative analysis path的reactor inventory，范围仍为`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE`。
- ReactorKey使用repository-relative root POM；ModuleKey使用entry analysis path相对POM路径。两类key都稳定排序并用于结构配对。
- `--scopes`默认`compile,runtime,provided,system`，并同时约束依赖存在性、resolved version、scope、分类和chain。

### 状态与退出码

- 单侧状态为`PRESENT`、`ABSENT`、`UNAVAILABLE`；复合状态为`COMPARABLE`、`STRUCTURE_MISMATCH`、`UNAVAILABLE`，其中`UNAVAILABLE`优先。
- ReactorKey或ModuleKey单侧缺失时报告`STRUCTURE_MISMATCH`，该范围没有dependency-level diff。
- 全部Module成功可比较时终态`SUCCESS`、退出`0`。
- 至少一个Module可比较且另有结构或采集问题时终态`COMPLETED_WITH_ISSUES`、退出`2`。
- 没有可比较Module，或pipeline/publication失败时终态`FAILED`、退出`2`；CLI或preflight失败退出`1`。
- `COMPLETED_WITH_ISSUES`和`FAILED`都保留此前完整发布的Reactor页面。

### 依赖与链路语义

- 每侧先按`DependencyKey`聚合已选scope内occurrence，取标量resolved version、scope，并以“任一路径为direct”聚合directness。
- resolved version不同为`VERSION_CHANGED`；只存在target为`ADDED`；只存在baseline为`REMOVED`；两侧存在且resolved version相同为`RESOLVED_UNCHANGED`。
- 两侧均存在且scope不同则`scopeChanged=true`，可与`VERSION_CHANGED`或`RESOLVED_UNCHANGED`叠加。单侧依赖不使用scope变化标签。
- 主表一行对应一个`DependencyKey`，固定六列：Dependency、Version、Scope、Direct dependency、Change type、Actions。
- `PathKey`为移除Module根节点后的有序`DependencyKey`序列，忽略version与scope。长度和每个位置均相同才匹配。
- chain子表固定四列：Baseline dependency chain、Target dependency chain、Version、Dependency chain structure change type。结构值只为`Added`、`Removed`、`Unchanged`。
- chain occurrence resolved version独立展示；非空`managedFromVersion`显示`Managed from <version>`次级badge。

### 离线Report

- 输出包含Index、按Reactor隔离的HTML页面和相邻data directory。Reactor文件名使用稳定slug加SHA-256前6字节。
- Reactor HTML只内嵌轻量manifest与Module catalog。完整Module summary、Dependency catalog、主表row、chain row和tree pair使用callback shard。
- Module summary固定为Module、Status和五项英文指标共七列，不提供Actions或行点击；Module detail selector是唯一Module切换入口。默认选择第一个`COMPARABLE` Module，没有时选择稳定排序后的第一个Module；选择跨summary页的Module时summary同步跳转到对应页。
- Dependency与change type筛选按AND组合，使用预计算range求交，只加载当前页。Dependency combobox可检索当前Module全部候选，但一次最多创建50个option DOM。
- 主表和chain子表页大小为10、50、100；同一时间最多展开一个chain子表。chain分页与page size使用紧凑、弱化且右对齐的次级控件，不复用主表toolbar和pager视觉层级。
- Reactor主内容占满viewport并保留桌面16px、小屏9px安全边距。双侧文本树宽屏并排、窄屏上下堆叠；每侧树按内容完整撑开，不产生纵向滚动，超长行只在自身区域横向滚动。
- 普通shard UTF-8目标上限为4 MiB；单条超限record独占shard并生成技术告警，不截断、不改变终态。
- 数据通过`textContent`和DOM API构造；页面不使用backend、network、CDN或浏览器持久化存储。

## Core Flow

1. 校验common option、当前checkout路径、Git metadata、Maven runtime与Dependency Plugin runtime。
2. 创建tree command-owned run；准备baseline与target workspace，并把relative analysis path映射到两侧。
3. 两侧分别解析bounded reactor inventory，按ReactorKey做union配对。
4. 每个配对Reactor依次完成baseline采集、释放侧内中间数据、target采集、结构校验与occurrence-aware diff。
5. `TreeDiffEngine`按ModuleKey配对，聚合dependency、分类、计算指标并按PathKey配对chain。
6. `TreeDiffReportDataWriter`分配Reactor内连续ID、建立range与callback shard；Renderer原子发布完整Reactor页面。
7. Session刷新Index checkpoint并释放该Reactor重结果；结束时发布`SUCCESS`、`COMPLETED_WITH_ISSUES`或`FAILED`。

## Acceptance Criteria

### Functional

- Given annotated tag可peel为commit；When作为baseline或target；Then命令解析到commit并使用detached worktree，不执行fetch。
- Given target省略且当前工作区dirty；When执行diff；Thentarget直接使用当前工作区，Report记录Maven执行前的dirty状态，用户文件不被恢复或删除。
- Given一侧缺少Reactor或Module；When比较；Then状态为`STRUCTURE_MISMATCH`，不生成整Module的`ADDED`或`REMOVED`依赖。
- Given同一DependencyKey resolved version变化且scope变化；When比较；Then基础标签为`Version changed`并叠加`Scope changed`，Module两个指标各加一。
- Givenresolved version相同但scope变化；When比较；Then基础标签为`Resolved version unchanged`并叠加`Scope changed`。
- Givendirect变transitive且version/scope相同；When比较；Then主表显示`Yes → No`，但不新增标签或指标。
- Given同一dependency的PathKey一侧新增、一侧删除；When展开chain；Then子表分别显示`Added`与`Removed`，不改变dependency-level基础分类。
- GivenModule有26条依赖；When首屏页大小为10；Then只创建10条主表DOM，未展开时不加载chain shard。
- Given用户展开第二个dependency；When已有chain展开；Then旧子表关闭，页面只保留一个chain table。
- Given用户查看Module summary；When需要切换Module；Thensummary没有Actions列或按钮，只能使用Module detail selector。
- GivenTree Diff页面完成动态渲染；When检查固定界面文案；ThenHTML语言为英文且不包含中文固定文案，外部数据保持原文。
- Givenviewport为1920px；When查看Reactor内容；Then主要section左右边距不超过16px。Given viewport为390px；Then边距为9px、tree pair上下堆叠且document无横向溢出。
- Given任一dependency tree高于520px；When显示tree pair；Then`pre`按完整内容高度展开，无纵向滚动。
- Given局部Reactor采集失败且此前页面已完成；When命令结束；Then终态与退出码符合状态合同，已完成页面仍可从Index访问。

### Non-Functional

- [ ] Reactor、Module、Dependency、PathKey、row、range、shard和filename均deterministic。
- [ ] 每个普通shard不超过4 MiB目标；超限单record完整保留并可审计。
- [ ] Report在无backend、无network的`file://`环境完成Module切换、筛选、分页、chain展开和tree加载。
- [ ] 首屏只加载当前Module与当前页必需payload，不把全Reactor重数据常驻DOM。
- [ ] 动态内容不解释为HTML；交互支持键盘、focus ring、ARIA状态和不依赖颜色的文本标签。
- [ ] chain次级控件在尺寸、字号和背景上弱于主表控件，同时保持page size与Previous/Next键盘操作。

## Edge Cases

- 非Git目录、无入口POM、invalid commit-ish、ref中缺少relative analysis path、scope非法或Maven runtime不可用在preflight阻断，旧Report不替换。
- Maven采集失败使对应侧或Module为`UNAVAILABLE`；不得以空依赖集合继续比较。
- 空依赖Module仍可比较，指标全为零，tree pair显示明确空状态。
- shard缺失、损坏、callback缺失或Schema不匹配时对应组件显示Retry，不伪装成零结果，也不执行payload中的HTML。
- target当前工作区在分析中变化不建立持续snapshot；metadata只承诺Maven前采集的commit与dirty事实。

## Implementation Boundaries

- Workspace只拥有Git隔离和metadata；collector只拥有Maven采集；diff domain不执行Git、Maven或Report渲染。
- Tree Diff不复用`dependency.DependencyDiffEngine`。后者为Impact按artifact去重的JAR变更输入；Tree Diff保留occurrence、scope、directness和PathKey语义。
- Renderer不执行采集或分类；browser不回调Analyzer。Java tests拥有domain、Schema、range和publication合同，Playwright拥有真实浏览器交互与响应式合同。
- Tree Diff Schema v1只面向新生成的静态Report，不迁移旧artifact；重新执行命令生成完整目录。
