# Dependency Analyzer 用户手册

## 1. 产品简介

Dependency Analyzer 是 Java 17 CLI，面向 Maven project：

- `impact`：比较 dependency 升级前后的 resolved dependency、bytecode 和业务调用影响，输出 Overall HTML Index；每个非 `SKIPPED` Module 输出 Module Index、Affected Call Chains、Dependency Changes 三个英文页面。
- `tree`：扫描一个 Git repository 内的 Maven reactor，输出 repository 级 offline HTML dependency tree report。

当前稳定 release 为 Analyzer `2.0.0`、Artifact Path Plugin `2.1.0`。Root command 为 `dependency-analyzer`。

Source repository 包含 root Analyzer、Plugin、公共 JDK Method Model 与 JDK 8 Method Model 四个独立 Maven reactor。根 `pom.xml` 只聚合 Java 17 的
`analyzer/`，生成 `target/dependency-analyzer.jar`；`plugins/pom.xml` 独立聚合 Java 8
Plugin `plugins/artifact-path-resolver/`；`models/jdk/pom.xml` 与 `models/jdk8/pom.xml` 按 dependency 顺序独立构建。Analyzer uber JAR 内含两个 model artifact。Plugin reactor 通过 Maven local repository
交付 attached `repository` ZIP。Artifact Path Plugin goal 为
`io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:2.1.0:resolve-artifact-paths`。

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

`--verbose --verbose` 与 `-vv` 等价。Analyzer 运行日志统一写入 stderr，每个物理行固定为 `[时间][日志级别][阶段][子阶段][额外信息] message`；缺失段使用 `[-]`。第五段只包含当前四段无法唯一表达的阶段实例或日志分类 identity，当前为 `check/reactor/module/artifact/pool`；status、progress、elapsed、path、计数和 metrics value 等实际日志信息使用 message 中的 `key=value`。`INFO` 输出稳定的 stage、progress、warning 和 error，Maven subprocess 只透传 warning/error；`DEBUG` 额外输出 analysis option/decision、完整 Maven subprocess output，并在异常时逐行输出带完整 prefix 的 stack trace；`TRACE` 再输出 normalized path、ref、scope，以及启动后立即采样、随后每 10 秒采样的 Runtime Metrics。Picocli help/usage、参数解析错误和第三方库直接写入 stderr 的内容不保证五段 prefix。

`-vv` Runtime Metrics 包含 heap `used/committed/max` MiB，以及当前 Analyzer-owned `front-preparation`、`jar-diff`、`module-analysis`、`code-comparison` thread pool 的 core/max/size/active/queued/completed/tasks 和 lifecycle 状态。Heap 第五段为空；thread-pool 第五段只包含 `pool` identity；sample、elapsed 和全部指标值位于 message。`-v` 不创建 metrics scheduler，也不输出 metrics。Runtime Metrics、Maven output、Preflight evidence/fallback 和 stack trace 只进入 Console，不进入 HTML Diagnostics；HTML Diagnostics 与 Console 对 retained event 使用相同 timestamp 和 prefix。

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

每次 command 使用 UUID `run-id`、owner marker 和 `<config-dir>/locks/` file lock。Detached worktree、GraphML probe 和 command-generated intermediate file 只写入对应 subcommand run。正常和异常关闭只清理当前 run；启动时只回收 owner marker 有效且未被其他 process lock 的 stale run。`impact` 的 Maven build/dependency output 不写 `.log`，按 `-v` 直接输出到 Console；failure 只在内存保留 bounded tail。

`impact` 固定使用 JAR 内嵌 Maven Dependency Plugin `3.6.1` 和 Artifact Path Plugin `2.1.0`；`tree` 默认也使用前者，但保留高级 version override。两个 Plugin 都以内嵌 Maven repository ZIP 提供，runtime 分别解压到独立 cache，不再把 Artifact Path Plugin loose JAR/POM 手工安装进 Dependency Plugin repository。Stable version 复用 component/version cache；Artifact Path Plugin Snapshot 每次 command 解压独立 leaf、启用 `updatePolicy=always` 并传入 `-U`，不会刷新大型 Dependency Plugin repository。

