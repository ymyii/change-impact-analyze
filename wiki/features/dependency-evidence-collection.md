---
title: "Structured Dependency Evidence Collection"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "baseline/target dependency preparation 与 downstream consumer"
  - path: "wiki/features/maven-runtime.md"
    desc: "内嵌 Plugin repository、settings overlay 与 Maven compatibility"
  - path: "wiki/features/git-workspace-management.md"
    desc: "command run cache、owner 与 stale recovery"
  - path: "wiki/features/dependency-diff-engine.md"
    desc: "selected dependency tree 进入 Module dependency diff"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "coordinate-based repository 提供 JAR handle"
code_refs:
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/CollectDependencyEvidenceMojo.java"
    desc: "resolved graph、raw occurrence graph、artifact binding 与 Module publication"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/DependencyEvidenceOutputDirectory.java"
    desc: "cache owner、source workspace 与 symlink boundary"
  - path: "plugins/artifact-path-resolver/src/main/java/io/github/dependencyanalysis/maven/DependencyEvidenceJsonWriter.java"
    desc: "Schema v3 streaming output 与 atomic publication"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "command-owned cache、单 goal 执行、严格发现与清理"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyEvidenceJsonParser.java"
    desc: "Schema v3 strict parser 与 cross-field validation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ModuleDependencyOccurrenceGraph.java"
    desc: "winner-normalized occurrence、edge 与 graph validation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/ModuleDependencyEvidence.java"
    desc: "selected projection、occurrence topology、reactor keys 与 physical binding"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "baseline/target evidence cache 隔离"
---

# Feature: Structured Dependency Evidence Collection

## Summary

`impact` 不读取 GraphML，也不解析 Maven `omitted for ...` 展示标签。内嵌 Dependency Evidence Plugin `3.0.0` 在一次 Maven session 内直接读取 Maven Dependency Tree API 的结构化对象：`DependencyGraphBuilder` 提供 resolved winner graph，`DependencyCollectorBuilder` 提供 raw occurrence graph。Plugin 生成每 Module 一个 Dependency Evidence Schema v3 JSON；Analyzer 严格解析为唯一 `ModuleDependencyEvidence`。

Maven Dependency Plugin 的 text、DOT、GraphML、TGF 是展示序列化。它们仅服务 `tree` 等面向人的报告能力；当前 `tree` 使用 verbose text。任何展示字符串都不是 `impact` 的程序接口。

## Entrypoints

- Maven goal：`io.github.dependencyanalysis:dependency-analyzer-artifact-path-maven-plugin:3.0.0:collect-dependency-evidence`。
- `DependencyAnalyzer.analyzeResolved()`：执行一次 goal，直接返回完整 `DependencyAnalysisResult`。
- `DependencyAnalyzer.analyze()`：从 Schema v3 evidence 投影兼容的 `ModuleDependencyTree`。
- `PerModuleImpactPipeline`：分别传入 `<command-tmp>/dependency-evidence/baseline` 与 `<command-tmp>/dependency-evidence/target` cache parent。

## Collection Contract

### Resolved selected graph

- 保留 `compile`、`runtime`、`provided`、`system`；`test` 及其 subtree 不进入分析 classpath。
- 以 `groupId:artifactId:type[:classifier]` 建立唯一 winner index；同 key 对应不同 resolved coordinate 时 fail-fast。
- Reactor artifact 记录到 `selectedReactorKeys`，不创建 external artifact request；其 selected children 继续投影为 external dependencies。
- selected traversal order 是 classpath order authority。Dependency diff、reactor closure、JAR binding 与 Call Graph 均读取该顺序。

### Raw occurrence graph

- raw node 只读取 Maven `Artifact`、scope、parent/children 字段；禁止调用 `toNodeString()` 或解析 label。
- raw occurrence 命中 retained winner 时，coordinate 规范化为 winner；duplicate/version loser 的每条 parent path 与 edge 均保留。
- raw occurrence 没有 retained winner 时，表示该 occurrence 未进入分析 classpath；连同其分支删除。
- 每个 retained winner 必须至少匹配一条保留 occurrence；缺失时 fail-fast。
- 发布前 occurrence graph 必须有唯一 root、完整 edge、全 root-reachable node 且无 cycle。

直接 `test` winner 与 transitive `provided`/`compile` duplicate 是关键边界：Maven selected graph 中 `test` winner 被分析 scope 排除，因此 retained winner index 没有该 key；raw graph 中的 transitive duplicate 也必须整体删除。此规则保证 `jsr305` 类 scope conflict 不进入 dependency diff、artifact resolution、JAR diff 或 Call Graph。

### Physical artifacts

