---
title: "Maven Build Runner"
type: implementation
---

# Implementation: Maven Build Runner

## Background

用户可能把 `--path` 指向 aggregator、owned leaf 或 standalone POM。直接在 Git root 枚举 POM 会扩大分析范围，而对 leaf 总是执行当前目录 Maven 又会遗漏所需的 reactor upstream dependency。

## Overview

Maven Build Runner 以入口 POM 和 active module closure 规划 `AGGREGATOR`、`OWNED_LEAF` 或 `STANDALONE` mode，并只在 target side 执行 compile。它与 tree / impact 共享 bounded reactor scope，但 baseline 与 target 独立规划，防止结构假设跨 side 泄漏。

## Core Flow

1. 从入口 POM 读取 active module closure，并只检查入口到 Git root 之间的祖先 aggregator。
2. 入口自身拥有 active child 时选择 `AGGREGATOR`；被祖先 active closure 拥有时选择最外层 matching aggregator 与 `OWNED_LEAF`；否则选择 `STANDALONE`。
3. Aggregator 执行完整 compile；owned leaf 使用 `-pl <relative-path> -am`；standalone 直接执行入口 POM。
4. 解析 compile output 与 Module target classes，验证结果仍位于 bounded workspace。
5. Build failure 保留 bounded subprocess tail，并阻止依赖不完整的后续阶段。

## Key Mechanisms

- Profile activation 使用与实际 Maven JVM、settings 和 `-P` / `-D` 相同的 context，避免 scope 与 compile session 分叉。
- 只检查祖先 aggregator，不发现 non-ancestor POM，也不扫描 repository 中的无关 project。
- Pure aggregator 作为 execution context，不自动成为分析 Module；owned leaf 的 `-am` upstream 可以作为 reactor dependency 进入 classpath。
- 外部 process token 统一遵守 [Process Command Resolution](../rules/process-command-resolution.md)。

## Design Decisions

- None.

## Acceptance

完整验收条件见 [Maven Build Runner Acceptance](../acceptance/implementation/maven-build-runner.md)。
