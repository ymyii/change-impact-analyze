---
name: "Artifact Repository"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations: []
---

## Overview

Artifact Repository 将 Maven 逻辑坐标绑定到唯一选定的物理 JAR，并通过受跟踪的短期租约管理并发读取生命周期。

## Responsibilities

- 以稳定排序消解同一坐标的多个候选绑定。
- 对每个使用方暴露短生命周期 `JarLease`；repository 关闭后拒绝新租约。
- 阻止物理路径进入依赖变化点或 Report 的稳定身份。

## Interfaces

- `IJarRepository`：按逻辑坐标获取租约；JAR 缺失、候选不唯一或 owner 不一致时明确失败。
- `JarLease`：在 close 时释放并注销实际 `JarFile` handle。

## State and Data

Repository 只追踪当前分析会话中仍打开的 handle；唯一候选的选择不依赖 filesystem 遍历顺序。

## Boundaries

该 Component 负责已解析 artifact 的位置与 handle ownership；不执行 Maven resolution、不缓存跨 command JAR handle，也不决定 bytecode 或 Call Graph 语义。
