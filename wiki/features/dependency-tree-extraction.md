---
title: "Dependency Tree Extraction"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "baseline/target preparation 顺序"
  - path: "wiki/features/maven-runtime.md"
    desc: "内置 Plugin repository、settings overlay 与 Maven compatibility"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "selected dependency tree 进入 Module dependency diff"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "physical JAR path 输入"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "同 session GraphML/JSON 执行、配对和集合一致性校验"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/GraphMLParser.java"
    desc: "Analyzer GraphML selected tree 解析与 test subtree 排除"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyScope.java"
    desc: "impact 保留 compile/runtime/provided/system scope 的共享枚举"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ResolvedArtifactJsonParser.java"
    desc: "Artifact Path JSON Schema v1 strict streaming parser"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalysisResult.java"
    desc: "tree 与 physical artifact bindings"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/GraphmlDependencyReader.java"
    desc: "GraphML secure parsing、root validation 与 selected binding 提取"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ResolveArtifactPathsMojo.java"
    desc: "GraphML 驱动的 Reactor skip 与非传递 artifact resolution"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ArtifactPathJsonWriter.java"
    desc: "Schema v1 streaming output 与 atomic publish"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "fully-qualified goals 与 effective settings overlay"
---

# Feature: Dependency Tree Extraction

## Summary

`impact` 同时需要 Maven mediated dependency tree 与 exact physical artifact path。每个 workspace 在原 project 的同一个 Maven process/session 中先执行 Maven Dependency Plugin `tree`，再执行内置 Artifact Path Plugin。GraphML 是唯一 mediation authority，决定 selected coordinate 与 effective scope；Schema v1 JSON 只绑定 selected non-Reactor dependency 的 physical absolute path。Production path 不生成 temporary POM、不调用 `dependency:list`、不拼接 local repository path，也不添加 `-llr`。

## Actors / Entrypoints

- `DependencyAnalyzer.analyzeResolved()` 生成唯一 GraphML/JSON filename、执行 combined Maven command，并消费两个输出。
- Maven Dependency Plugin `3.6.1:tree` 生成每个 Module 的 GraphML。
- Artifact Path Plugin `1.0.1:resolve-artifact-paths` 读取同一 Module 的 GraphML，解析 external artifact physical path。
- Analyzer 将 Reactor dependency 映射到 target Module 的 `target/classes`。

## Behavior Contract

- GraphML root coordinate 必须与当前 `MavenProject` 完整 coordinate 一致。
- Artifact Path Plugin 只采用 GraphML 中 selected `compile/runtime/provided/system` binding；`test` 完全不进入 dependency diff、path binding、JAR diff 或 Call Graph。
- Exclusion 与 conflict loser 已由 GraphML mediation 结论排除，不会创建 `ArtifactRequest`。
- Reactor identity 使用 `groupId:artifactId:type:classifier:baseVersion`；Reactor binding 不解析 binary。
- 非 `system` binding 使用当前 project effective repositories 批量执行非传递 resolution；path 只取 `ArtifactResult.getArtifact().getFile()` 并 canonicalize。
- `system` binding 按完整 coordinate 匹配当前 effective `MavenProject` 的 `system` dependency；验证并绑定 absolute `systemPath`，不创建 remote `ArtifactRequest`。
- GraphML 与 JSON external binding 必须按 coordinate 和 scope 双向完全一致；不通过 scope 转换或宽松匹配掩盖差异。
- 任一输入校验、`systemPath` binding 或 artifact resolution 失败时 goal 失败，不发布 partial JSON。

## Design Decisions

