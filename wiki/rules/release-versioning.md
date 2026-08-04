---
title: "Release Versioning"
type: rule
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "Analyzer 与内置 Maven Plugin 的 module/GAV 边界"
  - path: "wiki/features/maven-runtime.md"
    desc: "内置 Plugin release coordinate 与 runtime fingerprint"
  - path: "wiki/runbooks/version-and-distribution.md"
    desc: "执行 version bump 与 distribution build 的操作入口"
  - path: "wiki/runbooks/build-test-package.md"
    desc: "日常 Maven quality gate 与正式 distribution gate 的分工"
code_refs:
  - path: "build-support/version-contract.properties"
    desc: "当前 version、历史 release fingerprint 与稳定 output timestamp ledger"
  - path: "scripts/version.sh"
    desc: "Version contract 的 POSIX shell 入口"
  - path: "scripts/internal/VersionTool.java"
    desc: "SemVer、fingerprint、单调性与原子 bump 实现"
  - path: "pom.xml"
    desc: "Analyzer revision、Plugin dependency version 与 reproducible timestamp"
  - path: "plugins/artifact-path-resolver/pom.xml"
    desc: "Artifact Path Plugin 独立 release version"
---

# Rule: Release Versioning

## Summary

Analyzer 与每个内置 Maven Plugin 使用独立 SemVer。Release version、source fingerprint 和稳定 build timestamp 由 `build-support/version-contract.properties` 统一登记；已登记的 release version 不可复写、回退或绑定不同输入。

<!-- version-contract:start -->
- Analyzer release: `1.1.0`
- Artifact Path Plugin release: `2.0.0`
<!-- version-contract:end -->

## Version Contract

- 正式 version 必须严格匹配 `MAJOR.MINOR.PATCH`；禁止 `SNAPSHOT`、pre-release 和 build metadata。
- `MAJOR`：CLI option/exit code、公开 Schema、Plugin goal/parameter、Maven compatibility boundary 或其他 breaking change。
- `MINOR`：向后兼容的新功能、可选字段或新能力。
- `PATCH`：bug fix、性能/可靠性修复、内部重构和 dependency/build 修复。
- Analyzer production binary 输入变化必须提升 Analyzer version。
- Artifact Path Plugin production binary或 consumer POM template 变化必须提升 Plugin version。
- Plugin bump 必须同时 bump Analyzer，因为 distribution 内嵌 Plugin；Analyzer-only change 不要求 Plugin bump。
- Plugin release GAV 不可复写。Maven local repository 中的旧 release 通过新 version coordinate 自然隔离，不删除用户 cache。
- Version magnitude 由开发者依据 compatibility 判断；version tool 只验证合法性、单调性、fingerprint 和跨 module 一致性。

## Fingerprint Boundary

Analyzer fingerprint 覆盖：

- root `pom.xml` 与 `analyzer/pom.xml`；
- `analyzer/src/main/` production source/resources；
- 由 root POM 引入的 Artifact Path Plugin version 与 fingerprint。

Artifact Path Plugin fingerprint 覆盖：

- `plugins/pom.xml` 与 `plugins/artifact-path-resolver/pom.xml`；
- Plugin `src/main/` production source/resources；
- `build-support/artifact-path-plugin-consumer.pom.template`。

Tests、wiki 和 user manual 不进入 binary fingerprint。它们仍必须与当前 contract 保持一致；受控 current-version marker 在 `version.sh bump` 中统一更新。

## Required Gate

任何 production build 前执行：

```sh
./scripts/version.sh verify
```

若 source fingerprint 与 ledger 不一致，必须先选择正确的 SemVer magnitude 并执行 bump；禁止直接编辑 ledger 中的 SHA-512 绕过 gate。

## Non-Goals

- 不根据 diff 自动猜测 breaking change。
- 不允许相同 version 重新 seal 新 fingerprint。
- 不以 Git tag 或 Maven local repository 代替 tracked release ledger。
- 不承诺 Maven 4 compatibility；当前 Plugin compatibility boundary 为 `3.6.3 <= Maven version < 4.0.0`。
