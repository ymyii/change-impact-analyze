---
title: "Maven Runtime"
type: feature
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "产品 runtime、独立 reactor 和 config dir 约定"
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "两个 subcommand 共享 runtime descriptor"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "Runtime executable、version 与 Java home preflight"
  - path: "wiki/features/dependency-evidence-collection.md"
    desc: "Schema v3 structured evidence goal"
  - path: "wiki/rules/process-command-resolution.md"
    desc: "Maven process 的跨平台解析约束"
  - path: "wiki/rules/release-versioning.md"
    desc: "Plugin coordinate、Snapshot 与 release version 约束"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "两个 repository ZIP 的构建与验证命令"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "Plugin 与 Analyzer 的 release 顺序"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenRuntimeManager.java"
    desc: "Config dir、lock、staging、必要文件和 Maven extraction"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenRuntimeDescriptor.java"
    desc: "两个 subcommand 共享的只读 Maven runtime 契约"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntimeManager.java"
    desc: "两个 repository ZIP、独立 cache 与 settings overlay"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntime.java"
    desc: "Plugin goals、repository list 与 command-scoped cleanup"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/Jdk8RuntimeProvider.java"
    desc: "impact target JDK 8 provider"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Java 8 Plugin 与 attached repository ZIP packaging"
  - path: "plugins/artifact-path-resolver/src/assembly/repository.xml"
    desc: "Dependency Evidence Plugin Maven repository layout"
  - path: "analyzer/src/main/resources/maven/apache-maven-3.6.3-bin.zip"
    desc: "未修改的 Apache Maven 3.6.3 binary distribution"
  - path: "analyzer/src/main/resources/maven/plugin-repositories/maven-dependency-plugin-3.6.1-repository.zip"
    desc: "Maven Dependency Plugin 3.6.1 与完整传递依赖 repository"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/runtime/MavenDependencyPluginRuntimeManagerTest.java"
    desc: "双 repository、cache、并发、blocked mirror 与 cleanup tests"
  - path: "analyzer/src/integration-test/java/io/github/dependencyanalysis/cli/PackagedJarCliIT.java"
    desc: "最终 JAR repository resource 与 packaged execution contract"
---

# Feature: Maven Runtime

## Summary

Maven Runtime 为 `impact` 和 `tree` 提供 `3.6.3 <= Maven version < 4.0.0` executable。未指定 `--maven` 时从 Analyzer JAR 离线准备 Apache Maven 3.6.3。工具控制的两个 Plugin 分别来自独立 Maven repository ZIP：Maven Dependency Plugin `3.6.1` 服务 `tree`，Dependency Evidence Plugin `3.0.0` 服务 `impact`。

## Design Decisions

- Maven distribution、Maven Dependency Plugin repository、Dependency Evidence Plugin repository 以 component/version 作为 cache identity，不使用内容 checksum 或 runtime fingerprint。
- 两个 Plugin repository 保持独立，Dependency Evidence Plugin Snapshot 刷新不会重复解压约 16 MB 的 Maven Dependency Plugin repository。
- Stable repository 由 completion marker 和必要 JAR/POM 判定可复用；Snapshot repository 每次 command 解压到独立 leaf，避免并发 command 相互覆盖，并在 runtime close 时清理。
- 解压使用 file lock、staging、Zip Slip 防护和 atomic publish；cleanup 只作用于工具拥有的 leaf。
- Global settings overlay 仅在 command 存续期间存在，一个 active profile 注册两个 file `pluginRepository`，并将两个 repository ID 从 wildcard mirror 排除。
- Dependency Evidence Plugin repository ZIP 只包含 Maven layout 下的 JAR 与 consumer POM；JAR class major `<=52`，Jackson Core relocate 到 Plugin private package。Consumer POM 引入 `maven-dependency-tree:3.2.1`，并排除 Maven 已导出的 Resolver API。

## Actors / Entrypoints

- `impact`/`tree` preflight 调用 `MavenRuntimeManager.prepare()` 和 `MavenDependencyPluginRuntimeManager.prepare()`。
- 用户通过 `--maven`、`--java-home`、`--config-dir` 和 `--dependency-plugin-version` 控制允许覆盖的 runtime 行为。
- `PreflightContext` 拥有 `MavenDependencyPluginRuntime`，command 结束时删除 temporary settings 与 Snapshot leaf。

## Behavior Contract

