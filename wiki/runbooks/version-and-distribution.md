---
title: "Version and Distribution"
type: runbook
relations:
  - path: "wiki/rules/release-versioning.md"
    desc: "独立 SemVer、immutable release 与 fingerprint gate"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "日常 Maven build/test/package 命令"
  - path: "wiki/features/maven-runtime.md"
    desc: "distribution 内嵌 Maven/Plugin runtime 及实际加载 evidence"
  - path: "wiki/project/dependency-analyzer.md"
    desc: "Module map、artifact 名称与 runtime boundary"
code_refs:
  - path: "scripts/version.sh"
    desc: "Version show、verify 与 bump 入口"
  - path: "scripts/build-distribution.sh"
    desc: "正式与 dev distribution 的统一入口"
  - path: "scripts/internal/VersionTool.java"
    desc: "Version ledger 与 fingerprint helper"
  - path: "scripts/internal/DistributionTool.java"
    desc: "Packaged JAR inspection、reproducibility compare 与 manifest publish"
  - path: "scripts/internal/PackagedRuntimeSmoke.java"
    desc: "隔离 local repository 的 combined-goal packaged smoke"
  - path: "build-support/version-contract.properties"
    desc: "Release ledger 与固定 outputTimestamp"
---

# Runbook: Version and Distribution

## Summary

本 runbook 是 version iteration 与正式 distribution 的稳定入口。日常开发可运行 Maven quality gate；对外交付必须先 seal version contract，再由 `build-distribution.sh` 完成 packaged smoke、可复现重建和原子发布。

<!-- version-contract:start -->
- Analyzer release: `0.1.0`
- Artifact Path Plugin release: `1.0.1`
<!-- version-contract:end -->

## Prerequisites

- macOS/Linux、POSIX shell、Git。
- Java 17 JDK 与 Maven `3.6.3 <= version < 4.0.0`。
- 正式 distribution 需要 clean Git commit。
- 正式 distribution 通过 `TEST_JDK8_HOME` 指向完整 JDK 8，至少存在 executable `bin/java`、`bin/javac` 与 JDK 8 runtime。
- 所有 command 从 repository root 执行。

## Inspect and Verify

```sh
./scripts/version.sh show
./scripts/version.sh verify
```

`verify` 同时检查 strict SemVer、POM/build metadata/template 一致性、current version 单调性，以及 Analyzer/Plugin source fingerprint。失败时先判断改动属于 Analyzer、Plugin 或两者，再进行 bump。

## Version Bump

Analyzer-only PATCH：

```sh
./scripts/version.sh bump --analyzer patch
```

Analyzer MINOR/MAJOR：

```sh
./scripts/version.sh bump --analyzer minor
./scripts/version.sh bump --analyzer major
```

Plugin 输入变化时必须同步 bump Analyzer：

```sh
./scripts/version.sh bump \
  --analyzer patch \
  --plugin patch
```

Command 原子更新 ledger、root revision、Plugin POM/property、稳定 timestamp 与文档 current-version marker，随后重新计算并 seal fingerprint。任一步失败会恢复原文件。Bump 后先 review diff，再 commit；不要手工复用历史 version。

## Formal Distribution

```sh
TEST_JDK8_HOME=/absolute/path/to/jdk8 \
  ./scripts/build-distribution.sh
```

正式模式依次执行：

1. `version.sh verify`；
2. clean worktree、release SemVer 与完整 JDK 8 gate；
3. 使用 ledger `outputTimestamp` 执行 `mvn clean verify`；
4. 检查 CLI dynamic version、manifest、内嵌 Plugin/consumer POM/checksum、`plugin.xml`、Java 8 class major 与 shading boundary；
5. 使用隔离 Maven local repository 执行 packaged `dependency:tree + resolve-artifact-paths` smoke，并保留旧 `1.0.0` cache regression fixture；
6. 使用相同 timestamp 再次重建并比较完整 CLI JAR SHA-512；
7. 原子发布正式 bundle。

正式输出：

```text
target/dependency-analyzer.jar
target/distribution/
├── dependency-analyzer-0.1.0.jar
├── dependency-analyzer-0.1.0.jar.sha512
└── dependency-analyzer-0.1.0-build-manifest.json
```

## Dev Prevalidation

Dirty worktree 或暂时没有 JDK 8 时：

```sh
./scripts/build-distribution.sh \
  --allow-dirty \
  --skip-jdk8-smoke
```

Dev 模式仍执行 version gate、完整 Maven verify、packaged combined-goal smoke、binary inspection 和 reproducibility comparison，但允许 dirty Git 并跳过真实 JDK 8 impact smoke。输出固定到 `target/distribution-dev/`；manifest 中 `releaseEligible=false`，不能作为正式交付。

## Manifest Contract

Build manifest `schemaVersion` 为 `1`，至少包含：

```text
schemaVersion
releaseEligible
analyzerVersion
artifactPathPluginVersion
gitCommit
gitDirty
outputTimestamp
javaVersion
mavenVersion
artifactSha512
embeddedPluginSha512
```

Versioned JAR 的 `.sha512` 与 manifest `artifactSha512` 必须一致。内嵌 Plugin `.jar.sha512`、preflight expected SHA-512 与 Mojo actual-loaded SHA-512 必须一致。

## Success Criteria

- CLI `--version` 输出 ledger Analyzer version。
- JAR 只包含 ledger 指定 Plugin version，不包含旧 Plugin release resource。
- Plugin descriptor 声明 `dependencyGraphFileName`；packaged smoke Console 出现 `(f) dependencyGraphFileName` 与 `implementation=graphml-v1`。
- Plugin base class major 不超过 `52`，不携带 Maven/Resolver implementation class，Jackson 已 relocate。
- 两次固定 timestamp build 得到相同 CLI JAR SHA-512。
- 正式 manifest 为 `releaseEligible=true`；任一 dirty/skip gate 只能产生 dev manifest。

## Failure Entrypoints

- `Analyzer inputs changed` / `Plugin inputs changed`：按 release rule 选择 bump；不要修改 SHA-512 值。
- `formal build requires a clean Git worktree`：review、commit 后重跑；本地预验证使用 dev mode。
- `TEST_JDK8_HOME must point to a complete JDK 8`：修正 JDK 8 root；不得用 `--skip-jdk8-smoke` 生成正式 bundle。
- Packaged smoke 缺少 `(f) dependencyGraphFileName`：检查内嵌 Plugin version、consumer POM、runtime goal 与 `plugin.xml`。
- Expected/actual Plugin SHA 不同：检查 Maven 实际加载 source path、旧 local repository coordinate 和 runtime fingerprint。
- Reproducibility mismatch：检查未受 `project.build.outputTimestamp` 控制的 archive timestamp、entry ordering 或生成 metadata。
- Maven test failure：查看 `target/surefire-reports/` 与 `target/failsafe-reports/`。
