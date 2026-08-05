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
    desc: "coordinate-based repository 提供 JAR handle"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "同 session GraphML/JSON 执行、配对和集合一致性校验"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/GraphMLParser.java"
    desc: "Analyzer GraphML selected tree 解析与 test subtree 排除"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyScope.java"
    desc: "impact 保留 compile/runtime/provided/system scope 的共享枚举"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ResolvedArtifactJsonParser.java"
    desc: "Artifact Path JSON Schema v2 strict streaming parser"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalysisResult.java"
    desc: "tree 与 physical artifact bindings"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ArtifactPathBindingResolver.java"
    desc: "manifest ingestion 的 Module-local coordinate binding 校验"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/IJarRepository.java"
    desc: "command-scoped coordinate-only JAR access boundary"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/CoordinateJarRepository.java"
    desc: "immutable deterministic repository implementation"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/GraphmlDependencyReader.java"
    desc: "GraphML secure parsing、root validation 与 selected binding 提取"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ResolveArtifactPathsMojo.java"
    desc: "GraphML 驱动的 Reactor skip 与非传递 artifact resolution"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/ArtifactPathJsonWriter.java"
    desc: "Schema v2 streaming output 与 atomic publish"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "fully-qualified goals 与 effective settings overlay"
---

# Feature: Dependency Tree Extraction

## Summary

`impact` 同时需要 Maven mediated dependency tree 与 Resolver 确认的 artifact file。每个 workspace 在原 project 的同一个 Maven process/session 中先执行 Maven Dependency Plugin `tree`，再执行内置 Artifact Path Plugin。GraphML 是唯一 mediation authority；Module-local Schema v2 JSON 是 repository ingestion manifest。Baseline/target ingestion 完成后立即构建一个 command-scoped immutable `IJarRepository`，后续 domain object 只保存 `ArtifactCoord`。Production path 不生成 temporary POM、不调用 `dependency:list`、不拼接 local repository path，也不添加 `-llr`。

## Actors / Entrypoints

- `DependencyAnalyzer.analyzeResolved()` 生成唯一 GraphML/JSON filename、执行 combined Maven command，并消费两个输出。
- Maven Dependency Plugin `3.6.1:tree` 生成每个 Module 的 GraphML。
- Artifact Path Plugin `2.1.0:resolve-artifact-paths` 读取同一 Module 的 GraphML，解析 external artifact physical path。
- Analyzer 将 Reactor dependency 映射到 target Module 的 `target/classes`。

## Behavior Contract

- GraphML root coordinate 必须与当前 `MavenProject` 完整 coordinate 一致。
- Artifact Path Plugin 只采用 GraphML 中 selected `compile/runtime/provided/system` binding；`test` 完全不进入 dependency diff、path binding、JAR diff 或 Call Graph。
- Exclusion 与 conflict loser 已由 GraphML mediation 结论排除，不会创建 `ArtifactRequest`。
- Reactor identity 使用 `groupId:artifactId:type:classifier:baseVersion`；Reactor binding 不解析 binary。
- 非 `system` binding 使用当前 project effective repositories 批量执行非传递 resolution；path 只取 `ArtifactResult.getArtifact().getFile()` 并 canonicalize。
- `system` binding 按完整 coordinate 匹配当前 effective `MavenProject` 的 `system` dependency；验证并绑定 absolute `systemPath`，不创建 remote `ArtifactRequest`。
- GraphML 与 JSON external binding 按 canonical coordinates 双向完全一致；scope 不进入 JSON、集合校验或 physical path lookup。
- 任一输入校验、`systemPath` binding 或 artifact resolution 失败时 goal 失败，不发布 partial JSON。
- Schema v2 `absolutePath` 只存在于 manifest ingestion；Analyzer 不从 coordinate 推导 local repository layout。
- Repository 以当前 `ArtifactCoord` equality 作为全局 JAR identity。Canonical path 相同则静默 deduplicate；同 coordinate 对应不同 canonical path 时按 `Path.toString()` 自然升序选择第一条并输出一次 warning，不将 Module 标记为 `INCONCLUSIVE`。
- Repository 建立后 immutable；unknown coordinate、文件消失、非 regular file 或 invalid JAR 均 fail-fast。`JarLease` 负责关闭 handle，repository close 兜底关闭未释放 lease。

