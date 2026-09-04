---
name: "Evidence Plugin"
type: container
parent: "[[c4/software-systems/dependency-analyzer]]"
relations:
  - target: "[[c4/containers/dependency-analyzer-cli]]"
    description: "发布模块依赖与 classpath 证据供摄取。"
    mechanism: "原子 JSON 文件与 owner marker"
---

## Overview

Dependency Evidence Plugin 是由 Apache Maven 加载的 Maven Plugin，在目标 project session 内采集 Analyzer 无法从 Console 文本可靠恢复的结构化 evidence。

## Responsibilities

- 采集 selected dependency graph、raw occurrence path、resolved artifact binding 与 effective classpath。
- 将 Module owner、coordinate 和 resolution facts 原子写入 command-owned output。

## Technology

- Java 8 bytecode：兼容目标 Maven runtime。
- Maven Plugin API：访问当前 project 与 Maven session。
- Jackson streaming JSON：写入结构化证据。

## Interfaces

- Maven Plugin goals：接收 Analyzer 控制的 output directory、owner token 和当前 Maven session；owner、path 或 dependency winner 不满足契约时使 goal 失败。
- Evidence publication：owner marker 先证明输出目录属于当前命令；Plugin 再写临时 JSON 并原子移动为最终文件，只有完整 schema 输出可供 CLI 消费。

## State and Data

Plugin 不保留跨 command 状态。Evidence 只存在于当前 command-owned cache，生命周期由 CLI 管理。

## Boundaries

该 Container 只观察并序列化 Maven session facts；不执行 bytecode diff、Call Graph、impact classification 或最终 Report publication，也不扫描 bounded reactor 之外的 project。