- GraphML 是唯一 mediation authority。Plugin 不执行第二次 dependency collection，避免两条 collection path 对同一 coordinate 得出不同 effective scope。
- JSON 只承担 physical path binding，Schema v1 保持与 mediation 策略解耦。
- `test` 是 `impact` 的明确排除边界；即使 binary 不存在也不能触发 resolution 或 `.lastUpdated`。
- `system` 仍由 GraphML 决定是否 selected，但 GraphML 不携带 `systemPath`；Plugin 只从当前 effective `MavenProject` 恢复和验证该本地 path，不重新决定 scope。
- 非 `system` 的 `ArtifactRequest` 仅使用当前 Module 的 effective project repositories；GraphML 不承载 transitive `DependencyNode` 专属 repository list，因此 Plugin 不重建该信息。
- 缺少 GraphML 时不提供 collection fallback；goal 只服务 `impact` combined command。

## Maven Command

```text
mvn <effective-arguments> <project-arguments>
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree
    -DoutputType=graphml
    -DoutputFile=<unique>.graphml
    io.github.dependencyanalysis:
    dependency-analyzer-artifact-path-maven-plugin:
    1.0.1:
    resolve-artifact-paths
    -Dcia.dependencyGraphFileName=<unique>.graphml
    -Dcia.resolvedArtifactsFileName=<unique>.json
    -B
```

- `SINGLE_MODULE` command 带工具生成的 `-pl <module> -am`；reactor root command 覆盖 active reactor。
- GraphML/JSON filename 每次唯一且相对各 Module `project.basedir`。Parse 完成或失败后只清理 command-owned GraphML/JSON。
- Plugin effective arguments 合并用户 settings、mirror、proxy、server、local repository 与内置 Plugin repository。Target `compile` 只使用普通 user arguments，不携带 overlay。
- Maven process output 不写 `.log`。默认 Console 只显示 warning/error，`-v` 显示完整 output；failure tail 仅在内存保留。

## Core Flow

1. Maven Dependency Plugin 对 effective project 执行 mediation，将 selected tree 写入 Module-local GraphML。
2. Analyzer 将相同 GraphML filename 通过 `cia.dependencyGraphFileName` 传给后续 Artifact Path goal。
3. Plugin 使用禁用 DTD、external entity 与 XInclude 的 XML parser 读取 GraphML，校验唯一 root、完整 Module coordinate、duplicate binding 与 scope conflict。
4. Plugin 遍历 root 可达节点；保留 `compile/runtime/provided/system`，忽略 `test` 及其 subtree，跳过 Reactor coordinate。
5. Plugin 通过 session `ArtifactTypeRegistry` 恢复 extension/default classifier，构造 `DefaultArtifact`。
6. 对 `system` binding，Plugin 按完整 coordinate 在 effective `MavenProject` dependencies 中要求唯一 `system` dependency，验证其 `systemPath` 是 absolute existing regular file，并直接形成 JSON binding。
7. 对其余 binding，Plugin 使用 `new ArtifactRequest(artifact, project.remoteProjectRepositories, "project")` 批量执行非传递 resolution。
8. 全部 binding 成功后，Plugin 将 physical absolute path 写为 sibling temporary JSON，再 atomic move 到目标 filename。
9. Analyzer 按 canonical `module.baseDirectory` 与完整 Module coordinate 配对 GraphML/JSON，严格校验 external binding 集合。

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
- `cia.dependencyGraphFileName` 与 `cia.resolvedArtifactsFileName` 都只允许 filename；拒绝 absolute path、目录分隔符和 `..`，解析到当前 Module basedir。
- Analyzer strict parser 要求 `schemaVersion == 1`、必填字段类型正确、binding 唯一、path absolute 且存在；禁止 duplicate property，忽略未知字段。
- Timestamped SNAPSHOT 可保留在 `version`；GraphML binding 使用 `baseVersion`。
- `scope: "system"` 的 `absolutePath` 直接来自 validated effective `systemPath`；Schema 不区分 Resolver file 与 system file。

## Acceptance Criteria

### Functional

