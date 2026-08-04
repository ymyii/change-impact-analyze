# impact 能力 Roadmap

本文用于持续跟踪 `impact` 的目标能力、当前实现和能力边界。能力基线为 commit `b5a11e8b00edc515742dc30be46e220891305913` 的 production code；Wiki 仅作背景信息，不作为完成依据。

核心实现：

- [`BytecodeDiffEngine`](analyzer/src/main/java/io/github/dependencyanalysis/bytecode/BytecodeDiffEngine.java)
- [`StableHashMethodVisitor`](analyzer/src/main/java/io/github/dependencyanalysis/bytecode/StableHashMethodVisitor.java)
- [`CallGraphEngine`](analyzer/src/main/java/io/github/dependencyanalysis/callgraph/CallGraphEngine.java)
- [`ChangePointRefScanner`](analyzer/src/main/java/io/github/dependencyanalysis/impact/ChangePointRefScanner.java)
- [`ImpactTracer`](analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactTracer.java)

状态约定：

- `[x]`：当前能力已完整实现，并能从 production code 或测试中找到依据。
- `[ ]`：当前未实现或仅部分实现；已完成部分写在“当前实现/缺口”中。
- `P0`：直接影响依赖变更分析的 soundness；`P1`：高频场景或结果可信度；`P2`：专项场景或精度增强。

## 1. Bytecode Diff 能力矩阵

