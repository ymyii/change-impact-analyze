---
name: "Call Graph Engine"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/components/dependency-analyzer-cli-artifact-repository]]"
    description: "读取构图范围内各模块的规范依赖实现。"
  - target: "[[c4/components/dependency-analyzer-cli-jdk-method-models]]"
    description: "安装所选 JDK model 以支持上下文敏感构图。"
---

## Overview

Call Graph Engine 从不可变的 Module 输入按所选算法构建 Call Graph，并冻结 topology、metadata、依赖方法体边界与 coverage limitation。

## Responsibilities

- 建立 class hierarchy、canonical class ownership、entrypoint set 与 dependency body boundary。
- 隔离正式 Class Hierarchy Analysis（CHA，类层次分析）与实验性 `k-obj` strategy。
- 在 graph build 完成后提供只读 snapshot，并把 timeout 转换为 handled Module failure。

## Technology

- WALA 1.8.0：承载 Class Hierarchy Analysis（CHA，类层次分析）与实验性 `k-obj` 构图。

## Interfaces

- `ModuleCallGraphInput`：提供 Module output、classpath、entrypoint、body policy、algorithm 与 timeout。
- Graph result：提供 immutable topology、stats、typed limitation、boundary finding 与 algorithm metadata；不接受下游修改。

## State and Data

Call Graph session 拥有 WALA hierarchy 与 graph resource；session close 后只保留冻结结果。Class ownership 对每个 binary class 只选择一个 canonical winner。

## Boundaries

该 Component 只提供算法中立图结果；不依赖 Impact 或 Report package，不绑定 ChangePoint，也不执行业务路径 classification。
