---
title: "Coordinate JAR Repository"
type: feature
relations:
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "Resolver manifest ingestion 与 repository 初始化"
  - path: "wiki/features/bytecode-diff-engine.md"
    desc: "repository-backed old/new JAR diff"
  - path: "wiki/features/call-graph-engine.md"
    desc: "ArtifactCoord logical ownership 与 WALA Module"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/IJarRepository.java"
    desc: "coordinate-only repository contract"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/JarLease.java"
    desc: "temporary JarFile handle ownership"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/jar/CoordinateJarRepository.java"
    desc: "immutable deterministic implementation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/PerModuleImpactPipeline.java"
    desc: "command-scoped repository lifecycle"
---

# Feature: Coordinate JAR Repository

## Summary

旧 `JarLocator`、`JarLocationResult` 与 local repository layout 推导已删除。`impact` 只信任 Maven Resolver/validated `systemPath` 写入 Schema v3 evidence 的实际 file；baseline/target ingestion 后构建一个 command-scoped immutable `IJarRepository`。业务对象、cache key、ownership 与 Report dependency source 只保留 `ArtifactCoord`。

## Contract

```java
public interface IJarRepository extends AutoCloseable {
    Set<ArtifactCoord> coordinates();
    JarLease open(ArtifactCoord coordinate) throws IOException;
}

public interface JarLease extends AutoCloseable {
    ArtifactCoord coordinate();
    JarFile jarFile();
}
```

- Repository 不暴露 `Path`。
- `ResolvedArtifact` 是唯一 physical binding ingestion DTO。
- 只收录 `type == jar` 的 artifact；non-JAR 继续保留在 dependency result/report，不进入 bytecode scope。
- Canonical path 相同：静默 deduplicate。
- 同 coordinate/different canonical path：按 `Path.toString()` 自然升序选择第一条；输入顺序、Module 顺序与 side 顺序不影响 winner；输出一次 Diagnostic warning。
- Repository construction 验证 selected file 是 regular valid JAR。Unknown coordinate、文件消失、repository closed 后 open 都 fail-fast。
- 每次 `open` 返回独立 tracked `JarLease`。Lease close 关闭 `JarFile`；repository close 兜底关闭 outstanding lease。

## Consumers

- `BytecodeDiffEngine`：old/new coordinate → two leases → class index/diff。
- `ClassOwnershipIndex`、`ModuleScopeValidator`、`StructuralImpactScanner`：coordinate → temporary lease；dependency `ClassSource` 保存 coordinate。
- `ModuleCallGraphEngine`：scope JAR Module 保持 lease 到 command repository close，保证 WALA lazy reads 可用。
- `SsaEquivalenceEngine`：baseline coordinate closure → old-side CHA，不保存 path identity。
- `CodeComparisonBuilder`：只在 lease scope 内将 `JarFile.getName()` 作为 Vineflower/ASM temporary physical input。

## Report Boundary

- Dependency old/new detail 显示 coordinate，不显示 physical JAR path。
- Dependency duplicate candidate/winner 与 method `sourceId` 显示 logical coordinate。
- Maven runtime、PROJECT/reactor directory、JDK/config/tmp/output 等原有 filesystem evidence 按各自 Report contract处理；repository 重构不改变它们。

## Acceptance

- Same coordinate/path deduplicate；same coordinate/different path deterministic winner；system scope 使用同一规则。
- Repository close 后拒绝 open；outstanding lease 被兜底关闭。
- `ModuleAnalysisUnit`、`DependencyUpgradeKey`、dependency ownership、`MethodId` 与 HTML dependency detail 不含 dependency JAR physical path。
- Analyzer 不依赖 Maven local repository layout，也不根据 coordinate 自行拼 path。
