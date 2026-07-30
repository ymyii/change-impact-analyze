---
title: "Build, Test, Package"
type: runbook
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "项目 stack、module map 和 artifact 名称"
  - path: "wiki/features/maven-runtime.md"
    desc: "打入 uber JAR 的 Maven distribution resources"
code_refs:
  - path: "pom.xml"
    desc: "Maven build、Surefire、Failsafe、Checkstyle 和 Shade 配置"
  - path: "docs/user-manual.md"
    desc: "打包后 CLI 使用手册"
  - path: "src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "最终 shaded JAR、真实 command 与 interruption black-box gate"
---

# Runbook: Build, Test, Package

## Summary

本 runbook 覆盖 Dependency Analyzer 的编译、unit tests、integration tests、Checkstyle、打包和本地 smoke verification。

## Prerequisites

- Java 17 JDK。
- Maven 3.x。
- 真实 `impact` integration/smoke 通过 `TEST_JDK8_HOME` 指向完整 JDK 8；Analyzer 仍由 Java 17 启动。
- integration tests 需要本地 `git` 和可运行 Maven executable；内嵌 runtime 测试不依赖 PATH Maven。

## Commands

### Checkstyle

```sh
mvn validate
```

### Unit tests

```sh
mvn test
```

### Integration tests 与全量 quality gate

```sh
mvn failsafe:integration-test failsafe:verify
mvn clean verify
```

带真实 JDK 8 `impact` gate：

```sh
TEST_JDK8_HOME=/path/to/jdk8 mvn clean verify
```

### 打包与 Root CLI smoke

```sh
mvn package
java -jar target/dependency-analyzer.jar --help
java -jar target/dependency-analyzer.jar impact --help
java -jar target/dependency-analyzer.jar tree --help
```

最终 JAR smoke 必须包含真实 `impact`、full-repository `tree`、subdirectory `tree` 与 process interruption recovery；不能只调用 Java command class。
Subdirectory `tree` fixture 必须包含 requested module、同 reactor dependency module 与无关 sibling，并断言 Report 只出现前两者；full-reactor fixture 必须断言全部 active module 使用 `REACTOR_ROOT_SCOPE`。

## Success Criteria

- `mvn validate` 输出 `0 Checkstyle violations`。
- `mvn test` 的 Surefire tests 全部通过。
- `mvn verify` 的 Surefire 和 Failsafe tests 全部通过。
- `target/dependency-analyzer.jar` 存在，manifest `Main-Class` 为 `io.github.dependencyanalysis.cli.DependencyAnalyzerCli`。
- JAR 包含 Maven distribution 和 `maven-dependency-plugin:3.6.1` 完整 repository archive、SHA-512、LICENSE 和 NOTICE。
- JAR 使用 WALA 1.8.0；JDK 8 smoke 覆盖 `jdk-analysis`、CHA/RTA，并断言 stdout/stderr 不含 `got NEW`。
- `impact` 缺少 JDK 8、传入 JDK 17、缺少 `javac`/`rt.jar` 时 Preflight 返回 exit `1`；`tree` 不受 JDK 8 限制。
- JAR 内 Maven/plugin archive 的实际 SHA-512 与 packaged checksum 一致。
- Root/两个 subcommand help 列出全部当前 option；无 subcommand或未知 option 返回 usage failure。
- Packaged JAR 中断测试保留已发布 reactor page 与最后一个 `RUNNING x/N` Index。
- Packaged JAR 在空 plugin cache 且无远程 plugin repository 时仍能执行 tree，Console 不出现 `evidence is incomplete`。
- Reactor HTML metadata 使用表格，dependency tree 无交互，依赖冲突表的检索、Module/Scope filter、排序和 10/50/100 分页在 `file://` 下可用。

## Failure Entrypoints

- Compile/test failure：`target/surefire-reports/`。
- Integration failure：`target/failsafe-reports/`。
- Checkstyle failure：Maven console 中的 file/line/check 名称。
- Embedded runtime/plugin failure：校验 `src/main/resources/maven/` 下 distribution、plugin repository、attribution 和 SHA-512。
- Shade/manifest failure：检查 `pom.xml` 的 `maven-shade-plugin` `finalName` 与 `mainClass`。
