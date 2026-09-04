---
name: "Package Boundaries"
type: rule
---

## Overview

本规则保护 [Call Graph Engine](../c4/components/dependency-analyzer-cli-call-graph-engine.md)、[Impact Tracing](../c4/components/dependency-analyzer-cli-impact-tracing.md) 与 [Report Publication](../c4/components/dependency-analyzer-cli-report-publication.md) 的职责和单向依赖。

## Scope

- 新增、迁移或删除 Call Graph strategy、protocol、scope、boundary、topology、Impact evidence、classpath model 或 Report code。
- 修改 package dependency、strategy registration 或 test package layout。

## Rules

- **必须**让主依赖方向为 `impact -> callgraph.engine -> callgraph.strategy`；`callgraph..` 不得依赖 `impact..` 或 `report..`。
- **必须**让 `strategy.cha` 与 `strategy.kobj` 隔离；共享能力进入算法中立的 `scope`、`entrypoint`、`protocol`、`model` 或 `local` package。
- **必须**让公共 `callgraph.protocol..` 独立于具体 strategy 与 engine；algorithm-specific adapter 位于对应 strategy package。
- **必须**让 `impact` 在 graph metadata 冻结后收集业务 evidence，并集中转换 typed Call Graph reason。
- **必须**让 `report` 只消费冻结结果，不访问 live strategy implementation。
- **必须**让共享 class ownership/conflict concept 由中立 `classpath` package 唯一拥有。
- **必须**让职责 package 通过 `package-info.java` 声明边界，test package 镜像 production package。
- **禁止**在 `callgraph` 根 package 放置 production class，或为破坏性迁移保留 compatibility wrapper。

## Verification

- 执行 `mvn test -Dtest=PackageArchitectureTest`。
- Review `package-info.java`、API ownership 与 tests，确认 ArchUnit 未覆盖的语义边界仍成立。

## Non-Goals

- ArchUnit 不替代职责命名、API ownership 与 domain review。
- 本规则不要求不同领域仅因代码形状相似而共享 package。
