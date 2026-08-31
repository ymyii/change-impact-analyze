---
title: "Structured Dependency Evidence Collection Acceptance"
type: acceptance
---

# Acceptance: Structured Dependency Evidence Collection

本页验收 [Structured Dependency Evidence Collection](../../implementation/dependency-evidence-collection.md)。

## Functional

1. 场景：发布 Module evidence
   - Given：Maven session 成功解析 bounded Module dependency graph。
   - When：Plugin 完成 Module collection。
   - Then：
     - JSON 包含 schema version、Module owner 和 logical artifact binding。
     - 文件原子提交后才出现 complete marker。

2. 场景：采集 tree occurrence
   - Given：同一 artifact 通过多条 dependency path 出现。
   - When：执行 classpath evidence goal。
   - Then：
     - 每条 occurrence 保留独立 path identity。
     - Resolved selected artifact binding 不因 occurrence 去重而丢失。

## Failure

1. 场景：输出越过 command cache
   - Given：Module owner 或目标 path 不属于当前 command bounded cache。
   - When：Plugin 或 Analyzer 验证 publication path。
   - Then：
     - 当前 evidence collection 失败。
     - 越界文件不被当作有效输入。

## Non-Functional

- [ ] 当相同 Maven session 重复采集时，JSON 中 Module、coordinate 与 occurrence 的排序完全相同。
