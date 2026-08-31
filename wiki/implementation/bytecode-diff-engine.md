---
title: "Bytecode Diff Engine"
type: implementation
---

# Implementation: Bytecode Diff Engine

## Background

[Dependency Impact Analysis](../features/dependency-impact-analysis.md) 需要把 logical old / new artifact pair 转换为可追踪的 ChangePoint，同时抑制 debug-only 或语义等价的方法体变化。物理 JAR path 不稳定，不能进入变化身份或跨 Module 共享键。

## Overview

该机制对唯一 logical coordinate pair 执行 class、member、method body、JVM access 与 ServiceLoader resource diff。方法体固定先比较 Vineflower 文本，未命中时再比较 normalized Static Single Assignment（SSA，静态单赋值）；结果与 compact evidence 可跨 Module 共享，源码只进入当前 command cache。

## Core Flow

1. 通过 [Coordinate JAR Repository](jar-locator.md) 的 lease 打开 old / new JAR，并建立 class、member 和 service resource index。
2. 生成结构、descriptor、strict access narrowing 和 raw method body candidates；同一 logical pair 只执行一次。
3. 对全部 body candidate 比较反编译文本；`IDENTICAL` 立即抑制，只有 `DIFFERENT` 或 `UNKNOWN` 进入 pair-local SSA session。
4. Normalized SSA `MATCHED` 抑制候选，`DIFFERENT` 或 `UNKNOWN` fail-open 保留；非 body ChangePoint 不进入该双阶段过滤。
5. ServiceLoader registration 变化与 class removal 合并去重，最终结果按 stable key 排序并绑定 Module upgrade provenance。

## Key Mechanisms

- Stable method hash 编码 control-flow target、try / catch、bootstrap metadata 和 typed constant，同时忽略 line number、local variable table、stack map frame 等 debug-only metadata。
- Access transition 只允许 `PUBLIC`、`PROTECTED`、`PACKAGE_PRIVATE`、`PRIVATE` 之间的 strict narrowing；扩宽和其他 modifier 不属于该语义。
- Service configuration 会删除 comment 与空行、规范 binary name 并去重；provider class 与 registration 同时删除时只保留 class removal。
- Pair failure 与其他 pair 隔离；但 command cache fragment、complete marker 或 JSON 完整性失败属于 command-level failure，因为继续运行会发布不完整证据。
- `-vv` 只为实际执行且 SSA 结果为 `DIFFERENT` 或 `UNKNOWN` 的候选构造高成本审计文本，且不把源码写入 Report schema。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Bytecode Diff Engine Acceptance Criteria](../ac/implementation/bytecode-diff-engine.md)。