Runtime 生成 command-scoped global settings overlay：一个 active profile 注册两个 file `pluginRepository`，保留用户 `-gs` 中的 mirror、proxy、server、local repository 等配置以及独立 `-s` 参数；两个内置 repository ID 从通配 mirror 中排除。Settings 使用 owner-only permission，command 结束时删除；内容不会输出到 Console。Overlay 用于工具控制的 Plugin goal，不应用于 target `compile`。

Preflight evidence 包含 `artifactPathPlugin=2.1.0` 与 `repositories=2`。Mojo 启动后输出
`implementation=graphml-v2`、Maven 实际加载的 JAR absolute path 与
`dependencyGraphFileName`。若 CodeSource evidence 不可用，Plugin 输出 warning 但继续执行。Maven `-X` output 中的
`(f) dependencyGraphFileName = ...` 是新 Plugin descriptor 已加载的额外证据。

Artifact Path Plugin 支持 `3.6.3 <= Maven version < 4.0.0`，编译为 Java 8 bytecode。它以 Maven 3.6.3 所携 Resolver API 为最低编译基线，只使用 Maven 3.x 共享的 public Maven/Resolver API；Maven 4 不在兼容范围。

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
  -j, --java-home <jdk8-home> \
  [-p, --path <dir>] \
  -b, --baseline <local-ref> \
  [-t, --target <local-ref>] \
  -o, --output <file> \
  [-f, --format html] \
  [--analysis-target spring-backend] \
  [--analysis-parallelism <count>] \
  [--call-graph-algorithm <rta|zero-cfa|optimized-0-1-cfa|1-object-1-call-site>] \
  [--jdk-model <jdk8|none>] \
  [--wala-reflection-options <WALA-enum-name>] \
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
- `--analysis-parallelism` 默认 `2`，必须 `>=1`；统一控制 Module analysis、JAR diff 和代码反编译各自的 bounded pool。超过 CPU 数只输出 warning，不静默截断。
- `--call-graph-algorithm` command-wide选择全部 Module 使用的 WALA算法；默认`rta`，可显式选择`zero-cfa`、`optimized-0-1-cfa`或`1-object-1-call-site`。值大小写不敏感，但不接受`rapid`、`zero`、`zerocfa`等alias，也不执行timeout fallback。RTA直接使用WALA `BasicRTABuilder`，按全局已实例化compatible class求virtual/interface reachability；`1-object-1-call-site`同时保留一层receiver allocation string与一层call string，使用精确allocation-site和constant-specific identity且不启用smushing，因此通常需要更多时间与内存。
- `--jdk-model` command-wide选择全部 Module 使用的 JDK Method Model。默认`jdk8`，通过JDK 8 catalog为精确public contract安装conservative WALA Synthetic IR；`none`完全跳过catalog读取与selector安装，恢复直接分析真实JDK bytecode的行为。值大小写不敏感，只接受精确标识符`jdk8`或`none`，不接受alias。
- `--wala-reflection-options`（alias `--reflection-options`）command-wide选择WALA `AnalysisOptions.ReflectionOptions`，接受原生enum name且大小写不敏感。默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`；可显式使用`FULL`、`NO_FLOW_TO_CASTS`、`STRING_ONLY`、`NONE`等WALA 1.8.0 value。该选项不随algorithm自动改变，也不执行fallback。
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
- `jdk8`要求WALA hierarchy支持Synthetic loader，并要求完整JDK 8中的384个catalog target全部available；安装异常或任一target unavailable都会失败当前Module，禁止静默降级到`none`。model hit为0不是错误。

| Short | Long | 含义 |
|---:|---|---|
| `-p` | `--path` | Git repository 内的 Maven project/分析目录。 |
| `-b` | `--baseline` | 比较起点 local commit。 |
| `-t` | `--target` | 比较终点 local commit；不是 tree snapshot ref。 |
| `-o` | `--output` | HTML Index 文件。 |
| `-f` | `--format` | 仅 `html`；`md` 已移除。 |
|  | `--analysis-target` | 仅 `spring-backend`。 |
|  | `--analysis-parallelism` | Module analysis、JAR diff、代码反编译并发数，默认 `2`。 |
|  | `--call-graph-algorithm` | `rta`（默认）、`zero-cfa`、`optimized-0-1-cfa`或`1-object-1-call-site`；全部 Module 使用同一算法。 |
|  | `--jdk-model` | `jdk8`（默认）或`none`；全部 Module 使用同一选择。 |
|  | `--wala-reflection-options` | WALA `ReflectionOptions` enum name；默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。 |
|  | `--entrypoint-include` | 只选择匹配 slash class path 的 target class declared methods；可重复。 |
|  | `--entrypoint-exclude` | 从 include/default selection 中排除匹配 slash class path 的 target class；可重复且优先。 |
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

显式使用legacy ZeroCFA与WALA `FULL` reflection：

```sh
java -jar dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --baseline release-1.2 \
  --output build/impact-zero.html \
  --call-graph-algorithm zero-cfa \
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

