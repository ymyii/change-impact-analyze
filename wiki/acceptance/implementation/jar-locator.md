---
title: "Coordinate JAR Repository Acceptance"
type: acceptance
---

# Acceptance: Coordinate JAR Repository

本页验收 [Coordinate JAR Repository](../../implementation/jar-locator.md)。

## Functional

1. 场景：按 coordinate 打开 JAR
   - Given：resolver manifest 为 logical coordinate 提供一个有效 canonical binding。
   - When：consumer 请求 lease。
   - Then：
     - Lease 打开 canonical physical JAR。
     - Domain-facing identity 仍为 logical coordinate，不包含 path。

2. 场景：关闭 lease
   - Given：repository 正在跟踪一个 open lease。
   - When：lease 关闭。
   - Then：
     - Physical handle 被关闭并从 tracker 移除。
     - 其他 lease 保持可用。

## Failure

1. 场景：binding ambiguous
   - Given：同一 coordinate 的候选无法通过 canonical ordering 形成唯一合法 binding。
   - When：repository 建立 index。
   - Then：
     - 初始化失败并报告 coordinate。
     - Consumer 不获得任意候选 path。

## Non-Functional

- [ ] 当 manifest 内容相同但记录遍历顺序不同时，canonical coordinate-to-path 选择完全相同。
