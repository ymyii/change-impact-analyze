---
title: "Maven Runtime"
type: feature
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "产品 runtime 和 config dir 约定"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "两个 subcommand 共享 runtime descriptor"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "Runtime executable/version/Java home preflight"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "GraphML 驱动的 Artifact Path combined goals"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Maven process 的跨平台解析约束"
  - path: "wiki/rules/release-versioning.md"
    desc: "不可复写 Plugin GAV 与 source fingerprint gate"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "内嵌 Plugin binary inspection 与 packaged smoke"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenRuntimeManager.java"
    desc: "Config dir、SHA-512、lock、staging 和 extraction 实现"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenRuntimeDescriptor.java"
    desc: "两个 subcommand 共享的只读 runtime 契约"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/Jdk8RuntimeProvider.java"
    desc: "impact target JDK 8 provider"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/JavaRuntimeProbe.java"
    desc: "JDK executable/version/boot/ext probe"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenExecutor.java"
    desc: "Maven process token 和 JAVA_HOME 执行入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntimeManager.java"
    desc: "Dependency Plugin/Artifact Path Plugin repository 准备、combined fingerprint 和 settings overlay"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "Tree collection 使用的 plugin goal、arguments 和 cache descriptor"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactPreflightService.java"
    desc: "内嵌 Plugin version、SHA-512 和 runtime fingerprint evidence"
  - path: "analyzer/src/main/resources/maven/apache-maven-3.6.3-bin.zip"
    desc: "未修改的 Apache Maven 3.6.3 binary distribution"
  - path: "analyzer/src/main/resources/maven/apache-maven-3.6.3-bin.zip.sha512"
    desc: "官方 SHA-512"
  - path: "analyzer/src/main/resources/maven/LICENSE"
    desc: "Apache Maven distribution LICENSE"
  - path: "analyzer/src/main/resources/maven/NOTICE"
    desc: "Apache Maven distribution NOTICE"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/runtime/MavenRuntimeManagerTest.java"
    desc: "Maven archive launcher 与 platform selection contract tests"
  - path: "analyzer/src/main/resources/maven/dependency-plugin/maven-dependency-plugin-3.6.1-repository.zip"
    desc: "Maven Dependency Plugin 3.6.1 与完整传递依赖 repository"
  - path: "analyzer/src/main/resources/maven/dependency-plugin/maven-dependency-plugin-3.6.1-repository.zip.sha512"
    desc: "Dependency Plugin repository SHA-512"
  - path: "analyzer/src/main/resources/maven/dependency-plugin/DEPENDENCIES"
    desc: "Dependency Plugin repository third-party attribution"
  - path: "build-support/artifact-path-plugin-consumer.pom.template"
    desc: "构建时按当前 Plugin version 生成的 consumer POM template"
  - path: "build-support/version-contract.properties"
    desc: "Plugin release version、fingerprint 与 Analyzer distribution version"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Java 8 Plugin packaging、Maven 3.6.3 API baseline 与 Jackson relocation"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/runtime/ArtifactPathPluginPackagingTest.java"
    desc: "Java 8 class major、shading 与 implementation-class boundary"
---

# Feature: Maven Runtime

## Summary

Maven Runtime 在不隐式使用 PATH 或 Maven Wrapper 的前提下，为两个 subcommand 提供 `3.6.3 <= Maven version < 4.0.0` executable。默认从 JAR resource 离线准备 Apache Maven 3.6.3；内置 file repository 同时提供 Maven Dependency Plugin `3.6.1` 与 Artifact Path Plugin `2.0.0`。

<!-- version-contract:start -->
- Analyzer release: `1.1.0`
- Artifact Path Plugin release: `2.0.0`
<!-- version-contract:end -->

## Design Decisions

