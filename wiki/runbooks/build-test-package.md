---
title: "Build, Test, Package"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "项目stack、四reactor module map和artifact名称"
  - path: "wiki/features/maven-runtime.md"
    desc: "打入 uber JAR 的 Maven distribution 与两个 repository ZIP"
  - path: "wiki/runbooks/impact-benchmark.md"
    desc: "使用打包后 JAR 执行持续 impact benchmark"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Stable version、release profile、commit 与 tag 操作"
  - path: "wiki/rules/release-versioning.md"
    desc: "独立 SemVer、Snapshot 复用与 release gate"
  - path: "wiki/rules/benchmark-scenario-coverage.md"
    desc: "Analyzer 能力新增的 semantic benchmark 完成条件"
  - path: "wiki/features/jdk-method-models.md"
    desc: "Analyzer依赖的两个独立model artifact与packaging contract"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer parent/aggregator、Enforcer 与 shared build management"
  - path: "analyzer/pom.xml"
    desc: "Analyzer JDK 8 model dependency、Surefire、Failsafe、repository ZIP copy与Shade配置"
  - path: "models/jdk/pom.xml"
    desc: "公共JDK model engine bootstrap"
  - path: "models/jdk8/pom.xml"
    desc: "JDK 8 model bootstrap"
  - path: "plugins/pom.xml"
    desc: "独立 Plugin parent/aggregator、Java 8 与 Enforcer"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Maven Plugin、shading、flatten 与 repository ZIP packaging"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "最终 shaded JAR 与真实 command black-box gate"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/HtmlReportUsabilityVerifier.java"
    desc: "Impact单文件与Tree多页面HTML可用性gate"
---

# Runbook: Build, Test, Package

## Summary

本runbook覆盖四个独立Maven reactor的本地开发、model/Plugin bootstrap、artifact-first打包、Checkstyle、ArchUnit、unit/integration tests和CLI smoke。日常验证不修改version、不要求Git commit。

## Prerequisites

- Maven JVM 使用 Java 17 JDK。
- Maven 3.x。
- root POM 的 `test.jdk8.home` 默认指向 `/absolute/path/to/jdk8`；必须存在 `bin/java`、`bin/javac`、`jre/lib/rt.jar`，且 probe 结果为 Java 8。其他环境使用 `-Dtest.jdk8.home=/absolute/path/to/jdk8` 覆盖。
- Integration tests 需要本地 `git` 和可运行 Maven executable。
- 所有 command 从 repository root 执行。

## Commands

### JDK Method Model reactors

