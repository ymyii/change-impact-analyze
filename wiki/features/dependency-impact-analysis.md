---
title: "Dependency Impact Analysis"
type: feature
---

# Feature: Dependency Impact Analysis

## Background

Maven dependency version变化能够引入 binary incompatibility 或行为变化，但单纯列出 dependency diff 无法说明哪些业务入口能够到达变化。Dependency Impact Analysis 面向需要在升级前定位潜在受影响调用路径的开发者。

## Overview

`impact` 比较 Git baseline 与 target，在 bounded reactor scope 中解析版本变化、bytecode ChangePoint、Call Graph 和引用证据，并发布离线 HTML Report。结果只表达静态分析可证明或明确标记为不确定的路径，不证明运行时一定发生故障，也不替代集成测试。

## Usage

- Actor：在本地 repository 评估 dependency upgrade 的开发者。
- Public entrypoint：`dependency-analyzer impact` CLI command。
- Prerequisites：本地 Git ref 可解析、入口 POM 可读、Maven 3.x 可用、`--java-home` 指向完整 JDK 8。
- Input：baseline、可选 target、Maven project path、scope 和 selector。
- Output：单个离线 HTML Report，以及 Console Diagnostic；显式请求时另写 topology JSON。

```sh
java -jar /path/to/dependency-analyzer.jar impact \
  --java-home /opt/jdk8 \
  --path . \
  --baseline main \
  --target feature/dependency-upgrade \
  --output build/impact.html
```

完整选项和操作边界见项目用户手册；构建可执行 JAR 见 [Build, Test, Package](../runbooks/build-test-package.md)。

## Core Flow

1. CLI 与 [CLI Preflight and Diagnostics](../implementation/cli-preflight-diagnostics.md) 验证 Git、入口 POM、JDK、Maven runtime、selector 与输出边界；失败时保留旧 Report。
2. [Git Workspace Management](../implementation/git-workspace-management.md) 准备 baseline 与 target，[Maven Build Runner](../implementation/maven-build-runner.md) 在两侧 bounded scope 中规划编译和证据采集。
3. [Impact Dependency Diff Engine](../implementation/dependency-diff-engine.md) 选择版本变化的 logical artifact pair，[Bytecode Diff Engine](../implementation/bytecode-diff-engine.md) 生成有效 ChangePoint。
4. 每个相关 Module 通过 [Call Graph Engine](../implementation/call-graph-engine.md) 构图，并由 [Impact Tracing](../implementation/impact-tracing.md) 绑定 evidence、反向查询和选择代表路径。
5. [Report Generator](../implementation/report-generator.md) 消费冻结结果并发布离线 Report；partial 或 inconclusive 结果保留明确限制。

## Acceptance

完整验收条件见 [Dependency Impact Analysis Acceptance](../acceptance/features/dependency-impact-analysis.md)。