### 5.3 Pipeline 与报告

Preflight 后先识别模式：选择 reactor root 时，全 reactor 只 compile 一次并逐 Module 分析；选择 leaf POM 时，从 reactor root 使用 `-pl <module> -am` compile，只报告该 Module。当前 Module 是 `PROJECT`，上游 reactor Module 是 `REACTOR_DEPENDENCY`。

前置阶段并行执行 baseline dependency resolution 与 target Maven compile；join 后执行 target dependency resolution。Baseline 不 compile、不构建 Call Graph。每次 dependency analysis 在原 project 的同一个 Maven process/session 中依次执行 fully-qualified `org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree` 和 `io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:2.1.0:resolve-artifact-paths`；Analyzer 通过 `-Dcia.dependencyGraphFileName=<unique>.graphml` 将前一个 goal 的 Module-local GraphML 传给后一个 goal。Target `compile` 仍只使用普通 user Maven arguments。

GraphML 是 `impact` 唯一的 mediation authority，决定 selected coordinate 与 effective scope；Artifact Path JSON 只负责 physical artifact binding。Artifact Path Plugin 不执行第二次 dependency collection。它安全解析 GraphML，校验 root coordinate 与当前 `MavenProject` 一致，只读取 selected `compile/runtime/provided/system` dependency，并完全忽略 `test`。因此 `test` 不进入 dependency diff、path binding、JAR diff 或 Call Graph。Exclusion 与 conflict loser 已由 GraphML 结论排除，不会创建下载请求；Reactor coordinate 直接跳过，由 Analyzer 映射到 target `target/classes`。

Module classpath 使用固定 precedence：JDK boot classpath、JDK extension classpath、当前 Module `target/classes`、依赖 Module `target/classes`、external dependencies。Reactor 与 external tier 内保持当前 Module 的 GraphML pre-order traversal，不按 coordinate 或 path 二次排序。同一 binary name 的内容冲突按该顺序选择唯一 winner；byte-identical duplicate 静默去重。根级 `module-info.class` 与 `META-INF/versions/**` 不参与 ownership。内容不同的 duplicate 输出 `WARN` 与 winner/loser evidence，但不进入 Coverage limitations、不改变 Module `SUCCESS` 或 exit code。WALA、Structural Reference 与 invokedynamic evidence 都只读取 winner；loser source 中其他唯一 class/resource 不受影响。

对非 `system` binding，Plugin 通过 session `ArtifactTypeRegistry` 恢复 extension/default classifier，使用当前 Module 的 `project.remoteProjectRepositories` 批量创建非传递 `ArtifactRequest`，并只采用 Maven Resolver 返回的 `ArtifactResult` file。RepositorySystemSession 继续提供 mirror、proxy、authentication、local repository、offline policy、cache 与 WorkspaceReader。GraphML 不包含 transitive node-specific repository list，因此 request 不重建该信息。对 GraphML selected `system` binding，Plugin 按相同 coordinate 匹配当前 effective `MavenProject` 的 `system` dependency，要求唯一匹配，并验证 `systemPath` 是 absolute existing regular file；JSON 直接绑定该 path，不创建 remote `ArtifactRequest`。该路径不生成 temporary POM、不调用 `dependency:list`、不自行拼接 local repository path，也不附加 `-llr`。

