---
title: "Coordinate JAR Repository"
type: implementation
---

# Implementation: Coordinate JAR Repository

## Background

同一 dependency artifact 在 baseline、target、Module 和 Maven repository 中可能拥有不同 physical path。Impact domain 需要以 logical coordinate 表达身份，同时确保 `JarFile` handle 的生命周期不会跨并行阶段泄漏。

## Overview

Coordinate JAR Repository 从 [Structured Dependency Evidence Collection](dependency-evidence-collection.md) 的 resolver binding 建立 immutable coordinate index，并通过 tracked `JarLease` 暴露短生命周期 handle。它服务 bytecode diff 与 Call Graph module construction，不把 physical path 写入 domain key 或 Report contract。

## Core Flow

1. 读取每个 side 的 resolver manifest，并验证 coordinate、owner、path boundary 与文件存在性。
2. 对同一 logical coordinate 的候选 binding 执行固定排序，选择 canonical physical artifact。
3. Consumer 按 coordinate 请求 lease；repository 打开 handle 并登记 owner。
4. Lease close 后注销并关闭 handle；repository close 会拒绝新 lease 并回收剩余 tracked handle。
5. Missing、ambiguous 或 owner 不一致的 binding 在进入 bytecode / Call Graph 前形成明确 failure。

## Key Mechanisms

- Repository contract 只接受 logical artifact coordinate，不接受 consumer 直接传入 path。
- Canonical binding 选择与 filesystem iteration 顺序无关；同一 manifest 输入产生相同结果。
- Lease 明确区分 repository identity 与 physical handle ownership，避免缓存一个长生命周期 `JarFile` 被并发关闭。
- Report 只展示 coordinate 与 resolution source；physical artifact location 限于本地诊断边界。

## Design Decisions

- None.

## Acceptance

完整验收条件见 [Coordinate JAR Repository Acceptance](../acceptance/implementation/jar-locator.md)。
