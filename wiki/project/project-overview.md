---
title: "Project Overview"
type: project
relations:
  - path: "wiki/runbooks/build-test-package.md"
    desc: "构建、测试、打包操作手册"
code_refs:
  - path: "pom.xml"
    desc: "Maven 构建配置，依赖和插件定义"
  - path: "src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java"
    desc: "CLI 主入口"
---

# Project: Project Overview

## Summary

Change Impact Analyze 是一个命令行变更影响分析工具，用于分析代码库中变更的影响范围。项目采用 Maven 构建，Java 17 运行时，picocli 作为 CLI 框架。

## Module Map

- `src/main/java/io/github/changeimpact/analyze/cli/` - CLI 入口和命令行参数解析。
- `src/test/java/` - 单元测试。
- `src/integration-test/java/` - 集成测试（通过 build-helper-maven-plugin 注册为测试源码目录）。

## Technical Stack

- **Runtime**: Java 17（`maven.compiler.release=17`）。
- **Build**: Maven（`pom.xml`），uber-jar 通过 maven-shade-plugin 打包。
- **CLI**: picocli 4.7.6，命令行参数解析和 `--help` 生成。
- **Logging**: slf4j-simple 2.0.13（SLF4J 2.0 API + simple 实现）。
- **Testing**: JUnit Jupiter 5.10.2 + AssertJ 3.25.3。
- **Code Style**: maven-checkstyle-plugin 3.3.1，使用 `sun_checks.xml`，在 `validate` 阶段执行，`failsOnError=true`。

## Main Entrypoints

- `src/main/java/io/github/changeimpact/analyze/cli/ChangeImpactAnalyzeCli.java` - CLI 主类，picocli `@Command` 注解定义命令名、版本和帮助信息。`main()` 方法为 JVM 入口。

## Repository Conventions

- 基础包路径：`io.github.changeimpact.analyze`。
- 单元测试放在 `src/test/java/`，与主代码包结构一致。
- 集成测试放在 `src/integration-test/java/`，与主代码包结构一致。
- 构建产物输出到 `target/`，uber-jar 名为 `change-impact-analyze.jar`。
- 临时文件和设计文档放在 `tmp-files/`，不提交 git。
