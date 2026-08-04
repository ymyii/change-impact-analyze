# Dependency Analyzer

Java 17 + Maven dependency analysis CLI。

- `impact`：dependency upgrade 的 bytecode 与业务调用影响分析。
- `tree`：Git repository 内 Maven reactor/module dependency tree 的 offline HTML report。

## Build

```sh
mvn verify
mvn package
```

Uber JAR：`target/dependency-analyzer.jar`。

Repository 是标准 Maven multi-module：

```text
pom.xml                          parent + aggregator
analyzer/                        Java 17 CLI/application
plugins/                         内置 Maven Plugin aggregator
└── artifact-path-resolver/      Java 8 Artifact Path Maven Plugin
```

Analyzer GAV 仍为
`io.github.dependencyanalysis:dependency-analyzer:0.1.0-SNAPSHOT`；内置
Plugin GAV/goal 为
`io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:1.0.0:resolve-artifact-paths`。

## Usage

```sh
java -jar target/dependency-analyzer.jar --help

java -jar target/dependency-analyzer.jar impact \
  --java-home /path/to/jdk8 \
  --baseline main \
  --output build/impact.html

java -jar target/dependency-analyzer.jar tree \
  --path . \
  --output build/dependency-tree
```

`impact` 与 `tree` 都使用 `-p, --path <dir>`：默认 current directory，
同时定位所属 Git repository 并限定分析目录。`impact --target` 表示比较目标
commit；`tree --ref` 表示单个 snapshot Git ref。

`tree --path` 命中 reactor root 时完整分析该 reactor；只命中 child module
时，工具使用 Maven `-pl/-am`，Report 仅包含用户选择的 module 及其符合
`--scopes` 的同 reactor 传递依赖 module。无关 sibling、仅用于
parent/aggregator/plugin/extension 的 support project 不进入 Report。Packaging
为 `pom` 且存在 active child 的纯 aggregator root 仍作为 Maven execution
entrypoint，但不生成 Module result。

| Scope | Short | Long |
|---|---:|---|
| Root | `-m` | `--maven` |
| Root | `-j` | `--java-home` |
| Root | `-c` | `--config-dir` |
| Root | `-a` | `--maven-arg` |
| impact | `-p` | `--path` |
| impact | `-b` | `--baseline` |
| impact | `-t` | `--target` |
| impact | `-o` | `--output` |
| impact | `-f` | `--format` |
| impact | `-k` | `--include-change-kinds` |
| impact |  | `--call-graph-timeout-seconds` |
| tree | `-p` | `--path` |
| tree | `-r` | `--ref` |
| tree | `-o` | `--output` |
| tree | `-s` | `--scopes` |
| tree | `-d` | `--dependency-plugin-version` |

Tree command-level Preflight 通过后先生成 `RUNNING 0/N` Index；每完成一个
reactor，先原子发布 reactor page，再刷新 Index checkpoint。正常结束为
`SUCCESS N/N`；所有 reactor 都已处理但存在 issue 时为
`COMPLETED_WITH_ISSUES`；pipeline/report failure 为 `FAILED`；process 被硬中断时，
已发布页面和最后一个 `RUNNING x/N` checkpoint 保留。

`tree` 默认使用 JAR 内置的 `maven-dependency-plugin:3.6.1`，无需
从远程 repository 下载 plugin。Console 按 `Preflight → Analysis → Summary`
输出进度；长时间 Maven collection 每 10 秒输出 heartbeat。

`impact` 在同一个 target Maven process/session 中执行 fully-qualified
`dependency:tree` 与内置 `resolve-artifact-paths` goal。GraphML 提供 mediated
dependency tree；versioned JSON 提供 Resolver 返回的 external artifact absolute
path。Plugin 直接复用原 session 的 effective repository、mirror、proxy、credential、
local repository、offline policy 与 WorkspaceReader，对 selected artifact 执行非传递
`ArtifactRequest`。Reactor coordinate 不解析 binary，后续继续映射到
`target/classes`。不生成 temporary POM、不调用 `dependency:list`、不添加 `-llr`。

Artifact Path JSON Schema v1 的顶层字段为 `schemaVersion`、`module` 和
`artifacts`；每个 coordinates 包含 `groupId`、`artifactId`、`type`、
`extension`、`classifier`、`version`、`baseVersion`，artifact 另含 `scope` 与
`absolutePath`。Analyzer 严格校验 `schemaVersion == 1`、必填类型、唯一 binding、
absolute existing path，以及 GraphML/JSON external dependency 集合完全一致；未知字段
允许用于后续兼容扩展。

Index 的 Metadata、Summary 和 Reactors 均使用表格；Reactors 分别统计
Internal conflicts 与 Cross-module conflicts。Reactor page 独立展示跨 Module
冲突，并用 Module tabs 展示每个 Module 的 internal conflicts 与 Maven-style
verbose `<pre>` dependency tree。Tree 内没有展开、检索或过滤交互，全部 assets
可离线通过 `file://` 使用。两类 conflict table 各自支持检索、Scope filter、
排序和 10/50/100 分页；Cross-module table 另有 Module filter，交互状态互不影响。

`tree` 只分析实际进入 resolved project dependency tree 的 occurrence，
不会枚举未使用的 `dependencyManagement` 或 plugin dependency。Module 内
`MODULE_MEDIATION` 比较实际 dependency path 的原始 requested version 与
实际应用的 dependencyManagement version；任意两个版本来源不同即报告。
`managedFrom` 是 Maven 报告的 management 前值，不是提供 management 的
POM/BOM path。`duplicate` 只作为 omitted evidence；跨
Module `CROSS_MODULE_RESOLUTION` 则表示 selected occurrence 最终解析出至少
两个不同 version。Internal conflict 的 Evidence 列展示 Source、Dependency
chain、Original version 与 Scope；Cross-module Evidence 额外展示 Module。
`DEPENDENCY_MANAGEMENT` Evidence 的 Dependency chain 保持为空，不将 occurrence
path 误写成 management source。

完整 CLI、Maven runtime、preflight、report 和 troubleshooting 说明见 [用户手册](docs/user-manual.md)。

工程架构与编码知识入口见 [wiki/index.md](wiki/index.md)。
