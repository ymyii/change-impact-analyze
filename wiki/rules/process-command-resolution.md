---
title: "Process Command Resolution"
type: rule
relations:
  - path: "wiki/features/git-workspace-management.md"
    desc: "Git 命令执行适用跨平台命令解析规则"
  - path: "wiki/features/maven-build-runner.md"
    desc: "Maven 编译命令执行适用跨平台命令解析规则"
  - path: "wiki/features/dependency-tree-extraction.md"
    desc: "Maven dependency plugin 命令执行适用跨平台命令解析规则"
  - path: "wiki/features/maven-runtime.md"
    desc: "Maven runtime process 适用跨平台命令解析规则"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree Git/Maven process 适用跨平台命令解析规则"
code_refs:
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/util/CommandResolver.java"
    desc: "跨平台命令解析 canonical implementation"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/workspace/GitCommandRunner.java"
    desc: "Git 命令执行入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/build/BuildRunner.java"
    desc: "Maven compile 命令执行入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java"
    desc: "Maven dependency plugin 命令执行入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/runtime/MavenExecutor.java"
    desc: "共享 Maven process 执行入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/GitSnapshotProvider.java"
    desc: "tree Git process 执行入口"
  - path: "analyzer/src/test/java/io/github/dependencyanalysis/util/CommandResolverTest.java"
    desc: "跨平台命令解析规则测试"
---

# Rule: Process Command Resolution

## Summary

所有通过 `ProcessBuilder` 执行的外部工具命令必须先经过 `CommandResolver.resolve()`，以保证 Windows 上能够通过 `cmd.exe /c` 解析 `.cmd` 或 `.exe`，同时保持 Linux/macOS 命令列表不变。

## Rules

- 新增或修改 Git、Maven、Maven plugin 等外部命令执行逻辑时，命令 token list 必须传入 `CommandResolver.resolve()` 后再交给 `ProcessBuilder`。
- 不要在 feature 实现中分散硬编码 Windows `cmd.exe /c` 包裹逻辑；平台差异集中在 `CommandResolver`。
- Linux/macOS 上 `CommandResolver.resolve()` 必须原样返回原命令列表，保持绝对 executable path 和 Git command token 不变。
- Windows 上 `CommandResolver.resolve()` 必须返回 `cmd.exe /c <original tokens...>`，使 `cmd.exe` 负责 `PATHEXT` 解析。
- 未来增加新的外部命令执行入口时，应补充覆盖 Windows 和非 Windows 行为的测试。

## Applies To

- 使用 `ProcessBuilder` 启动外部命令的 production code。
- Git workspace 准备、Maven compile、Maven dependency tree 提取等本地工具调用。
- 需要处理 `.cmd` wrapper 或平台 executable 解析的代码路径。

## Verification

```sh
mvn test -Dtest=CommandResolverTest
```

## Reference Files

- `analyzer/src/main/java/io/github/dependencyanalysis/util/CommandResolver.java` - 规则来源和 canonical implementation。
- `analyzer/src/main/java/io/github/dependencyanalysis/workspace/GitCommandRunner.java` - Git 命令调用方。
- `analyzer/src/main/java/io/github/dependencyanalysis/build/BuildRunner.java` - Maven compile 调用方。
- `analyzer/src/main/java/io/github/dependencyanalysis/dependency/DependencyAnalyzer.java` - Maven dependency plugin 调用方。
- `analyzer/src/test/java/io/github/dependencyanalysis/util/CommandResolverTest.java` - 平台解析行为测试入口。

## Non-Goals

- 不在本规则中枚举所有遵守规则的测试 fixture 或临时命令。
- 不规定外部命令的业务参数；各 Feature 页拥有自己的命令语义。
- 不替代错误处理、日志收集或诊断事件规范。
