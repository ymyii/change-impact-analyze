# Dependency Analyzer 用户手册

## 1. 产品简介

Dependency Analyzer 是 Java 17 CLI，面向 Maven project：

- `impact`：比较 dependency 升级前后的 resolved dependency、bytecode 和业务调用影响，输出 Overall HTML Index；每个非 `SKIPPED` Module 输出 Module Index 与 Affected Paths 两个英文页面。
- `tree`：扫描一个 Git repository 内的 Maven reactor，输出 repository 级 offline HTML dependency tree report。

当前Analyzer开发构建为`3.0.0-SNAPSHOT`；Dependency Evidence Plugin保持`3.0.0`，公共JDK engine与JDK 8 model保持`0.1.0-SNAPSHOT`。Root command为`dependency-analyzer`。

Source repository 包含 root Analyzer、Plugin、公共 JDK Method Model 与 JDK 8 Method Model 四个独立 Maven reactor。根 `pom.xml` 只聚合 Java 17 的
`analyzer/`，生成 `target/dependency-analyzer.jar`；`plugins/pom.xml` 独立聚合 Java 8
Plugin `plugins/artifact-path-resolver/`；`models/jdk/pom.xml` 与 `models/jdk8/pom.xml` 按 dependency 顺序独立构建。Analyzer uber JAR 内含两个 model artifact。Plugin reactor 通过 Maven local repository
交付 attached `repository` ZIP。Dependency Evidence Plugin goal 为
`io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:3.0.0:collect-dependency-evidence`。

## 2. 环境与运行

要求：

- Analyzer 使用 Java 17 runtime 启动。
- `impact` 必须通过 `--java-home` 指定完整 JDK 8。
- `tree` 可使用与 Maven/project 兼容的其他 JDK。
- Git command 可运行。
- Target Maven project 所需 repository、mirror、proxy、credential 和 local repository 已通过 Maven settings 配置。

已构建 JAR 执行：

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
| `-j` | `--java-home <jdk-home>` | Maven subprocess 的 `JAVA_HOME`；`impact` 同时用它编译用户代码并构建 WALA target JDK scope。 |
| `-c` | `--config-dir <dir>` | 覆盖完整 Dependency Analyzer config dir。 |
| `-a` | `--maven-arg=<token>` | 重复传入一个 Maven option/property token，例如 `--maven-arg=-Pprod`。 |
| `-v` | `--verbose` | 提升日志级别；默认 `INFO`，`-v` 为 `DEBUG`，`-vv` 为 `TRACE`。可放在 subcommand 前或后。 |

`--verbose --verbose` 与 `-vv` 等价。Analyzer控制流程只使用Stage与Phase：Stage是具有开始、完成、失败与耗时的执行边界；Phase是Stage内可选的算法活动，只在存在明确内部算法步骤时出现。运行日志统一写入stderr，每个物理行固定为`[时间][日志级别][stage][substage][phase + identity] message`；缺失段使用`[-]`。第五段canonical顺序固定为`phase, check, reactor, module, artifact, pool`，其他stable identity按名称排序；只有Phase时例如`[phase=REVERSE_BFS]`，Phase与identity并存时例如`[phase=REVERSE_BFS;module=g:a:1]`。status、progress、elapsed、path、计数和metrics value等实际日志信息使用message中的`key=value`。Stage生命周期正文统一为`started`、`completed; elapsedMs=...`和`failed; reason=...; elapsedMs=...`。`INFO`输出稳定的Stage、progress、warning和error，Maven subprocess只透传warning/error；JAR pair diff failure的WARN固定包含异常类型和完整message。`DEBUG`额外输出analysis option/decision、完整Maven subprocess output，并在command或隔离的JAR pair异常时逐行输出带完整prefix的stack trace与cause chain；`TRACE`再输出normalized path、ref、scope，以及启动后立即采样、随后每10秒采样的Runtime Metrics。SSA比较为`DIFFERENT`或`UNKNOWN`时，`TRACE`还以`substage=ssa-equivalence-audit`输出同一方法的old/new ASM字节码、old/new原始Intermediate Representation（IR，中间表示）和old/new归一化IR；每行包含可搜索的`method=<owner>#<name><descriptor>`，并以`retention=ssaDifferentRetained|ssaUnknownRetained`标识保留原因。Picocli help/usage、参数解析错误和第三方库直接写入stderr的内容不保证五段prefix。

`-vv` Runtime Metrics包含heap `used/committed/max` MiB，以及唯一Analyzer-owned `common` thread pool的core/max/size/active/queued/completed/submitted和lifecycle状态。`common`顺序承载front preparation、JAR diff、Impact Query与并发code comparison。Heap第五段为空；thread-pool第五段只包含`pool` identity；sample、elapsed和全部指标值位于message。`-v`不创建metrics scheduler，也不输出metrics。全部Diagnostic line、Runtime Metrics、Maven output、Preflight fallback和stack trace只进入Console；HTML不包含Diagnostics栏目。显式`--call-graph-diagnostics-output`仍可单独输出Schema 10 topology JSON。

```text
[2026-08-05T14:30:01.123+08:00][INFO][analysis][reactor][reactor=root] Maven collection completed; progress=1/2; status=SUCCESS; modules=8
```

## 3. Maven Runtime

Maven 选择优先级：

1. `--maven` 指定 executable。
2. Config dir 中 completion marker 与必要文件完整的 Apache Maven 3.6.3 runtime。
3. 从 JAR resource 离线解压 Apache Maven 3.6.3。

工具不会隐式使用 PATH Maven，也不会自动使用 repository Maven Wrapper。支持 version 为 `>=3.6.3 && <4.0.0`；Maven 3.6.2 和 Maven 4 会在 preflight 阻断。

默认 config dir：