- Given GraphML 将 dependency 选为 `provided`；When Plugin 解析 physical path；Then JSON scope 保持 `provided`，不根据 POM 或 Resolver 重新推导为 `compile`。
- Given GraphML 存在 `test` dependency 且 binary 不存在；When combined goals 执行；Then goal 成功，JSON 与 local repository `.lastUpdated` 均不出现该 coordinate。
- Given GraphML selected `system` binding 在 effective `MavenProject` 中存在唯一同坐标 dependency，且 `systemPath` 是 absolute existing file；When Plugin 执行；Then JSON scope 保持 `system` 并绑定该 path，不创建 remote `ArtifactRequest`。
- Given baseline 或 target 存在 selected `system` JAR；When Analyzer 消费 GraphML/JSON；Then 该 binding 进入 dependency diff，并与其他 retained JAR 一样进入适用的 JAR diff 与 Call Graph scope。
- Given GraphML selected `system` binding 缺少唯一 effective dependency，或 `systemPath` 非 absolute existing regular file；When Plugin 执行；Then goal 失败且不发布 JSON。
- Given dependency 被 exclusion 或 mediation loser 排除；When Plugin 执行；Then 不创建对应 `ArtifactRequest`。
- Given clean Reactor 且 upstream binary/classes 不存在；When Plugin 执行；Then Reactor coordinate 跳过，Analyzer 在 target compile 后映射到 `target/classes`。
- Given GraphML 没有 external dependency；When Plugin 执行；Then JSON 输出 `artifacts: []`。
- Given GraphML 与 JSON external binding 不一致；When Analyzer 配对；Then dependency preparation 失败并报告 missing/unexpected binding。

### Non-Functional

- [ ] GraphML reader 拒绝 malformed XML、DOCTYPE、external entity、duplicate binding 与 scope conflict。
- [ ] JSON 使用 sibling temporary file 与 atomic move，不发布 partial output。
- [ ] `system` binding 不访问 remote repository，也不在 local repository 产生 `.lastUpdated`。
- [ ] Maven 3.6.3、3.8.9 和当前 Maven 3.9.x compatibility suite 通过；Maven 4 不执行。
- [ ] Plugin class major `<=52`，不包含 Maven/Resolver implementation、connector 或 transport class。

## Edge Cases

- GraphML filename 缺失、不存在、不是普通文件或 root Module 不匹配时，goal 立即失败且无 fallback。
- 非 `system` classifier、`test-jar`、non-JAR、native classifier、SNAPSHOT 与 relocation 均以 GraphML selected coordinate 和 Resolver result 为准；`system` path 只来自 effective `MavenProject`。
- `system` coordinate 必须与 effective `MavenProject` dependency 完整匹配；缺失、重复、scope 不为 `system`、`systemPath` 为空、非 absolute、文件不存在或不是 regular file 时拒绝绑定。
- Duplicate coordinate 同 scope 与同 coordinate 不同 scope 都拒绝，避免一个 physical path 对应不唯一 binding。
- Path 可包含 Unicode、空格或 Windows drive letter；JSON escaping 与 absolute-path validation 必须保持可解析。
- `ArtifactTypeRegistry` 不认识 type 时使用公开 Resolver fallback type，不推断 local repository layout。

## Implementation Boundaries

- RepositorySystemSession 继续提供 mirror、proxy、authentication、offline policy、cache、WorkspaceReader 与 LocalRepositoryManager；Plugin 不读取或重建 `settings.xml`。
- 非 `system` Plugin request 只使用当前 project effective repositories，不保留 transitive node-specific repository list；`system` binding 不使用 repository resolution。
- GraphML 与 JSON 只服务 `impact`；`tree --dependency-plugin-version` 行为和 verbose text report 不变。
- Non-JAR dependency 保留在 result/Report，但不进入 bytecode diff 或 Call Graph scope。
- Baseline `analyzeResolved()` 与 target `mvn compile` 并行；join 后执行 target `analyzeResolved()`，避免同一 target workspace 同时运行两个 Maven process。
- Plugin extraction、settings overlay 或 GraphML capability probe failure 属于 command-level Preflight failure；pipeline 不启动，旧 Report 不替换。
