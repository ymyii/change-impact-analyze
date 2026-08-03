# Impact Medium Java Fixture

Deterministic Java 8 Maven fixture for `dependency-analyzer impact`.

- 42 compile-scope external dependencies：40 个 lightweight vendor artifacts、`scenario-api` 和 `legacy-impact-bridge`。
- `impact-baseline`：`scenario-api:1.0.0`。
- `impact-target`：`scenario-api:2.0.0`。
- 显式分析 `CLASS_ADDED`、`CLASS_REMOVED`、`METHOD_ADDED`、`METHOD_REMOVED`、`METHOD_DESCRIPTOR_CHANGED`、`METHOD_BODY_CHANGED`、`FIELD_ADDED`、`FIELD_REMOVED`、`FIELD_DESCRIPTOR_CHANGED`。
- bridge 固定以 API v1 编译，使 target bytecode 保留 old method、field 和 removed class references。