```text
${user.home}/.dependency-analyzer/
├─ runtime/apache-maven/3.6.3/content/
├─ runtime/plugin-repositories/
│  ├─ maven-dependency-plugin/3.6.1/content/
│  ├─ dependency-analyzer-artifact-path-maven-plugin/<version>/content/
│  ├─ dependency-analyzer-artifact-path-maven-plugin/<snapshot>/runs/<uuid>/
│  └─ settings/command-<random>.xml
├─ locks/
├─ impact/
│  ├─ workspaces/<run-id>/
│  └─ tmp/<run-id>/
└─ tree/
   ├─ workspaces/<run-id>/
   └─ tmp/<run-id>/
```

Apache Maven 3.6.3 已 EOL；只有实际选择内嵌 3.6.3 时，version evidence 才展示该 warning。显式 `--maven` 时，所有 Maven process 和 Report 使用用户 executable 的实际 probe version；runtime evidence 只描述 `USER_CONFIGURED` 或 `EMBEDDED` source。

Config dir 中未知文件和用户文件不会被自动删除。Runtime marker 或必要文件缺失时只重建明确归属工具的 component/version leaf；工具不对完整 archive 内容计算额外 fingerprint。

每次 command 使用 UUID `run-id`、owner marker 和 `<config-dir>/locks/` file lock。Detached worktree 和 command-generated intermediate file 只写入对应 subcommand run。Dependency evidence 位于 `impact/tmp/<run-id>/dependency-evidence/{baseline|target}/<nonce>/`，不写入用户 source repository。正常和异常关闭只清理当前 run；启动时只回收 owner marker 有效且未被其他 process lock 的 stale run。Current target `mvn compile` 仍可在对应 workspace 生成 `target/`。`impact` 的 Maven build/dependency output 不写 `.log`，按 `-v` 直接输出到 Console；failure 只在内存保留 bounded tail。

`impact` 固定使用 JAR 内嵌 Dependency Evidence Plugin `3.0.0`；`tree` 默认使用 Maven Dependency Plugin `3.6.1` 并保留高级 version override。两个 Plugin 都以内嵌 Maven repository ZIP 提供，runtime 分别解压到独立 cache。Stable version 复用 component/version cache；Dependency Evidence Plugin Snapshot 每次 command 解压独立 leaf、启用 `updatePolicy=always` 并传入 `-U`，不会刷新大型 Dependency Plugin repository。

Runtime 生成 command-scoped global settings overlay：一个 active profile 注册两个 file `pluginRepository`，保留用户 `-gs` 中的 mirror、proxy、server、local repository 等配置以及独立 `-s` 参数；两个内置 repository ID 从通配 mirror 中排除。Settings 使用 owner-only permission，command 结束时删除；内容不会输出到 Console。Overlay 用于工具控制的 Plugin goal，不应用于 target `compile`。

Preflight evidence 包含 `dependencyEvidencePlugin=3.0.0` 与 `repositories=2`。Mojo 启动后输出 `implementation=dependency-evidence-v3`、version 和 command cache output path。

Dependency Evidence Plugin 支持 `3.6.3 <= Maven version < 4.0.0`，编译为 Java 8 bytecode。它使用 Maven Dependency Tree API 的 `DependencyGraphBuilder` 和 `DependencyCollectorBuilder`；Maven 4 不在兼容范围。

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

不允许附加Maven lifecycle phase或goal，也不允许覆盖工具控制的参数。至少拒绝：

```text
-f --file -pl --projects -am -amd -N --non-recursive -q --quiet
-DoutputFile -DoutputType -DappendOutput -Dverbose -Dtokens
```

## 5. `impact` Subcommand

### 5.1 CLI

```text
dependency-analyzer impact \
  -j, --java-home <jdk8-home> \
  [-p, --path <dir>] \
  -b, --baseline <local-ref> \
  [-t, --target <local-ref>] \
  -o, --output <file> \
  [-f, --format html] \
  [--analysis-target spring-backend] \
  [--analysis-parallelism <count>] \
  [--call-graph-algorithm <cha|k-obj>] \
  [--k-obj-depth <positive-integer>] \
  [--jdk-model <jdk8|none>] \
  [--wala-reflection-options <WALA-enum-name>] \
  [--dependency-analysis-scope <changed-paths|full>] \
  [--result-refinement-algorithms <selection>] \
  [--entrypoint-include '<class-path-pattern>']... \
  [--entrypoint-exclude '<class-path-pattern>']... \
  [-k, --include-change-kinds <csv>] \
  [--call-graph-timeout-seconds <seconds>]
```

