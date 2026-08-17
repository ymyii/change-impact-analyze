---
title: "Dependency Analyzer"
type: project
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Root CLI、共享基础设施和两条分析 pipeline 的架构边界"
  - path: "wiki/features/maven-runtime.md"
    desc: "内嵌 Maven 与两个 Plugin repository 的 runtime 行为"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "外部命令执行的跨平台约束"
  - path: "wiki/rules/release-versioning.md"
    desc: "四个独立artifact的SemVer与Git tag约束"
  - path: "wiki/rules/code-concept-reuse.md"
    desc: "Repository-wide代码与概念复用约束"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包和本地验证操作"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Maven-native version iteration 与 release 操作"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "打包后 impact 的持续性能与场景完整性验证"
  - path: "wiki/features/jdk-method-models.md"
    desc: "Analyzer内嵌的公共engine与JDK 8 model artifact"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer parent/aggregator、SemVer 和 release profile"
  - path: "analyzer/pom.xml"
    desc: "Java 17 Analyzer、JDK 8 model dependency、repository ZIP copy与uber JAR配置"
  - path: "models/jdk/pom.xml"
    desc: "独立公共JDK model engine reactor"
  - path: "models/jdk8/pom.xml"
    desc: "独立JDK 8 catalog/façade reactor"
  - path: "plugins/pom.xml"
    desc: "独立 Plugin parent/aggregator、Java 8 与 release profile"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Dependency Evidence Maven Plugin 与 repository ZIP packaging"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI 和 global options 入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "Spring backend per-Module impact pipeline 编排"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "tree pipeline 编排"
  - path: "benchmarks/impact-medium/run-suite.sh"
    desc: "单scope CHA-only benchmark suite"
---

# Project: Dependency Analyzer

## Summary

Dependency Analyzer 是 Java 17 + Maven + picocli CLI。`impact` 使用显式完整 JDK 8 比较 dependency 升级前后的 bytecode 与业务调用影响；`tree` 扫描 Git repository 内的 Maven reactor，生成 repository 级 offline HTML dependency tree report。

Source repository包含四个独立Maven reactor：root reactor只聚合Analyzer，`plugins/pom.xml`只聚合内置Maven Plugin，`models/jdk/pom.xml`与`models/jdk8/pom.xml`分别构建公共engine和JDK 8 catalog/façade。独立artifact通过Maven local repository按dependency顺序交付，不加入root`<modules>`。

## Design Decisions

- Analyzer开发版本`3.0.0-SNAPSHOT`与Dependency Evidence Plugin`3.0.0`独立使用SemVer。
- 日常开发在下一次 release 前复用同一个 `X.Y.Z-SNAPSHOT`；只有 release 决策才切换 stable version、commit 并创建 Git tag。
- Dependency Evidence Plugin 以 Java 8 bytecode 发布；执行 Plugin reactor 的 Maven JVM 可以使用 Java 8 以上版本。Analyzer 使用 Java 17 构建和运行；root POM 的 `test.jdk8.home` 提供完整 JDK 8 默认值，Surefire/Failsafe 将其作为 `TEST_JDK8_HOME` 注入 test JVM，其他环境可通过 `-Dtest.jdk8.home=...` 覆盖。
- Analyzer JAR 将 Maven Dependency Plugin 和 Dependency Evidence Plugin 内嵌为两个独立 Maven repository ZIP；runtime 不安装 loose JAR/POM，也不维护项目自有 checksum/fingerprint。
- `impact` 通过 Maven API 结构化采集 resolved/raw graph，Schema v3 JSON 在 command cache 内交付 selected tree、occurrence topology、reactor keys 和 physical bindings；`impact` 不读 GraphML。`tree` 使用 verbose text 采集面向人的 dependency occurrence。
- `impact`只构建target per-Module selected Call Graph；`cha`为正式默认algorithm，`k-obj`为显式opt-in的experimental algorithm。Call Graph层冻结自身metadata后，由Impact层统一采集Evidence并执行Impact query。
- CHA固定`jdk-model none`、不应用WALA ReflectionOptions且不遍历JDK body；JDK声明的virtual/interface dispatch不扩展到非JDK实现。`k-obj`默认`jdk8`并可显式`none`。Analyzer仍将JDK model class/catalog打入uber JAR。
- SSA equivalence固定在JAR Diff阶段启用；CHA固定执行caller-local `cha-local-receiver-inference` Impact Path pruning extension。不存在result refinement CLI selection或关闭分支。
- 两个 subcommand 共享 Maven runtime 和 preflight Schema，但分别组装检查 DAG；pipeline 只消费 preflight decision。

