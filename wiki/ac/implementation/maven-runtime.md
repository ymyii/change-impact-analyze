---
title: "Maven Runtime Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Maven Runtime

本页验收 [Maven Runtime](../../implementation/maven-runtime.md)。

## Functional

1. 场景：准备内嵌 Plugin repository
   - Given：Analyzer JAR 包含两个合法 repository ZIP。
   - When：runtime manager 准备 command descriptor。
   - Then：
     - Maven Dependency Plugin 与 Dependency Evidence Plugin 的 artifact / POM 可从 command repository 解析。
     - Descriptor 记录实际 Maven executable、version 与 Java home。

2. 场景：合并 settings overlay
   - Given：用户存在 mirror、proxy、server 或 active profile settings。
   - When：生成 command settings overlay。
   - Then：
     - 用户 settings 语义被保留。
     - Command repository 被追加而不是替换用户 repository policy。

## Failure

1. 场景：Maven capability 不满足
   - Given：Maven version、Java runtime 或 Plugin goal schema 不符合 command contract。
   - When：Preflight 探测 runtime。
   - Then：
     - Command 在 pipeline 前失败。
     - 不产生新的 analysis Report。

## Non-Functional

- [ ] 当 Diagnostic 输出 runtime 信息时，settings credential 与 server secret 的明文出现次数为 `0`。
