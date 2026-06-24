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
  - path: "src/main/java/io/github/changeimpact/analyze/util/CommandResolver.java"
    desc: "跨平台命令解析，Windows 上通过 cmd.exe /c 包裹命令"
---

# Feature: Maven Build Runner

## Summary

调用用户环境 `mvn` 编译 baseline 与 target/current workspace，并收集 main classes 目录（`target/classes`）。支持通过 `--build-java-home` 覆盖 Maven 子进程的 `JAVA_HOME`，使工具运行在 JDK 17+ 的同时目标项目可使用旧版 JDK 编译。不内嵌 Maven Resolver，不绕过用户 settings.xml。

## Design Decisions

- 通过 `ProcessBuilder.environment()` 覆盖 `JAVA_HOME` 实现 JDK 隔离，而非修改工具运行 JDK 或目标项目 pom.xml。
- 提供向后兼容的 3 参数构造函数重载，不传 `buildJavaHome` 时 Maven 继承当前 JVM 的 `JAVA_HOME`，行为与旧版本一致。
- Windows 上通过 `CommandResolver.resolve()` 将命令包裹为 `cmd.exe /c ...`，利用 `cmd.exe` 的 `PATHEXT` 解析能力找到 `mvn.cmd`。Linux/macOS 不经过任何转换。新增 ProcessBuilder 调用时必须使用 `CommandResolver.resolve()`。

## Behavior

- 使用系统 PATH 中的 `mvn`，执行 `mvn compile -B`。Windows 上通过 `CommandResolver.resolve()` 将命令包裹为 `cmd.exe /c mvn ...`，使 `cmd.exe` 负责 `PATHEXT` 解析，解决 `ProcessBuilder` 无法直接找到 `mvn.cmd` 的问题。Linux/macOS 上命令列表不经过任何转换。
- 编译日志重定向到临时文件。
- 收集所有模块的 `target/classes` 目录。
- 忽略 `target/test-classes`。
- 编译失败时抛出 `BuildException`，包含 side/module/command/exitCode/stderr 摘要/logFile 路径。
- 失败时读取日志最后 20 行作为诊断摘要。
- 当构造时传入 `buildJavaHome`（非 null），在 `ProcessBuilder.environment()` 中覆盖 `JAVA_HOME` 为该路径的绝对路径。
- 当 `buildJavaHome` 为 null 时，不覆盖环境变量，Maven 继承当前进程的 `JAVA_HOME`。

## Flow

1. `BuildRunner` 接收 side 名称、workspace 路径、DiagnosticCollector 和可选的 `buildJavaHome`。
2. `build()` 执行 `mvn compile -B`，日志写入临时文件。
3. `runMvnCompile()` 构建命令列表后通过 `CommandResolver.resolve()` 处理（Windows 包裹 `cmd.exe /c`），再构建 ProcessBuilder，若 `buildJavaHome` 非 null 则覆盖 `JAVA_HOME` 环境变量。
4. 编译失败时抛出 `BuildException`。
5. 编译成功后遍历 workspace 发现所有 `target/classes` 目录。
6. 返回 `BuildResult`，包含 `ModuleBuildOutput` 列表。

## Implementation Files

- `src/main/java/io/github/changeimpact/analyze/build/BuildRunner.java` - 编译执行、模块发现、日志收集。
- `src/main/java/io/github/changeimpact/analyze/build/BuildResult.java` - 编译结果，包含模块输出列表。
- `src/main/java/io/github/changeimpact/analyze/build/ModuleBuildOutput.java` - 单模块的 moduleDir 和 classesDir。
- `src/main/java/io/github/changeimpact/analyze/build/BuildException.java` - 编译失败异常，包含诊断上下文。

## Verification

- 单元测试：`src/test/java/io/github/changeimpact/analyze/build/` 下的测试类。
- 单元测试：`src/test/java/io/github/changeimpact/analyze/build/BuildRunnerConstructorTest.java`
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/build/BuildRunnerIT.java`
- 集成测试：`src/integration-test/java/io/github/changeimpact/analyze/build/BuildRunnerJavaHomeIT.java`
- 单模块 Java 8 Maven fixture 编译成功。
- 多模块 Java 8 Maven fixture 编译成功。
- main classes 路径被正确收集。
- test classes 不进入分析输入。
- 编译失败时终止，诊断包含 side/command/exitCode/stderr/logFile。
- 3 参数构造函数向后兼容，`buildJavaHome` 默认为 null。
- 4 参数构造函数传入 `buildJavaHome` 时，`ProcessBuilder` 环境变量 `JAVA_HOME` 被正确覆盖。
