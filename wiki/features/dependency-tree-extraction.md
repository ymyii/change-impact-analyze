---
title: "Dependency Tree Extraction"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "baseline/target preparation 顺序"
  - path: "wiki/features/maven-runtime.md"
    desc: "内置 Plugin repository、settings overlay 与 Maven compatibility"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "physical JAR path 输入"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "同 session GraphML/JSON 执行、配对和集合一致性校验"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ResolvedArtifactJsonParser.java"
    desc: "Artifact Path JSON Schema v1 strict streaming parser"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalysisResult.java"
    desc: "tree + physical artifact bindings"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ResolveArtifactPathsMojo.java"
    desc: "Maven session collection、mediation、reactor skip 与非传递 resolution"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ArtifactPathJsonWriter.java"
    desc: "Schema v1 streaming output 与 atomic publish"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "fully-qualified goals 与 effective settings overlay"
---

# Feature: Dependency Tree Extraction

## Summary

`impact` 同时需要 Maven mediated dependency tree 与 exact physical artifact path。每个 workspace 在原 project 的同一个 Maven process/session 中执行 Maven Dependency Plugin `tree` 和内置 Artifact Path Plugin；GraphML 描述 dependency graph，versioned JSON 绑定 Maven Resolver 返回的 external artifact absolute path。Production path 不生成 temporary POM、不调用 `dependency:list`、不拼接 local repository path，也不添加 `-llr`。

## Maven Command

```text
mvn <effective-arguments> <project-arguments>
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree
    -DoutputType=graphml
    -DoutputFile=<unique>.graphml
    io.github.dependencyanalysis:
    dependency-analyzer-artifact-path-maven-plugin:
    1.0.0:
    resolve-artifact-paths
    -Dcia.resolvedArtifactsFileName=<unique>.json
    -B
```

- `SINGLE_MODULE` command 带工具生成的 `-pl <module> -am`；reactor root command 覆盖 active reactor。
- GraphML/JSON filename 每次唯一且相对各 Module `project.basedir`。Parse 完成或失败后只清理 command-owned GraphML/JSON。
- Plugin effective arguments 合并用户 settings、mirror、proxy、server、local repository 与内置 Plugin repository。Target `compile` 只使用普通 user arguments，不携带 overlay。
- Maven process output 不写 `.log`。默认 Console 只显示 warning/error，`-v` 显示完整 output；failure tail 仅在内存保留。

## Plugin Collection and Resolution

- Mojo 不声明 `requiresDependencyCollection` 或 `requiresDependencyResolution`，避免 Maven 在 goal 前解析 dependency binary。
- 从 effective `MavenProject` dependencies、dependencyManagement、exclusions、optional、classifier、system path 和 ArtifactType 构造 public Resolver `CollectRequest`。
- 复用注入的 `RepositorySystemSession`、`${project.remoteProjectRepositories}`、WorkspaceReader、mirror、proxy、authentication、offline policy、cache、LocalRepositoryManager、DependencySelector、DependencyManager 和 DependencyGraphTransformer；不读取或重建 `settings.xml`。
- Session copy 只关闭 conflict verbose。遍历 mediated graph 时仅保留 selected `compile/runtime/provided` dependency，并在 `conflict.winner` loser 节点截断子树。
- Reactor identity 为 `groupId:artifactId:type:classifier:baseVersion`。Reactor node 不创建 `ArtifactRequest`。
- External selected node 通过 `new ArtifactRequest(dependencyNode)` 保留 effective repositories、request context、relocation、extension 和 classifier；`RepositorySystem.resolveArtifacts()` 批量执行非传递解析。
- Path 只取 `ArtifactResult.getArtifact().getFile()` 并 canonicalize。任一 artifact resolution 失败时 goal 失败，JSON 不发布。

## JSON Schema v1

```json
{
  "schemaVersion": 1,
  "module": {
    "coordinates": {
      "groupId": "com.example",
      "artifactId": "service",
      "type": "jar",
      "extension": "jar",
      "classifier": "",
      "version": "1.0-SNAPSHOT",
      "baseVersion": "1.0-SNAPSHOT"
    },
    "baseDirectory": "/absolute/project/service"
  },
  "artifacts": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "test-jar",
        "extension": "jar",
        "classifier": "tests",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "scope": "compile",
      "absolutePath": "/absolute/repository/library-2.0-tests.jar"
    }
  ]
}
```

- Output 使用 UTF-8；`artifacts` 按完整 coordinates、scope、absolutePath 排序；无 external dependency 时为 `[]`。
- `cia.resolvedArtifactsFileName` 只允许 filename；拒绝 absolute path、目录分隔符和 `..`，解析到当前 Module basedir。
- Writer 先写 sibling temporary file，成功后 atomic move；partial resolution 不留下目标 JSON。
- Analyzer strict parser 要求 `schemaVersion == 1`、必填字段类型正确、binding 唯一、path absolute 且存在；禁止 duplicate property，忽略未知字段。
- Timestamped SNAPSHOT 可保留在 `version`；GraphML binding 使用 `baseVersion`。

## Binding Contract

- Module 先按 canonical `module.baseDirectory` 配对，再校验 JSON module coordinates 与 GraphML root coordinates 相等。
- External binding identity 为 `groupId:artifactId:type:classifier:baseVersion:scope`。
- GraphML 与 JSON 的 external dependency set 必须双向完全一致；缺失、多余或重复 binding 都是 global preparation failure。
- 每个 resolved artifact 保留 owning Module absolute path 与 canonical physical path。
- Non-JAR dependency 保留在 result/Report，但不进入 bytecode diff 或 Call Graph scope。
- Reactor dependency 不在 JSON 中；Analyzer 使用 target Module 的 `target/classes`。

## Scheduling

- Baseline `analyzeResolved()` 与 target `mvn compile` 并行。
- 两者 join 后运行 target `analyzeResolved()`，避免同一 target workspace 同时运行两个 Maven process。
- Baseline 不 compile、不构建 Call Graph；clean reactor upstream JAR/classes 不影响 dependency collection。
- 前置任一 branch failure 会 interrupt 另一 branch；运行中的 Maven process tree 先 graceful destroy，再 forced destroy 并回收。
- Plugin extraction、settings overlay 或 GraphML capability probe failure 属于 command-level Preflight failure；pipeline 不启动，旧 Report 不替换。
