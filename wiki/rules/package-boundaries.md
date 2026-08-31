---
title: "Package Boundaries"
type: rule
---

# Rule: Package Boundaries

## Summary

生产代码按职责分包，依赖从业务 pipeline 单向流向 Call Graph engine 与公共 strategy contract。算法实现、公共 protocol、业务 evidence 和 Report 各自拥有明确边界；Java API 发生破坏性调整时直接迁移调用方，不保留旧 package wrapper 或 deprecated alias。

## Rules

- `callgraph` 根 package 只允许 `package-info.java`；production class 必须进入职责子包。
- 固定主方向为 `impact -> callgraph.engine -> callgraph.strategy`。Call Graph 任意 package 禁止依赖 `impact` 或 `report`。
- `callgraph.strategy.cha` 与 `callgraph.strategy.kobj` 禁止相互依赖。共享能力必须下沉到算法无关的 `scope`、`entrypoint`、`protocol`、`model`、`local` 等 package。
- `callgraph.protocol` 及其子包只定义算法无关 contract、fact、resolution 与 limitation，禁止依赖具体 strategy 或 engine。
- 算法专属 protocol selector、installer、context 和 summary 必须位于对应 strategy 子包；`k-obj` adapter 使用 `strategy.kobj.invokedynamic`、`strategy.kobj.methodhandle`、`strategy.kobj.serviceloader`。
- `impact` 负责把业务 domain 投影成 `ModuleCallGraphInput`，在 Call Graph metadata 冻结后采集 evidence，并通过单一 mapper 将 Call Graph typed code 转成业务 reason。
- `report` 只消费 `impact` 已转换、冻结的结果；禁止访问 live CHA 或 `k-obj` implementation。
- `classpath`拥有`CodeOrigin`、`ClassSource`、`ClassOwnershipIndex`、`ClassConflictResolution`和`ClassConflictRisk`唯一共享模型。`impact`与`tree`都依赖该中立package；禁止在consumer package保留平行duplicate/conflict alias。
- 每个职责 package 必须有 `package-info.java`，说明责任、允许依赖方向和禁止事项。
- test package 镜像 production package。跨层集成测试放在实际 orchestration consumer package，不以测试便利为由破坏 production 依赖。
- 删除能力时同步删除 implementation、registration、CLI identifier、专用测试和文档；保留能力应完成真实 package 迁移，不留兼容壳。

## Applies To

- 新增、迁移或删除 Call Graph、impact refinement、dynamic protocol、scope、boundary、topology、evidence 或 Report 代码。
- 修改共享 classpath model、strategy registration、package dependency 或 test package layout。

普通 Data Transfer Object（DTO，数据传输对象）只需遵守所属 package contract，不要求重复添加 Wiki 注释；稳定架构入口可以引用本规则。

## Verification

`PackageArchitectureTest` 在 `mvn verify` 中强制：

- `callgraph..` 不依赖 `impact..`、`report..`。
- CHA 与 `k-obj` strategy 隔离。
- 公共 protocol 不依赖 strategy 或 engine。
- algorithm adapter 位于对应 strategy 子包。
- `callgraph` 根 package 无 production class。
- `report` 不依赖 live strategy implementation。

源码评审同时检查 `package-info.java` 与 test package 镜像。ArchUnit 是 durable gate，不替代职责命名与 API review。

## Non-Goals

- ArchUnit 不替代职责命名、API ownership 与单向依赖 review。
- 本规则不要求不同领域语义仅因代码形状相似而共享 package。
