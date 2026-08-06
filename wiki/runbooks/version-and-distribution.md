---
title: "Version and Distribution"
type: runbook
relations:
  - path: "wiki/rules/release-versioning.md"
    desc: "独立 SemVer、Snapshot 复用、Stable release 与 Git tag 规则"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "日常双 reactor build/test/package 命令"
  - path: "wiki/features/maven-runtime.md"
    desc: "Analyzer 内嵌的两个 repository ZIP 与 runtime cache"
  - path: "wiki/project/dependency-analyzer.md"
    desc: "双 reactor boundary、artifact 名称与 runtime boundary"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer revision、Plugin version property 与 release profile"
  - path: "plugins/pom.xml"
    desc: "Plugin revision、Java 8 与 release profile"
  - path: "analyzer/pom.xml"
    desc: "Stable Plugin repository ZIP dependency 与 final Analyzer JAR"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Plugin JAR、flattened POM 与 attached repository ZIP"
---

# Runbook: Version and Distribution

## Summary

本 runbook 使用 Maven 自身的 Versions、Enforcer、Install、Assembly、Shade、Surefire 与 Failsafe 能力完成 version iteration 和 release。Repository 不提供自有 version/release script，也不维护 version contract 或 fingerprint ledger。

当前新的 release 起点：Analyzer `2.0.0`，Artifact Path Plugin `2.1.0`。

## Prerequisites

- Java 17 JDK、Maven 3.x、Git。
- root `test.jdk8.home` 指向 absolute、完整且实际 version 为 Java 8 的 JDK root；其他环境通过 `-Dtest.jdk8.home=...` 覆盖。
- Plugin 与 Analyzer release version 已依据 compatibility 选择。
- 所有 command 从 repository root 执行。

## Snapshot Iteration

一个 release 周期只设置一次下一版本 Snapshot，之后重复使用：

```sh
mvn -f plugins/pom.xml versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.2.0-SNAPSHOT \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.1.0-SNAPSHOT \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=artifact-path-plugin.version \
  -DnewVersion=2.2.0-SNAPSHOT \
  -DgenerateBackupPoms=false
```

Plugin source 变化后刷新同一个 Snapshot coordinate：

```sh
mvn -f plugins/pom.xml clean install
mvn clean verify
```

无需为每次本地自测 bump 或 commit。Analyzer build 会重新 copy local repository 中的 Snapshot repository ZIP；runtime 为 Artifact Path Plugin Snapshot 添加 `-U`，并只刷新该小型 repository cache。

## Stable Release

以下示例发布 Artifact Path Plugin `2.1.0` 与 Analyzer `2.0.0`。

### 1. 切换并安装 Plugin Stable version

```sh
mvn -f plugins/pom.xml versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.1.0 \
  -DgenerateBackupPoms=false

mvn -f plugins/pom.xml -Prelease clean install
```

### 2. 切换 Analyzer Stable version 和 Plugin dependency

```sh
mvn versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.0.0 \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=artifact-path-plugin.version \
  -DnewVersion=2.1.0 \
  -DgenerateBackupPoms=false
```

### 3. 执行 release quality gate

```sh
mvn -Prelease clean verify

java -jar target/dependency-analyzer.jar --version
```

Expected version output：`Dependency Analyzer 2.0.0`。

检查打包资源：

```sh
jar tf target/dependency-analyzer.jar | \
  grep '^maven/plugin-repositories/.*-repository.zip$'
```

必须恰好得到 `maven-dependency-plugin-3.6.1-repository.zip` 与 `dependency-analyzer-artifact-path-maven-plugin-2.1.0-repository.zip`。

### 4. Commit 并记录 Git tags

Release 验证通过后创建一个 commit；两个 annotated tag 指向同一 commit：

```sh
git add -A
git commit -m "build(release): start analyzer 2.0.0 and plugin 2.1.0"
git tag -a artifact-path-plugin-v2.1.0 -m "Artifact Path Plugin 2.1.0"
git tag -a analyzer-v2.0.0 -m "Dependency Analyzer 2.0.0"
```

Tag 是 release record。是否 push commit/tag 或创建远端 release 由后续明确操作决定。

## Success Criteria

- Plugin `-Prelease` 只使用 Stable SemVer，生成 JAR 与 attached `repository` ZIP，class major `<=52`。
- Analyzer `-Prelease` 只使用 Stable SemVer 与 Stable Plugin dependency，Surefire/Failsafe 全部通过且无 skip。
- Analyzer JAR `--version` 输出 `2.0.0`。
- Analyzer JAR 只包含两个 repository ZIP，不包含旧 loose Plugin JAR/POM 或项目生成 checksum。
- Empty local repository 在 Plugin install 前不能构建 Analyzer；Plugin install 后可以构建。
- Git tags `artifact-path-plugin-v2.1.0` 与 `analyzer-v2.0.0` 指向同一 release commit。
- Stable POM 保留在 release commit；开始下一开发周期时再切换下一 Snapshot。

## Failure Entrypoints

- `release revision must be stable`：仍为 `-SNAPSHOT`；重新执行对应 `versions:set-property`。
- `requireReleaseDeps` failure：Analyzer 仍引用 Snapshot Plugin repository；先安装 Plugin Stable version 并更新 root property。
- Plugin attached ZIP 无法解析：确认 `mvn -f plugins/pom.xml -Prelease clean install` 成功，检查 Maven local repository classifier `repository`。
- `test.jdk8.home` failure：修正 root 默认值或使用 `-Dtest.jdk8.home=...` 指向 absolute JDK 8 root；不得通过跳过 tests 发布。
- `--version` 不一致：检查 root `revision` 和 filtered build metadata。
- Tag 已存在：先检查它是否为既有 release record；禁止移动或覆盖已发布 tag。

## Configuration

- 默认 profile：接受 `X.Y.Z` 或 `X.Y.Z-SNAPSHOT`。
- `release` profile：只接受 `X.Y.Z`，并执行 `requireReleaseDeps`。
- Maven build 本身使用 Java 17；Plugin compilation 由 `maven.compiler.release=8` 控制。
- Analyzer test JDK 8 默认来自 root `test.jdk8.home`；Surefire/Failsafe 自动注入 `TEST_JDK8_HOME`，无需预先导出环境变量。
- Release 不要求额外 checksum、fingerprint、build manifest 或 distribution directory；正式 Analyzer artifact 是 `target/dependency-analyzer.jar`。
