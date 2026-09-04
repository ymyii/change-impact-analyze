---
name: "Maven Runtime"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/software-systems/apache-maven]]"
    description: "探测并执行所选 Maven 安装。"
    mechanism: "CLI subprocess"
  - target: "[[c4/containers/dependency-analyzer-evidence-plugin]]"
    description: "在目标 Maven session 中调用随包交付的证据 goals。"
    mechanism: "Plugin goal invocation"
  - target: "[[c4/components/dependency-analyzer-cli-evidence-ingestion]]"
    description: "提供本次命令所有的证据输出用于校验和摄取。"
    mechanism: "Local filesystem"
---

## Overview

Maven Runtime 统一选择 executable、Java home、settings overlay 和内嵌 Plugin repository，并在限定的 Reactor 范围内执行 compile 与证据采集。

## Responsibilities

- 验证 Maven `3.6.3 <= version < 4.0.0` 与实际 Maven JVM。
- 保留 user/global settings、mirror、proxy、server、profile 和 local repository 语义，并仅追加 command repository。
- 根据 bounded scope 执行 compile 与 dependency evidence goals，保留 bounded failure tail。

## Technology

- Apache Maven CLI：探测并执行用户选择或内嵌的 Maven 3 runtime。
- Apache Maven 3.6.3：随 Analyzer 提供最低兼容版本的内嵌 runtime。
- Maven Dependency Plugin 3.6.1：作为 repository ZIP 随包交付并参与证据采集。

## Interfaces

- Runtime descriptor：一次解析后供 scope resolver 与全部 Maven stage 共用；缺失 executable、非法版本或损坏 embedded artifact 时停止 command。
- Build execution：接受 immutable scope plan 与安全 Maven token；返回 exit code、bounded Console evidence 和 output locations。

## State and Data

可复用 runtime cache 由 version 与 integrity identity 管理；repository overlay、temporary extraction 与 execution output 受 command owner 限制。

## Boundaries

该 Component 负责 Maven availability 与 execution mechanism；不判断 dependency semantics、不拥有用户 credential，也不把 Maven Console 文本当作完整结构化 evidence。