## Module Map

- `pom.xml` - Analyzer reactor parent/aggregator，只包含 `analyzer/`。
- `analyzer/` - GAV `io.github.dependencyanalysis:dependency-analyzer:3.0.0-SNAPSHOT`；Java 17 CLI/application，输出为`target/dependency-analyzer.jar`。
- `analyzer/src/main/java/io/github/dependencyanalysis/runtime/` - 内嵌或用户指定 Maven runtime、两个 Plugin repository cache 与 settings overlay。
- `analyzer/src/main/java/io/github/dependencyanalysis/preflight/` - DAG preflight framework 和结果 Schema。
- `analyzer/src/main/java/io/github/dependencyanalysis/impact/` - `impact` command、Call Graph input/reason adapter、evidence绑定、pipeline和影响追踪domain。
- `analyzer/src/main/java/io/github/dependencyanalysis/impact/pruning/` - 固定CHA caller-local Impact Path edge extension contract、receiver resolver与统一summary；Static Single Assignment（SSA，静态单赋值）equivalence位于`bytecode/`。
- `analyzer/src/main/java/io/github/dependencyanalysis/tree/` - Git snapshot、reactor inventory、dependency collection、version analysis 和 HTML report。
- `analyzer/src/main/java/io/github/dependencyanalysis/callgraph/` - 仅保留package边界；production class按engine、strategy、protocol、jdk、entrypoint、scope、boundary、topology、model与local职责进入子包。
- `analyzer/src/main/java/io/github/dependencyanalysis/callgraph/strategy/{cha,kobj}/` - 互相隔离的CHA与`k-obj`实现；动态协议adapter只位于`kobj`子包。
- `analyzer/src/main/java/io/github/dependencyanalysis/{build,dependency,bytecode,jar,report,workspace}/` - `impact` pipeline的其他稳定阶段实现。
- `analyzer/src/test/java/` - unit tests；`analyzer/src/integration-test/java/` - Failsafe integration tests。
- `plugins/pom.xml` - 独立 Plugin reactor parent/aggregator。
- `plugins/artifact-path-resolver/` - GAV `io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:3.0.0`；Java 8 `collect-dependency-evidence` goal 与 attached `repository` ZIP。
- `models/jdk/` - GAV`io.github.dependencyanalysis:dependency-analyzer-jdk-models:0.1.0-SNAPSHOT`；公共Synthetic IR engine/API。
- `models/jdk8/` - GAV`io.github.dependencyanalysis:dependency-analyzer-jdk8-models:0.1.0-SNAPSHOT`；384-target catalog与安装façade。
- `benchmarks/impact-medium/` - 可复现的CHA-only中型impact fixture；每scope 6个进程，双scope共12个进程；baseline确认前不发布tracked TSV snapshot。

## Technical Stack

- Analyzer runtime/build 为 Java 17；`impact` target runtime 与 Analyzer tests 使用完整 JDK 8。
- Maven 3.x；应用默认内嵌 Apache Maven 3.6.3 runtime。
- picocli 4.7.6、ASM/ASM Tree 9.7、WALA 1.8.0、Vineflower 1.12.0 slim、JUnit 5、AssertJ、ArchUnit 1.5.0。
- maven-shade-plugin 生成 uber JAR；maven-assembly-plugin 生成 Dependency Evidence Plugin repository ZIP。

## Main Entrypoints

- `DependencyAnalyzerCli.main()` - JVM 和 Root CLI 入口。
- `ImpactCommand.call()` - dependency upgrade impact 分析入口。
- `TreeCommand.call()` - repository dependency tree report 入口。
- `plugins/pom.xml` - Plugin bootstrap/install 入口。
- `pom.xml` - Analyzer build/test/package 入口。

## Repository Conventions

- Analyzer build前按公共model、JDK 8 model、Plugin顺序执行独立reactor`clean install`；任一source变化后不得依赖旧local Snapshot。
- 所有外部 process token 都先经过 `CommandResolver.resolve()`，并使用 `ProcessBuilder`，不经过 shell 拼接。
- 用户 Maven argument 必须逐 token 传入，禁止覆盖工具控制的 POM、module selection、output、verbose 和 token 参数。
- `tmp-files/` 仅用于临时产物，不提交 Git。
- wiki 描述当前稳定工程事实；用户操作写入 `docs/user-manual.md`。
