# Dependency Analyzer 用户手册

## 1. 产品简介

Dependency Analyzer 是 Java 17 CLI，面向 Maven project：

- `impact`：比较 dependency 升级前后的 resolved dependency、bytecode 和业务调用影响，输出 HTML 或 Markdown。
- `tree`：扫描一个 Git repository 内的 Maven reactor，输出 repository 级 offline HTML dependency tree report。

当前版本为 `0.1.0-SNAPSHOT`。Root command 为 `dependency-analyzer`，旧的无 subcommand 调用不再支持。

## 2. 环境与运行

要求：

- Java 17 runtime。
- Git command 可运行。
- Target Maven project 所需 repository、mirror、proxy、credential 和 local repository 已通过 Maven settings 配置。

构建后执行：

```sh
java -jar target/dependency-analyzer.jar --help
```

通用调用形态：

```text
java -jar dependency-analyzer.jar [global-options] <subcommand> [options]
```

Global options 可放在 subcommand 前或后：

| Short | Long | 说明 |
|---:|---|---|
| `-m` | `--maven <executable>` | 指定 Maven executable，例如 `/opt/maven/bin/mvn` 或 `C:\tools\maven\bin\mvn.cmd`。必须为 executable path，不是 Maven home。 |
| `-j` | `--maven-java-home <dir>` | 仅覆盖 Maven subprocess 的 `JAVA_HOME`。 |
| `-c` | `--config-dir <dir>` | 覆盖完整 Dependency Analyzer config dir。 |
| `-a` | `--maven-arg=<token>` | 重复传入一个 Maven option/property token，例如 `--maven-arg=-Pprod`。 |

## 3. Maven Runtime

Maven 选择优先级：

1. `--maven` 指定 executable。
2. Config dir 中已经通过 SHA-512 校验的 Apache Maven 3.6.3 runtime。
3. 从 JAR resource 离线解压 Apache Maven 3.6.3。

工具不会隐式使用 PATH Maven，也不会自动使用 repository Maven Wrapper。支持 version 为 `>=3.6.3 && <4.0.0`；Maven 3.6.2 和 Maven 4 会在 preflight 阻断。

默认 config dir：

```text
${user.home}/.dependency-analyzer/
├─ runtime/apache-maven/3.6.3/<zip-sha512>/
├─ runtime/maven-dependency-plugin/3.6.1/<repository-zip-sha512>/
├─ runtime/maven-dependency-plugin/settings/<settings-sha512>.xml
├─ locks/
└─ config.properties  # 预留，可不存在
```

Apache Maven 3.6.3 已 EOL；preflight evidence 会展示该事实。工具仍支持该内嵌 version，以提供确定、offline 的默认 runtime。

Config dir 中未知文件和用户文件不会被自动删除。Runtime 损坏时只重建明确归属工具的 version/SHA leaf。

`tree` 默认还会从 JAR 解压经过 SHA-512 校验的 Dependency Plugin 3.6.1 file repository。工具生成 global settings overlay，将该 repository 加入 active profile，并保留用户 `-gs` 中的 mirror、proxy、server 等配置以及独立 `-s` 参数；内置 repository 会从通配 mirror 中排除。Overlay 按内容 hash 复用，使用 file lock、owner-only permission 和 atomic publish，且不会在 Console 输出用户 settings 内容。

## 4. Maven Argument 安全规则

每个 `--maven-arg` value 作为独立 process token，不经 shell。允许 profile、property 和 settings option，例如：

```sh
java -jar dependency-analyzer.jar \
  --maven-arg=-Pproduction \
  --maven-arg=-Denv=ci \
  --maven-arg=-s \
  --maven-arg=config/settings.xml \
  tree --output build/dependency-report
```

`-s`/`--settings`、`-gs`/`--global-settings` 后的相对 path 以 repository root 解析。

不允许附加 lifecycle phase 或 goal，也不允许覆盖工具控制的参数。至少拒绝：

```text
-f --file -pl --projects -am -amd -N --non-recursive -q --quiet
-DoutputFile -DoutputType -DappendOutput -Dverbose -Dtokens
```

## 5. `impact` Subcommand

### 5.1 CLI