- Maven 选择顺序固定为用户 executable、config dir 中已校验 runtime、JAR resource 解压。
- Runtime leaf 同时包含 Maven version 和 ZIP SHA-512；completion marker 与 executable 共同判定可复用。
- 解压前校验官方 SHA-512，并拒绝 normalized path 越出 staging directory 的 ZIP entry。
- Config dir cleanup 只允许删除 `runtime/apache-maven/<version>/<sha>` leaf，不整体删除 config dir。
- Maven 3.6.3 已 EOL，但 warning 只在实际 probe version 为 `3.6.3` 时展示；用户指定较新 Maven 时不会混入内嵌 runtime version/EOL 文案。
- Plugin cache 与 Maven distribution 使用相同的 checksum、leaf lock、staging 和 atomic move 安全模型，但两者是独立 resource。
- Plugin runtime fingerprint 是 Dependency Plugin repository ZIP、Artifact Path Plugin self-contained JAR 和 consumer POM 三者 SHA-512 的组合 hash；任一输入变化都不会复用旧 cache leaf。
- Artifact Path Plugin release GAV 不可复写；实现 contract 变化时升级 Plugin version，避免 Maven local repository 复用旧 release artifact。
- Artifact Path Plugin 编译为 Java 8 bytecode，以 Maven API/Core `3.6.3` 和 Resolver API `1.4.1` 为最低 compile baseline；Maven/Resolver dependencies 均为 `provided`，Jackson Core 被 relocate 到 Plugin private package。
- Plugin 只依赖 Maven 3.x public API，不携带 Resolver implementation、connector 或 transport class；Maven 4 明确不支持。
- 内嵌 Maven ZIP 同时携带 `mvn`、`mvn.cmd`、`mvnDebug` 和 `mvnDebug.cmd`；launcher selection 提取为可测试的 OS contract。

## Actors / Entrypoints

- `impact`/`tree` preflight 调用 `MavenRuntimeManager.prepare()`。
- 用户通过 `--maven`、`--java-home` 和 `--config-dir` 控制 runtime。

## Behavior Contract

- 默认 config dir 为 `${user.home}/.dependency-analyzer`，结构包含 Maven runtime、组合 Plugin repository 和 `locks/`。
- 用户 executable 优先级最高，descriptor source 为 `USER_CONFIGURED`；内嵌 runtime source 为 `EMBEDDED`。
- `*.maven-runtime` evidence 只报告 runtime source；`*.maven-version` 报告实际 `--version` probe 结果。Report 主视图展示实际 Maven version/source，executable path 只进入 `Technical details`。
- Plugin runtime preflight evidence 输出 Artifact Path Plugin version、内嵌 JAR 完整 SHA-512 和组合 runtime fingerprint；Mojo 输出 `implementation=graphml-v2`、实际加载 JAR path/SHA-512 与 GraphML filename。两个 Plugin SHA-512 必须一致。
- 显式 `--maven` 时 version probe、GraphML probe、target build、baseline/target dependency analysis 都使用同一 executable；未指定时才使用内嵌 3.6.3。
- 内嵌 preparation 使用 file lock、staging 和 atomic move，其他进程不会看到半解压 runtime。
- 损坏 runtime 只重建对应 leaf；未知 config/user 文件保持不变。
- POSIX/macOS 依靠 `.` 前缀隐藏默认目录；Windows best-effort 设置 DOS hidden attribute。
- Windows 选择 `bin/mvn.cmd`，Linux/macOS 选择 `bin/mvn`；最终 process token 仍统一经过 `CommandResolver.resolve()`，Windows 包装为 `cmd.exe /c`。
- `--java-home` 设置 Maven subprocess 的 `JAVA_HOME`；`impact` 同时用它编译用户代码并构建目标 JDK scope，且只接受完整 JDK 8。`tree` 只要求该 JDK 与 Maven/project 兼容。

## Core Flow