- `-p, --path` 默认 current directory；定位所属 Git repository，并指定本次分析的 Maven project directory。该目录必须存在 readable root `pom.xml`。
- `--baseline` 必填，只解析 local ref。
- `--target` 省略时使用 current checkout；显式提供时使用 detached worktree。
- `--output` parent 必须存在且可写。
- `--format` 仅接受 `html`；`md` compatibility token 会 fail fast。
- `--analysis-target` 默认且首版只接受 `spring-backend`。
- `--analysis-parallelism`未传值时默认使用`max(1, 可用CPU核数 / 2)`，向下取整；显式值必须`>=1`。该参数决定唯一`common`pool大小，全局限制front preparation、JAR diff、Impact Query与并发code comparison；值为`1`时baseline依赖分析与target构建串行。显式值超过可用CPU数时输出warning，不静默截断。
- `--call-graph-algorithm` command-wide选择全部Module使用的WALA算法；默认`cha`，可显式选择experimental `k-obj`。值大小写不敏感，不接受alias，不执行timeout fallback；其他标识在参数解析阶段失败。CHA使用WALA Class Hierarchy Analysis（CHA，类层次分析）进行context-insensitive dispatch，不构建points-to。
- `--k-obj-depth`只可与`--call-graph-algorithm k-obj`同时使用；必须为正整数，默认`1`，不设置人为上限。普通static调用复用object Context，递归在固定`k`的有限Context空间内收敛；较大的`k`仍可能显著增加CGNode、CGEdge、内存与耗时。
- `--jdk-model` command-wide选择全部Module使用的JDK Method Model。默认值依algorithm解析：CHA固定`none`；`k-obj`未指定时为`jdk8`。显式`cha + jdk8`在分析前失败；`k-obj`仍可显式`none`。
- `--wala-reflection-options`（alias `--reflection-options`）command-wide选择WALA `AnalysisOptions.ReflectionOptions`。CHA不应用该设置，Report显示`not applied by cha`；`k-obj`使用配置值，默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。
- `--result-refinement-algorithms`接受`none`、`cha-local-receiver-inference`、`ssa-equivalence`或两者逗号组合，默认`ssa-equivalence`。显式值完整覆盖默认值；传`none`关闭全部refinement。Static Single Assignment（SSA，静态单赋值）在JAR Diff的ChangePoint收集阶段运行，只比较body hash不同且class file major version不同的方法；`MATCHED`抑制ChangePoint，`DIFFERENT`与`UNKNOWN`保留。旧`--experimental-bytecode-semantic-comparison`已移除。
- `--entrypoint-include`/`--entrypoint-exclude` 接受 slash-separated JVM internal class path，例如 `com/icbc/payment/OrderService`；可选 WALA `L` 前缀会在匹配前移除。选项可重复，多个 include 取并集，exclude 优先。
- 普通 segment 中 `*` 匹配零到多个字符，`?` 匹配一个字符，均不跨越 `/`。最后一个普通 segment 始终是 class segment，允许 `$` 匹配 nested class；前面的 package segment 不允许 `$`。例如 `com/*/A?`、`com/ic?c/*Controller`、`com/icbc/*$Handler`。
- `**` 只能作为最后一个完整 segment。`com/icbc/**` 匹配该路径下直属及任意深度 package 中的全部 class，`**` 匹配全部 class；`com/**/A`、`com/icbc/A**`、leading/trailing slash、空 segment、`.`、`\\` 与 `:` 均非法。`com/icbc/**` 后不能追加 class pattern；需要限定 class 名时使用确定深度的普通 pattern，例如 `com/*/*Controller`。旧 colon/dot selector 不兼容，参数校验直接 exit `1`。
- Entrypoint class 只来自当前 Module `target/classes` 的 immutable index，与 classpath precedence 无关。Interface/annotation和private nested class不进入index；abstract class保留non-private、non-abstract declared method。命中class的public、protected和package-private concrete declared method成为entrypoint，包括static、native和bridge/synthetic method；private method与constructor不成为root。不自动加入inherited method或subtype。Private method仍保留在Analysis Scope中，从non-private root可达时作为普通CGNode参与Impact tracing。
- 每个 JVM parameter slot仅提供一个 candidate：primitive、array 与 concrete reference使用 declared type；interface/abstract reference使用 Module 内按 declared type共享的 synthetic concrete placeholder。Abstract class的 instance method和 constructor使用 fake receiver；placeholder不连接真实 subtype或implementor，因此可能遗漏 implementation-only impact path。
- Relevant Module 没有匹配时标记 `SKIPPED_USER_ENTRYPOINT_SCOPE`；所有 relevant Module 都没有匹配时 exit `1`，不替换旧 Report。
- `--include-change-kinds` 控制 bytecode `ChangePointKind`。
- `--java-home` 必填且必须是完整 JDK 8：Preflight 校验 `bin/java`、`bin/javac`、Java major、`rt.jar`，并读取 `sun.boot.class.path` 与 `java.ext.dirs`。
- `--java-home` 同时决定 Maven subprocess `JAVA_HOME`、用户代码编译 JDK 和 WALA Primordial/Extension target runtime。
- `--call-graph-timeout-seconds` 默认 `0`，表示无限等待；按 Module 从实际 WALA build 开始计时。Timeout 失败当前 Module，其他 Module 继续。
- `k-obj`的`jdk8`要求WALA hierarchy支持Synthetic loader，并要求384个catalog target全部available；安装异常会失败当前Module且不降级。CHA仍加载完整JDK classpath以解析hierarchy和JDK leaf，但不安装model、不遍历JDK body。

| Short | Long | 含义 |
|---:|---|---|
| `-p` | `--path` | Git repository 内的 Maven project/分析目录。 |
| `-b` | `--baseline` | 比较起点 local commit。 |
| `-t` | `--target` | 比较终点 local commit；不是 tree snapshot ref。 |
| `-o` | `--output` | HTML Index 文件。 |
| `-f` | `--format` | 仅 `html`；`md` 已移除。 |
|  | `--analysis-target` | 仅 `spring-backend`。 |
|  | `--analysis-parallelism` | 全部Analyzer并行任务的全局上限；默认可用CPU核数的一半，最少`1`。 |
|  | `--call-graph-algorithm` | `cha`（默认）或experimental `k-obj`；全部Module使用同一算法。 |
|  | `--k-obj-depth` | `k-obj`的receiver allocation string深度，正整数，默认`1`；其他算法禁止使用。 |
|  | `--jdk-model` | CHA固定`none`；`k-obj`默认`jdk8`并可显式`none`。 |
|  | `--wala-reflection-options` | WALA `ReflectionOptions` enum name；CHA不应用，`k-obj`默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。 |
|  | `--result-refinement-algorithms` | `none`、`cha-local-receiver-inference`、`ssa-equivalence`或组合；默认`ssa-equivalence`。 |
|  | `--entrypoint-include` | 只选择匹配 slash class path 的 target class declared methods；可重复。 |
|  | `--entrypoint-exclude` | 从 include/default selection 中排除匹配 slash class path 的 target class；可重复且优先。 |
|  | `--dependency-include` | 只分析匹配`groupPattern:artifactPattern`的changed JAR；可重复，多个include取并集。 |
|  | `--dependency-exclude` | 从include/default selection中排除匹配的changed JAR；可重复且始终优先。 |
| `-k` | `--include-change-kinds` | 纳入分析的 `ChangePointKind` CSV。 |
|  | `--call-graph-timeout-seconds` | Per-Module WALA timeout；`0` 表示无限等待。 |