| 状态 | 能力域 | 能力项 | 预期识别结果 | 当前实现/缺口 | 建议优先级 |
|---|---|---|---|---|---|
| `[x]` | 分析输入 | `VERSION_CHANGED` dependency | 定位 old/new JAR 并执行 bytecode diff | `ImpactPipeline` 只将 `VERSION_CHANGED` 交给 `JarLocator` 和 `BytecodeDiffEngine` | — |
| `[ ]` | 分析输入 | `ADDED` dependency 内容 | 对新增 JAR 建立完整 class/member inventory，并区分新增能力与新风险 | 当前 dependency change 可报告，但不进入 JAR bytecode 分析 | P1 |
| `[ ]` | 分析输入 | `REMOVED` dependency 内容 | 对删除 JAR 建立完整 class/member inventory，并关联业务引用 | 当前 dependency change 可报告，但不进入 JAR bytecode 分析 | P0 |
| `[x]` | 分析输入 | old/new JAR 定位 | 使用 artifact coordinate 定位两侧 Maven local repository JAR | 已由 `JarLocator` 实现 | — |
| `[x]` | 分析输入 | 损坏或不可读 JAR/Class | 失败并携带 JAR path、class name 等诊断 | `JarClassIndexer` 将 ZIP、I/O、ASM parse failure 包装为 `BytecodeDiffException` | — |
| `[ ]` | JAR | Multi-Release JAR | 根据目标 Java runtime 选择 `META-INF/versions/N` 的 effective class | 当前扫描全部 `.class`，相同 internal name 可能被后扫描 entry 覆盖 | P0 |
| `[x]` | JAR | 非 class resource 排除 | resource 变化不误报为 bytecode ChangePoint | 当前只处理 `.class` entry | — |
| `[ ]` | JAR | 与行为相关的 resource diff | 单独识别 `META-INF/services`、Spring metadata、native config 等行为配置变化 | 当前所有非 class resource 均被忽略 | P1 |
| `[x]` | Class | class 新增 | 生成 `CLASS_ADDED` | 已按 internal name 差集实现；默认 filter 不包含该 kind | — |
| `[x]` | Class | class 删除 | 生成 `CLASS_REMOVED` | 已按 internal name 差集实现 | — |
| `[ ]` | Class | class 重命名/迁移 | 在有充分证据时将 remove+add 关联为 relocation | 当前只产生相互独立的 `CLASS_REMOVED`/`CLASS_ADDED` | P2 |
| `[ ]` | Class | `module-info.class` | 识别 module requires/exports/opens/uses/provides 变化 | 根目录 `module-info.class` 被直接排除 | P1 |
| `[ ]` | Class | class file major/minor version | 识别最低 runtime 要求变化 | 当前不保存或比较 class version | P1 |
| `[ ]` | Class | access flag | 识别 `public`、`final`、`abstract`、`interface`、`enum`、`record` 等变化 | ASM visitor 收到 access，但 `ClassInfo` 不保存 | P0 |
| `[ ]` | Class | superclass | 识别继承关系和 binary compatibility 变化 | 当前不保存 `superName` | P0 |
| `[ ]` | Class | implemented interface | 识别 interface 增删 | 当前不保存 interfaces | P0 |
| `[ ]` | Class metadata | generic signature | 识别影响 source/reflection contract 的 generic 变化 | 当前忽略 class signature | P1 |
| `[ ]` | Class metadata | annotation/type annotation | 识别 runtime/class retention annotation 变化 | 当前没有 annotation visitor | P1 |
| `[ ]` | Class metadata | record component | 识别 record component、descriptor、annotation 变化 | 当前没有 record component model | P1 |
| `[ ]` | Class metadata | sealed hierarchy | 识别 permitted subclass 变化 | 当前不读取 `PermittedSubclasses` | P1 |
| `[ ]` | Class metadata | nest metadata | 识别 `NestHost`/`NestMembers` 变化 | 当前不读取 nest metadata | P2 |
| `[ ]` | Class metadata | inner/enclosing metadata | 识别 `InnerClasses`、`EnclosingMethod` 变化 | 当前不读取 inner/enclosing metadata | P2 |
| `[x]` | Method | method 新增 | 生成 `METHOD_ADDED` | 已按 `name + descriptor` 差集实现；默认 filter 不包含该 kind | — |
| `[x]` | Method | method 删除 | 生成 `METHOD_REMOVED` | 已按 `name + descriptor` 差集实现 | — |
| `[x]` | Method | 非 overload descriptor 变化 | 同名 method 两侧各只有一个时生成 `METHOD_DESCRIPTOR_CHANGED` | 已实现该受限场景；同时可能另有 method remove/add ChangePoint | — |
| `[ ]` | Method | overload descriptor reconciliation | 对 overload 增删、参数迁移、return type 变化进行无歧义匹配 | 两侧同名 method 任一侧不止一个时不生成 descriptor change | P0 |
| `[ ]` | Method | access flag | 识别 visibility、`static`、`final`、`abstract`、`native`、`synchronized` 等变化 | access 仅用于跳过 abstract/native body hash，未参与 diff | P0 |
| `[ ]` | Method | generic signature | 识别 generic method contract 变化 | 当前忽略 signature | P1 |
| `[ ]` | Method | declared exception | 识别 `throws` 列表变化 | 当前忽略 exceptions | P1 |
| `[ ]` | Method | annotation/type annotation | 识别 method、receiver、return type annotation 变化 | 当前没有对应 visitor | P1 |
| `[ ]` | Method | parameter metadata | 识别 parameter annotation、method parameter flag/name 的有效变化 | 当前不读取 parameter metadata | P2 |
| `[ ]` | Method | annotation default | 识别 annotation element default value 变化 | 当前不读取 `AnnotationDefault` | P1 |
| `[x]` | Method body | zero-operand instruction | opcode 变化导致 `METHOD_BODY_CHANGED` | `visitInsn` 将 opcode 写入 SHA-256 | — |
| `[x]` | Method body | integer/local variable operand | opcode、operand、local slot、`IINC` 变化导致 body change | `visitIntInsn`、`visitVarInsn`、`visitIincInsn` 已覆盖 | — |
| `[x]` | Method body | type instruction | `NEW`、`ANEWARRAY`、`CHECKCAST`、`INSTANCEOF` 的 type 变化导致 body change | `visitTypeInsn` 已覆盖 | — |
| `[x]` | Method body | field instruction | opcode、owner、name、descriptor 变化导致 body change | `visitFieldInsn` 已覆盖 | — |
| `[x]` | Method body | method invocation | opcode、owner、name、descriptor、interface flag 变化导致 body change | `visitMethodInsn` 已覆盖 | — |
| `[x]` | Method body | basic `LDC` constant | primitive、String、Type、Handle 等常量的稳定文本变化导致 body change | 当前使用 `String.valueOf(value)`；尚未定义跨 ASM/JDK 的 canonical encoding | — |
| `[ ]` | Method body | `ConstantDynamic` | 比较 name、descriptor、bootstrap handle 和全部 arguments | 当前仅经通用 `String.valueOf`，没有结构化 canonical encoding | P1 |
| `[ ]` | Method body | branch/CFG topology | 比较 jump opcode 及其规范化目标 basic block | 当前只 hash jump opcode，改变 jump target 可能漏报 | P0 |
| `[ ]` | Method body | `TABLESWITCH` 完整结构 | 比较 min/max、default target 和每个 case target | 当前只 hash min/max | P0 |
| `[ ]` | Method body | `LOOKUPSWITCH` 完整结构 | 比较 keys、default target 和每个 key target | 当前只 hash keys | P0 |
| `[ ]` | Method body | exception handler 完整结构 | 比较 protected range、handler target、catch type 和顺序 | 当前只 hash catch type | P0 |
| `[x]` | Method body | `MULTIANEWARRAY` instruction | descriptor 或 dimensions 变化导致 body change | `visitMultiANewArrayInsn` 已覆盖 | — |
| `[x]` | Method body | `invokedynamic` 基础 call site | name、descriptor 或 bootstrap handle 变化导致 body change | 已 hash name、descriptor 和 `Handle.toString()` | — |
| `[ ]` | Method body | `invokedynamic` bootstrap arguments | 比较 bootstrap method arguments、nested handle、method type 和 dynamic constant | 当前完全忽略 `bsmArgs` | P0 |
| `[x]` | Method body | monitor、array、arithmetic、return 等 opcode | instruction 序列变化导致 body change | 均通过 `visitInsn` 覆盖 | — |
| `[ ]` | Method body | code type annotation/custom attribute | 识别具有 runtime/instrumentation 语义的 code attribute 变化 | 当前不读取 | P2 |
| `[x]` | Method body | abstract/native 无 Code attribute | 不将无 method body 的 method 误报为 body change | `bodyHash` 为 `null`，不参与 body 比较 | — |
| `[x]` | Field | field 新增 | 生成 `FIELD_ADDED` | 已按 field name 差集实现；默认 filter 不包含该 kind | — |
| `[x]` | Field | field 删除 | 生成 `FIELD_REMOVED` | 已按 field name 差集实现 | — |
| `[x]` | Field | field descriptor 变化 | 生成 `FIELD_DESCRIPTOR_CHANGED` | 已按同名 field descriptor 比较实现 | — |
| `[ ]` | Field | access flag | 识别 visibility、`static`、`final`、`volatile`、`transient` 等变化 | 当前不保存 access | P0 |
| `[ ]` | Field | generic signature | 识别 generic field contract 变化 | 当前忽略 signature | P1 |
| `[ ]` | Field | constant value | 识别 compile-time constant 改变及调用方常量内联风险 | 当前忽略 `ConstantValue` | P0 |
| `[ ]` | Field | annotation/type annotation | 识别 field annotation 变化 | 当前没有对应 visitor | P1 |
| `[x]` | 输出 | ChangePoint kind filter | 只输出 CLI 指定的 kind | `BytecodeDiffEngine(Set<ChangePointKind>)` 已实现；默认包含 6 类非 `ADDED` 变化 | — |
| `[x]` | 输出 | method body hash evidence | `METHOD_BODY_CHANGED` 保存 old/new SHA-256 | `ChangePoint.oldHash/newHash` 已填充 | — |
| `[ ]` | 输出 | 结构变化 old/new evidence | descriptor、access、superclass 等变化同时保存 old/new 值 | 当前 descriptor change 只保存一个 descriptor，其他 metadata 尚无模型 | P1 |
| `[ ]` | 输出 | 稳定排序 | 相同输入始终返回完全相同的 ChangePoint 顺序 | class/member union 使用 `HashSet`，engine 返回前未排序 | P1 |
| `[ ]` | 输出 | semantic reconciliation/去重 | descriptor change 等复合变化只产生清晰、无重复语义的结果 | method descriptor 变化可能同时产生 remove/add/change | P1 |
| `[x]` | 输出 | 不可变结果 | 下游不能修改 ChangePoint list | 返回 `Collections.unmodifiableList` | — |

