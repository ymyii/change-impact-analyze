---
title: "Dependency Tree Extraction"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "baseline/target preparation 顺序"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "physical JAR path 输入"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "GraphML 与 dependency:list 执行"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/ResolvedArtifactListParser.java"
    desc: "absolute artifact filename parser"
  - path: "src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalysisResult.java"
    desc: "tree + physical artifact bindings"
  - path: "src/main/java/io/github/dependencyanalysis/impact/ModuleScopePlanner.java"
    desc: "reactor/leaf execution scope"
  - path: "src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "fully-qualified goal 与 effective settings overlay"
---

# Feature: Dependency Tree Extraction

## Summary

`impact` 同时需要 Maven mediated dependency tree 与 exact physical artifact path。Command Preflight 固定准备内嵌 Maven Dependency Plugin `3.6.1` repository/settings runtime；GraphML 和 artifact list 使用同一 runtime。生产路径不使用 shorthand goal，也不从 `~/.m2` 拼接 JAR 路径。

## Commands

- Tree：`mvn <plugin-effective-arguments> org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree -DoutputType=graphml -DoutputFile=<unique>.graphml -B`
- Paths：先从每个 Module 的 mediated GraphML 展平 external `compile/runtime/provided` artifact，过滤 reactor coordinates；再在 command temporary directory 生成 isolated resolution POM，执行 `mvn ... -f <model-pom> org.apache.maven.plugins:maven-dependency-plugin:3.6.1:list -DoutputFile=<absolute-unique>.txt -DoutputAbsoluteArtifactFilename=true -DappendOutput=false -DexcludeReactor=true -DexcludeTransitive=true -B`。相同 dependency-set fingerprint 复用一次 resolution result。
- Isolated resolution POM 把 GraphML 已 mediation 的 external artifact 作为 direct dependency；`excludeTransitive=true` 防止二次 mediation 扩大 scope。不使用语义容易误解的 `excludeScope=test`。Absolute path contract 只约束 external dependency，不要求 baseline reactor classes/JAR 已构建。
- `SINGLE_MODULE` 的 GraphML command 带工具生成的 `-pl <module> -am`；isolated path-resolution command 不带 reactor project selector。
- GraphML/output filename 每次唯一；parse 完成或失败后清理 command-generated file 和 isolated resolution model。
- Dependency Plugin process output 不写 `.log`。默认 Console 只显示 warning/error，`-v` 显示完整 output；failure tail 仅在内存保留。
- Plugin effective arguments 合并用户 settings、mirror、proxy、server、local repository 与内嵌 Plugin repository。Target `compile` 只使用普通 user arguments，不携带该 overlay。

## Binding Contract

- Identity：`groupId:artifactId:type:classifier:version:scope`。
- 每个 resolved artifact 保留 owning Module absolute path 与 canonical physical path。
- 支持 custom local repository、classifier、SNAPSHOT timestamp path。
- GraphML node 缺 path、path 不存在、非 absolute path 或 binding ambiguity 均为 global preparation failure。
- Non-JAR dependency 保留在 result/Report，但不进入 bytecode diff 或 Call Graph scope。
- Reactor dependency 从 external artifact 集合移除，改用 target Module classes directory。

## Scheduling

- Baseline `analyzeResolved()` 与 target `mvn compile` 并行。
- 两者 join 后运行 target `analyzeResolved()`，避免同一 target workspace 同时运行两个 Maven process。
- Baseline 不 compile、不构建 Call Graph。
- 前置任一 branch failure 会 interrupt 另一 branch；运行中的 Maven process tree 先 graceful destroy，再 forced destroy并回收。
- Plugin extraction、settings overlay 或 GraphML capability probe failure 属于 command-level Preflight failure；pipeline 不启动，旧 Report 不替换。