## Design Decisions

- GraphML 是唯一 mediation authority。Plugin 不执行第二次 dependency collection，避免两条 collection path 对同一 coordinate 得出不同 effective scope。
- JSON 只承担 coordinates 到 physical path 的 binding，Schema v2 与 Module identity、dependency scope 和 mediation 策略解耦。
- `ResolvedArtifact` 只作为 ingestion DTO；repository 建立后，`ModuleAnalysisUnit`、`DependencyUpgradeKey`、ownership、JAR diff、scope、SSA 与 code comparison 均使用 coordinate。
- JSON filename 仍属于当前 Module execution；Analyzer 通过 JSON/GraphML 的 canonical parent directory 配对，payload 不重复保存 Module 信息。
- `baseVersion` 是与 GraphML 对齐的 logical version；`version` 可保留 Resolver 返回的 timestamped SNAPSHOT。
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
    2.1.0:
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
3. Plugin 使用禁用 DTD、external entity 与 XInclude 的 XML parser 读取 GraphML，校验唯一 root、完整 Module coordinate、cycle 与 unreachable node。
4. Plugin 遍历 root 可达节点；保留 `compile/runtime/provided/system`，忽略 `test` 及其 subtree，跳过 Reactor coordinate。
5. Plugin 通过 session `ArtifactTypeRegistry` 恢复 extension/default classifier，构造 `DefaultArtifact`。
6. 对 `system` occurrence，Plugin 按完整 coordinate 在 effective `MavenProject` dependencies 中要求唯一 `system` dependency，验证其 `systemPath` 是 absolute existing regular file。
7. 对其余 occurrence，Plugin 按 canonical coordinates 去重，使用 `new ArtifactRequest(artifact, project.remoteProjectRepositories, "project")` 批量执行非传递 resolution。
8. 普通与 `system` occurrence 最终映射到同一 coordinates 时，canonical path 相同则合并；path 不同则以 physical path ambiguity 失败。
9. 全部 binding 成功后，Plugin 将 physical absolute path 写为 sibling temporary JSON，再 atomic move 到目标 filename。
10. Analyzer 按 JSON/GraphML canonical parent directory 配对，并只按 coordinates 严格校验 external binding 集合。
11. Analyzer 合并 baseline/target 全部 `ResolvedArtifact`，按 coordinate 构建 command-scoped `CoordinateJarRepository`；从此丢弃 Module/side 到 dependency path 的业务绑定。
12. JAR diff、WALA scope、Structural scan、SSA/code comparison 通过 `IJarRepository.open(coordinate)` 获取 temporary `JarLease`。

## JSON Schema v2

```json
{
  "schemaVersion": 2,
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
      "absolutePath": "/absolute/repository/library-2.0-tests.jar"
    }
  ]
}
```

- Output 使用 UTF-8；`artifacts` 按完整 coordinates、absolutePath 排序；无 external dependency 时为 `[]`。
- `cia.dependencyGraphFileName` 与 `cia.resolvedArtifactsFileName` 都只允许 filename；拒绝 absolute path、目录分隔符和 `..`，解析到当前 Module basedir。
- Analyzer strict parser 要求 `schemaVersion == 2`、必填字段类型正确、coordinate binding 唯一、path absolute 且存在；禁止 duplicate property，忽略未知字段。
- Timestamped SNAPSHOT 可保留在 `version`；GraphML binding 使用 `baseVersion`。
- `system` dependency 的 `absolutePath` 直接来自 validated effective `systemPath`；Schema 不区分 Resolver file 与 system file。

## Acceptance Criteria

### Functional

