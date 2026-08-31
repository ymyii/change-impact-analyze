---
title: "Structured Dependency Evidence Collection"
type: implementation
---

# Implementation: Structured Dependency Evidence Collection

## Background

Analyzer 不能从 Maven Console 文本可靠恢复 dependency occurrence、resolved artifact path、Module ownership 和 effective classpath。证据必须由与实际 Maven session 一致的 Plugin 在 bounded scope 中结构化发布。

## Overview

Dependency Evidence Plugin 在 Maven reactor 内采集 impact 所需的 resolved selected graph，以及 tree pipeline 所需的 raw occurrence 与 classpath evidence。Analyzer 通过 [Maven Runtime](maven-runtime.md) 调用 Plugin，并将输出限制在 command-owned cache；Plugin 不写最终 Report。

## Core Flow

1. Analyzer 为 side 和 Module 规划唯一 command cache 目录，并启动与 scope resolver 使用同一 profile / settings 语义的 Maven invocation。
2. Plugin 在实际 Maven session 中识别 Module owner、resolved selected dependency、raw occurrence path 和 physical artifact binding。
3. 每个 Module 先写临时文件，完成 schema 校验后原子发布，并写入完成标记。
4. Analyzer 验证 owner、路径边界、coordinate 与 schema，再投影为 impact 或 tree domain input。
5. Command 结束后由 workspace lifecycle 清理 owned cache；既有 project output 不作为证据来源。

## Key Mechanisms

- Impact evidence 保留 selected graph 与 logical artifact binding；Tree classpath evidence 额外保留 occurrence、requested / resolved version、scope、directness 与 resolution source。
- 所有输出路径必须位于 command cache，且 Module owner 与 bounded reactor inventory 一致；跨 owner 或路径逃逸立即失败。
- JSON 与 complete marker 共同定义 publication boundary；缺少任一项都不能被当作完整证据。
- Maven profile activation、user / global settings 与实际 Maven JVM 必须和 scope resolver 一致，避免 Analyzer scope 与 Plugin session 分叉。

## Design Decisions

- None.

## Acceptance

完整验收条件见 [Structured Dependency Evidence Collection Acceptance](../acceptance/implementation/dependency-evidence-collection.md)。