## 2. 调用链分析能力矩阵

| 状态 | 能力域 | 能力项 | 预期识别结果 | 当前实现/缺口 | 建议优先级 |
|---|---|---|---|---|---|
| `[x]` | Scope | target main classes | 基于升级后的业务 `target/classes` 构图和扫描 seed | `BuildResult` 只提供 main classes，Call Graph 和 scanner 均消费 target build | — |
| `[ ]` | Scope | baseline main classes | 对删除 API 保留升级前业务引用证据，并与 target 结果对照 | 当前 Call Graph 和 seed scanner 都不读取 baseline build | P0 |
| `[ ]` | Scope | test classes | 可选分析测试调用链和回归测试覆盖 | `target/test-classes` 明确排除 | P2 |
| `[x]` | Scope | reactor 跨 module application classes | 识别业务 module 之间的调用并记录 boundary | 所有 `ModuleBuildOutput.classesDir` 加入 Application scope，已有跨 module 测试 | — |
| `[ ]` | Scope | dependency JAR classes | 在需要时识别第三方中间调用和 callback | scope 不加入 resolved dependency JAR | P0 |
| `[x]` | Scope | JDK primordial classes | 使用 JDK class hierarchy 辅助解析 application 调用 | 通过 JRT best-effort 加载；失败只 warn | — |
| `[ ]` | Scope | 完整 runtime classpath | 使用与 target 实际运行一致的 dependency/module path | 当前只有 application classes 和 JDK | P0 |
| `[x]` | 静态调用 | `invokestatic` | 创建 application caller → application callee edge | WALA target 提取为 `INVOKE_STATIC` | — |
| `[x]` | 静态调用 | `invokespecial` | 识别 constructor、private method、super call | WALA target 提取为 `INVOKE_SPECIAL` | — |
| `[x]` | 静态调用 | `invokevirtual` | 识别 RTA 可确认的 virtual target | WALA target 提取为 `INVOKE_VIRTUAL` | — |
| `[x]` | 静态调用 | `invokeinterface` | 识别 RTA 可确认的 interface implementation | WALA target 提取为 `INVOKE_INTERFACE`，已有 interface dispatch 测试 | — |
| `[ ]` | 静态调用 | 完整 `<clinit>` trigger | 识别 class initialization 的全部 JVM 触发规则和 ordering | WALA 可能产生部分边；只有 literal `Class.forName` 有显式补充和测试 | P1 |
| `[x]` | 静态调用 | 基础 RTA 多态分派 | 将已实例化 application subtype 纳入 possible target | 使用 WALA RTA | — |
| `[ ]` | 静态调用 | context-sensitive receiver 分派 | 按 call site/context 排除不可能 target，降低 false positive | RTA 为较粗粒度全局类型近似 | P1 |
| `[x]` | 静态调用 | override relation inventory | 保存 application parent/interface method → overriding method | `CallGraphEngine.buildOverrideMap` 已实现基础关系 | — |
| `[ ]` | 静态调用 | override relation 参与 impact tracing | ChangePoint/seed 能通过 override relation 扩展 caller/callee | `ImpactTracer` 从不调用 `CallGraph.getOverrides()` | P0 |
| `[ ]` | 静态调用 | self recursion edge | 保留 method → 自身的递归证据 | extraction 主动丢弃 caller 与 callee 相同的 edge | P1 |
| `[x]` | 静态调用 | 多 method cycle | 构图并在反向追踪时避免死循环 | BFS 使用 visited set，已有循环测试 | — |
| `[ ]` | JVM 动态机制 | lambda 完整建模 | 将 lambda creation、synthetic body、captured target 串成调用链 | 依赖 WALA 默认能力，无专项实现、seed 支持或验收测试 | P0 |
| `[ ]` | JVM 动态机制 | method reference | 从 bootstrap handle 识别被引用 method 和业务 caller | seed scanner 不读取 `invokedynamic` | P0 |
| `[ ]` | JVM 动态机制 | 通用 `invokedynamic` | 解析 bootstrap semantics 并形成可信 edge | 无 dedicated enricher | P1 |
| `[x]` | Reflection | literal `Class.forName` 到 application `<clinit>` | 创建 caller → target `<clinit>` edge | `ReflectionEnricher` 已实现并测试该受限场景 | — |
| `[ ]` | Reflection | literal 数据流准确性 | 仅当字符串确实是该 `Class.forName` 参数时创建 edge | 当前只记忆最近一次 String `LDC`，没有 operand stack/data-flow 分析 | P0 |
| `[ ]` | Reflection | non-literal `Class.forName` | 通过常量传播、配置证据或 runtime hint 解析 class | 当前只 warn 并跳过 | P1 |
| `[ ]` | Reflection | `Method.invoke`/`Constructor.newInstance`/field access | 解析 member target 并创建动态调用或访问 edge | 当前未实现 | P0 |
| `[ ]` | Reflection | `MethodHandle`/`VarHandle` | 解析 lookup、handle target 和 invoke semantics | 当前未实现 | P1 |
| `[ ]` | Reflection | dynamic proxy | 连接 caller、InvocationHandler、interface method 和 target | 检测到 `Proxy.newProxyInstance` 时只 warn | P0 |
| `[x]` | ServiceLoader | 同 module service resource → provider constructor | 从 `target/classes/META-INF/services` 创建 synthetic service edge | provider 必须位于同一 classes directory | — |
| `[ ]` | ServiceLoader | 真实 `ServiceLoader.load` caller → provider | 将实际 load/iteration caller 连接到 provider constructor/method | synthetic `<service-loader>` 没有连接真实 caller | P0 |
| `[ ]` | ServiceLoader | 跨 module provider | 从所有 application outputs 联合解析 interface/resource/provider | provider 只在 service resource 所在 classes directory 查找 | P1 |
| `[ ]` | ServiceLoader | dependency JAR provider/resource | 扫描 runtime classpath 中的 service 配置和 provider | 当前不扫描 dependency JAR | P1 |
| `[ ]` | JVM 动态机制 | JNI/native callback | 通过配置、symbol 或 runtime evidence 建立 native boundary | 当前未实现 | P2 |
| `[ ]` | Framework/runtime | DI container | 识别 constructor/field/method injection 和 bean lookup | 当前无 Spring/CDI/Guice model | P0 |
| `[ ]` | Framework/runtime | AOP/interceptor/proxy | 识别 advice、interceptor、proxy target 链 | 当前无 framework model | P0 |
| `[ ]` | Framework/runtime | controller/router | 将 HTTP route、controller method 和下游调用关联 | annotation/config 未扫描 | P1 |
| `[ ]` | Framework/runtime | event/listener | 识别 publish → listener callback | 当前无 event model | P1 |
| `[ ]` | Framework/runtime | scheduler | 识别 annotation/configured scheduled entrypoint | 当前无 scheduler model | P1 |
| `[ ]` | Framework/runtime | async/thread/executor | 识别 submit/start → `Runnable`/`Callable` callback | 当前无 callback enricher | P0 |
| `[ ]` | Framework/runtime | serialization lifecycle | 识别 serializer、deserializer、`readObject` 等 callback | 当前无 serialization model | P2 |
| `[ ]` | 跨边界调用 | RPC/HTTP client | 通过 interface/config/IDL 连接跨服务调用 | 当前只分析进程内 application bytecode | P2 |
| `[ ]` | 跨边界调用 | MQ/stream | 连接 producer topic 与 consumer handler | 当前未实现 | P2 |
| `[x]` | ChangePoint seed | method instruction 精确匹配 | 按 owner、name、descriptor 匹配 method body/remove/descriptor change | `visitMethodInsn` 已覆盖三类可扫描 method ChangePoint | — |
| `[x]` | ChangePoint seed | field instruction 精确匹配 | 按 owner、name、descriptor 匹配 field remove/descriptor change | `visitFieldInsn` 已覆盖 | — |
| `[x]` | ChangePoint seed | type instruction class reference | `NEW`、`ANEWARRAY`、`CHECKCAST`、`INSTANCEOF` 引用 removed class 时形成 seed | `visitTypeInsn` 已覆盖 | — |
| `[x]` | ChangePoint seed | method/field owner class reference | invocation/access 的 owner 为 removed class 时形成 seed | method 和 field matcher 都调用 class matcher | — |
| `[x]` | ChangePoint seed | descriptor changed 使用 new descriptor | target bytecode 引用新 descriptor 时形成 seed | ChangePoint 保存 new descriptor，scanner 精确匹配 | — |
| `[ ]` | ChangePoint seed | baseline removed member reference | 从 baseline bytecode 找到升级前对旧 descriptor/member 的引用 | scanner 只读取 target classes | P0 |
| `[ ]` | ChangePoint seed | class literal | `SomeType.class` 引用 removed class 时形成 seed | scanner 不处理 `visitLdcInsn(Type)` | P0 |
| `[ ]` | ChangePoint seed | method/field descriptor type | removed class 仅出现在 declaration/call descriptor 时形成 seed | scanner 不解析 descriptor 内 type | P0 |
| `[ ]` | ChangePoint seed | superclass/interface | 继承或实现 removed class/interface 时形成 seed | scanner 的 class visitor 不匹配 `superName/interfaces` | P0 |
| `[ ]` | ChangePoint seed | annotation/type annotation | annotation type/value 引用 changed class/member 时形成 seed | scanner 不访问 annotation | P1 |
| `[ ]` | ChangePoint seed | `invokedynamic` bootstrap reference | method reference/bootstrap argument 指向 changed method 时形成 seed | scanner 不处理 `visitInvokeDynamicInsn` | P0 |
| `[ ]` | ChangePoint seed | `MULTIANEWARRAY` class reference | 多维 reference array element 为 removed class 时形成 seed | scanner 不处理 `visitMultiANewArrayInsn` | P1 |
| `[ ]` | ChangePoint seed | generic signature | changed class 仅出现在 generic signature 时形成 seed | scanner 不解析 signature | P2 |
| `[x]` | ChangePoint seed | 非 `ADDED` kind gate | 只扫描 6 类删除/修改 ChangePoint | `isScannable` 明确排除三类 `ADDED` | — |
| `[ ]` | ChangePoint seed | `ADDED` change impact semantics | 定义新增 API/class/field 对行为、shadowing、dispatch 的影响模型 | 即使 CLI 纳入 `ADDED`，tracer 也记为 `CHANGE_KIND_NOT_APPLICABLE` | P2 |
| `[x]` | 路径追踪 | seed 精确解析 | 使用 owner、name、descriptor 映射 Call Graph method | `ImpactTracer.resolveInCg` 已实现 | — |
| `[x]` | 路径追踪 | incoming edge 反向 BFS | 从直接依赖引用 seed 找到所有可达 application caller | 已实现 visited queue traversal | — |
| `[x]` | 路径追踪 | graph root 识别 | 将 visited 子图中无 incoming application edge 的 method 作为 root | `findRoots` 已实现 | — |
| `[x]` | 路径追踪 | cycle fallback | 纯环无 root 时使用 seed 作为 root | 已实现 | — |
| `[x]` | 路径追踪 | 跨 module boundary | 记录 path 涉及 module 和 `callerModule->calleeModule` | 已实现并测试 | — |
| `[ ]` | 路径追踪 | 所有可能调用路径枚举 | 同一 root/seed 的多条不同路径均可输出或汇总 | predecessor 每个 caller 只保留首次发现 edge | P1 |
| `[ ]` | 路径追踪 | 真实业务入口识别 | 区分 HTTP endpoint、consumer、scheduler、public API 等语义入口 | 当前 root 仅表示“没有 application incoming edge” | P0 |
| `[ ]` | 路径追踪 | override map traversal | 通过 parent/override 关系补充反向和正向链 | override map 未被 tracer 消费 | P0 |
| `[ ]` | 路径追踪 | 第三方中间链 | 识别 application → dependency A → application/dependency B | Call Graph 只保留 application-to-application edge | P0 |
| `[ ]` | 路径追踪 | 最终 dependency call edge | 将 seed → ChangePoint owner/member 显式建模为有 evidence 的 path edge | 当前 ChangePoint 单独挂在 `ImpactPath`，不属于 CallEdge | P1 |
| `[x]` | 结果质量 | `NO_SEED_FOUND` | 区分没有 application bytecode 直接引用 | 已实现计数 | — |
| `[x]` | 结果质量 | `INCOMPLETE_CHAIN` | 区分 seed 无法精确映射到 Call Graph | 已实现计数 | — |
| `[x]` | 结果质量 | `CHANGE_KIND_NOT_APPLICABLE` | 区分不适用于当前追踪模型的 ChangePoint | 已实现计数 | — |
| `[x]` | 结果质量 | path 稳定排序 | 按 affected method 和 ChangePoint stable key 排序 | `ImpactTracer.pathComparator` 已实现 | — |
| `[x]` | 结果质量 | edge kind/evidence | 保存 invoke kind 或 enricher evidence | `CallEdge` 已包含 `EdgeKind` 和 evidence | — |
| `[ ]` | 结果质量 | path 去重 | 合并多 seed、重复 edge 或相同语义 path | 当前没有统一 semantic dedup | P1 |
| `[ ]` | 结果质量 | confidence/不确定性 | 标明 RTA over-approx、动态缺口和每条 edge 的可信度 | 当前只有 kind/evidence，没有 confidence model | P1 |
| `[ ]` | 结果质量 | false positive/false negative 汇总 | 报告当前 scope、未解析 class、动态机制等覆盖风险 | 仅部分场景输出 warn，未形成完整 coverage summary | P1 |