- 仅 selected external graph 创建 `ArtifactRequest`；exclusion、version loser、scope-conflict loser 与 `test` 不请求 binary。
- Resolver result 提供 resolved version 和 physical file；Schema 同时保存 logical `baseVersion` 与 resolved `version`。
- `system` 依赖按完整 coordinate 匹配 effective `MavenProject` dependency，并绑定 absolute existing `systemPath`。
- selected external coordinates 与 artifact bindings 必须双向完全一致；duplicate、missing、unexpected binding 均 fail-fast。
- Analyzer 从不推导 local repository layout，也不扫描 source repository 查找 evidence。

## Schema v3

```json
{
  "schemaVersion": 3,
  "module": {
    "groupId": "org.example",
    "artifactId": "application",
    "type": "jar",
    "extension": "jar",
    "classifier": "",
    "version": "1.0",
    "baseVersion": "1.0"
  },
  "moduleDirectory": "/canonical/source/application",
  "dependencies": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "jar",
        "extension": "jar",
        "classifier": "",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "scope": "runtime",
      "children": []
    }
  ],
  "occurrenceGraph": {
    "rootId": "root",
    "occurrences": [
      {
        "id": "root",
        "coordinates": {
          "groupId": "org.example",
          "artifactId": "application",
          "type": "jar",
          "extension": "jar",
          "classifier": "",
          "version": "1.0",
          "baseVersion": "1.0"
        },
        "scope": "",
        "moduleRoot": true,
        "reactor": false
      }
    ],
    "edges": []
  },
  "selectedReactorKeys": [],
  "artifacts": [
    {
      "coordinates": {
        "groupId": "org.example",
        "artifactId": "library",
        "type": "jar",
        "extension": "jar",
        "classifier": "",
        "version": "2.0",
        "baseVersion": "2.0"
      },
      "absolutePath": "/absolute/repository/library-2.0.jar"
    }
  ]
}
```

- JSON 使用 UTF-8；unknown property、duplicate property、缺失字段、错误类型与 trailing content 均拒绝。
- `moduleDirectory` 与 `absolutePath` 必须 absolute、存在并 canonicalize。
- Module coordinate 必须等于 occurrence root coordinate。
- selected dependency coordinate set 必须等于 artifact binding coordinate set。

## Cache Isolation

- Analyzer 为每次调用创建随机 nonce 目录，并写 `.cia-evidence-owner` 随机 token。
- impact 路径：`<command-tmp>/dependency-evidence/{baseline|target}/<nonce>/`。
- 未传 cache 的 Java API 调用使用操作系统临时目录；禁止回退到 Module basedir。
- Analyzer 只传 absolute `cia.dependencyEvidenceDirectory` 和 `cia.dependencyEvidenceOwner`；不传相对 output filename。
- Plugin 同时验证 configured path 与 canonical path 均不在 Maven execution root 内，拒绝 source 内 symlink escape、source 指向路径、symlink owner marker 与 token mismatch。
- 文件名由 canonical Module directory 与完整 Module identity 的 SHA-256 派生。已有目标文件视为 duplicate Module publication 并失败。
- JSON 先写同目录随机 temporary sibling，再以 atomic move 发布；失败不留 partial target。
- Maven 成功或失败后 Analyzer 均删除 nonce；异常进程残留由 `CommandRunDirectory` stale-run recovery 删除。
- 最终 HTML report 仍发布到用户 `--output`。Current target 的 `mvn compile` 可在其 Git workspace 生成 `target/`，不属于 dependency evidence 隔离范围。

## Maven Invocation

```text
mvn <effective-arguments> <project-arguments>
    io.github.dependencyanalysis:
    dependency-analyzer-artifact-path-maven-plugin:
    3.0.0:
    collect-dependency-evidence
    -Dcia.dependencyEvidenceDirectory=<absolute-command-cache-nonce>
    -Dcia.dependencyEvidenceOwner=<random-token>
    -B
```

- Plugin arguments合并用户settings、mirror、proxy、server、local repository与两个内嵌Plugin repository。
- Target `compile` 仍只使用普通用户 Maven arguments，不携带 Plugin settings overlay。
- Maven output 不写 `.log`；failure tail 仅在内存保留，诊断命令中的 owner token 被 redacted。

## Acceptance Criteria

- direct `test` winner + transitive retained-scope duplicate：marker 不进入 selected dependencies、occurrences 或 artifacts，也不产生 binary request/`.lastUpdated`。
- selected v1 winner + raw v1/v2 多路径：全部 occurrence 保存 v1，父路径与 edge 完整。
- unmatched 分支删除后 graph 仍通过 root、reachability、acyclic validation。
- retained winner 在 raw graph 缺失：goal fail-fast，不发布 JSON。
- 多 Module、空格和中文 cache path：每 Module 发布唯一 Schema v3 JSON，Analyzer 成功解析并清理。
- 空 local repository + blocked wildcard mirror + Maven 3.6.3：内嵌 repository 可离线运行 goal。
- Maven 成功或失败：source repository 均不存在 `dep-tree-cia-*`、`resolved-artifacts-cia-*` 或 `module-*.json` evidence。
