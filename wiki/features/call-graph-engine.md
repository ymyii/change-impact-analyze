---
title: "Call Graph Engine"
type: feature
relations:
  - path: "wiki/architecture/dependency-analysis-pipelines.md"
    desc: "Call Graph Engine 是影响追踪前的调用关系构建阶段"
  - path: "wiki/features/impact-tracing.md"
    desc: "Impact Tracing 消费 Call Graph Engine 产物"
code_refs:
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallGraphEngine.java"
    desc: "WALA RTA Call Graph 构建引擎"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/JdkAnalysisStage.java"
    desc: "目标 JDK 8 Primordial/Extension scope 准备入口"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallGraphProgressMonitor.java"
    desc: "RTA heartbeat 与 cooperative timeout"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/QuietClassBasedInstanceKeys.java"
    desc: "无 debug 输出的 class-based InstanceKeyFactory"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallGraph.java"
    desc: "不可变 Call Graph 数据模型"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallEdge.java"
    desc: "调用边数据模型"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/MethodId.java"
    desc: "方法唯一标识"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/EdgeKind.java"
    desc: "调用边类型枚举"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallGraphStats.java"
    desc: "Call Graph 构建统计信息"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/ServiceLoaderEnricher.java"
    desc: "ServiceLoader 间接调用边补充"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/ReflectionEnricher.java"
    desc: "Reflection 间接调用边补充"
  - path: "src/main/java/io/github/dependencyanalysis/callgraph/CallGraphException.java"
    desc: "Call Graph 构建异常"
---

# Feature: Call Graph Engine

## Summary

Call Graph Engine 基于 target build 的 main classes 使用 WALA RTA 构建应用方法调用图，并补充 ServiceLoader 与 Reflection 间接调用边。产物供 Impact Tracing 反向追踪受影响业务方法。

## Design Decisions

- Call Graph 只基于 target/current workspace 的 main classes 构建，因为报告需要回答升级后业务代码中的受影响路径。
- 使用 WALA RTA 作为核心调用图算法，避免手写 Java bytecode 调用解析。
- Analyzer 由 Java 17 启动；CLI 分析 scope 只使用 `--java-home` 指定的 JDK 8 boot jars 和 extension jars，不读取 analyzer JRT。
- 保留 `AllApplicationEntrypoints` 和完整 RTA，不按 ChangePoint seed 缩减 entrypoint，不启用 JDK method bypass。
- RTA 使用与 WALA `ClassBasedInstanceKeys` allocation 语义等价的自有 `InstanceKeyFactory`，避免 WALA 对 method-handle allocation 的无条件 `got NEW` 输出。
- 默认不设置 Call Graph 时限；正数 timeout 通过 WALA progress monitor cooperative cancel，不能生成部分报告。
- ServiceLoader 和 Reflection enricher 在 WALA 结果之后补充间接调用边，提高常见动态调用场景的可见性。
- `CallGraph` 对外暴露不可变 methods、edges、overrides 和 stats，避免 impact 阶段修改调用图。

## Actors / Entrypoints

- CLI pipeline 仅在至少存在一个 bytecode seed 时运行 `JdkAnalysisStage` 和 `CallGraphEngine`。
- `CallGraphEngine.build(buildResult, targetJdkScope, timeoutSeconds)` 是 CLI Call Graph 构建入口；兼容 overload 不用于 CLI。
- `CallGraph.getIncomingEdges()` 和 `CallGraph.getOutgoingEdges()` 是 Impact Tracing 查询调用关系的入口。

## Behavior Contract

- 输入为 target side 的 `BuildResult`，只读取其中的 main classes 目录。
- Classes directory 不存在或不是目录时抛出 `CallGraphException`。
- `impact` target runtime 必须是完整 JDK 8；boot class path 进入 Primordial loader，extension jars 进入 Extension loader，业务 main classes 进入 Application loader。
- JDK method body 可沿可达调用参与分析，但最终只提取 application-to-application edges。
- RTA 每 10 秒输出 elapsed、heap used/max 和 WALA progress units；`0` 表示无限等待。
- 只保留 application methods 和 application-to-application call edges。
- `EdgeKind` 根据 invoke instruction 分为 static、special、interface 和 virtual。
- Override map 记录 parent method 到 overriding method 的关系。
- 构建完成后记录 methods count、edges count、elapsed time 和 memory usage stats。

## Core Flow

1. `build()` 接收 target `BuildResult` 并记录起始时间和内存基线。
2. `[jdk-analysis]` 从目标 JDK descriptor 创建 Primordial/Extension base scope。
3. `createScope()` 将各模块 classes dir 加入 Application scope。
4. 构建 CHA 和全部 application entrypoints，并输出 checkpoint。
5. 使用 WALA RTA builder、静默 allocation key factory 和 progress monitor 生成 Call Graph。
6. 提取 application methods/edges，构建 override map并补齐 declared methods。
7. 运行 ServiceLoader 和 Reflection enricher，返回不可变 `CallGraph`。

## Acceptance Criteria

### Functional

- Given target build 含有效 classes dir，When `build()` 执行，Then 返回包含 application methods 和 call edges 的 `CallGraph`。
- Given classes dir 不存在，When `build()` 创建 WALA scope，Then 抛出 `CallGraphException`。
- Given call site 是 static invoke，When 提取边，Then `EdgeKind` 为 `INVOKE_STATIC`。
- Given application method override 另一个 application method，When 构建 override map，Then parent method 能查询到 overriding method。
- Given ServiceLoader 或 Reflection 间接调用可识别，When enricher 执行，Then 对应边补充到 Call Graph。

### Non-Functional

- [ ] Call Graph 产物必须不可变，保证 Impact Tracing 只读消费。
- [ ] 目标 JDK scope 不完整时必须失败，不能回退到 analyzer JRT。
- [ ] stdout/stderr 不得出现 WALA `got NEW` debug 行。
- [ ] timeout 只能产生失败，不得发布不可信的部分报告。
- [ ] 构建统计必须进入诊断，便于观察性能和图规模。

## Edge Cases

- 零 seed 在 pipeline 边界直接跳过 JDK analysis、CHA 和 RTA。
- WALA RTA 构建失败时包装为 `CallGraphException`。
- 自调用边不加入 edges，避免反向追踪中产生无意义循环。
- 部分动态调用无法静态确认时，只能通过 enricher 覆盖已知模式。

## Implementation Boundaries

- Call Graph Engine 不扫描 ChangePoint，也不决定影响路径；前置 seed scanning 和后置路径计算属于 Impact Tracing。
- Call Graph 只描述 target/current 业务代码内部调用关系，不展示第三方库内部调用链。
- ServiceLoader 和 Reflection enricher 是调用图补充边界，不改变 WALA 原始 class hierarchy。
