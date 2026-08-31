---
title: "Call Graph Engine"
type: implementation
---

# Implementation: Call Graph Engine

## Background

Impact tracing 需要 per-Module Call Graph，但正式 Class Hierarchy Analysis（CHA，类层次分析）与实验性 `k-obj` 的 scope、model 和精度语义不同。构图机制必须与 impact domain 解耦，避免把 ChangePoint、业务 evidence 或 Report projection 反向带入 algorithm layer。

## Overview

Call Graph Engine 接收 immutable Module input，按 command-wide algorithm 产生 graph、metadata、typed limitation 与 boundary finding，并由 [Impact Tracing](impact-tracing.md) 解释为业务结果。CHA 固定不安装 JDK model；`k-obj` 可通过 [JDK Method Models](jdk-method-models.md) 安装 JDK 8 Synthetic IR。

## Core Flow

1. Adapter 将 Module classpath、entrypoint selector、dependency body policy 和 timeout 投影为 algorithm-neutral input。
2. Engine 建立 class hierarchy、ownership index 与 entrypoint set，并选择唯一 strategy。
3. CHA 对 JDK declared dispatch target 应用固定裁剪；`k-obj` 按 depth 与 protocol model 构建 context-sensitive graph。
4. Engine 冻结 topology、stats、coverage limitation、boundary finding 和 algorithm metadata 后关闭 session。
5. 下游只通过冻结结果执行 evidence collection 与 reverse query，不修改 graph topology。

## Key Mechanisms

- Algorithm identifier 只使用 `cha` 与 `k-obj`；大小写解析后不接受 alias 或自动 fallback。
- Entrypoint include 取并集、exclude 优先，并与实际 root selection 共用同一 immutable class index。
- Classpath ownership 只保留每个 binary class 的 canonical winner，防止同一 class 由多个 dependency body 重复进入 graph。
- Strongly Connected Components（SCC，强连通分量）utility 同时服务 topology 解释和代表 root 选择，排序规则不依赖 hash iteration。
- Timeout 从实际 WALA build 开始按 Module 计时；一个 Module 失败不取消其他 Module，但会形成 handled failed result。

## Design Decisions

- None.

## Acceptance Criteria

完整验收条件见 [Call Graph Engine Acceptance Criteria](../ac/implementation/call-graph-engine.md)。
