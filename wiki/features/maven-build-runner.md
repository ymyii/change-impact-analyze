---
title: "Maven Build Runner"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Build Runner 是分析流水线的第三阶段"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Maven 命令执行必须遵守跨平台命令解析规则"
  - path: "wiki/features/maven-runtime.md"
    desc: "Build Runner 消费 preflight 选定的 Maven runtime"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/build/BuildRunner.java"
    desc: "Maven 编译执行和 main classes 收集"
  - path: "src/main/java/io/github/dependencyanalysis/build/BuildResult.java"
    desc: "编译结果"
  - path: "src/main/java/io/github/dependencyanalysis/build/ModuleBuildOutput.java"
    desc: "单模块编译输出"
  - path: "src/main/java/io/github/dependencyanalysis/build/BuildException.java"
    desc: "编译异常"
  - path: "src/main/java/io/github/dependencyanalysis/util/CommandResolver.java"
    desc: "跨平台命令解析工具"
---

# Feature: Maven Build Runner

## Summary

Maven Build Runner 使用 impact preflight 选定的 Maven executable 执行 `compile -B`，编译 baseline 和 target/current workspace，并收集 main classes 目录作为后续 Call Graph 和 impact 阶段输入。

## Design Decisions

- Runtime 来源为用户 `--maven` 或内嵌 Maven 3.6.3；不隐式使用 PATH 或 Maven Wrapper。
- 通过 `ProcessBuilder.environment()` 应用 global `--java-home`；`impact` Preflight 已确保它是完整 JDK 8，同时保留用户 `settings.xml`、mirror、proxy 和 local repository 行为。
- 编译只收集 `target/classes`，不把 `target/test-classes` 作为分析输入。
- Maven 命令统一经过 `CommandResolver.resolve()`，确保 Windows 上可解析 `mvn.cmd`。

## Actors / Entrypoints

- `ImpactPipeline` 在 baseline 和 target side 上分别创建 `BuildRunner`，并传入同一 runtime descriptor 与 Maven arguments。
- `BuildRunner.build()` 是编译和 classes 目录发现入口。

## Behavior Contract

- 执行命令为 `mvn compile -B`。
- 编译日志写入临时文件，失败时读取日志尾部作为 stderr 摘要。
- 编译失败抛出 `BuildException`，携带 side、module、command、exitCode、stderr 摘要和 logFile。
- 成功后递归发现所有 `target/classes` 目录，并以 `ModuleBuildOutput` 记录 module path 与 classes dir。
- 构造时传入 `buildJavaHome` 时，Maven 子进程 `JAVA_HOME` 必须设置为该路径的绝对路径。
- `buildJavaHome` 为 null 时，Maven 子进程继承当前环境。

## Core Flow

1. `BuildRunner` 接收 side、workspace、DiagnosticCollector 和可选 `buildJavaHome`。
2. `build()` 启动 build stage 并执行 `runMvnCompile()`。
3. `runMvnCompile()` 构建 Maven 命令，经过 `CommandResolver.resolve()` 后启动 ProcessBuilder。
4. Maven 成功时扫描 workspace 下所有 `target/classes`。
5. Maven 失败时构造 `BuildException` 并保留日志路径。
6. 返回包含模块输出列表的 `BuildResult`。

## Acceptance Criteria

### Functional

- Given 单模块 Maven 项目，When `build()` 成功，Then 返回一个包含 main classes 目录的 `BuildResult`。
- Given 多模块 Maven 项目，When `build()` 成功，Then 每个模块的 `target/classes` 都作为 `ModuleBuildOutput` 返回。
- Given Maven 编译失败，When `build()` 处理退出码，Then 抛出 `BuildException` 并包含日志摘要。
- Given `buildJavaHome` 非空，When Maven 子进程启动，Then `JAVA_HOME` 使用该路径。
- Given `buildJavaHome` 为空，When Maven 子进程启动，Then 不覆盖继承环境。

### Non-Functional

- [ ] Build 阶段必须复用用户真实 Maven 环境，避免解析结果与本地构建不一致。
- [ ] 编译失败必须保留可定位的日志文件路径和摘要。
- [ ] 命令执行必须跨平台，遵守 `CommandResolver` 规则。

## Edge Cases

- workspace 中没有 `target/classes` 时返回空 outputs，后续阶段根据空输入处理。
- Maven 日志很长时只在异常摘要中保留尾部内容，完整日志通过 logFile 排查。
- Windows 上不直接执行 `mvn` token，由 `cmd.exe /c` 解析 Maven 可执行文件。

## Implementation Boundaries

- Build Runner 只负责编译和 main classes 发现，不解析依赖树、不构建 Call Graph。
- JDK 切换仅限 Maven 子进程环境变量，不修改目标项目文件或当前 JVM。
- 编译结果通过 `BuildResult` 传递给 dependency、call graph 和 impact 阶段。
