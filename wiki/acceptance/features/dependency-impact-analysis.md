---
title: "Dependency Impact Analysis Acceptance"
type: acceptance
---

# Acceptance: Dependency Impact Analysis

本页验收 [Dependency Impact Analysis](../../features/dependency-impact-analysis.md)。

## Functional

1. 场景：比较两个 local ref
   - Given：baseline 与 target 均可解析为 local commit，入口 POM、Maven 与完整 JDK 8 可用。
   - When：用户执行 `impact` 并指定输出文件。
   - Then：
     - Analyzer 不切换用户 current checkout。
     - 输出包含每个相关 Module 的 dependency changes、ChangePoint、affected path 或明确的无路径 / 不确定状态。

2. 场景：比较 baseline 与 current workspace
   - Given：baseline 可解析且未指定 target，current workspace 含未提交修改。
   - When：用户执行 `impact`。
   - Then：
     - Target 分析包含 current workspace 的未提交修改。
     - Report 标识 target workspace 状态。

## Failure

1. 场景：Preflight 阻断分析
   - Given：JDK、Maven、入口 POM、Git ref 或 selector 不满足 command contract。
   - When：用户执行 `impact`。
   - Then：
     - Command 返回 exit code `1`。
     - 既有输出 Report 不被替换。

## Non-Functional

- [ ] 当 baseline、target、options 与 dependency artifact 完全相同时，并行度为 `1` 与大于 `1` 的两次运行产生相同顺序的 Module、ChangePoint 和代表路径 identity。