### 5.2 示例

比较 release ref 与 current checkout：

```sh
java -jar dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --path . \
  --baseline release-1.2 \
  --output build/impact.html
```

比较两个 local ref，并将 analysis 并发数设为 `4`，同时将 entrypoints 限定到 payment package，但排除 generated class：

```sh
java -jar dependency-analyzer.jar \
  --java-home /opt/jdk8 \
  --maven /opt/apache-maven/bin/mvn \
  impact \
  --baseline main \
  --target feature/dependency-upgrade \
  --output build/impact.html \
  --analysis-parallelism 4 \
  --entrypoint-include 'com/acme/payment/**' \
  --entrypoint-exclude 'com/acme/payment/generated/**'
```

只分析`com.acme.payment`下的changed dependency，但排除internal artifact：

```sh
java -jar dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline main \
  --target feature/dependency-upgrade \
  --output build/impact.html \
  --dependency-include 'com.acme.payment:*' \
  --dependency-exclude 'com.acme.payment:*-internal'
```

Dependency Glob必须恰好包含一个`:`，两段均非空且不含空白。`*`匹配当前段零到多个字符（包括`groupId`中的`.`），`?`匹配一个字符；二者不跨`:`，`**`没有特殊语义。匹配区分大小写，只比较target `groupId:artifactId`，忽略version、type与classifier。重复pattern按首次出现去重；未传include表示全部，exclude始终优先。格式错误或显式selector整体未选中任何`VERSION_CHANGED` JAR pair时返回exit code `1`，并保留旧Report与diagnostics output。

显式使用experimental `k-obj`与WALA `FULL` reflection：

```sh
java -jar dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline release-1.2 \
  --output build/impact-kobj.html \
  --call-graph-algorithm k-obj \
  --wala-reflection-options FULL
```

禁用JDK Method Model并对照真实JDK bytecode语义：

```sh
java -jar dependency-analyzer.jar impact \
  --java-home /absolute/path/to/jdk8 \
  --baseline main \
  --output build/impact-real-jdk.html \
  --jdk-model none
```

默认CHA支持以下caller-local constant形式：

```java
String className = "com.example.Removed";
Class.forName(className);

Class<MyService> service = MyService.class;
ServiceLoader.load(service);
```

所有分支都赋同一常量的SSA `phi`同样支持。Method参数、field、array、其他method return、字符串拼接、`StringBuilder`、string-concat `invokedynamic`和不同常量的`phi`不解析，并形成typed coverage limitation。该机制只沿单个caller的SSA definition有限回溯，不是points-to、heap、跨method或通用数据流分析。

### 5.3 Pipeline 与报告

Preflight 后先识别模式：选择 reactor root 时，全 reactor 只 compile 一次并逐 Module 分析；选择 leaf POM 时，从 reactor root 使用 `-pl <module> -am` compile，只报告该 Module。当前 Module 是 `PROJECT`，上游 reactor Module 是 `REACTOR_DEPENDENCY`。

前置阶段并行执行 baseline dependency resolution 与 target Maven compile；join 后执行 target dependency resolution。Baseline 不 compile、不构建 Call Graph。每次 dependency analysis 在同一 Maven process/session 中执行 fully-qualified `io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:3.0.0:collect-dependency-evidence`。Target `compile` 仍只使用普通 user Maven arguments。

Plugin 直接使用 `DependencyGraphBuilder` 读取 Maven resolved winner graph，使用 `DependencyCollectorBuilder` 读取 raw occurrence graph。它不读 GraphML，不调用 `toNodeString()`，不解析 `omitted for ...` 展示标签。Resolved graph 只保留 selected `compile/runtime/provided/system`，建立 winner 与 classpath order；`test` 完全排除。Raw occurrence 命中 retained winner 时规范化到 winner，保留 duplicate/version loser 的全部 parent path；没有 retained winner 时删除 occurrence branch。因此 direct `test` winner 与 transitive `provided/compile` duplicate 都不进入 dependency diff、artifact binding、JAR diff 或 Call Graph。

Module classpath 使用固定 precedence：JDK boot classpath、JDK extension classpath、当前 Module `target/classes`、依赖 Module `target/classes`、external dependencies。Reactor 与 external tier 内保持 Schema v3 selected traversal order，不按 coordinate 或 path 二次排序。同一 binary name 的内容冲突按该顺序选择唯一 winner；byte-identical duplicate 静默去重。根级 `module-info.class` 与 `META-INF/versions/**` 不参与 ownership。内容不同的 duplicate 输出 `WARN` 与 winner/loser evidence，但不进入 Coverage limitations、不改变 Module `SUCCESS` 或 exit code。

对非 `system` binding，Plugin 仅对 selected external graph 创建非传递 `ArtifactRequest`，并只采用 Maven Resolver 返回的 `ArtifactResult` file。RepositorySystemSession 继续提供 mirror、proxy、authentication、local repository、offline policy、cache 与 WorkspaceReader。对 selected `system` binding，Plugin 按 coordinate 匹配effective `MavenProject` 中的 `system` dependency，要求唯一匹配，并验证 `systemPath` 是 absolute existing regular file；JSON 直接绑定该 path。Plugin 不生成 temporary POM、不调用 `dependency:list`、不自行拼接 local repository path。

