---
title: "Build, Test, Package"
type: runbook
relations:
  - path: "wiki/project/change-impact-analyze.md"
    desc: "项目概览和技术栈"
code_refs:
  - path: "pom.xml"
    desc: "Maven 构建配置，插件和命令定义"
---

# Runbook: Build, Test, Package

## Summary

本 runbook 覆盖项目的编译、测试、代码风格检查和打包操作。所有命令基于 Maven，需要 Java 17 和 Maven 3.x 环境。

## Prerequisites

- Java 17 JDK。
- Maven 3.x。

## Commands

### 编译

```sh
mvn compile
```

### 运行单元测试

```sh
mvn test
```

Surefire 插件执行 `src/test/java/` 下的测试。

### 代码风格检查

```sh
mvn checkstyle:check
```

Checkstyle 在 `validate` 阶段自动执行，使用 `sun_checks.xml` 规则，`failsOnError=true`。也可单独运行。

### 运行集成测试

```sh
mvn verify
```

Failsafe 插件执行 `src/integration-test/java/` 下的测试（通过 build-helper-maven-plugin 注册）。`verify` 阶段包含编译、单元测试、checkstyle 和集成测试。

### 打包 uber-jar

```sh
mvn package
```

Shade 插件在 `package` 阶段生成 `target/change-impact-analyze.jar`，包含所有依赖。

### 运行

```sh
java -jar target/change-impact-analyze.jar --help
```

## Success Criteria

- `mvn test`：输出 `BUILD SUCCESS`，测试全部通过。
- `mvn verify`：输出 `BUILD SUCCESS`，包含 checkstyle 0 violations 和集成测试通过。
- `mvn package`：生成 `target/change-impact-analyze.jar`（uber-jar）。
- `java -jar target/change-impact-analyze.jar --help`：输出 CLI 帮助信息。

## Failure Entrypoints

- **编译失败**：检查 `mvn compile` 输出中的错误信息和行号。
- **Checkstyle 违规**：`mvn checkstyle:check` 输出会列出具体违规文件和行号。规则配置在 `pom.xml` 的 `maven-checkstyle-plugin` 中。
- **测试失败**：查看 `target/surefire-reports/` 下的测试报告。
- **集成测试失败**：查看 `target/failsafe-reports/` 下的测试报告。
- **打包失败**：检查 `pom.xml` 中 shade 插件配置和 `mainClass` 是否正确。
