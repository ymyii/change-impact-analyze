---
title: "Repository Dependency Tree Diff"
type: feature
---

# Feature: Repository Dependency Tree Diff

## Background

依赖升级 review 需要区分 artifact 版本变化、dependency occurrence 路径变化和 Reactor 结构变化。将两侧依赖 flatten 为 artifact set 会丢失 scope、directness 与 chain identity，因此 `tree diff` 以双侧 repository snapshot 为用户目标。

## Overview

`tree diff` 比较一个 baseline local commit-ish 与显式 target commit-ish 或 current workspace，生成 occurrence-aware 的离线差异报告。它不执行远程 fetch；两侧 Reactor 或 Module 结构不一致时报告 `STRUCTURE_MISMATCH`，不把整个 Module 误判为 dependency 新增或删除。

## Usage

- Actor：review dependency tree 变化的开发者。
- Public entrypoint：`dependency-analyzer tree diff` CLI command。
- Prerequisites：baseline 可 peel 为 local commit，入口 POM 在所选 side 可用，Maven runtime 可用。
- Input：baseline、可选 target、project path、scope 和输出目录。
- Output：按 Reactor 增量发布的双侧 dependency diff 离线 Report。

```sh
java -jar /path/to/dependency-analyzer.jar tree diff \
  --path services/payment \
  --baseline main \
  --output build/payment-dependency-diff
```

## Core Flow

1. [Git Workspace Management](../implementation/git-workspace-management.md) 准备 baseline；显式 target 使用 detached workspace，省略 target 时直接读取 current workspace 并记录 dirty 状态。
2. 两侧独立解析相同入口路径和 bounded reactor scope；结构身份先于 dependency diff 比较。
3. [Structured Dependency Evidence Collection](../implementation/dependency-evidence-collection.md) 为两侧采集 occurrence 与 classpath evidence。
4. Diff domain 按 dependency identity 分类、按路径 identity 配对 chain，并保留 side、scope、directness 与 resolution source。
5. [Report Generator](../implementation/report-generator.md) 以独立 Tree Diff schema 增量发布；已完成 Reactor 不依赖其他 Reactor 才能浏览。

## Acceptance

完整验收条件见 [Repository Dependency Tree Diff Acceptance](../acceptance/features/repository-dependency-tree-diff.md)。
