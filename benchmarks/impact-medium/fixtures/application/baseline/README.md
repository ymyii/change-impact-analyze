# Impact Medium Java Fixture

Deterministic Java 8 Maven fixture for `dependency-analyzer impact`.

- 40 个 direct dependencies；`scope-conflict-marker` 是 direct `test` winner，`external-plain` 同时引入其 transitive `compile` duplicate。
- `vendor-lib-34` 由 direct 改为 `external-plain` 的 transitive dependency，selected external classpath 规模不变。
- `impact-baseline`：`scenario-api:1.0.0`。
- `impact-target`：`scenario-api:2.0.0`。
- 显式分析全部 ChangePointKind，包括 `SERVICE_PROVIDER_REGISTRATION_REMOVED`。
- bridge 固定以 API v1 编译，使 target bytecode 保留 old method、field 和 removed class references。
- `DynamicLoadingUseCase`固定覆盖`Class.forName`与ServiceLoader的direct/local/same-phi，以及concat/field/return unsupported输入。
- `scenario-api` v1同时注册`RemovedProvider`、`RegistrationOnlyProvider`与`StableProvider`；v2删除provider class、仅删除registration，并保留一个有效provider。
