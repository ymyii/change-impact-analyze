---
title: "Repository Dependency Tree Report"
type: feature
---

# Feature: Repository Dependency Tree Report

## Background

开发者需要从 repository 与 Module 视角理解 Maven 实际解析的 dependency occurrence、版本来源和 class conflict，而不仅是读取单个 `pom.xml` 的声明。`tree analyze` 将选定 Git snapshot 的 bounded reactor scope 转换为可离线浏览的报告。

## Overview

`tree analyze` 分析 current checkout 或一个 local ref，按 Reactor 和 Module 展示 resolved dependency tree、requested / resolved version、dependency management 和 class conflict。它不比较两个 snapshot，也不执行远程 fetch。

## Usage

- Actor：排查 Maven dependency resolution 和 classpath conflict 的开发者。
- Public entrypoint：`dependency-analyzer tree analyze` CLI command。
- Prerequisites：入口 POM 可读、本地 Git repository 和 Maven runtime 可用。
- Input：project path、可选 local ref、scope、Maven profile 参数和输出目录。
- Output：可离线浏览的 Repository、Reactor 和 Module HTML pages 与数据 shard。

```sh
java -jar /path/to/dependency-analyzer.jar tree analyze \
  --path services/payment \
  --output build/payment-dependencies
```

## Core Flow

1. Command 验证输入并由 [Git Workspace Management](../implementation/git-workspace-management.md) 选择 current checkout 或 detached local-ref snapshot。
2. Scope resolver 与 [Maven Build Runner](../implementation/maven-build-runner.md) 确定 aggregator、owned leaf 或 standalone 执行模式。
3. [Structured Dependency Evidence Collection](../implementation/dependency-evidence-collection.md) 采集全量 occurrence、resolved classpath 和 resolution source。
4. Analyzer 在选定 scope 内计算 dependency management、内部版本冲突和 class conflict，不枚举 Git root 下无关 POM。
5. [Report Generator](../implementation/report-generator.md) 按 Reactor 增量发布离线 Report；失败 Module 保留可行动状态，未完成 shard 不冒充成功结果。

## Acceptance Criteria

完整验收条件见 [Repository Dependency Tree Report Acceptance Criteria](../ac/features/repository-dependency-tree-report.md)。