```text
dependency-analyzer impact \
  [-p, --path <dir>] \
  -b, --baseline <local-ref> \
  [-t, --target <local-ref>] \
  -o, --output <file> \
  [-f, --format html|md] \
  [-k, --include-change-kinds <csv>]
```

- `-p, --path` 默认 current directory；定位所属 Git repository，并指定本次分析的 Maven project directory。该目录必须存在 readable root `pom.xml`。
- `--baseline` 必填，只解析 local ref。
- `--target` 省略时使用 current checkout；显式提供时使用 detached worktree。
- `--output` parent 必须存在且可写。
- `--format` 默认 `html`。
- `--include-change-kinds` 控制 bytecode `ChangePointKind`。
- 旧 `--build-java-home` 已 breaking rename 为 global `--maven-java-home`。
- 旧 `--project` 已删除，不保留 alias。

| Short | Long | 含义 |
|---:|---|---|
| `-p` | `--path` | Git repository 内的 Maven project/分析目录。 |
| `-b` | `--baseline` | 比较起点 local commit。 |
| `-t` | `--target` | 比较终点 local commit；不是 tree snapshot ref。 |
| `-o` | `--output` | HTML/Markdown Index 文件。 |
| `-f` | `--format` | `html` 或 `md`。 |
| `-k` | `--include-change-kinds` | 纳入分析的 `ChangePointKind` CSV。 |

### 5.2 示例

比较 release ref 与 current checkout：

```sh
java -jar dependency-analyzer.jar impact \
  --path . \
  --baseline release-1.2 \
  --output build/impact.html
```

比较两个 local ref，并输出 Markdown：

```sh
java -jar dependency-analyzer.jar \
  --maven /opt/apache-maven/bin/mvn \
  impact \
  --baseline main \
  --target feature/dependency-upgrade \
  --output build/impact.md \
  --format md
```

### 5.3 Pipeline 与报告

Preflight 全部通过后，pipeline 执行：workspace → Maven compile → GraphML dependency tree → dependency diff → JAR locator → bytecode diff → Call Graph → impact tracing → report。

Report 包含 dependency changes、internal changes、impact paths、diagnostics、完整 preflight checks 和 Maven runtime metadata。

`impact` 没有可安全降级的核心分析阶段；build、dependency extraction、bytecode 或 Call Graph failure 为 pipeline failure。

## 6. `tree` Subcommand

### 6.1 CLI

```text
dependency-analyzer tree \
  [-p, --path <dir>] \
  [-r, --ref <local-ref>] \
  -o, --output <dir> \
  [-s, --scopes <csv>] \
  [-d, --dependency-plugin-version <version>]
```

- `-p, --path` 默认 current directory；解析真实 directory、所属 Git root 及 Git-root-relative analysis path。
- `--ref` 省略时分析 current checkout；提供时创建单个 local commit snapshot 的 detached temporary worktree，不 fetch。它不表示 impact comparison target。
- `--output` 是 HTML report directory。
- `--scopes` 默认 `compile,runtime,provided,test,system`。
- `--dependency-plugin-version` 默认为 JAR 内置的 `3.6.1`；指定时作为高级 override，并必须通过完整 evidence capability check。
- 旧 `--repository` 已删除，不保留 alias。

| Short | Long | 含义 |
|---:|---|---|
| `-p` | `--path` | Git repository 与 Report 分析范围。 |
| `-r` | `--ref` | 单个 tree snapshot 的 local Git ref。 |
| `-o` | `--output` | Offline HTML report directory。 |
| `-s` | `--scopes` | 纳入 parser/统计/report 的 dependency scope CSV。 |
| `-d` | `--dependency-plugin-version` | Maven Dependency Plugin version override。 |

### 6.2 Repository/Reactor 发现规则