Plugin 参数 `-Dcia.dependencyGraphFileName=<unique>.graphml` 与 `-Dcia.resolvedArtifactsFileName=<unique>.json` 都只接受 filename。Absolute path、目录分隔符与 `..` 会被拒绝；文件解析到当前 Module `project.basedir`。缺少 GraphML、root coordinate 不匹配、XML malformed、graph cycle/unreachable node、`system` coordinate 无唯一 effective dependency、`systemPath` 非 absolute existing file、physical path ambiguity 或 artifact resolution failure 时 goal 失败，不提供 collection fallback。Plugin 先写 sibling temporary file，全部 binding 成功后 atomic move；失败不发布部分 JSON。

#### Artifact Path JSON Schema v2

```json
{
  "schemaVersion": 2,
  "artifacts": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "test-jar",
        "extension": "jar",
        "classifier": "tests",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "absolutePath": "/absolute/repository/library-2.0-tests.jar"
    }
  ]
}
```

JSON 为 UTF-8；`artifacts` 按完整 coordinates、absolutePath 排序，无 external dependency 时为 `[]`。Analyzer 要求 `schemaVersion == 2`、所有必填字段类型正确、coordinate binding 唯一、path 为 absolute existing file；禁止 duplicate property，忽略未知字段。每个 Module 的 JSON 与 GraphML 通过两者所在 canonical directory 配对，external dependency 集合只按 coordinates 双向比较，不比较 scope。`version` 可保留 timestamped SNAPSHOT，binding 使用 `baseVersion` 与 Maven dependency tree 对齐。`system` dependency 使用相同 coordinates/path Contract，`absolutePath` 直接来自 validated effective `systemPath`。

Scope 只存在于 GraphML dependency graph。相同 coordinates/version 只有 scope 变化时不产生 impact change；version 与 scope 同时变化时只产生一个 `VERSION_CHANGED`。Baseline 与 target physical path 分别按各自 Module-local manifest 中的 coordinates 查找，不使用 `DependencyChange.scope`。

Physical JAR pair 按 `--analysis-parallelism` 并行 bytecode diff。每个 relevant target Module 的 `target/classes` 只扫描一次，生成的 immutable entrypoint class index 同时用于 selector 门禁和实际 Call Graph roots；该步骤只缩小 root methods，不裁剪 Module scope、CHA、Reflection、ServiceLoader 或其他 origin reachability。Entrypoint 参数按 declared type 建模，interface/abstract type使用 synthetic placeholder，不枚举真实 subtype。存在默认纳入的removal/modification/access-narrowing ChangePoint且命中entrypoint scope的Module独立执行scope validation、JDK 8 CHA、selected WALA strategy、selected ReflectionOptions、MethodHandle/ServiceLoader/`invokedynamic` fixed-point model和read-only query。默认RTA使用`BasicRTABuilder`且不模拟points-to dataflow；`zero-cfa`按concrete class合并普通allocation并保留constant-specific keys；optimized 0-1-CFA保留allocation-site/constant identity并smush高成本对象。Module内build/query单线程，Module之间按`--analysis-parallelism`并行。没有post-build overlay、零seed skip、baseline Call Graph或full predecessor copy。

Bytecode diff额外产生`CLASS_ACCESS_NARROWED`、`METHOD_ACCESS_NARROWED`（含constructor）和`FIELD_ACCESS_NARROWED`。Query使用target CHA解析actual declaration，并按Java 8 runtime package、subclass、symbolic owner与caller-local verifier receiver type判断new access。`ACCESSIBLE`不建path；`INACCESSIBLE`与`POTENTIALLY_INACCESSIBLE`保守保留path；全部reference仍合法时在Dependency Changes显示`ACCESS_REMAINS_VALID`，不生成Affected Call Chain。该能力分析pre-existing bytecode的JVM binary compatibility，不分析source compatibility、Reflection/JNI/custom ClassLoader或Java 9 module exports。