Analyzer 为每次 Maven 调用创建 command-owned nonce 目录和 `.cia-evidence-owner` random token，通过 absolute `cia.dependencyEvidenceDirectory` 与 `cia.dependencyEvidenceOwner` 传入 Plugin。Plugin 拒绝 source workspace 内输出、symlink escape、symlink owner marker 和 token mismatch。文件名由 canonical Module directory 与 coordinate 的 SHA-256 派生；先写 sibling temporary file，再 atomic move。Analyzer 在 Maven 成功或失败后都删除 nonce，不扫描 source repository。

#### Dependency Evidence JSON Schema v3

```json
{
  "schemaVersion": 3,
  "module": {
    "groupId": "org.example",
    "artifactId": "application",
    "type": "jar",
    "extension": "jar",
    "classifier": "",
    "version": "1.0.0",
    "baseVersion": "1.0.0"
  },
  "moduleDirectory": "/absolute/source/application",
  "dependencies": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "jar",
        "extension": "jar",
        "classifier": "",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "scope": "compile",
      "children": []
    }
  ],
  "occurrenceGraph": {
    "rootId": "n0",
    "occurrences": [
      {
        "id": "n0",
        "coordinates": {
          "groupId": "org.example",
          "artifactId": "application",
          "type": "jar",
          "extension": "jar",
          "classifier": "",
          "version": "1.0.0",
          "baseVersion": "1.0.0"
        },
        "scope": "",
        "moduleRoot": true,
        "reactor": false
      }
    ],
    "edges": []
  },
  "selectedReactorKeys": [],
  "artifacts": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "jar",
        "extension": "jar",
        "classifier": "",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "absolutePath": "/absolute/repository/library-2.0.jar"
    }
  ]
}
```

JSON 为 UTF-8，固定包含 Module coordinate、canonical Module directory、selected external dependency tree、winner-normalized occurrence graph、selected reactor keys 与 physical artifact bindings。Analyzer 要求 `schemaVersion == 3`、全部字段和类型严格匹配、无 duplicate/unknown property、occurrence graph root/reachability/acyclic 有效、selected external coordinates 与 artifact bindings 双向一致、所有 physical path 为 absolute existing regular file。`version` 可保留 timestamped SNAPSHOT，`baseVersion` 保留 logical version；`system` dependency 的 `absolutePath` 直接来自 validated effective `systemPath`。

Scope 由结构化 dependency 与 occurrence 节点直接携带。相同 coordinates/version 只有 scope 变化时不产生 impact change；version 与 scope 同时变化时只产生一个 `VERSION_CHANGED`。Baseline 与 target physical path 分别按各自 Module-local Schema v3 evidence 查找，不使用展示标签或 `DependencyChange.scope`。

完整Maven Dependency Diff后先执行`dependency-selection` Stage：从`VERSION_CHANGED`且old/new type均为`jar`的logical pair应用Dependency Glob。只有selected pair按`--analysis-parallelism`并行执行bytecode Diff、`META-INF/services/*`resource Diff与SSA比较；`--include-change-kinds`在该选择之后作用于selected JAR。Resource配置会删除comment/空行、去重并校验baseline provider；仅registration删除生成`SERVICE_PROVIDER_REGISTRATION_REMOVED`，provider class与配置同时删除时只保留`CLASS_REMOVED`。每个logical pair只生成一组immutable ChangePoint，由多个Module共享。

Selector不裁剪baseline/target resolved classpath，也不改变完整dependency change统计。`changed-paths`只用selected changed artifact作为seed，但仍反向保留到seed的全部真实中间dependency；例如排除A的changed-member来源、选择B，且A依赖B时，A的方法体仍进入Analysis Scope。Evidence、Impact/Structural path、Module relevance与code comparison只来自selected ChangePoint；无selected ChangePoint的Module沿用`SKIPPED_NO_RELEVANT_CHANGE`。

JAR diff聚合结束的INFO日志包含`changes`、`pairs`、`failedPairs`和`workers`。`changes`只汇总成功logical pair的唯一ChangePoint，同一pair绑定多个Module只统计一次；空diff四项均为`0`。

每个relevant Module依次构造target ownership、AnalysisScope、Class Hierarchy和selected WALA strategy。默认CHA使用`Everywhere` Context，JDK method只保留leaf edge；`changed-paths`路径外dependency method是no-op leaf，实际到达时产生boundary limitation。`full`展开external body。CHA不会生成factory或dangerous transfer metadata。

Call Graph完成后，统一`ChangePointEvidenceCollector`扫描reachable method一次，将method、field、type、structural、Class.forName、ServiceLoader、`invokedynamic`和MethodHandle reference绑定为公共`ReferenceEvidence`，再冻结Module session。Impact query先串行完成Structural Reference准备、ordinary seed resolution和access observation，再按exact `QueryNode`分组并发执行反向BFS；一个QueryNode query复用一个局部`ReverseTrace`处理关联的全部evidence。PROJECT direct structural reference不进入QueryNode query。Removed class/method/field/resource永远只作为terminal，不进入WALA Call Graph node/edge。

Relevant Module按稳定顺序严格串行：当前Module完成Call Graph、Impact Query、可选SSA filtering、diagnostics、snapshot detach和cache spill后，才开始下一个Module。一个QueryNode失败只取消并等待当前Module剩余query，随后当前Module记为`FAILED_ANALYSIS`；共享Impact Query pool继续服务后续Module。

每个Module开始Impact Query时，INFO日志打印evidence binding总数`seeds`、去重后`queryNodes`和实际worker上限。`-vv`使用`query-node-started`、`query-node-progress`、`query-node-completed`跟踪稳定ordinal、`evidenceSeeds`、elapsed、recent node与当前QueryNode独立的`visited`；当前Phase位于第五段最前面，值为`REVERSE_BFS`、`PATH_MATERIALIZATION`或`REPRESENTATIVE_SELECTION`，message中不重复`phase=`。不同QueryNode不共享visited或心跳状态。

