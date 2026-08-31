---
title: "Impact Dependency Diff Engine"
type: implementation
---

# Implementation: Impact Dependency Diff Engine

## Background

Impact pipeline 只需要识别每个 Module 的 resolved artifact 新增、移除与版本变化，并把 `VERSION_CHANGED` pair 交给 bytecode diff。Tree Diff 需要 occurrence、scope、directness 和 path identity，不能复用这种 flatten 语义。

## Overview

该机制比较 baseline 与 target 的 resolved Module dependency tree，生成稳定排序、不可变的 `DependencyChange`。它服务 [Dependency Impact Analysis](../features/dependency-impact-analysis.md)，不解析 Maven、不访问 JAR，也不承担 [Repository Dependency Tree Diff](../features/repository-dependency-tree-diff.md) 的双侧 occurrence 语义。

## Core Flow

1. 两侧 Module 以 artifact `diffKey()` 建立索引，并对 Module key 取 union。
2. 单侧存在的 Module 将该侧 flatten 后的 dependency 标记为 `ADDED` 或 `REMOVED`。
3. 双侧 Module 对 dependency artifact key 取 union；版本不同生成 `VERSION_CHANGED`，相同版本不生成变化。
4. Domain constructor 校验 ChangeType 与 old / new artifact 的空值组合。
5. 输出按 Module、ChangeType 和 artifact key 排序并冻结。

## Key Mechanisms

- Flatten 对同一 artifact 的多条传递路径只保留第一次出现；这适合 impact artifact pair 去重，但不适合 Tree Diff。
- `DependencyChange` 不保存 physical JAR path，后续由 [Coordinate JAR Repository](jar-locator.md) 解析 logical coordinate。
- 新增和移除 dependency 不进入 JAR pair diff；只有双方 artifact 都存在且 version 变化时才执行 bytecode comparison。
- Compile-time API risk 只基于 dependency scope，不推断实际调用路径。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Impact Dependency Diff Engine Acceptance Criteria](../ac/implementation/dependency-diff-engine.md)。