Module analysis 检查分类如下。这里的“阻塞”只终止当前 Module，其他 Module 继续；全部 Module 完成后再按既有规则形成 Overall status 与 exit code。

| 检查项 | 分类 | 结果 |
|---|---|---|
| `PROJECT`/`REACTOR_DEPENDENCY` 引用 excluded JDK class；scope scanner unreadable/failure | 阻塞 | 当前 Module `FAILED_SCOPE_VALIDATION`。 |
| 零 `PROJECT` entrypoint、CHA/Call Graph construction failure | 阻塞 | 当前 Module `FAILED_ANALYSIS`。 |
| Call Graph timeout | 阻塞 | 当前 Module `FAILED_CALL_GRAPH_TIMEOUT`。 |
| external dependency 引用 excluded JDK class | Coverage warning | 保留分析结果，Module 为 `INCONCLUSIVE_SCOPE_VALIDATION`。 |
| reachable unsupported `invokedynamic` | Coverage warning | 保留分析结果，Module为`INCONCLUSIVE_INVOKEDYNAMIC_MODEL`。 |
| RTA MethodHandle local target unresolved | Coverage warning | 保留分析结果，Module为`INCONCLUSIVE_METHOD_HANDLE_MODEL`。 |
| ServiceLoader unresolved evidence | Coverage warning | 保留分析结果，Module 为 `INCONCLUSIVE_SERVICE_LOADER`。 |
| SSA equivalence `UNKNOWN` | Coverage warning | 不删除 Impact Path，原 `SUCCESS` Module 转为 `INCONCLUSIVE`。 |
| 内容不同的 duplicate class | Non-blocking warning | 按 classpath precedence 选择 winner；不改 status/reason、Coverage limitations 或 exit code。 |
| Code comparison unavailable | Evidence warning | 保留 Impact 结果；只在 Diagnostics/Technical details 说明，不改 Module status。 |

所有 Module query 完成后，全局串行比较 candidate path 中唯一 `METHOD_BODY_CHANGED` 的 old/new normalized WALA SSA/CFG。只有 `PROVEN_EQUIVALENT` 删除路径；`DIFFERENT` 与 `UNKNOWN` 保留，`UNKNOWN` 使 Module 为 `INCONCLUSIVE`。

`--output` 指向 Overall Index；同级 `<output-stem>-modules/` 为每个非 `SKIPPED` Module 生成三页：

```text
<module-base>.html          Module Index
<module-base>-impact.html   Affected Call Chains
<module-base>-changes.html  Dependency Changes
```

Overall Index 提供 `How to read this report`、`Analysis scope and limitations`、`Terminology` 与 Module 汇总，并统计 duplicate conflict 与 shadowed ChangePoint。Technical details展示实际Algorithm、WALA ReflectionOptions与JDK Method Model选择值`jdk8`或`none`；不展示catalog available/hit计数或target列表。Module Index展示易懂的status、scope、metrics、typed coverage limitations、Module Diagnostics，以及`Duplicate class resolution` winner/loser表；完整physical path位于`Technical details`。Affected Call Chains分别展示最终call chains、默认折叠的SSA-equivalent candidate chains，以及带PROJECT boundary的Structural Reference Chains。Dependency Changes列出至少关联candidate/final Impact Path、Structural Reference Path，或disposition为`SHADOWED_BY_DUPLICATE`/`ACCESS_REMAINS_VALID`的member；access change展示old/new access、typed decision/reason与代表性reference evidence。Shadowed/access-remains-valid member不生成Impact Path。其他raw changes仅计数，不逐项展示。

相关 method、field 与 class 提供默认折叠的 old/new Unified diff。内容来自 local dependency bytecode 的 Vineflower decompiled Java representation，不保证与原始 source 相同；反编译失败或文本相同时保留 ASM instruction fallback。Filesystem path、WALA/SSA、descriptor/hash、raw enum、Maven executable、JDK/config/output path 等 evidence 只放在 `Technical details`。所有页面为英文，并提供 top breadcrumbs、Module sibling navigation 和 responsive sticky TOC；不使用 JavaScript 或外部 asset。

