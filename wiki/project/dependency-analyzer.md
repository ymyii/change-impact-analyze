---
title: "Dependency Analyzer"
type: project
---

# Project: Dependency Analyzer

## Background

Dependency Analyzer 面向需要评估 Maven repository 依赖升级风险的开发者。它在不运行目标业务系统的前提下，对 Git baseline 与 target 的依赖、bytecode、Call Graph 和引用证据进行静态分析，并生成可离线查看的 HTML Report。

## Overview

项目提供 `impact`、`tree analyze` 和 `tree diff` 三个 CLI 用户目标，以及为 Analyzer 内嵌运行的 Maven Plugin 与 JDK method model artifact。整体结构和依赖方向见 [Dependency Analysis Pipelines](../architecture/dependency-analysis-pipelines.md)。构建、测试和交付操作见 [Build, Test, Package](../runbooks/build-test-package.md)。

## Design Decisions

- None.

## Project Structure

- Analyzer reactor 使用 Java 17，交付可执行 uber JAR，并拥有 CLI、workspace、分析 orchestration 和离线 Report publication。
- Dependency Evidence Plugin reactor 使用 Java 8，交付 Maven Plugin JAR 和可被 Analyzer 内嵌的 repository ZIP。
- 公共 JDK model engine reactor与 JDK 8 model reactor 独立构建、独立版本化；Analyzer 只消费已安装的 model artifact。
- 四个 reactor 不组成单一 Maven reactor。依赖 artifact 必须按公共 JDK model、JDK 8 model、Dependency Evidence Plugin、Analyzer 的方向准备。
- `impact` 提供 [Dependency Impact Analysis](../features/dependency-impact-analysis.md)；`tree analyze` 和 `tree diff` 分别提供单侧依赖树报告与双侧依赖树差异报告。

## Technical Stack

- Java 17：Analyzer runtime、测试和打包。
- Java 8：Dependency Evidence Plugin bytecode target，以及被分析项目和 JDK 8 model 的兼容边界。
- Maven 3.x：四个 reactor 的构建、依赖解析、Plugin 执行和 release gate。
- Git：baseline、target、worktree 与当前工作区快照来源。
- WALA、ASM 与 Vineflower：Call Graph、bytecode、Static Single Assignment（SSA，静态单赋值）和反编译证据。
- Picocli：CLI 参数解析和 command dispatch。
- HTML、JavaScript 与 callback shard：无需服务器的离线 Report。
