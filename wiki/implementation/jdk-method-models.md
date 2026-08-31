---
title: "JDK Method Models"
type: implementation
---

# Implementation: JDK Method Models

## Background

实验性 context-sensitive Call Graph 无法仅依靠 JDK library bytecode可靠恢复 callback、serialization 和 native boundary 的调用语义。Model 又必须与被分析 JDK version 对齐，不能由 Analyzer 隐式使用 host JDK 17 的实现替代 JDK 8 contract。

## Overview

公共 JDK model engine 定义 catalog、Synthetic IR、installation 和 metadata contract；JDK 8 artifact 提供版本精确的 model catalog。正式 CHA 固定选择 `none`，实验性 `k-obj` 默认选择 `jdk8`，显式安装失败不 fallback。

## Core Flow

1. CLI 将 command-wide algorithm 与 `--jdk-model` 解析为 `none` 或 `jdk8`，非法组合在 Preflight 前失败。
2. `k-obj` 构图前从 Analyzer uber JAR 加载 JDK 8 model façade、公共 engine 与 catalog。
3. Installer 针对当前 JDK 8 class hierarchy 验证 owner、descriptor、static contract、callback target 和 native summary conflict。
4. 合法 definition 生成 Synthetic IR，并按 fixed-point 顺序安装到 WALA selector / interpreter boundary。
5. Installation metadata 记录 catalog、available、unavailable 和 hit count，随 Module result 进入 Diagnostic 与 Report。

## Key Mechanisms

- 公共 engine 与 JDK 8 catalog 是独立 reactor 和 artifact；Analyzer 不拥有 catalog 内容。
- CHA 跳过安装，即使用户未显式设置 model；`cha + jdk8` 是 argument error。
- `k-obj` 的 missing catalog、incomplete installation 或 Synthetic IR failure 是 Module Call Graph failure，不转成 limitation，也不自动改用 `none`。
- JDK 8 exact contract 以完整 JDK 8 `rt.jar` 和固定 catalog 验证，host JDK 17 只用于构建工具链。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [JDK Method Models Acceptance Criteria](../ac/implementation/jdk-method-models.md)。
