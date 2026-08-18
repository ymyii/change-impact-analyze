---
title: "Maven Build Runner"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "target-only compile 与 front concurrency"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "与 dependency process 的调度边界"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree与impact共享的reactor scope语义"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/reactor/ReactorInventoryBuilder.java"
    desc: "共享入口POM与祖先aggregator resolver"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ModuleScopePlanner.java"
    desc: "impact baseline/target scope适配"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/build/BuildRunner.java"
    desc: "target Maven compile"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/build/BuildResult.java"
    desc: "target main classes outputs"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/util/ProcessConsoleExecutor.java"
    desc: "Maven output Console streaming 与 failure tail"
---

# Feature: Maven Build Runner

## Summary

Build Runner只编译target/current workspace。`impact`与`tree`使用同一个reactor scope resolver，因此同一`--path`具有一致的aggregator、leaf和standalone边界。Baseline不执行Maven compile；old bytecode始终来自resolved dependency artifact。

## Mode

- `REACTOR`：在 target reactor root 执行一次 `mvn compile -B`，随后发现各 active Module `target/classes`。
- `SINGLE_MODULE`：在所属 reactor root 执行 `mvn -pl <module> -am compile -B`；只分析 requested Module，上游 Module classes 进入 `REACTOR_DEPENDENCY`。
- `STANDALONE` scope在impact领域仍映射为单Module分析，但直接从入口POM目录执行，不附加`-pl/-am`；没有祖先ownership的同repository project不会被自动并入其他reactor。
- 只收集 main `target/classes`；排除 `target/test-classes`。

## Process Contract

- Maven executable、JDK 8 `JAVA_HOME`、settings/mirror/proxy/local repository与安全user arguments来自Preflight。同一组`-P`、`-D`和settings token同时参与scope解析与Maven subprocess。
- Command token 经 `CommandResolver.resolve()`；不使用 shell string 拼接。
- Baseline dependency branch 与 target build branch 并行。
- Branch 被 interrupt 时终止 Maven descendants 和 root process：先 `destroy`，限时后 `destroyForcibly`，最后等待回收。
- Maven output 不写 command log file。默认 Console 只显示 warning/error，`-v` 显示完整 output。
- Build failure 只在内存保留 bounded output tail，作为 global preparation failure；不发布新 Report。

## Boundary

Build Runner不解析dependency、不构建Call Graph。共享resolver负责scope；`ModuleScopePlanner`分别对baseline和target workspace解析，以保留Module增删、mode差异及`BASELINE_ONLY`/`TARGET_ONLY`语义。
