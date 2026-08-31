---
title: "Maven Runtime"
type: implementation
---

# Implementation: Maven Runtime

## Background

Analyzer 必须在用户 Maven 与内嵌 Maven、用户 settings 与 command overlay、内嵌 Plugin repository 与 local repository 之间保持同一解析语义。只根据 executable 名称或复制 loose Plugin JAR 无法稳定复现实际 Maven JVM 与 transitive Plugin dependency。

## Overview

Maven Runtime 为每个 command 解析可执行文件、版本、Java home、settings overlay 和两个 repository ZIP，并产生 immutable runtime descriptor。所有 Maven stage 共用该 descriptor，[Structured Dependency Evidence Collection](dependency-evidence-collection.md) 不自行重建 runtime。

## Core Flow

1. 解析用户指定或内嵌 Maven executable，并探测实际 Maven version、Java home 与 global / user settings。
2. 将 Maven Dependency Plugin repository ZIP 与 Dependency Evidence Plugin repository ZIP 解压到 owner-scoped cache。
3. 生成只追加 command repository 的 settings overlay，保留用户 mirror、server、proxy、profile 和 active profile 语义。
4. Scope resolver 与 Maven subprocess 共用 runtime descriptor、profile token 和 property token。
5. Command close 时只回收 owned transient cache；可复用的完整 runtime cache 通过 version / integrity identity 管理。

## Key Mechanisms

- Plugin 以 repository ZIP 交付，包含 transitive artifact 和 POM；Analyzer 不依赖网络补齐内嵌 Plugin。
- Overlay 不复制 credential 到日志或 Report，也不以手写 settings 替换用户 settings。
- Snapshot Dependency Evidence Plugin invocation可使用 `-U` 刷新小型 cache；Stable repository 以 artifact version 隔离。
- Maven executable 与所有外部 command 统一遵守 [Process Command Resolution](../rules/process-command-resolution.md)。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Maven Runtime Acceptance Criteria](../ac/implementation/maven-runtime.md)。