Module failure 不取消其他 Module；handled failure 仍发布 partial Report。空态固定为 `No affected call chain was found within the documented analysis scope.`，不表示已经证明没有业务影响。

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

RTA、ZeroCFA与optimized 0-1-CFA的graph规模取决于application/JDK reachability与selected ReflectionOptions。默认`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`是RTA的bounded policy；显式`FULL`可能显著增加WALA 1.8.0 fixed-point时间与heap。ReflectionOptions只选择WALA原生Reflection实现，不承诺三种builder产生相同target set：WALA 1.8.0 Basic RTA不提供Reflection metadata-object `InstanceKey`，`Class.newInstance`/`Method.invoke`业务target可能无法闭合；本项目不会通过fake value或构图后补边绕过该边界。WALA monitor只提供cooperative timeout/cancel，不创建后台scheduler，也不输出progress event。默认无timeout；设置正数`--call-graph-timeout-seconds`后，仅超时Module fail，其他Module继续并发布partial Report，不自动切换algorithm或ReflectionOptions。需要观察资源时使用`-vv`：command-scoped Runtime Metrics每10秒输出heap和Analyzer-owned thread pool状态；它不代表WALA internal progress。

### JDK Method Model 安装失败

默认`jdk8`需要完整JDK 8 hierarchy、Synthetic loader和完整384-target catalog。安装异常、Synthetic loader不支持或catalog target unavailable时，对应Module失败，Analyzer不会改用`none`。先确认`--java-home`指向完整JDK 8；只有需要对照旧的真实JDK bytecode分析语义时才显式使用`--jdk-model none`。

### Duplicate class warning

`Resolved conflicting duplicate classes by classpath precedence` 表示同一 binary name 有内容不同的多个定义。Analyzer 不再因此阻塞 Module；请在 Module Index 的 `Duplicate class resolution` 查看 winner、shadowed sources 与 precedence reason。Dependency Changes 中的 `SHADOWED_BY_DUPLICATE` 表示该 changed definition 是 loser，因此没有生成 Impact Path。该 warning 本身不代表 Coverage limitation；若 Module 同时为 `INCONCLUSIVE` 或 `FAILED`，应查看独立的 reason/Diagnostics。

### SSA equivalence 为 `UNKNOWN`

Old/new IR 缺失、unsupported instruction、bootstrap evidence 不足、CFG mapping ambiguity 或 exception 会返回 `UNKNOWN`。该结果不会缩小影响范围；相关 Impact Paths 保留，并在 Module page 的 SSA 与 Coverage Limitations 中展示原因。

### Maven dependency resolution failure

Dependency Analyzer 不把 project dependency local repository 放入 config dir。内置 file repository 仅提供默认 Dependency Plugin、内置 Artifact Path Plugin 及其运行依赖；project artifact 仍由原 Maven session 按用户 settings、mirror、proxy、credential 和 local repository 解析。

### Dependency Plugin capability failure

默认内置 `3.6.1` 提供完整 verbose evidence。显式 `--dependency-plugin-version` 若不具备完整 omitted/managed/optional evidence，会在 Command Preflight 阻断；选择 2.9/2.10 或 3.2.0+，并确保该 override 可从用户 Maven repository 解析。

### Reactor 为 `FAILED`

在 index/reactor page 查看“问题”表和 Analysis diagnostics。常见原因包括 malformed POM、active module POM 缺失、module path 越出 repository、plugin goal 无法解析或 Maven 部分 module failure。

### Config dir runtime 损坏

再次执行 command 会检查 completion marker 和必要文件，并只重建对应 Maven 或 Plugin component/version leaf。Generated settings 为 command-scoped temporary file。不要整体删除 config dir；其中可能包含未来配置或用户文件。工具不执行 archive 内容 fingerprint；文件存在但内容被外部修改时，可删除对应 completion marker 或必要文件触发重建。

## 10. Version 与 Distribution 构建

