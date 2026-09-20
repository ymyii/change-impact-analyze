---
name: "JDK Models Build and Test"
type: runbook
---

## Purpose and Scope

本 Runbook 独立构建并验证 [JDK Method Models](../c4/components/dependency-analyzer-cli-jdk-method-models.md) 的公共 engine 与 JDK 8 catalog，再由 Analyzer gate 验证 façade、selection 和 shaded packaging。

## Prerequisites

- Maven command 使用 Java 17 JDK 与 Maven 3.x。
- 按 [Build Test and Package](build-test-and-package.md) 配置本机 `.env` 或传入 `-Dtest.jdk8.home=/absolute/path/to/jdk8`。
- 所有 command 从 repository root 执行。

## Procedure

1. 构建并安装公共 model engine。

   ```sh
   ./mvn-local -f models/jdk/pom.xml clean install
   ```

2. 构建并安装 JDK 8 model artifact。

   ```sh
   ./mvn-local -f models/jdk8/pom.xml clean install
   ```

3. 验证 Analyzer 对两个 artifacts 的集成与 packaging。

   ```sh
   ./mvn-local clean verify
   ```

4. 需要聚焦公共 catalog/API 时执行对应 tests。

   ```sh
   ./mvn-local -f models/jdk/pom.xml \
     -Dtest=JdkModelDefinitionTest,JdkModelCatalogTest,JdkModelsTest,JdkRuntimeCompatibilityTest \
     test
   ```

5. 需要聚焦 Synthetic IR template 时执行 template gate。

   ```sh
   ./mvn-local -f models/jdk/pom.xml -Dtest=JdkSummaryTemplateTest test
   ```

6. 公共 engine 已安装后，执行 JDK 8 fixed-point acceptance。

   ```sh
   ./mvn-local -f models/jdk8/pom.xml \
     -Dtest=Jdk8ModelFixedPointAcceptanceTest \
     test
   ```

7. 人工检查两个普通 JAR 的职责分离。

   ```sh
   jar tf models/jdk/target/dependency-analyzer-jdk-models-0.1.0-SNAPSHOT.jar
   jar tf models/jdk8/target/dependency-analyzer-jdk8-models-0.1.0-SNAPSHOT.jar
   ```

## Success Criteria

- 两个 module 的 Checkstyle、Surefire 与 Failsafe 均通过且无 skip。
- JDK 8 metadata 为 `modelId=jdk8`、catalog `384`、available `384`、unavailable `0`。
- Required callback、serialization hook 与 downstream application method 在 fixed point 中可达。
- 公共 JAR 包含 engine API、session、metadata 与 templates，不包含 production JDK 8 catalog。
- JDK 8 JAR 包含 `Jdk8Models` 与 `jdk8-models.tsv`，不复制公共 engine class。
- 两个 JAR 不包含 Analyzer 或 `com/ibm/wala/` class；flattened consumer POM 不依赖 root parent。
- Analyzer JAR 包含公共 engine、JDK 8 façade/catalog，且 `impact --help` 包含 `--jdk-model`。

## Failure Entry Points

- JDK 8 path failure：修正 `-Dtest.jdk8.home`，确认路径来自完整 Java 8 JDK。
- 无法解析公共 model artifact：先安装 `models/jdk`，并确认 `models/jdk8/pom.xml` dependency version 一致。
- Unresolved parent/property：检查 `target/flattened-pom.xml` 与 Flatten Maven Plugin execution。
- Catalog unavailable 或 duplicate target：检查 resource absolute path 与 owner/name/descriptor uniqueness。
- Static contract、callback target 或 WALA native summary conflict：以 JDK 8 public API 校正 definition，不使用 no-op 或 selector order 掩盖。
- Fixed-point failure：检查 `models/jdk8/target/surefire-reports/`；Packaging failure 检查对应 `failsafe-reports/`。