- 默认 config dir 为 `${user.home}/.dependency-analyzer`。
- 用户 executable 优先，descriptor source 为 `USER_CONFIGURED`；内嵌 runtime source 为 `EMBEDDED`。
- Stable Maven distribution 位于 `runtime/apache-maven/3.6.3/content`；必要 launcher、`conf/settings.xml`、boot JAR 与 marker 都存在时复用。
- Stable Plugin repository 位于 `runtime/plugin-repositories/<component>/<version>/content`；Snapshot 位于 `.../<version>/runs/<uuid>`。
- Plugin runtime 按顺序暴露 Dependency Plugin repository 与 Dependency Evidence Plugin repository；fully-qualified goals 使用 build metadata 中的 Plugin version。
- Dependency Evidence Plugin 为 Snapshot 时，settings 启用 Snapshot、`updatePolicy=always` 并追加 `-U`；Stable 时只启用 release。
- 用户 `-gs` 内容被合并；独立 `-s`、mirror、proxy、server、local repository 和普通 Maven arguments 保留。Settings 内容不输出到 Console。
- Preflight evidence 只报告 Dependency Plugin version、Dependency Evidence Plugin version 与 repository 数量；Mojo evidence 报告 `implementation=dependency-evidence-v3`、version 和 command cache output path。
- `--java-home` 设置 Maven subprocess `JAVA_HOME`；`impact` 只接受完整 JDK 8，`tree` 只要求该 JDK 与 Maven/project 兼容。

## Core Flow

1. 选择用户 Maven executable，或准备 versioned Apache Maven distribution cache。
2. 分别准备 Maven Dependency Plugin repository ZIP 与 Dependency Evidence Plugin repository ZIP。
3. 检查 marker 与必要文件；需要重建时在 staging 解压并 atomic publish。
4. 合并用户 global settings，注册两个 file `pluginRepository`，必要时启用 Snapshot refresh。
5. `tree` 执行 `dependency:tree`；`impact` 执行 `collect-dependency-evidence`。
6. Command 结束时清理 temporary settings 与 Snapshot repository leaf；Stable cache 保留复用。

## Acceptance Criteria

### Functional

- Given config dir 为空；When preparation；Then 不联网即可得到可运行 Maven 3.6.3。
- Given 两个 Plugin cache 为空且 wildcard mirror 指向不可用地址；When 执行 structured evidence goal；Then Plugin 及 `maven-dependency-tree` runtime 均从内嵌 file repository 解析并成功运行。
- Given Stable repository marker 或必要 JAR/POM 缺失；When 再次 preparation；Then 只重建对应 component/version leaf。
- Given Stable repository 文件内容变化但必要文件仍存在；When 再次 preparation；Then 不执行项目自有内容 fingerprint 检查。
- Given Dependency Evidence Plugin 使用 Snapshot；When 连续 preparation；Then Dependency Plugin cache path 保持不变，Evidence Plugin 使用新 leaf 并传入 `-U`。
- Given并发 preparation；When四个 command 同时准备；Then Stable cache 唯一完整，Snapshot leaf 彼此独立。
- Given command 关闭；When runtime cleanup；Then temporary settings 和 Snapshot leaf 删除，Stable cache 保留。
- Given `os.name` 为 Windows；When 选择内嵌 launcher；Then 返回 `mvn.cmd`；Linux/macOS 返回 `mvn`。

### Non-Functional

- [ ] Analyzer JAR 的 `maven/plugin-repositories/` 下恰有两个 `*-repository.zip`。
- [ ] Dependency Evidence Plugin repository ZIP 只有当前 version 的 JAR 与 POM，不包含项目生成的 checksum sidecar。
- [ ] Dependency Evidence Plugin class major `<=52`，不 shade Maven/Resolver implementation class，Jackson package 已 relocate。
- [ ] 所有解压 path 先 normalize 并检查 destination boundary。
- [ ] 并发 publish 使用 JVM guard、cross-process file lock、staging 与 atomic move。

## Edge Cases

- Resource 缺失、ZIP entry 越出 staging 或必要文件缺失时 preparation 失败，不发布 completion marker。
- Content corruption 不由项目自有 checksum 自动发现；缺少 marker/必要文件会触发重建，完整内容验证交给 Maven artifact resolution 与构建测试。
- Snapshot runtime 未正常关闭时可能留下 run leaf；下一次 preparation 不复用该 leaf，也不影响 Stable cache。
- Contract tests 覆盖 Windows launcher 选择，但不等同于真实 Windows 端到端验证。

## Implementation Boundaries

- 内嵌 Plugin repository 仅保证工具控制的两个 Plugin 及其运行依赖；project dependency 仍由用户 Maven session 的 settings/local repository 管理。
- Dependency Evidence Plugin 直接读取当前 Maven session 的 resolved/raw graph，仅对 selected external artifact 做 non-transitive resolution；不读取 GraphML。
- Runtime manager 不下载 Maven distribution，不读取 PATH，不选择 repository Maven Wrapper。
- Maven Dependency Plugin repository ZIP 内原有第三方 `.sha1` 属于上游 repository 内容；Analyzer 不新增或验证这些 sidecar。