Analyzer构建前按dependency顺序安装公共engine和JDK 8 model；两个module不加入root`<modules>`：

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
```

### Dependency Evidence Plugin reactor

Dependency Evidence Plugin 保留现有 artifactId；第一次 bootstrap 或 Plugin source/package 变化后执行：

```sh
mvn -f plugins/pom.xml clean install
```

该 reactor 使用 `maven.compiler.release=8`，但 Maven command 本身不要求在 JDK 8 上运行。输出包括：

```text
plugins/artifact-path-resolver/target/
├── dependency-analyzer-artifact-path-maven-plugin-<version>.jar
└── dependency-analyzer-artifact-path-maven-plugin-<version>-repository.zip
```

Attached `repository` ZIP 同时安装到 Maven local repository。

### Analyzer reactor

代码修改完成后先产出可交付artifact：

```sh
mvn -DskipTests package
```

确认`target/dependency-analyzer.jar`可用后，再执行完整quality gate：

```sh
mvn clean verify
```

只运行 unit tests：

```sh
mvn test
```

普通打包仍会编译并执行tests，因此同样使用配置的JDK 8：

```sh
mvn package
```

覆盖默认 JDK 8：

```sh
mvn -Dtest.jdk8.home=/absolute/path/to/jdk8 clean verify
```

Surefire/Failsafe 自动将 `test.jdk8.home` 作为 `TEST_JDK8_HOME` 注入 test JVM；执行 Maven 前不需要设置同名环境变量。

Analyzer reactor不构建`plugins/`、`models/jdk`或`models/jdk8`。若local repository缺少对应Plugin repository ZIP或`jdk8-models.version` artifact，dependency resolution必须失败；按model dependency顺序及Plugin入口执行`clean install`。

### CLI smoke

```sh
java -jar target/dependency-analyzer.jar --version
java -jar target/dependency-analyzer.jar --help
java -jar target/dependency-analyzer.jar impact --help
java -jar target/dependency-analyzer.jar tree --help
```

最终HTML可用性不是手工`ls`检查。`mvn clean verify`中的`PackagedJarCliIT`使用shaded JAR生成真实Impact与Tree输出，并由`HtmlReportUsabilityVerifier`递归检查HTML结构、本地资源、Report root边界和Tree Reactor page可达性。失败详情位于`target/failsafe-reports/`。

## Success Criteria

- Plugin reactor 输出 `0 Checkstyle violations`，tests 全部通过，Plugin class major 不超过 `52`。Graph tests 覆盖scope-conflict pruning、multi-path winner normalization、missing winner fail-fast；output tests 覆盖owner/path/symlink 与 atomic publication。
- Plugin repository ZIP 只有 Maven layout 下当前 version 的 JAR 与 consumer POM，不包含项目生成的 checksum sidecar。
- Analyzer `mvn clean verify` 的 Surefire 与 Failsafe tests 全部通过且 `Skipped: 0`。
- `PackageArchitectureTest`在`mvn verify`中强制Call Graph与Impact/Report解耦、CHA与`k-obj`隔离、protocol依赖方向、根包无production class以及Report不访问live strategy。
- `StagePhaseTerminologyTest`在unit test中扫描主代码、测试、Wiki和用户文档；除WALA/Java强制外部API名称外，不允许项目自有控制流程重新引入旧术语。
- Diagnostic与Impact Query tests验证五段prefix、可选Phase、Phase不进入Stage计时key、统一`started/completed/failed`正文，以及QueryNode消息正文不再重复`phase=`。
- 真实JDK 8 test完成JDK probe、WALA scope、默认CHA与显式`k-obj`，不因缺少环境变量跳过。CHA验证JDK leaf不展开；`k-obj`验证JDK model callback dispatch。
- CLI/config/report tests确认默认组合为`cha + none`；`k-obj`默认`jdk8`且可显式`none`；`cha + jdk8`统一拒绝。Reflection配置只应用于`k-obj`。
- CHA与`k-obj`通过统一Evidence Schema、timeout和metadata regression；MethodHandle、ServiceLoader、Class.forName与`invokedynamic`由聚焦`k-obj` capability tests覆盖。CHA额外覆盖local constant、JDK leaf、路径外external target pruning和完整external祖先链。
- Entrypoint tests覆盖private nested class、constructor、static/instance method过滤；公开root调用的private method仍作为普通CGNode存在。
- `target/dependency-analyzer.jar` 存在，manifest `Main-Class` 为 `io.github.dependencyanalysis.cli.DependencyAnalyzerCli`。
- Analyzer JAR包含公共`JdkModels.class`、`Jdk8Models.class`和`jdk8-models.tsv`；help包含`--jdk-model`。
- Analyzer JAR 在 `maven/plugin-repositories/` 下恰有 Maven Dependency Plugin 与 Dependency Evidence Plugin 两个 `repository` ZIP；不存在 loose Plugin JAR/POM/checksum resource。
- Empty Maven local repository + blocked wildcard mirror 下，packaged Maven 3.6.3 runtime 可执行 `collect-dependency-evidence`。
- Analyzer integration tests 覆盖空格/中文 cache path、scope-conflict missing `test` binary，以及 Maven 成功/失败后 source repository 无 evidence 中间文件。
- 最新 duplicate class precedence tests、report tests 与 `PackagedJarCliIT` 全部通过。
- Root/两个 subcommand help 列出当前 option；CLI `--version` 与 Maven build metadata 一致。
- Packaged help只接受`cha`与`k-obj`，后者标记`experimental`；`rta`、`zero-cfa`和`optimized-0-1-cfa`在解析阶段失败。
- Packaged JAR保留公共JDK model engine、JDK 8 façade与catalog；不包含已删除algorithm class、旧Call Graph根包class或兼容wrapper。
- `PackagedJarCliIT`使用真实changed-dependency fixture执行默认JDK 8的`k-obj`，要求fixed point完成、报告生成并显示`experimental`。
- `PackagedJarCliIT`使用最终shaded JAR生成真实Impact与Tree Report。`HtmlReportUsabilityVerifier`要求entry file可读、HTML/head/title/body结构完整、无未展开模板标记；所有本地`href/src`必须留在Report root内且目标可读，Tree Index必须能到达至少一个Reactor page。
- Analyzer能力新增或行为扩展时，必须同步新增/更新benchmark fixture、verification与expected baseline。只有用户明确要求执行benchmark时才运行matrix；获得授权后，14个JVM、4组semantic baseline和两份HTML Report必须全部成功。当前4个`PENDING`baseline必须先经授权calibration和人工review锁定；calibration不得发布tracked snapshot。

## Failure Entrypoints

- `test.jdk8.home must point to a complete JDK 8`：确认默认路径存在，或通过 `-Dtest.jdk8.home=...` 指向 absolute JDK 8 root；不能使用 JRE 或 Java 17 home。
- Dependency Evidence Plugin repository ZIP resolution failure：重新执行 `mvn -f plugins/pom.xml clean install`，确认 Analyzer property 与 installed version 一致。
- JDK model artifact resolution failure：先执行`models/jdk`再执行`models/jdk8`的`clean install`，确认root`jdk8-models.version`匹配。
- Plugin test failure：`plugins/artifact-path-resolver/target/surefire-reports/`。
- Analyzer unit/integration failure：`target/surefire-reports/`、`target/failsafe-reports/`。
- Checkstyle failure：Maven Console 中的 file/line/check 名称。
- Shade/manifest failure：检查 `analyzer/pom.xml` 的 `maven-shade-plugin` `finalName` 与 `mainClass`。
- Embedded repository failure：检查 Analyzer JAR 中 `maven/plugin-repositories/` 的两个 ZIP 和 runtime manager 的必要文件路径。

## Configuration

- Analyzer development version：root `revision`。
- Dependency Evidence Plugin development version：`plugins/pom.xml` 的 `revision`，当前 `3.0.0`。
- Analyzer test JDK 8：root `test.jdk8.home`；command-line `-Dtest.jdk8.home=...` 优先。
- Analyzer 使用的 Plugin version：root `artifact-path-plugin.version`。
- Analyzer使用的JDK 8 model version：root`jdk8-models.version`。
- 详细 release 切换见 [Version and Distribution](version-and-distribution.md)。
