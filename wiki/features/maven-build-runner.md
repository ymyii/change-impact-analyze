---
title: "Maven Build Runner"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "target-only compile 与 front concurrency"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "与 dependency process 的调度边界"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/build/BuildRunner.java"
    desc: "target Maven compile"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/build/BuildResult.java"
    desc: "target main classes outputs"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/util/ProcessConsoleExecutor.java"
    desc: "Maven output Console streaming 与 failure tail"
---

# Feature: Maven Build Runner

## Summary

Build Runner 只编译 target/current workspace。Baseline 不执行 Maven compile；其 old bytecode/SSA 输入来自 resolved dependency artifact。

## Mode

- `REACTOR`：在 target reactor root 执行一次 `mvn compile -B`，随后发现各 active Module `target/classes`。
- `SINGLE_MODULE`：在所属 reactor root 执行 `mvn -pl <module> -am compile -B`；只分析 requested Module，上游 Module classes 进入 `REACTOR_DEPENDENCY`。
- 只收集 main `target/classes`；排除 `target/test-classes`。

## Process Contract

- Maven executable、JDK 8 `JAVA_HOME`、settings/mirror/proxy/local repository 与安全 user arguments 来自 Preflight。
- Command token 经 `CommandResolver.resolve()`；不使用 shell string 拼接。
- Baseline dependency branch 与 target build branch 并行。
- Branch 被 interrupt 时终止 Maven descendants 和 root process：先 `destroy`，限时后 `destroyForcibly`，最后等待回收。
- Maven output 不写 command log file。默认 Console 只显示 warning/error，`-v` 显示完整 output。
- Build failure 只在内存保留 bounded output tail，作为 global preparation failure；不发布新 Report。

## Boundary

Build Runner 不解析 dependency、不构建 Call Graph。它只产出 target Module path 与 main classes directory。
