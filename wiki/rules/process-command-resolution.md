---
name: "Process Command Resolution"
type: rule
---

## Overview

本规则统一 [Workspace Scope](../c4/components/dependency-analyzer-cli-workspace-scope.md) 与 [Maven Runtime](../c4/components/dependency-analyzer-cli-maven-runtime.md) 启动 external process 的跨平台 executable resolution。

## Scope

- Production code 中通过 `ProcessBuilder` 启动 Git、Maven、Java 或 Plugin-related command 的路径。

## Rules

- **必须**在构造 `ProcessBuilder` 前将完整 command token list 传入 `CommandResolver.resolve()`。
- **必须**在 Linux/macOS 保持原 token list；在 Windows 返回 `cmd.exe /c <original tokens...>`，由 `PATHEXT` 解析 `.cmd` 或 `.exe`。
- **必须**为新增 external command 入口补充 Windows 与非 Windows behavior tests。
- **禁止**在 feature 或 Component 内分散硬编码 `cmd.exe /c`；平台差异由 `CommandResolver` 唯一拥有。

## Verification

- 执行 `mvn test -Dtest=CommandResolverTest`。
- 检索新增 `ProcessBuilder`，确认 command 已通过 canonical resolver。

## Non-Goals

- 本规则不规定 command 的业务参数、failure classification 或 Diagnostic content。