## 3. Bytecode 分析应忽略项矩阵

本表只描述“不会改变目标程序语义，或应经 canonicalization 后消除”的差异。不能因为 ASM 使用了 `Label` 等中间对象，就连同 branch/CFG 语义一起忽略。

| 状态 | 忽略域 | 条目 | 忽略理由 | 当前实现/缺口 | 验证方式 |
|---|---|---|---|---|---|
| `[x]` | Debug | `LineNumberTable` | 仅映射 source line，不改变 JVM execution semantics | class 读取和 method hash 均使用 `ClassReader.SKIP_DEBUG` | `StableHashMethodVisitorTest.lineNumberDoesNotAffectHash`、`BytecodeDiffEngineTest.debugInfoDoesNotProduceChange` |
| `[x]` | Debug | `LocalVariableTable` | debug local name/scope 不改变执行语义 | `SKIP_DEBUG` 不向 visitor 发送 local variable 信息 | 增加/改变 local debug info 时 hash 应相同 |
| `[x]` | Debug | `LocalVariableTypeTable` | debug generic local type 不改变执行语义 | 随 local variable debug 信息跳过 | 增加/改变 local generic debug info 时 hash 应相同 |
| `[x]` | Debug | `SourceFile`/`SourceDebugExtension` | source 文件名和扩展 debug mapping 不改变执行语义 | class model 不保存，`SKIP_DEBUG` 跳过 source debug visit | 修改 source debug attribute 时 ChangePoint 应为空 |
| `[x]` | Verification metadata | `StackMapTable`/ASM frame | frame 可从 code 推导，不应单独形成业务变更 | `visitFrame` no-op；hash reader 未要求 expand frame | 只改变 frame encoding 时 hash 应相同且 class 仍合法 |
| `[ ]` | Control flow identity | ASM `Label` identity、byte offset | 对象 identity 和绝对 offset 应忽略，但规范化 CFG target 必须保留 | 当前 `visitLabel` no-op 且 jump/switch 不记录 target，过度忽略导致真实 CFG 变化漏报 | 构造 opcode 相同、jump target 不同的两个 method；应产生 body change |
| `[x]` | Derived code metadata | `max_stack`、`max_locals` | 合法 class 中可由 code/data-flow 重算，不应单独形成业务变更 | visitor 未将 `visitMaxs` 纳入 hash | 仅改变合法的冗余 max 值时 hash 应相同 |
| `[x]` | ClassFile encoding | constant pool index 和排列顺序 | index 是序列化细节，应按解析后的 symbolic value 比较 | ASM visitor 提供解析后的 owner/name/descriptor/value，hash 不读取 raw index | 重排 constant pool 且保持引用语义时 diff 应为空 |
| `[x]` | ClassFile encoding | class member declaration 顺序 | method/field 顺序通常不改变 JVM lookup semantics | class、method、field 均先转为 map 后比较 | 调换 method/field declaration 顺序时 diff 应为空 |
| `[ ]` | ClassFile encoding | attribute/annotation 非语义顺序 | 应按 attribute 类型和 canonical value 比较，而非 raw order | 当前相关 attribute/annotation 整体未分析，不能证明只忽略顺序 | 实现 metadata diff 后增加顺序置换测试 |
| `[x]` | Instruction encoding | switch padding/alignment | padding 只用于 ClassFile 对齐 | ASM visitor 不暴露 raw padding | 重新编码 padding、保持 switch 语义时 hash 应相同 |
| `[x]` | JAR container | ZIP compression method、timestamp、CRC、extra field | 容器 metadata 不改变 class bytecode 语义 | `JarClassIndexer` 只读取 entry name 和解压后 bytes | 用不同 ZIP metadata 打包相同 class 时 diff 应为空 |
| `[x]` | JAR container | 普通 JAR entry 顺序 | 每个唯一 internal class name 应独立比较 | class index 为 map，普通无重复 class 不依赖 entry 顺序 | 调换唯一 class entry 顺序时 diff 应为空 |
| `[ ]` | JAR container | Multi-Release duplicate entry 顺序 | entry 顺序应忽略，但必须先按 runtime 选择 effective version | 当前相同 internal name 会覆盖，结果可能依赖 ZIP enumeration 顺序 | 同一 Multi-Release JAR 重排 entry 后结果必须相同 |
| `[x]` | JAR container | manifest build timestamp、JAR signature 等非 class entry | 不属于 class bytecode diff；需要时应由独立 artifact integrity/resource analyzer 处理 | 所有非 `.class` entry 均跳过 | 只改变 manifest/signature entry 时 bytecode ChangePoint 应为空 |
| `[ ]` | Compiler noise | local variable slot 重编号 | 在 data-flow 等价时不应形成业务变更 | 当前 hash 包含 `visitVarInsn.varIndex` 和 `IINC.varIndex` | 对等价 method 做 local slot canonicalization 后 hash 应相同 |
| `[ ]` | Compiler noise | synthetic temporary/local naming | 无 runtime/reflection contract 的临时命名应忽略 | debug name 已忽略，但 synthetic method/class/member 仍无语义分类 | 分离 compiler artifact 与用户可见 synthetic/bridge contract 后测试 |
| `[ ]` | Compiler noise | 等价 instruction 变体、冗余 `NOP` | 可配置 semantic mode 可忽略等价 codegen 差异；strict mode 仍应报告 | 当前任何 opcode 序列变化都会改变 hash | 定义 strict/semantic policy，并用等价 codegen fixtures 验证 |
| `[ ]` | Compiler noise | lambda/synthetic class 不稳定编号 | 编译器生成编号变化应通过结构关联，避免 remove+add 噪音 | 当前按 raw internal name 和 method name 比较 | 相同 lambda 仅编号变化时应关联为同一逻辑实体 |
| `[ ]` | Custom metadata | 未知/custom ClassFile attribute | 只有确认无 runtime/instrumentation 语义后才能忽略 | 当前未读取，等同于无条件忽略 | 建立 attribute allowlist/denylist；未知项进入诊断而非静默跳过 |

## 完成定义

更新任一 checkbox 前，必须同时满足：

1. production code 已覆盖该行描述的完整边界，而不是只有相邻能力或部分分支。
2. 至少存在一个 positive test；涉及 ignore、false positive 或 soundness 的能力还必须有 negative test。
3. 报告能够表达该能力的结果或明确的未报告原因。
4. 新能力没有通过扩大静默忽略范围掩盖真实变更。
