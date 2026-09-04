---
name: "Evidence Ingestion"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/containers/dependency-analyzer-evidence-plugin]]"
    description: "消费按模块发布的结构化证据。"
    mechanism: "原子 JSON 文件与 owner marker"
  - target: "[[c4/components/dependency-analyzer-cli-artifact-repository]]"
    description: "提供已校验的逻辑坐标与物理 artifact 绑定。"
---

## Overview

Evidence Ingestion 验证 Plugin 的原子 JSON publication，并将 Maven session 中的依赖事实转换为 Impact 或 Tree 的领域输入。

## Responsibilities

- 校验 schema、Module owner、coordinate、path boundary 与 resolved artifact existence。
- 为 Impact 保留 selected graph 与 artifact binding；为 Tree 保留 occurrence、scope、directness 和 resolution source。
- 隔离每个 Module 的 evidence failure，禁止半成品冒充完整结果。

## Interfaces

- Impact evidence：输出 logical dependency tree 与 resolved artifact binding。
- Classpath evidence：输出 occurrence-aware graph、classpath order 与 Module ownership；缺少 winner 或 owner mismatch 时失败。

## Boundaries

该 Component 只规范化外部 evidence；不运行 Maven、不执行 dependency diff、Call Graph 或 class conflict classification，也不从 Console 文本补猜缺失事实。