Bytecode diff额外产生`CLASS_ACCESS_NARROWED`、`METHOD_ACCESS_NARROWED`（含constructor）和`FIELD_ACCESS_NARROWED`。Query使用target CHA解析actual declaration，并按Java 8 runtime package、subclass、symbolic owner与caller-local verifier receiver type判断new access。`ACCESSIBLE`不建path；`INACCESSIBLE`与`POTENTIALLY_INACCESSIBLE`保守保留path；全部reference仍合法时disposition为`ACCESS_REMAINS_VALID`，不生成Affected Path或code comparison，但Module changed member指标仍显示该member。该能力分析pre-existing bytecode的JVM binary compatibility，不分析source compatibility、Reflection/JNI/custom ClassLoader或Java 9 module exports。

Module analysis 检查分类如下。这里的“阻塞”只终止当前 Module，其他 Module 继续；全部 Module 完成后再按既有规则形成 Overall status 与 exit code。

| 检查项 | 分类 | 结果 |
|---|---|---|
| `PROJECT`/`REACTOR_DEPENDENCY` 引用 excluded JDK class；scope scanner unreadable/failure | 阻塞 | 当前 Module `FAILED_SCOPE_VALIDATION`。 |
| 零 `PROJECT` entrypoint、CHA/Call Graph construction failure | 阻塞 | 当前 Module `FAILED_ANALYSIS`。 |
| Call Graph timeout | 阻塞 | 当前 Module `FAILED_CALL_GRAPH_TIMEOUT`。 |
| external dependency 引用 excluded JDK class | Coverage warning | 保留分析结果，Module 为 `INCONCLUSIVE_SCOPE_VALIDATION`。 |
| reachable unsupported `invokedynamic` | Coverage warning | 保留分析结果，Module为`INCONCLUSIVE_INVOKEDYNAMIC_MODEL`。 |
| MethodHandle caller-local target unresolved | Coverage warning | 保留分析结果，Module为`INCONCLUSIVE_METHOD_HANDLE_MODEL`。 |
| ServiceLoader unresolved evidence | Coverage warning | 保留分析结果，Module 为 `INCONCLUSIVE_SERVICE_LOADER`。 |
| CHA `Class.forName` local constant unresolved/invalid | Coverage warning | 保留分析结果，Module为`INCONCLUSIVE_REFLECTION`。 |
| CHA实际到达`changed-paths` no-op dependency leaf | Coverage warning | 保留caller→leaf edge，Module为`INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY`。 |
| ChangePoint收集期SSA equivalence `UNKNOWN` | Evidence warning | Fail-open保留`METHOD_BODY_CHANGED`，不改变Module status/reason；审计原因写入Report。 |
| 内容不同的 duplicate class | Non-blocking warning | 按 classpath precedence 选择 winner；不改 status/reason、Coverage limitations 或 exit code。 |
| Code comparison unavailable | Evidence warning | 保留Impact结果；Affected Paths仅显示`Unavailable`，详细原因写Console，不改Module status。 |

默认启用`ssa-equivalence`：每个唯一logical old/new JAR pair在Module binding前完成ChangePoint收集。只有method body hash不同且old/new class major version不同时才建立pair-local old/new WALA Class Hierarchy和独立SSA cache。`MATCHED`不进入effective ChangePoint；`DIFFERENT`或`UNKNOWN`保留。显式传入`--result-refinement-algorithms none`可完全关闭。该比较不消费Impact Path，不构建baseline Call Graph，也不产生path-level SSA状态。

`--output` 指向 Overall Index；同级 `<output-stem>-modules/` 为每个非 `SKIPPED` Module 生成两页：

```text
<module-base>.html               Module Index
<module-base>-impact.html        Affected Paths
<module-base>-impact-data/       Affected Paths本地数据分片
```

Overall Index提供`How to read this report`、`Analysis scope and limitations`、`Terminology`、Impact/Structural汇总与Module表，不展示path-level SSA filtering状态或Diagnostics。Technical details展示effective Algorithm、JDK Method Model、WALA Reflection applied状态、SSA matched/different/unknown总数，以及逐方法class version、body hash、status、reason和timing。即使SSA抑制全部ChangePoint并使Module跳过后续分析，Overall仍保留该证据。

Module Index展示status、scope、runtime metrics、typed coverage limitations，以及本Module全部selected effective changed members指标表。指标列为Changed dependency、精确`ChangePointKind`、Changed member/class、Impact、Structural、Impact total；`Impact total = Impact + Structural`。默认按Impact total降序，并支持dependency/member搜索、changed-member target `groupId:artifactId`的Include/Exclude Glob、`ChangePointKind`筛选和20/50/100分页。各条件使用AND组合；Impact/Structural均为0的member仍会显示。浏览器selector只能缩小CLI已分析数据，不能恢复CLI排除的changed member。

Affected Paths只提供Impact、Structural、All视图。每行对应唯一`(impactPath, changedMember)`，列出完整Root Impact Path中的全部PROJECT methods、changed dependency、精确`ChangePointKind`、changed member、path与Java code diff。A→B→changed member只显示A root path，B作为Affected application methods的一部分；不会为B生成后缀路径。Affected method与dependency Include/Exclude是draft条件，只有点击Search或在输入框按Enter才提交；输入期间不扫描分片。View type、分页与Rows per page复用最近一次已提交结果，不重新扫描index。

主HTML只保存Schema 4 manifest、排序后的source catalog与row ranges；source-index、affected-method index、row、path、method、member、dependency与diff按4 MiB目标上限写入本地JavaScript分片。Dependency Glob只匹配changed member的target source，不匹配path中的应用方法或中间依赖；合法未命中返回零结果，非法Glob内联报错并保留最近成功页面。直接通过`file://`打开时，source filter先加载轻量source ranges，再与affected-method ranges和View type求交；默认分页只加载当前页，Java diff在展开时才加载，不使用backend、network request、`fetch`或local storage。`-v`按Module输出分片数量与字节汇总；`-vv`追加每个分片的kind、进度、record数与字节数。

