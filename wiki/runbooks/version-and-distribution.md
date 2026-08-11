---
title: "Version and Distribution"
type: runbook
relations:
  - path: "wiki/rules/release-versioning.md"
    desc: "独立 SemVer、Snapshot 复用、Stable release 与 Git tag 规则"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "日常四reactor build/test/package命令"
  - path: "wiki/features/maven-runtime.md"
    desc: "Analyzer 内嵌的两个 repository ZIP 与 runtime cache"
  - path: "wiki/project/dependency-analyzer.md"
    desc: "四个独立reactor boundary、artifact名称与runtime boundary"
  - path: "wiki/features/jdk-method-models.md"
    desc: "公共engine与JDK 8 model的独立artifact/version contract"
code_refs:
  - path: "pom.xml"
    desc: "Analyzer revision、Plugin/JDK 8 model dependency version与release profile"
  - path: "plugins/pom.xml"
    desc: "Plugin revision、Java 8 与 release profile"
  - path: "analyzer/pom.xml"
    desc: "Stable Plugin repository ZIP、JDK 8 model dependency与final Analyzer JAR"
  - path: "models/jdk/pom.xml"
    desc: "公共engine version、flatten与release profile"
  - path: "models/jdk8/pom.xml"
    desc: "JDK 8 model version、公共engine dependency与release profile"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Plugin JAR、flattened POM 与 attached repository ZIP"
---

# Runbook: Version and Distribution

## Summary

本 runbook 使用 Maven 自身的 Versions、Enforcer、Install、Assembly、Shade、Surefire 与 Failsafe 能力完成 version iteration 和 release。Repository 不提供自有 version/release script，也不维护 version contract 或 fingerprint ledger。

当前release线：Analyzer`2.0.0`、Dependency Evidence Plugin`3.0.0`、公共JDK engine与JDK 8 model`0.1.0-SNAPSHOT`。四者独立使用Semantic Versioning（SemVer）。

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
  -DnewVersion=3.1.0-SNAPSHOT \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.1.0-SNAPSHOT \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=artifact-path-plugin.version \
  -DnewVersion=3.1.0-SNAPSHOT \
  -DgenerateBackupPoms=false
```

任一独立artifact source变化后刷新同一个Snapshot coordinate，并按dependency顺序构建：

```sh
mvn -f models/jdk/pom.xml clean install
mvn -f models/jdk8/pom.xml clean install
mvn -f plugins/pom.xml clean install
mvn clean verify
```

无需为每次本地自测 bump 或 commit。Analyzer build 会重新 copy local repository 中的 Snapshot repository ZIP；runtime 为 Dependency Evidence Plugin Snapshot 添加 `-U`，并只刷新该小型 repository cache。

## Stable Release

以下示例发布公共engine/JDK 8 model`0.1.0`、Dependency Evidence Plugin`3.0.0`与Analyzer`2.0.0`。

### 1. 切换并安装公共engine与JDK 8 model Stable version

分别将两个model POM的project version切换到`0.1.0`，并将`models/jdk8/pom.xml`的`jdk-models.version`切换到`0.1.0`。随后执行：

```sh
mvn -f models/jdk/pom.xml -Prelease clean install
mvn -f models/jdk8/pom.xml -Prelease clean install
```

### 2. 切换并安装 Plugin Stable version

```sh
mvn -f plugins/pom.xml versions:set-property \
  -Dproperty=revision \
  -DnewVersion=3.0.0 \
  -DgenerateBackupPoms=false

mvn -f plugins/pom.xml -Prelease clean install
```

### 3. 切换 Analyzer Stable version、Plugin dependency和JDK 8 model dependency

```sh
mvn versions:set-property \
  -Dproperty=revision \
  -DnewVersion=2.0.0 \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=artifact-path-plugin.version \
  -DnewVersion=3.0.0 \
  -DgenerateBackupPoms=false

mvn versions:set-property \
  -Dproperty=jdk8-models.version \
  -DnewVersion=0.1.0 \
  -DgenerateBackupPoms=false
```

### 4. 执行 release quality gate

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

必须恰好得到 `maven-dependency-plugin-3.6.1-repository.zip` 与 `dependency-analyzer-artifact-path-maven-plugin-3.0.0-repository.zip`。

同时确认JAR包含`JdkModels.class`、`Jdk8Models.class`与`jdk8-models.tsv`。

### 5. Commit 并记录 Git tags

Release验证通过后创建一个commit；四个annotated tag指向同一commit：

```sh
git add -A
git commit -m "build(release): publish dependency analyzer artifacts"
git tag -a jdk-models-v0.1.0 -m "JDK Models 0.1.0"
git tag -a jdk8-models-v0.1.0 -m "JDK 8 Models 0.1.0"
git tag -a dependency-evidence-plugin-v3.0.0 -m "Dependency Evidence Plugin 3.0.0"
git tag -a analyzer-v2.0.0 -m "Dependency Analyzer 2.0.0"
```

Tag 是 release record。是否 push commit/tag 或创建远端 release 由后续明确操作决定。

## Success Criteria

- Plugin `-Prelease` 只使用 Stable SemVer，生成 JAR 与 attached `repository` ZIP，class major `<=52`。
- 两个model artifact以独立Stable SemVer构建，JDK 8 model只引用Stable公共engine；flattened consumer POM不保留Snapshot或unresolved parent/property。
- Analyzer`-Prelease`只使用Stable SemVer、Stable Plugin dependency与Stable JDK 8 model dependency，Surefire/Failsafe全部通过且无skip。
- Analyzer JAR `--version` 输出 `2.0.0`。
- Analyzer JAR 只包含两个 repository ZIP，不包含旧 loose Plugin JAR/POM 或项目生成 checksum。
- Empty local repository在model与Plugin install前不能构建Analyzer；按公共model、JDK 8 model、Plugin顺序install后可以构建。
- 四个component tag指向同一release commit。
- Stable POM 保留在 release commit；开始下一开发周期时再切换下一 Snapshot。

## Failure Entrypoints

- `release revision must be stable`：仍为 `-SNAPSHOT`；重新执行对应 `versions:set-property`。
- `requireReleaseDeps` failure：JDK 8 model、Analyzer或Plugin仍引用Snapshot dependency；按公共model、JDK 8 model、Plugin、Analyzer顺序切换与安装Stable coordinate。
- Plugin attached ZIP 无法解析：确认 `mvn -f plugins/pom.xml -Prelease clean install` 成功，检查 Maven local repository classifier `repository`。
- `test.jdk8.home` failure：修正 root 默认值或使用 `-Dtest.jdk8.home=...` 指向 absolute JDK 8 root；不得通过跳过 tests 发布。
- `--version` 不一致：检查 root `revision` 和 filtered build metadata。
- Tag 已存在：先检查它是否为既有 release record；禁止移动或覆盖已发布 tag。

## Configuration

- 默认 profile：接受 `X.Y.Z` 或 `X.Y.Z-SNAPSHOT`。
- `release` profile：只接受 `X.Y.Z`，并执行 `requireReleaseDeps`。
- Maven build 本身使用 Java 17；Plugin compilation 由 `maven.compiler.release=8` 控制。
- Analyzer test JDK 8 默认来自 root `test.jdk8.home`；Surefire/Failsafe 自动注入 `TEST_JDK8_HOME`，无需预先导出环境变量。
- Analyzer使用的JDK 8 model coordinate由root`jdk8-models.version`维护；默认与release profile均执行SemVer gate。
- Release 不要求额外 checksum、fingerprint、build manifest 或 distribution directory；正式 Analyzer artifact 是 `target/dependency-analyzer.jar`。
