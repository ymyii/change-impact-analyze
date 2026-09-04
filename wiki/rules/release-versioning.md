---
name: "Release Versioning"
type: rule
---

## Overview

本规则约束 [Dependency Analyzer](../c4/software-systems/dependency-analyzer.md) 的 Analyzer、Dependency Evidence Plugin、公共 JDK model engine 与 JDK 8 model 使用独立 Semantic Versioning（SemVer）版本线，并以 Maven POM 和 Git annotated tag 保存 release identity。

## Scope

- 修改任一 artifact version、跨 artifact dependency version、consumer packaging 或 stable release 操作。

## Rules

- **必须**只使用 `MAJOR.MINOR.PATCH` 或 `MAJOR.MINOR.PATCH-SNAPSHOT`；release profile 只接受 Stable SemVer 并拒绝 Snapshot dependency。
- **必须**分别以 root `revision`、`plugins/pom.xml` 的 `revision`、两个 model POM 的 project version 作为四条版本线的唯一 build source。
- **必须**先安装公共 model engine，再安装 coordinate 匹配的 JDK 8 model；Analyzer 通过 root `jdk8-models.version` 选择 façade。
- **必须**让可独立消费的 model POM flatten，避免 consumer 解析 repository root parent 或 unresolved property。
- **必须**让 release commit 保存已验证 Stable POM，并创建 `analyzer-vX.Y.Z`、`dependency-evidence-plugin-vX.Y.Z`、`jdk-models-vX.Y.Z` 与 `jdk8-models-vX.Y.Z` annotated tags。
- **禁止**为日常 Snapshot build 每次 bump、commit 或 tag；同一 release cycle 复用固定 Snapshot version。
- **禁止**引入与 Maven POM/Git tag 并行的 project-owned version ledger、fingerprint 或 release script。

## Verification

- Maven Enforcer 在 `validate` 校验 SemVer、release dependency 与 JDK contract。
- 按 [Version and Distribution](../runbooks/version-and-distribution.md) 执行 stable build，并检查 flattened POM、JAR resources 与四个 tags。

## Non-Goals

- Apache Maven 与 Maven Dependency Plugin 的 external runtime versions 不使用本项目 tag namespace。
- 本规则不授权 push commit/tag 或创建远端 release。