- 扫描 Git tracked 与 non-ignored untracked `pom.xml`。
- 不扫描 ignored path 和 Git submodule。
- 未被其他 POM `<modules>` 引用的 POM 是 reactor root。
- 目录嵌套但无 `<modules>` 关系的 POM 是独立 reactor。
- 所有 profile module declaration 参与 ownership，只有本次 active profile module 生成 module section。
- Inventory 始终读取完整 Git repository POM，以获得真实 reactor root、ownership 和 active module closure。
- `activePoms` 保存完整 active reactor；`requestedPoms` 保存物理目录位于 `--path` 下的 active POM；`rootSelected` 表示 reactor root POM 是否被 path 命中。
- `rootSelected=true` 时以该 reactor 的全部 `activePoms` 建立 Maven execution scope。选择 Git root、单个 reactor root 或包含多个 independent reactor root 的上级目录时，每个命中的 reactor 都保持 full-reactor execution；root 与 child 同时命中时 root rule 优先。最终 Module result 仍排除 packaging 为 `pom` 且存在 active child 的纯 aggregator root。
- `rootSelected=false` 时使用工具控制的 `-pl <requested modules> -am`。Report 先展示用户选定 module，再展示其 resolved dependency tree 中符合 `--scopes`、GAV 匹配且属于同 reactor 的传递依赖 module，两组分别按 POM path 排序。
- 范围外 reactor root、parent、aggregator、无关 sibling 及仅因 plugin/extension 被 Maven 使用的 support project 可以辅助 Maven resolution，但不进入 Report。Independent reactor dependency 不跨 reactor 追踪，仍作为普通 resolved artifact。
- Root-selected reactor 会展示所有具有 dependency analysis result 的 active module。即使 root 的 `<module>` 指向其目录外、但仍在 Git repository 内，该 active module 也进入完整 reactor Report。
- Packaging 为 `pom` 且存在 active child 的纯 aggregator root 仍保留为 Maven execution entrypoint，以维持真实 reactor build context；它不生成 Module result，也不计入 Report 的 Module 统计。无 active child 的 `pom` project 与 packaging 为 `jar`、`war` 等且同时聚合 children 的 root 仍作为 Module 分析。
- Repository 外部 module path 会阻断对应 reactor，不影响其他 reactor。
- 只分析实际进入 resolved project dependency tree 的 `dependencies`。不分析 build/report plugin、extension 或 plugin dependency tree，也不枚举未被 dependency occurrence 使用的 `dependencyManagement`/imported BOM 清单。

### 6.3 示例

分析 current checkout：

```sh
java -jar dependency-analyzer.jar tree \
  --path . \
  --output build/dependency-tree
```

分析 local ref，激活 profile，并只保留 runtime 相关 scope：

```sh
java -jar dependency-analyzer.jar \
  --maven-arg=-Pproduction \
  tree \
  --path services/payment \
  --ref release-2.0 \
  --scopes compile,runtime \
  --output build/release-dependencies
```

高级 override（默认情况不需要指定）：

```sh
java -jar dependency-analyzer.jar tree \
  --dependency-plugin-version 3.6.1 \
  --output build/dependency-tree
```

### 6.4 Report Layout

```text
<output>/
├─ index.html
└─ dependency-report/
   ├─ reactors/
   │  └─ <reactor-slug>-<stable-hash>.html
   └─ assets/
      ├─ report.css
      └─ report.js
```

Index 使用表格展示：

- Metadata：repository/ref/commit/dirty、requested/analysis path、Maven runtime、scope 与 Maven arguments。
- Summary：Reactor、Module、Dependency、Internal conflict 与 Cross-module conflict 总数。
- Command Preflight：各 check 的 status、decision 与 evidence。
- Reactors：每个已发布 Reactor 的状态、Module/Dependency 数，以及分开的 Internal conflicts/Cross-module conflicts 计数。

Reactor page 展示：

- Reactor metadata 表：coordinate、root POM、analysis mode、status、module/dependency/issue 数，以及分开的 Internal conflicts/Cross-module conflicts 计数。
- Module metadata 表：Module、POM、status、dependency/issue 数，以及分开的 Internal conflicts/Cross-module conflicts 计数；不展示内部 role 或纳入原因。
- 单一“问题”表汇总 Reactor/module issue；无 issue 时整个 section 隐藏。
- 独立的 Cross-module conflicts section，汇总 Reactor 内不同 Module 的 resolved version 差异。
- Module tabs；每个 tab 只展示该 Module 的 internal conflicts 与一段 Maven-style verbose `<pre class="dependency-tree">` dependency tree。
- Internal 与 Cross-module conflict table 都支持 component-scoped 全字段 search、Scope filter、sortable columns 和 10/50/100 pagination；Cross-module table 额外提供 Module filter。每个 Module tab 与 Cross-module section 的交互状态互不影响；空表保留 header，但不显示 controls。
- Dependency tree 以 Maven 的 `+-`、`\-` 和缩进 chain 呈现；annotation 依次展示 `version managed from`、`scope managed from`、`optional`、omitted reason 与 `reactor module` evidence。Tree 内不生成 button、link、`<details>`、tooltip 或 click 行为，也不提供 expand/collapse、检索或过滤。