- 创建完整 config dir 和 locks directory。
- 若有 `--maven`，校验 absolute executable file 并返回 descriptor。
- 否则读取 SHA-512、获取 leaf lock、校验 marker/executable。
- 需要重建时，校验 resource checksum、Zip Slip 防护解压、写 completion marker、atomic replace。
- Preflight 以 `--version` probe 填充 descriptor version，并验证 `>=3.6.3 && <4.0.0`。
- Plugin runtime 解压 Dependency Plugin repository ZIP，再按固定 GAV 安装 Artifact Path Plugin JAR、consumer POM、SHA-512 和 Maven file repository 所需 checksum。
- Plugin runtime 为选中的同一个 Maven executable 生成 effective global settings，保留用户 `-gs`、`-s`、mirror、proxy 和 server，同时注入内置 file plugin repository；不输出 settings 中的敏感值。Overlay 用于 fully-qualified `tree` 与 `resolve-artifact-paths`，前一个 goal 的 Module-local GraphML filename 显式传给后一个 goal；不应用于 target `compile`。

## Acceptance Criteria

### Functional

- Given config dir 为空；When preparation；Then 不联网即可得到可运行 Maven 3.6.3。
- Given runtime marker/executable 损坏；When 再次 preparation；Then leaf 重建且未知文件保留。
- Given user executable；When preparation；Then 不解压内嵌 runtime。
- Given 本地无 Dependency Plugin cache 且网络不可用；When 执行默认 `tree`；Then 可从 JAR resource 准备并执行 `maven-dependency-plugin:3.6.1`。
- Given 本地无 Plugin cache、无公网且未启用 `-llr`；When 执行 `impact` dependency extraction；Then 同一 Maven session 可运行 `tree` 与 GraphML 驱动的 `resolve-artifact-paths`。
- Given plugin cache checksum/marker 损坏；When 再次 preparation；Then 只重建工具拥有的 cache leaf。
- Given Artifact Path Plugin JAR 或 consumer POM checksum 变化；When preparation；Then 使用新的 combined fingerprint leaf，不复用旧 runtime。
- Given Maven local repository 已存在旧 `1.0.0/1.0.1` release；When 执行 `impact`；Then Maven 使用 `2.0.0`，Console 的内嵌与实际加载 Plugin SHA-512 一致，`-X` 显示 `(f) dependencyGraphFileName`。
- Given `os.name` 为 Windows；When 选择内嵌 launcher；Then 返回 `mvn.cmd`；Given Linux/macOS，Then 返回 `mvn`。
- Given 检查内嵌 Maven ZIP；When 枚举 entries；Then 四个 Maven launcher 均存在。

### Non-Functional

- [ ] Maven distribution、Dependency Plugin repository ZIP，以及 Artifact Path Plugin JAR/POM/SHA-512 完整打入 uber JAR。
- [ ] Artifact Path Plugin class major `<=52`，JAR 不包含 Maven/Resolver implementation class，Jackson package 已 relocate。
- [ ] 并发 preparation 由 leaf lock 串行化。
- [ ] 所有 path 先 absolute normalize，再执行 allowlist cleanup。

## Edge Cases

- SHA-512 不匹配或 resource 缺失时 preparation 失败，不保留 completion marker。
- Mojo CodeSource 不是 regular JAR 或 SHA-512 evidence 计算失败时输出 warning，但不改变 artifact resolution 结果。
- ZIP entry 越出 staging directory 时拒绝解压。
- Unix executable bit 在 preparation 后设置；Windows 使用 `bin/mvn.cmd`。
- Contract tests 验证 Windows launcher 和 `cmd.exe /c` 命令构造，但不等同于真实 Windows 端到端验证。

## Implementation Boundaries

- Runtime distribution 不是 Maven local repository；内置 Plugin repository 仅保证工具控制的两个 Plugin 及其运行依赖，project dependency 仍由原 Maven session 的 settings/local repository 管理。
- Artifact Path Plugin 不执行 dependency collection；GraphML selected 非 `system` binding 使用当前 project effective repositories 和原 RepositorySystemSession 执行非传递 resolution，`system` binding 则从 effective `MavenProject` 验证并绑定 absolute `systemPath`，不发起 remote request。
- Runtime manager 不下载 distribution，不读取 PATH，不选择 repository Maven Wrapper。
