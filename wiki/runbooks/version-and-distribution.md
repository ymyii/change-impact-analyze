---
name: "Version and Distribution"
type: runbook
---

## Purpose and Scope

本 Runbook 按 [Release Versioning](../rules/release-versioning.md) 更新四条 artifact version line，构建 [Dependency Analyzer](../c4/software-systems/dependency-analyzer.md) 的 Stable artifacts，并创建同一 release commit 上的 annotated tags。它不授权 push 或创建远端 release。

## Prerequisites

- Java 17 JDK、Maven 3.x 与 Git 可用。
- `test.jdk8.home` 指向 absolute、完整 JDK 8；其他环境传入 `-Dtest.jdk8.home=/absolute/path/to/jdk8`。
- 已确定公共 engine、JDK 8 model、Plugin 与 Analyzer 的兼容 Stable versions。
- 所有 command 从 repository root 执行。

## Procedure

1. 开发周期内设置并复用计划 release 的 Snapshot versions。

   ```sh
   mvn -f plugins/pom.xml versions:set-property \
     -Dproperty=revision -DnewVersion=3.1.0-SNAPSHOT \
     -DgenerateBackupPoms=false
   mvn versions:set-property \
     -Dproperty=revision -DnewVersion=3.0.0-SNAPSHOT \
     -DgenerateBackupPoms=false
   mvn versions:set-property \
     -Dproperty=artifact-path-plugin.version \
     -DnewVersion=3.1.0-SNAPSHOT \
     -DgenerateBackupPoms=false
   ```

2. Source 变化后按 dependency 顺序刷新 Snapshot artifacts。

   ```sh
   mvn -f models/jdk/pom.xml clean install
   mvn -f models/jdk8/pom.xml clean install
   mvn -f plugins/pom.xml clean install
   mvn clean verify
   ```

3. Release 时将两个 model POM project version 与 `models/jdk8/pom.xml` 的公共 engine dependency 切换为选定 Stable versions，再安装。

   ```sh
   mvn -f models/jdk/pom.xml -Prelease clean install
   mvn -f models/jdk8/pom.xml -Prelease clean install
   ```

4. 将 Plugin `revision` 切换为 Stable version，并安装 Plugin 与 repository ZIP。

   ```sh
   mvn -f plugins/pom.xml versions:set-property \
     -Dproperty=revision -DnewVersion=3.1.0 \
     -DgenerateBackupPoms=false
   mvn -f plugins/pom.xml -Prelease clean install
   ```

5. 将 Analyzer `revision`、`artifact-path-plugin.version` 与 `jdk8-models.version` 切换为对应 Stable versions。

   ```sh
   mvn versions:set-property \
     -Dproperty=revision -DnewVersion=3.0.0 \
     -DgenerateBackupPoms=false
   mvn versions:set-property \
     -Dproperty=artifact-path-plugin.version \
     -DnewVersion=3.1.0 \
     -DgenerateBackupPoms=false
   mvn versions:set-property \
     -Dproperty=jdk8-models.version -DnewVersion=0.1.0 \
     -DgenerateBackupPoms=false
   ```

6. 执行 release quality gate，并检查 packaged version。

   ```sh
   mvn -Prelease clean verify
   java -jar target/dependency-analyzer.jar --version
   ```

7. 检查 JAR 中恰有两个 embedded repository ZIP，并包含 JDK model façade/catalog。

   ```sh
   jar tf target/dependency-analyzer.jar | \
     grep '^maven/plugin-repositories/.*-repository.zip$'
   ```

8. Release 验证通过后创建一个 commit 与四个 annotated tags；以下版本仅为示例，必须替换为实际 Stable versions。

   ```sh
   git add -A
   git commit -m "build(release): publish dependency analyzer artifacts"
   git tag -a jdk-models-v0.1.0 -m "JDK Models 0.1.0"
   git tag -a jdk8-models-v0.1.0 -m "JDK 8 Models 0.1.0"
   git tag -a dependency-evidence-plugin-v3.1.0 \
     -m "Dependency Evidence Plugin 3.1.0"
   git tag -a analyzer-v3.0.0 -m "Dependency Analyzer 3.0.0"
   ```

## Success Criteria

- 四个 artifacts 只使用 Stable SemVer；所有 Snapshot dependency 已移除。
- Plugin class major 不超过 `52`，并生成 JAR 与 attached repository ZIP。
- Model flattened POM 不保留 unresolved parent/property；JDK 8 model 只引用 Stable public engine。
- Analyzer Surefire/Failsafe 无 skip；`--version` 与 root Stable `revision` 一致。
- Analyzer JAR 恰好包含 Maven Dependency Plugin 与 Dependency Evidence Plugin repository ZIP，以及公共 engine、JDK 8 façade/catalog。
- 四个 annotated tags 指向同一 release commit；Stable POM 保留在该 commit。

## Failure Entry Points

- `release revision must be stable`：仍存在 `-SNAPSHOT`；修正对应唯一 version source。
- `requireReleaseDeps` failure：按公共 model、JDK 8 model、Plugin、Analyzer 顺序切换并安装 Stable coordinate。
- Attached ZIP 无法解析：重新执行 Plugin `-Prelease clean install`，检查 local repository classifier `repository`。
- JDK 8 contract failure：修正 `test.jdk8.home`；不得通过跳过 tests 发布。
- `--version` 不一致：检查 root `revision` 与 filtered build metadata。
- Tag 已存在：检查既有 release record；禁止移动或覆盖 published tag。