- Given baseline/target 同 coordinates 的 scope 不同；When Analyzer 绑定 physical path；Then 两侧均只按 coordinates 获取 path，scope-only change 不产生 impact change。
- Given GraphML 存在 `test` dependency 且 binary 不存在；When combined goals 执行；Then goal 成功，JSON 与 local repository `.lastUpdated` 均不出现该 coordinate。
- Given GraphML selected `system` binding 在 effective `MavenProject` 中存在唯一同坐标 dependency，且 `systemPath` 是 absolute existing file；When Plugin 执行；Then JSON 绑定该 path，不创建 remote `ArtifactRequest`。
- Given baseline 或 target 存在 selected `system` JAR；When Analyzer 消费 GraphML/JSON；Then 该 binding 进入 dependency diff，并与其他 retained JAR 一样进入适用的 JAR diff 与 Call Graph scope。
- Given GraphML selected `system` binding 缺少唯一 effective dependency，或 `systemPath` 非 absolute existing regular file；When Plugin 执行；Then goal 失败且不发布 JSON。
- Given dependency 被 exclusion 或 mediation loser 排除；When Plugin 执行；Then 不创建对应 `ArtifactRequest`。
- Given clean Reactor 且 upstream binary/classes 不存在；When Plugin 执行；Then Reactor coordinate 跳过，Analyzer 在 target compile 后映射到 `target/classes`。
- Given GraphML 没有 external dependency；When Plugin 执行；Then JSON 输出 `artifacts: []`。
- Given GraphML 与 JSON external binding 不一致；When Analyzer 配对；Then dependency preparation 失败并报告 missing/unexpected binding。
- Given 多个 Module/side 提供相同 coordinate/canonical path；When repository 初始化；Then 静默 deduplicate，结果与输入顺序无关。
- Given 相同 coordinate 对应不同 canonical path，包括不同 `systemPath`；When repository 初始化；Then 选择 path 字符串自然升序第一条并输出一次 warning，不产生 `INCONCLUSIVE`。
- Given repository coordinate 缺失、文件消失或 invalid JAR；When create/open；Then fail-fast；repository close 后拒绝再次 open。

### Non-Functional

- [ ] GraphML reader 拒绝 malformed XML、DOCTYPE、external entity、cycle 与 unreachable node；重复 coordinates 由 resolution 层去重。
- [ ] JSON 使用 sibling temporary file 与 atomic move，不发布 partial output。
- [ ] `system` binding 不访问 remote repository，也不在 local repository 产生 `.lastUpdated`。
- [ ] Maven 3.6.3、3.8.9 和当前 Maven 3.9.x compatibility suite 通过；Maven 4 不执行。
- [ ] Plugin class major `<=52`，不包含 Maven/Resolver implementation、connector 或 transport class。

## Edge Cases

- GraphML filename 缺失、不存在、不是普通文件或 root Module 不匹配时，goal 立即失败且无 fallback。
- 非 `system` classifier、`test-jar`、non-JAR、native classifier、SNAPSHOT 与 relocation 均以 GraphML selected coordinate 和 Resolver result 为准；`system` path 只来自 effective `MavenProject`。
- `system` coordinate 必须与 effective `MavenProject` dependency 完整匹配；缺失、重复、scope 不为 `system`、`systemPath` 为空、非 absolute、文件不存在或不是 regular file 时拒绝绑定。
- 单个 Plugin execution 内 duplicate coordinate/path ambiguity 仍拒绝发布 manifest；跨 Module/side repository ingestion 的同 coordinate/different path 使用 deterministic winner warning 规则。
- Path 可包含 Unicode、空格或 Windows drive letter；JSON escaping 与 absolute-path validation 必须保持可解析。
- `ArtifactTypeRegistry` 不认识 type 时使用公开 Resolver fallback type，不推断 local repository layout。

## Implementation Boundaries

- RepositorySystemSession 继续提供 mirror、proxy、authentication、offline policy、cache、WorkspaceReader 与 LocalRepositoryManager；Plugin 不读取或重建 `settings.xml`。
- 非 `system` Plugin request 只使用当前 project effective repositories，不保留 transitive node-specific repository list；`system` binding 不使用 repository resolution。
- GraphML 与 JSON 只服务 `impact`；`tree --dependency-plugin-version` 行为和 verbose text report 不变。
- Non-JAR dependency 保留在 result/Report，但不进入 bytecode diff 或 Call Graph scope。
- Plugin Schema v2 与 `absolutePath` 字段保持兼容；它们只负责 repository 初始化，不是后续 domain identity。
- Baseline `analyzeResolved()` 与 target `mvn compile` 并行；join 后执行 target `analyzeResolved()`，避免同一 target workspace 同时运行两个 Maven process。
- Plugin extraction、settings overlay 或 GraphML capability probe failure 属于 command-level Preflight failure；pipeline 不启动，旧 Report 不替换。
