# Dependency Analyzer 用户手册

Dependency Analyzer 是面向 Maven 项目的 Java 命令行工具，提供两个相互独立的任务：

- `impact`：比较依赖升级前后的 resolved dependency 和 bytecode，分析可能受影响的业务调用路径，生成离线 HTML 报告。
- `tree`：从一个入口POM解析active Maven reactor scope，生成离线dependency tree与版本冲突报告。

本手册从“已经获得可运行的 `dependency-analyzer.jar`”开始。所有示例使用
`/path/to/dependency-analyzer.jar` 表示该 JAR 的实际绝对路径。

## 目录

- [1. 选择任务](#1-选择任务)
- [2. 准备环境](#2-准备环境)
- [3. 快速开始：impact](#3-快速开始impact)
- [4. 快速开始：tree](#4-快速开始tree)
- [5. impact 操作指南](#5-impact-操作指南)
- [6. tree 操作指南](#6-tree-操作指南)
- [7. 命令参考](#7-命令参考)
- [8. 报告、状态与退出码](#8-报告状态与退出码)
- [9. 工作原理与限制](#9-工作原理与限制)
- [10. Troubleshooting](#10-troubleshooting)
- [11. 第三方许可](#11-第三方许可)

## 1. 选择任务

| 目标 | 使用命令 | 必要输入 | 主要输出 |
|---|---|---|---|
| 判断一次依赖升级可能影响哪些业务入口和调用路径 | `impact` | Maven project、baseline local Git ref、完整 JDK 8 | Overall Index、Module Index、Affected Paths |
| 查看一个Maven project scope的resolved dependency tree与版本冲突 | `tree` | Git repository内且直接包含POM的Maven project | Repository Index、Reactor page |

两条命令可以独立使用：

- 只需要依赖树或版本冲突时，不必先运行 `impact`。
- 需要业务影响路径时，直接运行 `impact`；不要求先生成 `tree` 报告。

## 2. 准备环境

### 2.1 通用准备清单

运行任一命令前，确认：

- Analyzer JAR 使用 Java 17 runtime 启动。
- `git` command 可运行。
- 分析路径位于Git repository内，并直接包含readable`pom.xml`；只包含若干子项目的容器目录不是有效入口。
- 目标 Maven project 所需的 repository、mirror、proxy、credential 和 local repository 已通过 Maven settings 配置。
- 输出位置可写。

检查 Analyzer runtime 和 JAR：

```sh
java -version
java -jar /path/to/dependency-analyzer.jar --version
java -jar /path/to/dependency-analyzer.jar --help
git --version
```

`java -version` 应显示 Java 17。`--version` 输出当前 Analyzer 版本。

### 2.2 impact 额外要求

`impact` 必须通过 `--java-home` 指定完整 JDK 8。该目录同时用于：

- Maven subprocess 的 `JAVA_HOME`。
- target source compilation。
- WALA target JDK class hierarchy。

目录必须包含可执行的 `bin/java`、`bin/javac` 和 JDK 8 runtime `rt.jar`。JRE 或 Java 17 home 均不能代替。

检查示例：

```sh
/opt/jdk8/bin/java -version
/opt/jdk8/bin/javac -version
```

### 2.3 Maven runtime

Maven 选择顺序：

1. 使用 `--maven <executable>` 显式指定的 Maven executable。
2. 使用 Analyzer config dir 中已经完整准备的 Apache Maven 3.6.3。
3. 从 Analyzer JAR 离线准备内嵌 Apache Maven 3.6.3。

支持范围为 `3.6.3 <= Maven version < 4.0.0`。工具不会隐式使用 PATH Maven，也不会自动使用 target repository 的 Maven Wrapper。

显式指定时必须传 executable path，而不是 Maven home：

```sh
/opt/apache-maven/bin/mvn -version
java -jar /path/to/dependency-analyzer.jar \
  --maven /opt/apache-maven/bin/mvn \
  tree analyze --output build/dependency-tree
```

Windows 应指向 `mvn.cmd`，例如
`C:\tools\apache-maven\bin\mvn.cmd`。

### 2.4 默认 config dir

默认 config dir 为：

```text
<user-home>/.da/
```

其中保存内嵌 Maven、Plugin runtime、command workspace、temporary evidence 和 lock。每次 command 使用独立 run，正常或异常关闭只清理当前 run。工具不会自动删除 config dir 中的未知文件或用户文件。

## 3. 快速开始：impact

本教程比较一个 baseline local ref 与 current checkout。

### 3.1 准备输入

确认：

1. 当前目录或 `--path` 指定目录位于 Git repository 内。
2. 分析目录存在 readable root `pom.xml`。
3. baseline ref 已存在于本地；工具不会 fetch。
4. output directory 可写，或能够在可写的父目录下创建；已存在普通文件会被拒绝。
5. 已准备完整 JDK 8。

示例输入：

- 分析路径：current directory
- baseline：`release-1.2`
- target：current checkout
- output：`build/impact`
- JDK 8：`/opt/jdk8`

### 3.2 执行

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --path . \
  --baseline release-1.2 \
  --output build/impact
```

`--target` 省略时分析 current checkout。显式提供 `--target` 时，baseline 和 target 都必须是 local ref。

### 3.3 判断执行结果

首先检查 process exit code：

| Exit code | 含义 |
|---:|---|
| `0` | 分析完成；Overall status 为 `SUCCESS` 或 `INCONCLUSIVE`。 |
| `1` | 参数、输入或 command-level Preflight 失败；分析未启动。 |
| `2` | 分析为 `PARTIAL_SUCCESS` 或 `FAILED`；至少一个必要结果失败。 |

exit code 为 `0` 时也必须检查 Overall status。`INCONCLUSIVE` 表示报告可用，但存在 Coverage limitation，不能当作完整覆盖证明。

### 3.4 打开报告

示例输出：

```text
build/
└─ impact/
   ├─ index.html
   └─ modules/
      ├─ <module-base>.html
      ├─ <module-base>-impact.html
      └─ <module-base>-impact-data/
```

使用浏览器直接打开 `build/impact/index.html`。报告完全离线，可通过
`file://` 使用。

推荐阅读顺序：

1. Overall Index：确认 status、Preflight、分析范围和 Coverage limitation。
2. Module Index：查看 changed member 的 Impact、Structural 和总数。
3. Affected Paths：查看业务入口、完整调用路径和 Java code diff。

Affected Paths 为空不等于已经证明没有业务影响。还应检查 Module status、changed member 指标和 Coverage limitation。

## 4. 快速开始：tree

本教程从current checkout当前目录的入口POM解析一个Maven scope。

### 4.1 准备输入

确认：

1. 当前目录或`--path`位于Git repository内。
2. 该目录直接包含readable`pom.xml`。
3. Maven 能够解析 project dependency。
4. output directory 可创建或可写。

### 4.2 执行

```sh
java -jar /path/to/dependency-analyzer.jar tree analyze \
  --path . \
  --output build/dependency-tree
```

### 4.3 判断执行结果

| Exit code | 含义 |
|---:|---|
| `0` | 入口scope成功，报告状态为`SUCCESS`。 |
| `1` | 参数或 command-level Preflight 失败；旧报告不被替换。 |
| `2` | 报告为 `COMPLETED_WITH_ISSUES` 或 `FAILED`。 |

### 4.4 打开报告

示例输出：

```text
build/dependency-tree/
├─ index.html
└─ dependency-report/
   ├─ reactors/
   │  ├─ <reactor-base>.html
   │  └─ <reactor-base>-data/
   │     └─ *.js
   └─ assets/
      ├─ report.css
      ├─ report-common.js
      └─ report.js
```

使用浏览器直接打开 `build/dependency-tree/index.html`。

推荐阅读顺序：

1. Repository Index：确认 metadata、Preflight、Reactor/Module/Dependency 总数和整体状态。
2. Reactors 表：定位 `DEGRADED` 或 `FAILED` Reactor。
3. Reactor page：在“跨模块依赖分析”中筛选全部dependency occurrence，查看版本徽标与requested/resolved version。
4. Module分析card：选择Module后查看冲突类、Internal conflicts与Maven-style verbose dependency tree。

`tree`会先在选中Reactor范围执行Maven `compile`。依赖、Module内容和源码按需读取；源码只有点击“查看反编译代码”后才加载。

## 5. impact 操作指南

### 5.1 比较两个 local ref

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --path . \
  --baseline main \
  --target feature/dependency-upgrade \
  --output build/impact
```

显式 target 在独立 detached workspace 中分析，不切换当前 checkout。两个 ref 都只在本地解析。

### 5.2 只分析指定 changed dependency

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact \
  --dependency-include 'com.acme.payment:*' \
  --dependency-exclude 'com.acme.payment:*-internal'
```

include 取并集，exclude 始终优先。selector 只决定哪些
`VERSION_CHANGED` JAR pair 产生 bytecode ChangePoint；不会裁剪完整 resolved classpath。

### 5.3 限定业务 entrypoint

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact \
  --entrypoint-include 'com/acme/payment/**' \
  --entrypoint-exclude 'com/acme/payment/generated/**'
```

exclude 优先。Relevant Module 没有匹配 class 时标记
`SKIPPED_USER_ENTRYPOINT_SCOPE`；全部 relevant Module 均无匹配时 command 返回 exit code `1`，旧报告不被替换。

### 5.4 调整 dependency method-body scope

默认 `changed-paths` 只展开通往 selected changed dependency 的必要 dependency body，速度和内存占用通常更低：

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact \
  --dependency-analysis-scope changed-paths
```

需要展开全部 external dependency body 时使用 `full`：

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact-full \
  --dependency-analysis-scope full
```

`full` 可能显著增加 Call Graph 时间和内存。`changed-paths` 实际到达边界时会保留路径并将 Module 标记为
`INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`。

### 5.5 选择 Call Graph Algorithm

默认 Class Hierarchy Analysis（CHA，类层次分析）：

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact \
  --call-graph-algorithm cha
```

实验性 `k-obj`：

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact-kobj \
  --call-graph-algorithm k-obj \
  --k-obj-depth 1 \
  --wala-reflection-options ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD
```

选择规则：

- `cha` 固定使用 `--jdk-model none`，不应用 WALA ReflectionOptions。
- `k-obj` 默认使用 `--jdk-model jdk8`，也可显式使用 `none`。
- `--k-obj-depth` 只允许与 `k-obj` 同时使用，必须为正整数。
- `cha + jdk8` 是参数错误。
- `k-obj` 是 experimental algorithm，可能显著增加节点、边、时间和内存。

### 5.6 控制并发、超时和日志

```sh
java -jar /path/to/dependency-analyzer.jar -vv impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact \
  --analysis-parallelism 4 \
  --call-graph-timeout-seconds 1800
```

- `--analysis-parallelism` 控制 Analyzer-owned work 的全局并发上限。
- 默认值为可用 CPU 核数的一半，向下取整，最少为 `1`。
- `--call-graph-timeout-seconds 0` 表示无限等待。
- timeout 按 Module 计算；超时 Module 失败，其他 Module 继续。
- 默认日志为 `INFO`；`-v` 为 `DEBUG`；`-vv` 为 `TRACE`。
- 当前程序日志统一写入 stderr。`-vv` 会增加 Runtime Metrics 和细粒度审计，输出量明显增大。
- Maven 默认使用原生日志级别；`-v`、`-vv` 均向 Maven 传递 `-X`。显式 Maven `-X`、`--debug`、`-e`、`--errors` 保持有效，重复参数合并；程序不额外添加 `-e`。Git 保持原生输出行为。
- 外部程序已产生的日志实时输出至控制台，不再按当前程序日志级别过滤；`[process]` 前缀标识来源，`pid` 与 `stream` 区分进程及输出流。因此默认级别也能看到 Maven 普通进度和无前缀续行。
- HTML 报告保留分析结果、检查状态、简短原因以及预检查中的版本和路径等结构化事实，不提供运行日志或异常堆栈。程序不创建临时日志；需要留存控制台输出时，在原命令末尾追加 `> run.log 2>&1`。日志文件随实际输出量增长，没有固定大小上限。

### 5.7 向 Maven 传递 profile、property 和 settings

每个 `--maven-arg` value 是一个独立 process token，不经 shell：

```sh
java -jar /path/to/dependency-analyzer.jar \
  --maven-arg=-Pproduction \
  --maven-arg=-Denv=ci \
  --maven-arg=-s \
  --maven-arg=config/settings.xml \
  impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --output build/impact
```

`-s`、`--settings`、`-gs`、`--global-settings` 后的相对 path 以 target Git repository root 解析。

同一组token同时传给scope resolver和Maven subprocess。除显式`-P`与`-D`外，resolver还遵循`activeByDefault`、实际Maven JVM的JDK/OS、POM-relative file activation，以及user/global settings中的active profile；inactive profile module不参与ownership。

## 6. tree 操作指南

### 6.1 分析指定 local ref

```sh
java -jar /path/to/dependency-analyzer.jar tree analyze \
  --path . \
  --ref release-2.0 \
  --output build/release-dependencies
```

`--ref`省略时分析current checkout；提供时创建local ref的detached snapshot，不执行fetch。同一Git-relative path必须在目标ref中存在directory和readable`pom.xml`。

### 6.2 分析指定Maven project scope

```sh
java -jar /path/to/dependency-analyzer.jar tree analyze \
  --path services/payment \
  --output build/payment-dependencies
```

范围规则：

- path的入口POM存在active module时，入口就是aggregator；递归分析其active module subtree，不向外层reactor扩大。
- path是leaf Module时，只检查从入口父目录到Git root的祖先`pom.xml`。若多个祖先active module closure包含入口，选择最外层匹配aggregator，并执行`-pl <aggregator-relative-path> -am`。
- leaf的Maven session包含`-am`上游依赖，但Report只有入口Module；上游仍可作为`REACTOR_DEPENDENCY`进入classpath。
- 没有匹配祖先时按`STANDALONE`执行当前POM，不附加`-pl/-am`。非祖先aggregator不会被自动发现；相关依赖可能转为Maven repository解析，或因artifact不可用而失败。
- Git root下其他POM不会被枚举。Ignored POM、Git submodule内POM、symlink逃逸和Git root外module被拒绝。
- `packaging=pom`且存在active child的pure aggregator只作为execution context，不生成Module分析条目；单POM project和非`pom` aggregator仍生成Module结果。

### 6.3 激活 profile 并限制 scope

```sh
java -jar /path/to/dependency-analyzer.jar \
  --maven-arg=-Pproduction \
  tree analyze \
  --path services/payment \
  --scopes compile,runtime \
  --output build/payment-runtime-dependencies
```

默认scope为`compile,runtime,provided,system`，不分析`test` dependency。需要恢复原行为时，在`--scopes`中显式加入`test`。`--scopes`同时控制dependency tree、版本冲突与external/Reactor dependency冲突类扫描；当前Module的主类始终纳入。Maven执行期间仍可能解析被过滤的`test` dependency，但它们不进入分析结果。

### 6.4 高级覆盖 Maven Dependency Plugin version

```sh
java -jar /path/to/dependency-analyzer.jar tree analyze \
  --dependency-plugin-version 3.6.1 \
  --output build/dependency-tree
```

通常无需指定。覆盖版本必须在 Preflight 中通过完整 evidence capability check，否则 command 被阻断，避免生成不完整的冲突结论。

### 6.5 比较两个 dependency tree

比较 baseline local Git commit-ish 与 current workspace：

```sh
java -jar /path/to/dependency-analyzer.jar tree diff \
  --path services/payment \
  --baseline main \
  --output build/payment-dependency-diff
```

比较两个 local Git commit-ish：

```sh
java -jar /path/to/dependency-analyzer.jar tree diff \
  --path . \
  --baseline release-1.1 \
  --target release-1.2 \
  --scopes compile,runtime \
  --output build/release-dependency-diff
```

baseline与显式target都必须能够peel为commit；Analyzer不执行fetch。省略`--target`时直接分析current workspace，未提交修改参与比较，dirty状态在Maven执行前记录。两侧Reactor与Module结构必须一致；结构不一致显示`STRUCTURE_MISMATCH`，不会把整个Module误报为dependency新增或删除。

旧的`tree --ref ...`语法已移除，应迁移为`tree analyze --ref ...`。

## 7. 命令参考

### 7.1 调用形态

```text
java -jar /path/to/dependency-analyzer.jar [global-options] <subcommand> [options]
```

Global options 可放在 subcommand 前或后。

### 7.2 Global options

| Short | Long | Required | Default | Repeatable | 说明 |
|---:|---|:---:|---|:---:|---|
| `-h` | `--help` | No | — | No | 显示当前 command help 并退出。 |
| `-V` | `--version` | No | — | No | 显示 Analyzer version 并退出。 |
| `-m` | `--maven <executable>` | No | 内嵌 Maven 3.6.3 | No | 指定 Maven executable；支持 `3.6.3 <= version < 4.0.0`。 |
| `-j` | `--java-home <jdk-home>` | `impact` Yes | — | No | Maven subprocess 的 `JAVA_HOME`；`impact` 要求完整 JDK 8。 |
| `-c` | `--config-dir <dir>` | No | `<user-home>/.da/` | No | 覆盖完整 Analyzer config dir。 |
| `-a` | `--maven-arg=<token>` | No | 空 | Yes | 传入一个 Maven option 或 property token。 |
| `-v` | `--verbose` | No | `INFO` | Yes | `-v` 为 `DEBUG`，`-vv` 为 `TRACE`。 |

### 7.3 impact options

```text
dependency-analyzer impact \
  --java-home <jdk8-home> \
  --baseline <local-ref> \
  --output <file> \
  [options]
```

| Short | Long | Required | Default | Repeatable | 说明 |
|---:|---|:---:|---|:---:|---|
| `-p` | `--path <dir>` | No | current directory | No | Git repository内、直接包含readable POM的Maven project目录。 |
| `-b` | `--baseline <local-ref>` | Yes | — | No | 比较起点 local Git ref。 |
| `-t` | `--target <local-ref>` | No | current checkout | No | 比较终点 local Git ref。 |
| `-o` | `--output <file>` | Yes | — | No | Overall HTML Index；parent 必须存在且可写。 |
| `-f` | `--format <format>` | No | `HTML` | No | 仅接受 `html`；`md` 会 fail fast。 |
|  | `--analysis-target <target>` | No | `spring-backend` | No | 当前只接受 `spring-backend`。 |
|  | `--analysis-parallelism <count>` | No | `max(1, CPU / 2)` | No | Analyzer 全局并发上限；必须 `>=1`。 |
|  | `--call-graph-algorithm <algorithm>` | No | `cha` | No | `cha` 或 experimental `k-obj`；大小写不敏感。 |
|  | `--call-graph-diagnostics-output <file>` | No | 不输出 | No | 输出只读 Call Graph topology/source/Intermediate Representation diagnostics JSON；不影响 HTML。 |
|  | `--call-graph-timeout-seconds <seconds>` | No | `0` | No | Per-Module timeout；必须 `>=0`；`0` 表示无限等待。 |
|  | `--dependency-analysis-scope <scope>` | No | `changed-paths` | No | `changed-paths` 或 `full`。 |
|  | `--dependency-include <glob>` | No | 全部 | Yes | 选择 changed dependency `groupPattern:artifactPattern`。 |
|  | `--dependency-exclude <glob>` | No | 空 | Yes | 排除 changed dependency；优先于 include。 |
|  | `--entrypoint-include <pattern>` | No | 全部 eligible class | Yes | 选择 PROJECT entrypoint class path。 |
|  | `--entrypoint-exclude <pattern>` | No | 空 | Yes | 排除 PROJECT entrypoint class path；优先于 include。 |
| `-k` | `--include-change-kinds <csv>` | No | 见 7.7 | Yes | 纳入分析的 `ChangePointKind`，逗号分隔。 |
|  | `--jdk-model <model>` | No | CHA: `none`；k-obj: `jdk8` | No | `jdk8` 或 `none`。 |
|  | `--k-obj-depth <count>` | No | `1` | No | 仅用于 `k-obj` 的正整数 receiver allocation-string depth。 |
|  | `--reflection-options <name>` | No | `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD` | No | `--wala-reflection-options` 的 alias。 |
|  | `--wala-reflection-options <name>` | No | `ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD` | No | WALA `ReflectionOptions` enum name；CHA 不应用。 |

### 7.4 tree options

```text
dependency-analyzer tree analyze \
  --output <dir> \
  [options]

dependency-analyzer tree diff \
  --baseline <local-commit-ish> \
  --output <dir> \
  [options]
```

两个操作共用：

| Short | Long | Required | Default | Repeatable | 说明 |
|---:|---|:---:|---|:---:|---|
| `-p` | `--path <dir>` | No | current directory | No | Git repository内、直接包含readable POM的Maven project目录。 |
| `-o` | `--output <dir>` | Yes | — | No | Offline HTML report directory。 |
| `-s` | `--scopes <csv>` | No | `compile,runtime,provided,system` | No | 纳入dependency tree、版本冲突和external/Reactor dependency冲突类扫描的scope。 |
| `-d` | `--dependency-plugin-version <version>` | No | 内嵌 `3.6.1` | No | Maven Dependency Plugin 高级 override。 |

`tree analyze`独有：

| Short | Long | Required | Default | Repeatable | 说明 |
|---:|---|:---:|---|:---:|---|
| `-r` | `--ref <local-commit-ish>` | No | current checkout | No | 单个tree snapshot的local Git commit-ish；同一relative path必须含POM。 |

`tree diff`独有：

| Short | Long | Required | Default | Repeatable | 说明 |
|---:|---|:---:|---|:---:|---|
| `-b` | `--baseline <local-commit-ish>` | Yes | — | No | baseline；必须能够peel为commit。 |
| `-t` | `--target <local-commit-ish>` | No | current workspace | No | target；提供时创建detached worktree。 |

Global `--java-home` 对 `tree` 仅设置 Maven subprocess 的
`JAVA_HOME`；`tree` 不要求 JDK 8。

`--scopes` 只接受 `compile`、`runtime`、`provided`、`test` 和
`system` 的非空逗号分隔组合；值大小写不敏感，重复值会去重。

### 7.5 Maven argument 安全规则

允许 profile、property 和 settings option。每个 value 必须是独立 token：

```text
--maven-arg=-Pproduction
--maven-arg=-Denv=ci
--maven-arg=-s
--maven-arg=config/settings.xml
```

不允许追加 Maven lifecycle phase 或 goal，也不允许覆盖工具控制的参数。至少拒绝：

```text
-f --file -pl --projects -am -amd -N --non-recursive -q --quiet
-DoutputFile -DoutputType -DappendOutput -Dverbose -Dtokens
```

### 7.6 Glob 规则

#### Dependency Glob

格式必须为：

```text
groupPattern:artifactPattern
```

规则：

- 必须恰好包含一个 `:`，两段均非空且不含空白。
- `*` 匹配当前段零到多个字符；`?` 匹配一个字符；均不跨 `:`。
- `**` 没有特殊语义。
- 匹配区分大小写。
- 只匹配 target `groupId:artifactId`，忽略 version、type 和 classifier。
- 多个 include 取并集；重复 pattern 按首次出现去重；exclude 始终优先。
- 未传 include 表示全部。
- 显式 selector 未选中任何 `VERSION_CHANGED` JAR pair 时返回 exit code `1`，旧报告和 diagnostics output 保持不变。

#### Entrypoint class-path pattern

规则：

- 使用 slash-separated Java Virtual Machine（JVM）internal class path，例如
  `com/acme/payment/OrderService`。
- 可选 leading WALA `L` 前缀会在匹配前移除。
- `*` 匹配 segment 内零到多个字符；`?` 匹配一个字符；均不跨 `/`。
- `**` 只能作为最后一个完整 segment；`com/acme/**` 匹配直属及任意深度 package 中的 class。
- 最后一个普通 segment 是 class segment，允许使用 `$` 匹配 nested class；package segment 不允许 `$`。
- 多个 include 取并集；exclude 优先。
- leading/trailing slash、空 segment、`.`、反斜杠和 `:` 非法。
- `com/**/A`、`com/acme/A**` 等不符合上述结构的 pattern 非法。

### 7.7 ChangePointKind

`--include-change-kinds` 接受以下 canonical value，匹配时大小写不敏感：

| Value | 默认纳入 | 含义 |
|---|:---:|---|
| `CLASS_ADDED` | No | 新增 class。 |
| `CLASS_REMOVED` | Yes | 删除 class。 |
| `CLASS_ACCESS_NARROWED` | Yes | class access 收窄。 |
| `METHOD_ADDED` | No | 新增 method。 |
| `METHOD_REMOVED` | Yes | 删除 method。 |
| `METHOD_DESCRIPTOR_CHANGED` | Yes | method descriptor 变化。 |
| `METHOD_BODY_CHANGED` | Yes | method body 变化。 |
| `METHOD_ACCESS_NARROWED` | Yes | method 或 constructor access 收窄。 |
| `FIELD_ADDED` | No | 新增 field。 |
| `FIELD_REMOVED` | Yes | 删除 field。 |
| `FIELD_DESCRIPTOR_CHANGED` | Yes | field descriptor 变化。 |
| `FIELD_ACCESS_NARROWED` | Yes | field access 收窄。 |
| `SERVICE_PROVIDER_REGISTRATION_REMOVED` | Yes | 删除有效 ServiceLoader provider registration。 |

### 7.8 WALA ReflectionOptions

`--wala-reflection-options` 与 `--reflection-options` 接受以下精确 enum name，匹配时大小写不敏感：

```text
FULL
APPLICATION_GET_METHOD
NO_FLOW_TO_CASTS
NO_FLOW_TO_CASTS_APPLICATION_GET_METHOD
NO_METHOD_INVOKE
NO_FLOW_TO_CASTS_NO_METHOD_INVOKE
ONE_FLOW_TO_CASTS_NO_METHOD_INVOKE
ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD
MULTI_FLOW_TO_CASTS_APPLICATION_GET_METHOD
NO_STRING_CONSTANTS
STRING_ONLY
NONE
```

该 option 只对 `k-obj` 生效；CHA 在报告中显示
`not applied by cha`。

## 8. 报告、状态与退出码

### 8.1 impact 报告

`--output` 指向报告目录，Overall Index 位于其中的 `index.html`。每个非 `SKIPPED` Module 在 `modules/` 下生成：

```text
<module-base>.html               Module Index
<module-base>-impact.html        Affected Paths
<module-base>-impact-data/       Affected Paths local data
```

#### Overall Index

用于确认：

- Overall status 与每个 Module status。
- Preflight 检查与实际 Maven/JDK runtime。
- dependency change、Impact、Structural 和 affected method 汇总。
- effective Call Graph Algorithm、JDK Method Model、dependency scope。
- Coverage limitation、Class conflict resolution、SSA evidence和Decompiled Java evidence。

#### Module Index

Changed members 表包含全部 selected effective changed member，包括 Impact 和 Structural 均为 `0` 的 member。

核心列：

- Changed dependency。
- `ChangePointKind`。
- Changed member/class。
- Impact。
- Structural。
- Impact total，其中 `Impact total = Impact + Structural`。
- Code diff；只对`Impact total > 0`的member按需展开，零链路member不生成额外comparison。

页面只有一个搜索框，对Changed dependency与完整changed member JVM签名执行大小写不敏感的子串搜索。Dependency scope使用独立Include/Exclude Glob与`Apply scope`；普通筛选支持target `groupId:artifactId`、完整changed member签名、是否存在Impact chain及`ChangePointKind`，并支持pagination。浏览器Scope和filter只能缩小CLI已分析的数据，不能恢复CLI selector排除的member。

#### Affected Paths

提供 Impact、Structural 和 All 视图。每行对应唯一的
`(impactPath, changedMember)`，展示：

- 受影响的 PROJECT methods。
- changed dependency 与精确 `ChangePointKind`。
- changed member。
- Root Impact Path。
- Vineflower decompiled Java unified diff。

Affected Paths只有一个全局搜索框，搜索Affected application methods、Changed dependency、Changed member/class与Impact path，任一列命中即可。Dependency scope独立提交；普通筛选支持target `groupId:artifactId`、完整changed member签名和单个Affected application method完整签名。Scope、搜索、筛选与View type共同缩小结果。一行包含多个Affected application methods时，选择任一方法均保留该行。

Report中的method使用`dotted.owner#name(JVM descriptor)`，field使用`dotted.owner#name:JVM descriptor`，class使用完整dotted binary name；descriptor变化同时显示完整old/new签名。

Impact Path包含末端`Changed member`，每两个节点强制换行并在行末保留箭头；超长class、method和descriptor可在节点内部软换行。Structural Path不强制按两个节点分组，但同样允许软换行，表格整体仍可横向滚动。

Java text完全相同的`METHOD_BODY_CHANGED`已在ChangePoint收集期过滤，不会进入Affected Paths。retained method body从当前command缓存生成diff；缓存中的反编译结果不可用时显示`Unavailable`，不会再次反编译。报告不使用backend、network request、CDN或浏览器持久化存储。

### 8.2 impact status

Overall status：

| Status | 含义 | Exit code |
|---|---|---:|
| `SUCCESS` | 全部必要分析成功。 | `0` |
| `INCONCLUSIVE` | 分析完成，但存在 Coverage limitation。 | `0` |
| `PARTIAL_SUCCESS` | 部分 Module 失败，仍有可用结果。 | `2` |
| `FAILED` | 未产生可信的必要分析结果。 | `2` |

Module status：

| Status | 含义 |
|---|---|
| `SUCCESS` | Module 分析完成。 |
| `INCONCLUSIVE` | 结果保留，但 coverage 不完整。 |
| `SKIPPED` | Module 不在比较双方、无 relevant change 或不匹配用户 entrypoint scope。 |
| `FAILED` | Module scope validation、Call Graph 或分析失败。 |

一个 Module 失败不会取消其他 Module。Handled failure 仍可发布 partial report。

### 8.3 tree 报告

Repository Index 展示：

- repository/ref/commit/dirty、analysis path、Maven runtime、scope 和 Maven arguments。
- Reactor、Module、Dependency、Internal conflict、Multi-version dependencies、Class conflicts和High-risk class conflicts汇总。
- Command Preflight。
- Reactor status 和已发布 page 链接。

Reactor page 展示：

- Reactor与Module metadata；Analysis mode明确为`FULL_REACTOR`、`SINGLE_MODULE`或`STANDALONE`。
- Reactor/Module issue。
- “跨模块依赖分析”展示全部selected和omitted dependency occurrence。固定列为Dependency、Scope、Module、Dependency chain、Original version、Resolved version、Resolution source。
- Dependency和Module支持输入过滤的单选筛选，输入框右侧提供明确的展开按钮；Scope为精确筛选。全字段检索在七列中任一命中即可，四类条件按AND组合。
- Dependency候选旁的红色数字徽标表示整个Reactor中该Dependency的distinct非空resolved version数量；中性色`N unique`徽标表示全部occurrence的original/resolved非空版本并集。相同值只计一次，两个徽标均显示`0`或`1`并提供可读说明。
- 全量依赖表支持七列排序与10/50/100分页。候选超过50时只显示前50项，并提示继续输入缩小范围。
- Module分析位于一个完整card内；顶部选择Module后，下方同时切换coordinate、冲突类、模块内部依赖分析和Maven-style verbose dependency tree。模块内部依赖分析展示当前Module的全部selected与omitted occurrence，列与跨模块表一致但不含Module，并提供Module范围的Dependency双徽标、Scope、检索、排序和分页。

Dependency与Module筛选支持Enter选中、Escape关闭、Arrow Up/Down、Home和End导航；清空可选筛选恢复“全部”。Module初始选择第一个候选。切换Module时保留该Module的轻量筛选和分页状态；切回时恢复，但已展开源码不会恢复。

“跨模块依赖分析”的筛选区在宽屏采用双行紧凑布局：检索、Dependency和Module位于第一行，Scope、每页及操作按钮位于第二行。中等屏幕先将检索独占一行，小屏幕再按单列排列；控件不会随宽表被过度拉伸。

冲突类与模块内部依赖分析同样只显示当前页。Resolution source说明resolved version由Direct selection、Dependency management、Conflict mediation、Duplicate mediation、Cycle omission或其他Maven omission得到；多步处理按发生顺序展示版本转换。该列不推断具体定义版本的POM、BOM或冲突winner path。点击“查看反编译代码”后Winner按钮默认active；切换Shadowed source后只有新按钮保持active，源码同步更新。Active状态使用持久`aria-pressed=true`样式，并保留键盘focus；同一时间最多展开一行。

报告完全离线，可直接通过`file://`打开。dependency、当前Module的Dependency候选与当前页内容、dependency tree和反编译源码按需加载，避免大型报告初次打开时一次性创建全部内容。文件缺失或损坏时页面显示Retry；恢复报告文件后可重试。空dependency、空Module、Module failure和JavaScript禁用均有明确提示。桌面和小屏幕的宽表只在表格区域内横向滚动。

### 8.4 tree diff 报告

`tree diff`输出`index.html`与`tree-diff-report/reactors/`下的独立Reactor页面。Index展示baseline/target commit、current workspace dirty状态、scope、Maven runtime、Reactor状态与五项DependencyKey汇总指标。

Reactor页面使用英文界面。Module summary固定展示Module、Status和五项指标，不提供Actions；用户只在Module detail的Module selector中切换Module。DependencyKey级六列主表保留Actions，用于按需展开四列dependency chain子表。基础变更类型为`Version changed`、`Added`、`Removed`或`Resolved version unchanged`；`Scope changed`可叠加。direct dependency使用`Yes`、`No`和`—`显示双侧事实，不产生额外类型或指标。

chain通过忽略version/scope的完整有序PathKey配对；`Managed from <version>`只作为chain版本证据。chain结构的`Added`、`Removed`和`Unchanged`不进入Module或Index指标。子表使用比主表更紧凑的独立分页控件。页面占满可用viewport宽度，只保留小幅安全边距；dependency tree按完整文本高度展示，不产生纵向滚动，长行可在单侧tree区域横向滚动。页面只创建当前Module、当前主表页、最多一个chain子表页和最多50个筛选候选，可直接通过`file://`打开。

状态与exit code：

| Report state | 条件 | Exit code |
|---|---|---:|
| `SUCCESS` | 全部Module可比较且采集成功。 | `0` |
| `COMPLETED_WITH_ISSUES` | 至少一个Module可比较，同时存在结构或采集问题。 | `2` |
| `FAILED` | 没有可比较Module，或report pipeline/publication失败。 | `2` |

参数、Git ref、映射path或runtime Preflight失败返回`1`且不执行dependency collection。`STRUCTURE_MISMATCH`与`UNAVAILABLE`都不会推导整Module dependency新增/删除。已完整发布的Reactor page在后续失败时保留。

### 8.5 tree analyze status 与增量发布

Command-level Preflight 失败返回 exit code `1`，不清理或覆盖旧报告。Preflight 成功后：

1. 只替换 output 中由工具拥有的 `index.html` 和
   `dependency-report/`。
2. 发布`RUNNING 0/1` Index。
3. 入口scope完成后先发布Reactor page，再刷新终态Index。
4. 成功时写`SUCCESS 1/1`。
5. 存在已处理 issue 时写 `COMPLETED_WITH_ISSUES`。
6. pipeline/report failure写`FAILED`，保留已经完整发布的page。

硬中断后，最后一次成功checkpoint仍可能保留，已完整发布page可继续打开。

Reactor status：

| Status | 含义 |
|---|---|
| `SUCCESS` | Reactor 收集与报告完整成功。 |
| `DEGRADED` | 报告可用，但存在不完整 evidence 或非阻塞 issue。 |
| `FAILED` | Reactor 无法形成完整结果。 |

### 8.6 Preflight status 与 decision

Status：

| Status | 含义 |
|---|---|
| `PASS` | 检查通过。 |
| `WARN` | 存在限制，但允许按 decision 继续。 |
| `FAIL` | 检查失败。 |
| `SKIPPED` | 因前置检查或范围原因未执行。 |

Decision：

| Decision | 含义 |
|---|---|
| `CONTINUE` | 正常继续。 |
| `DEGRADE` | 使用检查中明确记录的受限行为继续。 |
| `BLOCK_REACTOR` | 只阻断当前 Reactor。 |
| `BLOCK_COMMAND` | 阻断整个 command。 |

## 9. 工作原理与限制

### 9.1 impact 的判断过程

`impact` 在高层依次完成：

1. 验证 Git、Maven、JDK、输入路径和输出位置。
2. 解析 baseline dependency，并编译 target。
3. 解析 target dependency，找出版本变化。
4. 对 selected changed JAR 执行 bytecode 与 ServiceLoader resource diff；对全部method body候选先执行Vineflower文本比较并写入当前command缓存。
5. 以`Java text identical || SSA MATCHED`过滤method body ChangePoint：Java文本相同立即过滤并跳过SSA；只有Java未命中时才执行SSA。
6. 对每个 relevant Module 构造 target Call Graph。
7. 将 changed member evidence 反向追踪到 PROJECT entrypoint。
8. 从缓存生成retained method body的Java diff，并发布Overall、Module和Affected Paths离线报告。

Baseline 不编译，也不构建 Call Graph。影响路径基于 target bytecode 和 target Call Graph。

### 9.2 Entrypoint 边界

Entrypoint class 只来自当前 Module 的 compiled PROJECT class：

- interface、annotation 和 private nested class 不进入 entrypoint index。
- public、protected、package-private concrete declared method 可成为 root。
- private method、constructor 不成为 root，但从 root 可达时仍参与普通调用分析。
- 不自动加入 inherited method。

未显式传 selector 时使用全部 eligible entrypoint。selector 过窄可能导致
`SKIPPED_USER_ENTRYPOINT_SCOPE` 或漏掉实际业务入口。

### 9.3 CHA 与 k-obj

默认 CHA 不求解 points-to，通常比 `k-obj` 使用更少资源，但仍是保守的 class hierarchy over-approximation。

CHA 的已知边界：

- 固定使用 caller-local receiver facts 裁剪不可能的 edge；无法证明时保留，可能产生 false positive path。
- JDK 声明的 virtual/interface dispatch 不扩展到非 JDK implementation，可能漏报 callback、Service Provider Interface（SPI，服务提供者接口）、lambda、collection implementation 和应用 `Thread`/`Runnable` chain。
- 仅有限支持 caller-local constant 形式的 `Class.forName` 和
  `ServiceLoader.load`。
- method parameter、field、array、其他 method return、字符串拼接和跨方法数据流不作为通用 points-to 或 heap analysis 处理。

Experimental `k-obj` 提供不同的 receiver context，但更高的 `k` 可能显著增加状态空间。JDK Method Model 安装失败时 Module 失败，不自动降级。

### 9.4 dependency scope 与 Coverage limitation

`changed-paths` 在 selected changed dependency 路径外将 external dependency method 作为边界。实际到达边界时保留 caller-to-leaf edge，并标记
`INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`。

其他常见 Coverage limitation：

- external dependency 引用 excluded JDK class。
- reachable unsupported `invokedynamic`。
- MethodHandle target 无法解析。
- ServiceLoader 或 reflection evidence 无法解析。

`INCONCLUSIVE` 表示“有可用证据，但覆盖不完整”，不是
`SUCCESS` 的同义词。

### 9.5 方法体分阶段 equivalence

所有descriptor相同、old/new body均存在且hash不同的`METHOD_BODY_CHANGED`，无论class major version是否相同，都会先执行：

- Vineflower old/new method反编译文本比较：`IDENTICAL`、`DIFFERENT`或`UNKNOWN`。
- 仅当Java文本为`DIFFERENT`或`UNKNOWN`时，执行normalized Static Single Assignment（SSA，静态单赋值）比较：`MATCHED`、`DIFFERENT`或`UNKNOWN`。

过滤条件固定为`Java text identical || SSA MATCHED`，从左到右短路求值。Java text identical是现有换行规范化后的`String.equals`完全相等，不额外忽略空白、comment或import差异。Java为`IDENTICAL`时记录`JAVA_TEXT_IDENTICAL`并把SSA记为skipped；Java miss后SSA为`MATCHED`时记录`SSA_MATCHED`。两种原因不会同时出现。Java `UNKNOWN`继续执行SSA；SSA `DIFFERENT/UNKNOWN`时fail-open保留ChangePoint。`UNKNOWN`本身不改变Module status。

双Stage比较只用于ChangePoint collection，不构建baseline Call Graph，也不证明整条业务路径或完整runtime behavior等价。反编译源码仅保存在当前command的`report-cache`，发布成功或失败后删除，不跨command复用；cache以`ssaExecuted=false/NOT_EXECUTED/JAVA_TEXT_IDENTICAL_SHORT_CIRCUIT`区分短路和SSA `UNKNOWN`。HTML与显式diagnostics只保存不含源码的状态、原因、hash、class version、耗时、executed/skipped和suppression reason。

### 9.6 冲突类

同一binary name在一个Module实际classpath中存在两个及以上定义时即为冲突类。`tree`对全部candidate完成反编译后，对仅规范化换行的完整文本执行`String.equals`精确比较，不额外忽略空白、comment或import差异：文本全部一致为`LOW`，至少两份不同为`HIGH`。任一candidate反编译失败、classpath binding缺失或证据不完整时，回退到class bytes的SHA-256判断：摘要全部一致为`LOW`，存在不同摘要为`HIGH`。两种风险都展示，不因内容一致而过滤冲突finding。

Winner precedence固定为`PROJECT > REACTOR_DEPENDENCY > DEPENDENCY`，同一层按Maven classpath顺序。`tree`不扫描JDK；`impact`的JDK 8 scope保留更高的JDK parent precedence。该finding本身：

- 不阻断 Module。
- 不自动形成 Coverage limitation。
- 不改变 exit code。
- shadowed changed definition不生成Affected Path；即使内容一致仍按`SHADOWED_BY_DUPLICATE`处理。

`tree`扫描当前Module output、选中Reactor dependency output和`--scopes`纳入的external dependency。Multi-Release JAR按Maven JVM major只读取实际可见entry；`module-info.class`排除。`impact`的Class conflict resolution仍始终按candidate class bytes的SHA-256分类，不使用`tree`的反编译文本优先规则。

`tree`只执行`compile`，不会执行`test-compile`或`package`，因此不会生成`test-classes`或attached classifier artifact。只能由这些phase生成的Reactor classifier不会读取旧产物：Reactor标记为`DEGRADED`并在问题表记录classpath incomplete。反编译失败只显示`Unavailable`，不改变status或exit code。

### 9.7 tree 的版本与全量依赖口径

Dependency identity 使用 Maven conflict key：

```text
groupId + artifactId + type + classifier
```

Internal conflict：

- 以单个 Module 为边界。
- 比较实际 dependency path requested version 和 Maven 明确给出的 dependency management effective version。
- 至少存在两个不同 version source 才形成冲突。
- `omitted for duplicate` 单独出现不等于版本冲突。

跨Module版本识别：

- 只比较 selected occurrence。
- 同一 conflict key 在整个 Reactor 中distinct、非空resolved version数量大于`1`时，计入`Multi-version dependencies`。
- Dependency筛选候选旁的红色徽标显示该数量；值`1`同样显示，便于区分单版本与多版本。
- 中性色`N unique`徽标独立统计selected与omitted occurrence的requested original version和selected resolved version并集，不改变`Multi-version dependencies`判定。
- Reactor汇总按DependencyKey去重计数；Module汇总只统计该Module中实际selected且Reactor级为多版本的DependencyKey。

“跨模块依赖分析”和“模块内部依赖分析”都不只展示版本问题，而是展示各自范围内全部selected与omitted occurrence，每条保留完整DependencyKey、effective Scope、Module coordinate（仅跨模块表）、Dependency chain、requested original version、selected resolved version和Resolution source。相同DependencyKey、Module或chain不会合并。

分析只覆盖实际进入 resolved dependency tree 的 dependency。不会列出未被使用的完整
`dependencyManagement`、imported Bill of Materials（BOM，物料清单）、build/report Plugin、extension 或 Plugin dependency tree。

`tree diff`沿用同一`groupId + artifactId + type + classifier` identity，但在单个Module内按唯一DependencyKey聚合。resolved version与effective scope都是标量；版本分类只比较resolved version，scope变化是独立叠加事实。directness由该侧任一条PathKey长度为1聚合。PathKey排除Module根并保留中间节点顺序，忽略version与scope，仅用于chain结构配对，不改变DependencyKey级分类和指标。

### 9.8 Maven settings 与数据隔离

Project artifact 始终由用户 Maven session 按 settings、mirror、proxy、credential 和 local repository 解析。Analyzer 的内嵌 runtime 只提供工具自身需要的 Maven 与 Plugin。

Command-generated workspace、temporary evidence 和 settings overlay 位于 config dir；不会写入 target source repository。生成的 settings 不输出到 Console。正常或 handled failure 只清理当前 run。

## 10. Troubleshooting

### 10.1 Unsupported Maven version

- 现象：Preflight 中 Maven version check 为 `FAIL`。
- 检查：Console 中 `tree.maven-version` 或
  `impact.maven-version` evidence。
- 处理：将 `--maven` 指向 `3.6.3 <= version < 4.0.0` 的 executable，或移除 `--maven` 使用内嵌 3.6.3。
- 影响：Command-level Preflight 失败；分析不启动，旧报告保持不变。

### 10.2 Maven executable cannot run

- 现象：Maven probe 无法启动或 executable validation 失败。
- 检查：确认 `--maven` 指向 executable file，而不是 Maven home；Windows 使用 `mvn.cmd`。
- 处理：修正 executable path；需要特定 Maven JVM 时同时设置
  `--java-home`。
- 影响：Preflight 阻断 command。

### 10.3 impact target JDK Preflight failure

- 现象：`impact` 报 Java major、`javac` 或 `rt.jar` 缺失。
- 检查：确认 `--java-home` 是完整 JDK 8 root，不是 JRE 或 Analyzer 的 Java 17 home。
- 处理：改用完整 JDK 8；分别执行其 `bin/java -version` 和
  `bin/javac -version`。
- 影响：分析不启动，旧报告保持不变。

### 10.4 Maven dependency resolution failure

- 现象：Maven 无法下载或解析 project dependency。
- 检查：Console Maven output；用户 settings 中的 repository、mirror、proxy、credential 和 local repository。
- 处理：修正 Maven settings 或缓存所需 artifact；必要时使用
  `-v` 查看完整 Maven subprocess output。
- 影响：`impact`可能无法形成Module evidence；`tree`对应Reactor为`FAILED`或`DEGRADED`。若入口按`STANDALONE`执行，原本由非祖先aggregator提供的sibling artifact不会自动进入Maven session。

### 10.5 Call Graph 长时间运行

- 现象：Module 长时间停留在 Call Graph stage。
- 检查：使用 `-vv` 查看 Runtime Metrics、Module identity 和 progress；确认是否使用 `k-obj`、较大 `k` 或 `full`。
- 处理：设置正数 `--call-graph-timeout-seconds`；优先使用默认 CHA、`changed-paths` 和合理的 `--analysis-parallelism`。
- 影响：timeout 只失败当前 Module，其他 Module 继续；Overall 可能为
  `PARTIAL_SUCCESS` 或 `FAILED`。

### 10.6 JDK Method Model 安装失败

- 现象：`k-obj` Module 在 model installation 或 hierarchy validation 失败。
- 检查：确认完整 JDK 8、`--call-graph-algorithm k-obj` 和
  `--jdk-model` 组合。
- 处理：修复 JDK 8 输入；如不需要 model，可显式使用
  `--jdk-model none`；也可改用默认 CHA。
- 影响：失败 Module 不自动降级，其他 Module 继续。

### 10.7 Class conflict warning

- 现象：Console 显示
  `Resolved class conflicts by classpath precedence`。
- 检查：Module Index的Class conflict resolution，确认Risk、winner、shadowed source和precedence reason。
- 处理：修复 target project 的重复 classpath；同时检查独立 Module status 和 Coverage limitation。
- 影响：warning 本身不阻断、不改变 status 或 exit code；shadowed definition 不生成 Affected Path。

### 10.8 SSA equivalence 为 UNKNOWN

- 现象：报告中的 SSA evidence 为 `UNKNOWN`。
- 检查：该方法的decompiled Java必为`DIFFERENT`或`UNKNOWN`。查看Overall/Module的SSA evidence；使用 `-vv` 按 method 或
  `ssaUnknownCandidate` 搜索 Console audit，同时检查Decompiled Java evidence。
- 处理：结合old/new bytecode、Intermediate Representation（IR，中间表示）、SSA reason和Decompiled Java状态人工判断。
- 影响：fail-open保留`METHOD_BODY_CHANGED`。Decompiled Java为`IDENTICAL`时已经短路，不会产生SSA `UNKNOWN`。SSA `UNKNOWN`本身不改变Module status。

### 10.9 Reactor 为 FAILED

- 现象：tree Index 中 Reactor 为 `FAILED`。
- 检查：Repository Index 和 Reactor page 的问题表，以及 Console Analysis diagnostics。
- 处理：修复malformed POM、缺失active Module POM、repository外module path、active graph cycle、重复/不可解析coordinate、Plugin goal resolution或Maven Module failure。
- 影响：当前入口scope失败；最终exit code为`2`，已发布page保留。Scope preparation failure在Preflight阶段返回`1`，旧Report不被替换。

### 10.10 Reactor 为 DEGRADED 且 classpath incomplete

- 现象：问题表包含`INCOMPLETE_CLASSPATH`，冲突类结果可能不完整。
- 检查：Console中的classpath evidence warning，以及依赖是否为Reactor classifier、custom output directory是否由`compile`生成。
- 处理：调整project使主class output在`compile`后可用；若依赖必须通过`test-compile`或`package`生成classifier，将其作为可解析的external artifact提供后重试。
- 影响：现有dependency tree与已扫描finding保留，最终exit code为`2`；工具不会读取旧classifier产物。

### 10.11 Config dir runtime 损坏

- 现象：内嵌 Maven 或 Plugin runtime 检查失败。
- 检查：Console 中 completion marker、component 和 version evidence。
- 处理：删除明确损坏 component/version leaf 的 completion marker 或必要文件后重试。不要直接删除完整 config dir，其中可能有用户文件。
- 影响：Analyzer 只重建对应 component/version leaf；其他 cache 和用户文件保留。

## 11. 第三方许可

Analyzer JAR 包含用于离线运行的第三方组件及其许可材料：

- Apache Maven 3.6.3 binary distribution：Apache License 2.0。
- Maven Dependency Plugin 3.6.1 及其运行依赖：随分发材料保留对应
  `LICENSE`、`NOTICE` 和 `DEPENDENCIES`。
- WALA 1.8.0：Eclipse Public License 2.0（EPL-2.0）。
- Vineflower 1.12.0 slim：Apache License 2.0。

Vineflower runtime 已随 Analyzer 提供；内网执行 `impact` 不需要下载 decompiler artifact。Project dependency 的解析仍取决于用户 Maven settings 和可用 artifact。
