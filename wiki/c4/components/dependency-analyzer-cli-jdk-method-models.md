---
name: "JDK Method Models"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations: []
---

## Overview

JDK Method Models 为 context-sensitive Call Graph 提供版本精确的 callback、serialization 与 native-boundary Synthetic IR，不以 host JDK 实现替代 target JDK contract。

## Responsibilities

- 由公共 model engine 定义 catalog、template、state、installation 与 metadata contract。
- 由 JDK 8 artifact 提供 exact catalog，并针对 target `rt.jar` 验证 owner、descriptor、static contract 与 callback target。
- 以 fixed-point 顺序安装 summary；显式安装失败时不 fallback。

## Technology

- Maven artifact `dependency-analyzer-jdk-models`：提供公共 model engine。
- Maven artifact `dependency-analyzer-jdk8-models`：提供 JDK 8 catalog。
- WALA Synthetic Intermediate Representation（IR，中间表示）：表达补充的 JDK method semantics。

## Interfaces

- Model façade：`none` 不安装模型；`jdk8` 返回 catalog metadata 与 install session。
- Installation contract：invalid definition、unavailable target 或 summary conflict 使 Module Call Graph 失败。

## State and Data

Catalog 是 immutable resource；installation metadata 保存 catalog、available、unavailable 与 hit counts，不保存 target JDK implementation state。

## Boundaries

该 Component 只补充 JDK method semantics；不选择 Call Graph algorithm，不允许 CHA 安装 model，也不包含 Analyzer orchestration 或 Report 逻辑。