Analyzer、Artifact Path Plugin、公共 JDK Method Model 与 JDK 8 Method Model 使用独立 SemVer。日常开发在下一次 release 前复用固定 `X.Y.Z-SNAPSHOT`，不因每次本地自测 bump 或 commit。按dependency顺序安装两个model artifact与Plugin，再构建Analyzer：

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
  -DnewVersion=2.1.0 \
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

Release 验证通过后，创建一个 commit，并使 `artifact-path-plugin-v2.1.0` 与 `analyzer-v2.0.0` 两个 annotated tag 指向同一 commit。Git tag 是 release record；日常 build 不需要 tag。

Version policy、failure entrypoints 与完整发布步骤见
[`wiki/runbooks/version-and-distribution.md`](../wiki/runbooks/version-and-distribution.md)。

## 11. 持续 Impact Benchmark

Repository 内置 Git 管理的中型 `impact` benchmark。它从source生成42个compile-scope external dependencies、带`impact-baseline`/`impact-target` refs的临时Git project。Canonical matrix 对两个dependency scope与四种algorithm执行48个默认`jdk8` JVM（每个scope各1次warm-up topology capture和5次正式样本），并为每个scope/algorithm增加1个`none` semantic control，共56个独立Java Virtual Machine（JVM）进程；`BENCHMARK_WALA_REFLECTION_OPTIONS`默认并验收为`ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD`。Verifier校验实际Algorithm/ReflectionOptions/JDK Method Model、默认`jdk8`经lazy`Stream.map` private `Function` callback到changed dependency的call chain、9类legacy raw ChangePoint、按scope/model/algorithm锁定的candidate/final count、Structural Reference Path、过滤候选、反编译代码evidence及四页HTML Report。`none`仅按algorithm验收真实JDK bytecode semantic baseline；Performance Report与Git snapshot只发布默认`jdk8`正式样本。

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
mvn -f plugins/pom.xml clean install
mvn package
JAVA8_HOME=/absolute/path/to/jdk8 \
  benchmarks/impact-medium/run-scope-matrix.sh
```

固定HTML报告输出到`tmp-files/impact-medium-benchmark/benchmark-report.html`，四种algorithm与comparison分别使用一个CSS-only tab。Caller/callee父榜以精确CGNode为单位，每个父节点展示decompiled source、WALA IR，以及按IMethod聚合的Top 10相关节点；generated Method允许仅展示IR。不提供独立points-to set排行榜。最近一次完整成功的20个正式样本、algorithm summary和CGNode topology分别保存到Git管理的`samples.tsv`、`summary.tsv`、`topology.tsv`；任一run失败或发生`TOPOLOGY_DRIFT`时不替换旧snapshot。历史对比使用任意两个`summary.tsv`输出absolute change与ratio，不执行固定threshold或algorithm优劣判定。完整prerequisites、环境变量、measurement contract、成功条件和troubleshooting见[`benchmarks/impact-medium/README.md`](../benchmarks/impact-medium/README.md)。

## 12. Third-Party Attribution

Uber JAR 内包含未修改的 Apache Maven 3.6.3 binary distribution，以及 distribution 的 `LICENSE`、`NOTICE`；同时包含 Maven Dependency Plugin 3.6.1 完整运行 repository ZIP、`LICENSE`、`NOTICE`、`DEPENDENCIES`，以及 Artifact Path Plugin repository ZIP。Artifact Path Plugin ZIP 只包含当前 version 的 self-contained JAR 与 consumer POM。项目不生成或验证额外 checksum；Dependency Plugin repository ZIP 内既有第三方 `.sha1` sidecar 保持不变。Source repository 中静态 resources 位于 `analyzer/src/main/resources/maven/`，Plugin source/package 位于 `plugins/artifact-path-resolver/`。

WALA 1.8.0 以 EPL-2.0 使用，Vineflower 1.12.0 slim 以 Apache-2.0 使用；attribution 位于 `analyzer/src/main/resources/licenses/` 并随 uber JAR 打包。Vineflower 代码和 runtime dependency 已内嵌，内网执行 `impact` 不下载 decompiler artifact；重新构建工程时仍需要 Maven mirror 或已缓存 artifact。