Affected Paths不展示path evidence、member Technical details、Context、descriptor/hash、SSA reason、observations或raw comparison reason。Code diff展开区只包含Vineflower decompiled Java unified diff；Java文本相同显示`Java text identical`，无法生成显示`Unavailable`，不提供ASM fallback。只有Impact或Structural path关联member生成comparison；无路径member不反编译。Comparison使用`common`pool滚动并发执行，并按logical member去重。

页面数据按dependency、member、method、path、path step、diff和path-member relation分表并通过整数ID连接。同一path关联多个member时path只存一次，每个member的diff最多存一次。JavaScript只为当前页创建DOM，搜索、筛选与翻页使用`DocumentFragment`替换`tbody`。全部Report页面使用完整viewport宽度与响应式padding；桌面目录栏保持稳定宽度，主内容占满剩余空间。表格使用紧凑sticky header、横向滚动、badge、数字对齐与键盘focus样式；800px以下目录堆叠且表格不造成页面级横向溢出。页面不访问网络、不使用CDN、外部asset或浏览器持久化存储。

Module failure不取消其他Module；handled failure仍发布partial Report。Affected Paths空态不表示已经证明没有业务影响；应同时查看Module changed member指标和Coverage limitations。

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
- `--ref` 省略时分析 current checkout；提供时在 `<config-dir>/tree/workspaces/<run-id>/` 创建单个 local commit snapshot 的 detached worktree，不 fetch。
- `--output` 是 HTML report directory。
- `--scopes` 默认 `compile,runtime,provided,test,system`。
- `--dependency-plugin-version` 默认为 JAR 内置的 `3.6.1`；指定时作为高级 override，并必须通过完整 evidence capability check。
- Global `--java-home` 仅设置 Maven subprocess `JAVA_HOME`；`tree` 不要求 JDK 8。

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

`tree` Console 固定为 `Preflight → Analysis → Summary`，全部使用五段 Diagnostic prefix。Analysis 中每个 reactor 的 start/result 使用 `stage=analysis, substage=reactor`，第五段只保留 `reactor` identity；`SUCCESS` 为 `INFO`，degraded/issue 为 `WARN`，failed 为 `ERROR`。Maven collection 默认只转发 warning/error，`-v/-vv` 转发全部非空行；failure evidence 保留最后 100 行。Summary 第五段为空，`status` 与 `report` 位于 message。

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
| `0` | `impact` 为 `SUCCESS`/`INCONCLUSIVE`，或 `tree` 为 `SUCCESS`。 |
| `1` | CLI parse/validation 或 command-level preflight failure；不生成 report。 |
| `2` | `impact` 为 `PARTIAL_SUCCESS`/`FAILED`；或 `tree` 为 `COMPLETED_WITH_ISSUES`/`FAILED`。 |

## 9. Troubleshooting

### Unsupported Maven version

检查 console `tree.maven-version` 或 `impact.maven-version` evidence。将 `--maven` 指向 `3.6.3 <= version < 4.0.0` 的 Maven，或移除 `--maven` 使用内嵌 3.6.3。

### Maven executable cannot run

确认 `--maven` 是 executable file，不是 Maven home；Windows 应指向 `mvn.cmd`。通过 `--java-home` 设置 Maven subprocess 的 `JAVA_HOME`。

### `impact` target JDK Preflight failure

`impact` 只接受完整 JDK 8。确认 `--java-home` 指向 JDK root 而不是 JRE，且存在 executable `bin/java`、`bin/javac` 与 runtime `rt.jar`。Analyzer JAR 本身继续由 Java 17 启动；不能把 analyzer 的 Java 17 home 作为 `impact` target。

### Call Graph 长时间运行

默认CHA不求解points-to且不遍历JDK body，通常状态空间较小；完整class hierarchy的virtual/interface over-approximation仍可能产生大量edge。Experimental `k-obj`的规模取决于application/JDK reachability、ReflectionOptions和`k`。WALA monitor只提供cooperative timeout/cancel；设置正数`--call-graph-timeout-seconds`后仅超时Module失败，不自动切换algorithm。需要观察资源时使用`-vv`。

### JDK Method Model 安装失败

该问题只适用于`k-obj`。它默认`jdk8`，需要完整JDK 8 hierarchy、Synthetic loader和384-target catalog；失败时不会降级。CHA固定`none`，显式`cha + jdk8`是参数错误；即使为`none`，仍必须提供完整JDK 8用于hierarchy和leaf resolution。

### Duplicate class warning

`Resolved conflicting duplicate classes by classpath precedence` 表示同一 binary name 有内容不同的多个定义。Analyzer 不再因此阻塞 Module；请在 Module Index 的 `Duplicate class resolution` 查看 winner、shadowed sources 与 precedence reason。`SHADOWED_BY_DUPLICATE` changed definition是loser，因此不生成Affected Path或code comparison；Module changed member指标仍包含该member。该warning本身不代表Coverage limitation；若Module同时为`INCONCLUSIVE`或`FAILED`，应查看独立reason与Coverage limitations，并结合Console日志排查。

### SSA equivalence 为 `UNKNOWN`

默认`ssa-equivalence`下，old/new session或IR不可用、method lookup失败、unsupported instruction、bootstrap evidence不足、Control Flow Graph mapping ambiguity或exception会返回`UNKNOWN`。该结果fail-open：`METHOD_BODY_CHANGED`仍进入后续Impact analysis，不改变Module status。Overall或Module page的`SSA ChangePoint collection evidence`展示class versions、hash、reason与耗时。使用`-vv`时，可按`ssaUnknownRetained`或`method=<方法关键词>`搜索Console，查看old/new字节码、原始IR及归一化IR；不可用段显示稳定reason。若需要完全跳过该比较，显式传`--result-refinement-algorithms none`。

