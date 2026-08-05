---
title: "Release Versioning"
type: rule
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "Analyzer 与内置 Maven Plugin 的 reactor/GAV 边界"
  - path: "wiki/features/maven-runtime.md"
    desc: "内嵌 Plugin coordinate 与 Stable/Snapshot cache 行为"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "执行 version iteration、release build、commit 与 tag 的入口"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "日常双 reactor quality gate"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer revision、Plugin repository dependency 与 release profile"
  - path: "plugins/pom.xml"
    desc: "Artifact Path Plugin revision、Java 8 与 release profile"
  - path: "analyzer/pom.xml"
    desc: "Analyzer 对 Plugin repository ZIP 的 versioned dependency"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Plugin attached repository ZIP 与 flattened install POM"
---

# Rule: Release Versioning

## Summary

Analyzer 与 Artifact Path Plugin 使用独立 SemVer。Git annotated tag 是 release version 记录；Maven POM 是当前开发或 release version 的 build source。Repository 不维护额外 version contract、source fingerprint 或发布脚本。

## Reusable Constraint

- Version 只允许 `MAJOR.MINOR.PATCH` 或 `MAJOR.MINOR.PATCH-SNAPSHOT`。
- 日常开发使用下一次计划 release 的固定 Snapshot version；同一 release 周期内重复 `clean install`，不因每次自测 bump。
- Analyzer version 位于 root `revision`；Artifact Path Plugin version 位于 `plugins/pom.xml` 的 `revision`；Analyzer 引用 version 位于 root `artifact-path-plugin.version`。
- Plugin source、consumer POM 或 repository packaging 变化后必须先执行 Plugin reactor `clean install`，确保同一 Snapshot coordinate 在 Maven local repository 中更新，再构建 Analyzer。
- Plugin stable release 必须先完成并安装，Analyzer release 才能引用该 stable repository ZIP。
- `release` profile 只接受 Stable SemVer，并拒绝 Snapshot dependency；默认 profile 同时接受 Stable 与 Snapshot。
- `MAJOR` 用于 CLI/Schema/Plugin goal 等 breaking change；`MINOR` 用于向后兼容能力；`PATCH` 用于 bug fix、可靠性、内部重构和 build 修复。
- Release commit 同时包含已验证的 stable POM 与文档；Analyzer tag `analyzer-vX.Y.Z`、Plugin tag `artifact-path-plugin-vX.Y.Z` 指向该 commit。
- Dev build 不要求 Git commit 或 tag。Release 才要求 commit/tag；本规则不要求 clean worktree fingerprint gate。

## Applicability

- 修改任一 reactor version、Analyzer 内嵌 Plugin version、Plugin source/package 或 release 操作时适用。
- Maven Dependency Plugin `3.6.1` 与 Apache Maven `3.6.3` 是 runtime dependency version，不使用本项目 tag namespace。

## Stable Verification

默认 SemVer、Java 与 JDK 8 文件约束由 Maven Enforcer 在 `validate` 阶段执行。Release 约束由两个 reactor 的 `release` profile 执行：

```sh
mvn -f plugins/pom.xml -Prelease clean install
TEST_JDK8_HOME=/absolute/path/to/jdk8 mvn -Prelease clean verify
```

Version 修改使用 Versions Maven Plugin；禁止引入新的 project-owned version ledger 或发布脚本。

Git record verification：

```sh
git show analyzer-vX.Y.Z
git show artifact-path-plugin-vX.Y.Z
```

## Reference Files

- `pom.xml` - Analyzer SemVer、Artifact Path Plugin dependency version 和 release gate。
- `plugins/pom.xml` - Plugin SemVer、Java 8 compile target 和 release gate。
- `plugins/artifact-path-resolver/pom.xml` - Maven Plugin 与 repository ZIP attachment。
- `analyzer/pom.xml` - versioned repository ZIP copy 和 final Analyzer JAR。