Report 无 CDN、remote font、network API 或 server endpoint，可直接通过 `file://` 打开和 CI 归档。

重复生成时只替换 `<output>/index.html` 与 `<output>/dependency-report/`，output root 其他文件保持不变。

### 6.5 Incremental Report 生命周期

Command-level Preflight failure 返回 exit `1`，不清理或覆盖旧 Report。Command-level Preflight 成功后：

1. 清理旧工具拥有的 `index.html` 与 `dependency-report/`，保留 output root 其他文件。
2. 立即发布 assets、空 reactors directory 和 `RUNNING 0/N` Index。
3. 每个 reactor 完成后先原子发布 reactor page，再原子刷新 `RUNNING x/N` Index；Index 只链接已完整发布的 page。
4. 全部 reactor 无 issue 时写 `SUCCESS N/N`；全部已处理但存在 issue 时写 `COMPLETED_WITH_ISSUES`。pipeline/report failure 保留已有 page 并写 `FAILED x/N`。

如果 process 被 kill 或发生无法捕获的硬中断，最后一次成功 checkpoint 保持 `RUNNING x/N`，用户仍可直接通过 `file://` 打开已经发布的 page。Index metadata 展示输入 path、resolved Git root 与 relative analysis path。

## 7. Version Analysis 口径

Dependency identity 使用 Maven conflict key：

```text
groupId + artifactId + type + classifier
```

Module 内 dependency conflict：

- Type 为 `MODULE_MEDIATION`，以单个 Module 为边界。
- 每个 occurrence 的原始 requested version：若 Maven verbose text 含 `version managed from X`，取 `X`；否则取 occurrence node version。
- 每条实际 dependency path 提供一个 `DEPENDENCY_PATH` 版本来源；存在 `version managed from X` 时，同一 occurrence 额外提供一个 `DEPENDENCY_MANAGEMENT` 来源，其版本为 management 应用后的 effective version。
- 同一 conflict key 的全部版本来源至少出现两个不同版本即构成冲突。因此单条 path 从 `1.0` 被管理为 `2.0` 也必须报告；仅 path 不同、version 相同，或仅出现同版本 `omitted for duplicate`，不会单独构成冲突。
- 冲突成立后，Module tab 内的 internal conflict table 每个 dependency issue 一行，Evidence 是最后一列。Evidence 子表固定为 `Source`、`Dependency chain`、`Original version`、`Scope`；相同 version 的不同 dependency chain 不折叠。
- `DEPENDENCY_PATH` Evidence 展示完整 occurrence chain。`DEPENDENCY_MANAGEMENT` Evidence 的 `Dependency chain` 是空单元格，因为 Maven annotation 只证明 management 前后值，不提供 management 来源 chain。

`dependencyManagement` 与 `managedFrom`：

- 分析范围只覆盖已经进入 dependency tree 的 occurrence；工具不会列出完整 `dependencyManagement`、imported BOM 或未使用的 managed entry。
- `managedFromVersion`/`managedFromScope` 只在 Maven verbose annotation 明确给出 `version managed from ...`/`scope managed from ...` 时存在。
- `managedFrom` 的值是 management 覆盖前的 version/scope，不是 management 定义所在的 POM、parent 或 BOM。当前 Report 不推断管理来源文件。
- `effectiveVersion`/`effectiveScope` 是 management 应用后的 node 值；`resolvedVersion` 是 Maven conflict resolution 的最终 selected version。

Omitted evidence：

- `conflict` 表示该 occurrence 因同 conflict key 的另一个 version 被选中而省略；其 `resolvedVersion` 来自 `omitted for conflict with Y` 中的 `Y`。
- `duplicate` 表示 Maven 认为该 dependency path 重复。它保留在 Maven-style verbose tree annotation 中，但不等价于 version conflict。
- `cycle` 与 Maven 未归类的 raw reason 同样保留在 verbose tree annotation 中。

跨 module resolved version：

