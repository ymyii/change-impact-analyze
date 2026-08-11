# Impact Medium Java Fixture

Deterministic Java 8 Maven fixture for `dependency-analyzer impact`.

- 42 个 direct dependencies；`scope-conflict-marker` 是 direct `test` winner，`external-plain` 同时引入其 transitive `compile` duplicate。
- `vendor-lib-34` 由 direct 改为 `external-plain` 的 transitive dependency，selected external classpath 规模不变。
- `impact-baseline`：`scenario-api:1.0.0`。
- `impact-target`：`scenario-api:2.0.0`。
- 显式分析 `CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- bridge 固定以 API v1 编译，使 target bytecode 保留 old method、field 和 removed class references。
