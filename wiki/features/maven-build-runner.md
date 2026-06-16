---
title: "Maven Build Runner"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Build Runner 是分析流水线的第三阶段"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java"
    desc: "Maven 编译执行和 main classes 收集"
  - path: "src/main/java/io/github/changeimpact/analyze/build/BuildResult.java"
    desc: "编译结果"
  - path: "src/main/java/io/github/changeimpact/analyze/build/ModuleBuildOutput.java"
    desc: "单模块编译输出"
  - path: "src/main/java/io/github/changeimpact/analyze/build/BuildException.java"
    desc: "编译异常"
---

# Feature: Maven Build Runner

## Summary

调用用户环境默认 `mvn` 编译 baseline 与 target/current workspace，并收集 main classes 目录（`target/classes`）。不内嵌 Maven Resolver，不绕过用户 settings.xml。

## Behavior

- 使用系统 PATH 中的 `mvn`，执行 `mvn compile -B`。
- 编译日志重定向到临时文件。
- 收集所有模块的 `target/classes` 目录。
- 忽略 `target/test-classes`。
- 编译失败时抛出 `BuildException`，包含 side/module/command/exitCode/stderr 摘要/logFile 路径。
- 失败时读取日志最后 20 行作为诊断摘要。

## Flow

1. `BuildRunner` 接收 side 名称、workspace 路径和 DiagnosticCollector。
2. `build()` 执行 `mvn compile -B`，日志写入临时文件。
3. 编译失败时抛出 `BuildException`。
4. 编译成功后遍历 workspace 发现所有 `target/classes` 目录。
5. 返回 `BuildResult`，包含 `ModuleBuildOutput` 列表。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java` - 编译执行、模块发现、日志收集。
- `src/main/java/io/github/changeimpact/analyze/build/BuildResult.java` - 编译结果，包含模块输出列表。
- `src/main/java/io/github/changeimpact/analyze/build/ModuleBuildOutput.java` - 单模块的 moduleDir 和 classesDir。
- `src/main/java/io/github/changeimpact/analyze/build/BuildException.java` - 编译失败异常，包含诊断上下文。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/build/` 下的测试类。
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/build/BuildRunnerIT.java`
- 单模块 Java 8 Maven fixture 编译成功。
- 多模块 Java 8 Maven fixture 编译成功。
- main classes 路径被正确收集。
- test classes 不进入分析输入。
- 编译失败时终止，诊断包含 side/command/exitCode/stderr/logFile。