- Type 为 `CROSS_MODULE_RESOLUTION`，是否构成冲突只比较 selected occurrence。
- 同一 conflict key 在 Reactor 的不同 Module occurrence 中至少出现两个不同 resolved version 才构成冲突。
- 冲突成立后进入 Reactor-level Cross-module conflicts section，而不是复制到每个 Module tab。每个 dependency issue 一行，Evidence 是最后一列。
- Cross-module Evidence 子表固定为 `Source`、`Module`、`Dependency chain`、`Original version`、`Scope`。`DEPENDENCY_PATH` 行保留完整 chain；`DEPENDENCY_MANAGEMENT` 行的 chain 为空。Module、Scope、Resolved version 列展示该 conflict 涉及值的集合。

JAR 内置 `maven-dependency-plugin:3.6.1` 及其完整传递依赖 repository，默认执行不依赖远程 plugin download。`-d` override 无法提供完整 omitted/managed/optional evidence 时在 Command Preflight 阶段阻断，不生成不完整的冲突结论。

### 7.1 Console 进度

`tree` Console 固定为 `Preflight → Analysis → Summary`。Analysis 中输出每个 reactor 的 collection、analysis、page publish 和 checkpoint；Maven collection 超过 10 秒后每 10 秒输出 heartbeat。成功不输出 raw Maven log，失败只输出最后 100 行。Summary 只包含 `status` 与 `report`。

## 8. Preflight Status、Decision 与 Exit Code

Check status：`PASS`、`WARN`、`FAIL`、`SKIPPED`。

Decision：

| Decision | 含义 |
|---|---|
| `CONTINUE` | 正常继续。 |
| `DEGRADE` | 使用 check 明确记录的 fallback。 |
| `BLOCK_REACTOR` | 只跳过当前 reactor。 |
| `BLOCK_COMMAND` | Pipeline 不启动。 |

Exit code：

| Code | 含义 |
|---|---|
| `0` | `impact` 完整成功，或 `tree` 终态为 `SUCCESS`。 |
| `1` | CLI parse/validation 或 command-level preflight failure；不生成 report。 |
| `2` | `tree` 终态为 `COMPLETED_WITH_ISSUES`/`FAILED`，或其他 pipeline/report failure。 |

## 9. Troubleshooting

### Unsupported Maven version

检查 console `tree.maven-version` 或 `impact.maven-version` evidence。将 `--maven` 指向 Maven 3.6.3–3.x，或移除 `--maven` 使用内嵌 3.6.3。

### Maven executable cannot run

确认 `--maven` 是 executable file，不是 Maven home；Windows 应指向 `mvn.cmd`。若只需 Maven subprocess 使用另一 JDK，设置 `--maven-java-home`。

### Maven dependency resolution failure

Dependency Analyzer 不把 project dependency local repository 放入 config dir。内置 file repository 仅提供默认 Dependency Plugin 及其传递依赖；project artifact 仍按用户 Maven settings、mirror、proxy、credential 和 local repository 解析。

### Dependency Plugin capability failure

默认内置 `3.6.1` 提供完整 verbose evidence。显式 `--dependency-plugin-version` 若不具备完整 omitted/managed/optional evidence，会在 Command Preflight 阻断；选择 2.9/2.10 或 3.2.0+，并确保该 override 可从用户 Maven repository 解析。

### Reactor 为 `FAILED`

在 index/reactor page 查看“问题”表和 Analysis diagnostics。常见原因包括 malformed POM、active module POM 缺失、module path 越出 repository、plugin goal 无法解析或 Maven 部分 module failure。

### Config dir runtime 损坏

再次执行 command 会自动校验并只重建对应 Maven 或 Dependency Plugin version/SHA leaf；损坏的 generated settings 也会按内容重建。不要整体删除 config dir；其中可能包含未来配置或用户文件。

## 10. Third-Party Attribution

Uber JAR 内包含未修改的 Apache Maven 3.6.3 binary distribution，以及 distribution 的 `LICENSE`、`NOTICE` 和官方 SHA-512；同时包含 Maven Dependency Plugin 3.6.1 的完整运行 dependency repository、SHA-512、`LICENSE`、`NOTICE` 和 `DEPENDENCIES`。Source repository 中对应文件位于 `src/main/resources/maven/`。
