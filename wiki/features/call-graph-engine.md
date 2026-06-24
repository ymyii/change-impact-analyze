---
title: "Call Graph Engine"
type: feature
relations:
  - path: "wiki/architecture/analysis-pipeline.md"
    desc: "Call Graph Engine 是影响追踪前的调用关系构建阶段"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Tracing 消费 Call Graph Engine 产物"
code_refs:
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphEngine.java"
    desc: "WALA RTA Call Graph 构建引擎"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraph.java"
    desc: "不可变 Call Graph 数据模型"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallEdge.java"
    desc: "调用边数据模型"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/MethodId.java"
    desc: "方法唯一标识"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/EdgeKind.java"
    desc: "调用边类型枚举"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphStats.java"
    desc: "Call Graph 构建统计信息"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/ServiceLoaderEnricher.java"
    desc: "ServiceLoader 间接调用边补充"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/ReflectionEnricher.java"
    desc: "Reflection 间接调用边补充"
  - path: "src/main/java/io/github/changeimpact/analyze/callgraph/CallGraphException.java"
    desc: "Call Graph 构建异常"
---

# Feature: Call Graph Engine

## Summary

Call Graph Engine 基于 target build 的 main classes 使用 WALA RTA 构建应用方法调用图，并补充 ServiceLoader 与 Reflection 间接调用边。产物供 Impact Tracing 反向追踪受影响业务方法。

## Design Decisions

- Call Graph 只基于 target/current workspace 的 main classes 构建，因为报告需要回答升级后业务代码中的受影响路径。
- 使用 WALA RTA 作为核心调用图算法，避免手写 Java bytecode 调用解析。
- JDK primordial classes 通过 JRT filesystem best-effort 加入 analysis scope，失败只记录 warn，不阻塞应用类调用图构建。
- ServiceLoader 和 Reflection enricher 在 WALA 结果之后补充间接调用边，提高常见动态调用场景的可见性。
- `CallGraph` 对外暴露不可变 methods、edges、overrides 和 stats，避免 impact 阶段修改调用图。

## Actors / Entrypoints

- CLI pipeline 在存在 ChangePoint 时创建 `CallGraphEngine`。
- `CallGraphEngine.build(buildResult)` 是 Call Graph 构建入口。
- `CallGraph.getIncomingEdges()` 和 `CallGraph.getOutgoingEdges()` 是 Impact Tracing 查询调用关系的入口。

## Behavior Contract

- 输入为 target side 的 `BuildResult`，只读取其中的 main classes 目录。
- Classes directory 不存在或不是目录时抛出 `CallGraphException`。
- WALA scope 使用 Application loader 加载业务 classes。
- 只保留 application methods 和 application-to-application call edges。
- `EdgeKind` 根据 invoke instruction 分为 static、special、interface 和 virtual。
- Override map 记录 parent method 到 overriding method 的关系。
- 构建完成后记录 methods count、edges count、elapsed time 和 memory usage stats。

## Core Flow

1. `build()` 接收 target `BuildResult` 并记录起始时间和内存基线。
2. `createScope()` 将各模块 classes dir 加入 WALA Application scope。
3. `addJdkPrimordial()` best-effort 加入 JDK primordial classes。
4. 构建 class hierarchy 和 all application entrypoints。
5. 使用 WALA RTA builder 生成 WALA call graph。
6. 提取 application methods 和 call edges。
7. 构建 override map，并补齐所有 application declared methods。
8. 运行 ServiceLoader 和 Reflection enricher。
9. 返回不可变 `CallGraph`。

## Acceptance Criteria

### Functional

- Given target build 含有效 classes dir，When `build()` 执行，Then 返回包含 application methods 和 call edges 的 `CallGraph`。
- Given classes dir 不存在，When `build()` 创建 WALA scope，Then 抛出 `CallGraphException`。
- Given call site 是 static invoke，When 提取边，Then `EdgeKind` 为 `INVOKE_STATIC`。
- Given application method override 另一个 application method，When 构建 override map，Then parent method 能查询到 overriding method。
- Given ServiceLoader 或 Reflection 间接调用可识别，When enricher 执行，Then 对应边补充到 Call Graph。

### Non-Functional

- [ ] Call Graph 产物必须不可变，保证 Impact Tracing 只读消费。
- [ ] JDK primordial 加载失败不得阻塞应用调用图构建，但必须产生 warn 诊断。
- [ ] 构建统计必须进入诊断，便于观察性能和图规模。

## Edge Cases

- 空 build outputs 会生成空或近空 Call Graph，后续 Impact Tracing 根据 seeds 与图匹配情况处理。
- WALA RTA 构建失败时包装为 `CallGraphException`。
- 自调用边不加入 edges，避免反向追踪中产生无意义循环。
- 部分动态调用无法静态确认时，只能通过 enricher 覆盖已知模式。

## Implementation Boundaries

- Call Graph Engine 不扫描 ChangePoint，也不决定影响路径；这些属于 Impact Tracing。
- Call Graph 只描述 target/current 业务代码内部调用关系，不展示第三方库内部调用链。
- ServiceLoader 和 Reflection enricher 是调用图补充边界，不改变 WALA 原始 class hierarchy。
