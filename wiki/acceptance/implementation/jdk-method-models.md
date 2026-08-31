---
title: "JDK Method Models Acceptance"
type: acceptance
---

# Acceptance: JDK Method Models

本页验收 [JDK Method Models](../../implementation/jdk-method-models.md)。

## Functional

1. 场景：CHA 不安装 model
   - Given：algorithm 为 `cha` 且用户未显式指定 model。
   - When：解析 command-wide configuration。
   - Then：
     - Effective model 为 `none`。
     - Call Graph build 不加载 JDK model catalog。

2. 场景：`k-obj` 安装 JDK 8 model
   - Given：algorithm 为 `k-obj`，完整 JDK 8 hierarchy 与 catalog 可用。
   - When：执行 model installation。
   - Then：
     - Required callback、serialization 与 modeled target 可生成合法 Synthetic IR。
     - Metadata 记录 catalog、available、unavailable 与 hit count。

## Failure

1. 场景：model catalog 不完整
   - Given：required catalog resource、target 或 descriptor 无法解析。
   - When：`k-obj` 安装 model。
   - Then：
     - 当前 Module Call Graph 失败。
     - 系统不 fallback 到 `none`。

## Non-Functional

- [ ] 当 JDK 8 catalog gate 运行时，available count 等于 catalog count，unavailable count 等于 `0`。