### Maven dependency resolution failure

Dependency Analyzer 不把 project dependency local repository 放入 config dir。内置 file repository 仅提供 Maven Dependency Plugin、Dependency Evidence Plugin 及其运行依赖；project artifact 仍由原 Maven session 按用户 settings、mirror、proxy、credential 和 local repository 解析。

### Dependency Evidence Plugin capability failure

`impact` 固定调用内嵌 Dependency Evidence Plugin `3.0.0` 的 `collect-dependency-evidence` goal。Preflight 或 Mojo capability failure 表示 Plugin runtime 不完整、Maven 不在 `3.6.3 <= version < 4.0.0`，或结构化 Maven Dependency Tree API 不可用。`--dependency-plugin-version` 只影响 `tree` 的面向人报告，不影响 `impact`。

### Reactor 为 `FAILED`

在 index/reactor page 查看“问题”表和 Analysis diagnostics。常见原因包括 malformed POM、active module POM 缺失、module path 越出 repository、plugin goal 无法解析或 Maven 部分 module failure。

### Config dir runtime 损坏

再次执行 command 会检查 completion marker 和必要文件，并只重建对应 Maven 或 Plugin component/version leaf。Generated settings 为 command-scoped temporary file。不要整体删除 config dir；其中可能包含未来配置或用户文件。工具不执行 archive 内容 fingerprint；文件存在但内容被外部修改时，可删除对应 completion marker 或必要文件触发重建。

## 10. Version 与 Distribution 构建

Analyzer、Dependency Evidence Plugin、公共 JDK Method Model 与 JDK 8 Method Model 使用独立 SemVer。日常开发在下一次 release 前复用固定 `X.Y.Z-SNAPSHOT`，不因每次本地自测 bump 或 commit。按dependency顺序安装两个model artifact与Plugin，再构建Analyzer：

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
mvn -f plugins/pom.xml clean install
mvn clean verify
```

Root POM 的 `test.jdk8.home` 默认指向 `/absolute/path/to/jdk8`。其他环境使用 `-Dtest.jdk8.home=/absolute/path/to/jdk8` 覆盖；Surefire/Failsafe 自动向 test JVM 注入 `TEST_JDK8_HOME`，执行 Maven 前无需设置该环境变量。

Version 修改完全使用 Versions Maven Plugin。例如切换 Plugin stable version：

```sh
mvn -f plugins/pom.xml versions:set-property \
  -Dproperty=revision \
  -DnewVersion=3.0.0 \
  -DgenerateBackupPoms=false
```

正式 release 依次构建并安装公共model、JDK 8 model和Plugin，再构建Analyzer：

```sh
mvn -f models/jdk/pom.xml -Prelease clean install
mvn -f models/jdk8/pom.xml -Prelease clean install
mvn -f plugins/pom.xml -Prelease clean install
mvn -Prelease clean verify
java -jar target/dependency-analyzer.jar --version
```

`release` profile 只接受 Stable SemVer，并拒绝 Snapshot dependency。正式 Analyzer artifact 是 `target/dependency-analyzer.jar`；不生成 project-owned checksum、build manifest 或额外 distribution directory。

Release验证通过后，为各独立artifact创建对应annotated tag。当前Analyzer开发版本为`3.0.0-SNAPSHOT`；本次变更不修改Plugin或JDK model artifact version。

Version policy、failure entrypoints 与完整发布步骤见
[`wiki/runbooks/version-and-distribution.md`](../wiki/runbooks/version-and-distribution.md)。

## 11. 持续 Impact Benchmark

Repository内置CHA-only中型`impact` benchmark，覆盖`changed-paths`与`full`两种scope。每个scope执行1次Static Single Assignment（SSA，静态单赋值）warm-up、5次SSA formal与1次CHA local receiver control，共7个Java Virtual Machine（JVM）进程；双scope共14个。4组semantic baseline当前为`PENDING`，人工确认前不能发布tracked snapshot。Benchmark命令只在用户明确要求时执行。

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
mvn -f plugins/pom.xml clean install
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

固定HTML报告输出到`tmp-files/impact-medium-benchmark/benchmark-report-changed-paths.html`与`benchmark-report-full.html`。每个scope汇总5个CHA formal sample、1份summary和warm-up topology；任一run失败、baseline为`PENDING`或发生`TOPOLOGY_DRIFT`时不发布tracked snapshot。完整contract见[`benchmarks/impact-medium/README.md`](../benchmarks/impact-medium/README.md)。

## 12. Third-Party Attribution

Uber JAR 内包含未修改的 Apache Maven 3.6.3 binary distribution，以及 distribution 的 `LICENSE`、`NOTICE`；同时包含 Maven Dependency Plugin 3.6.1 完整运行 repository ZIP、`LICENSE`、`NOTICE`、`DEPENDENCIES`，以及 Dependency Evidence Plugin repository ZIP。Dependency Evidence Plugin ZIP 包含当前 version JAR、consumer POM 与 Maven Dependency Tree API runtime；Maven 提供的 Resolver API 不在 consumer POM 中重复打包。项目不生成或验证额外 checksum；Dependency Plugin repository ZIP 内既有第三方 `.sha1` sidecar 保持不变。Source repository 中静态 resources 位于 `analyzer/src/main/resources/maven/`，Plugin source/package 位于 `plugins/artifact-path-resolver/`。

WALA 1.8.0 以 EPL-2.0 使用，Vineflower 1.12.0 slim 以 Apache-2.0 使用；attribution 位于 `analyzer/src/main/resources/licenses/` 并随 uber JAR 打包。Vineflower 代码和 runtime dependency 已内嵌，内网执行 `impact` 不下载 decompiler artifact；重新构建工程时仍需要 Maven mirror 或已缓存 artifact。
