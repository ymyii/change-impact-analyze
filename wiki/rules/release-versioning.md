---
title: "Release Versioning"
type: rule
---

# Rule: Release Versioning

## Summary

Analyzer、Dependency Evidence Plugin、公共JDK model engine与JDK 8 model使用四条独立SemVer版本线。Git annotated tag记录Stable release version；各自Maven POM是当前开发或release version的build source。Repository不维护额外version ledger、source fingerprint或发布脚本。

## Rules

- Version只允许`MAJOR.MINOR.PATCH`或`MAJOR.MINOR.PATCH-SNAPSHOT`。
- 日常开发使用下一次计划release的固定Snapshot version；同一release周期内重复`clean install`不因每次自测bump。
- Analyzer version位于root`revision`；Dependency Evidence Plugin version位于`plugins/pom.xml`的`revision`。
- 公共JDK engine version位于`models/jdk/pom.xml`的`project.version`。
- JDK 8 model version位于`models/jdk8/pom.xml`的`project.version`；其公共engine dependency version位于`jdk-models.version`。
- Analyzer消费的JDK 8 model dependency version位于root`jdk8-models.version`；默认与release profile均以SemVer gate验证。
- JDK 8 model构建前必须先安装coordinate匹配的公共engine；公共Snapshot source变化后必须重新`clean install`。
- 每个独立artifact的`release` profile只接受Stable SemVer并拒绝Snapshot dependency；默认profile接受Stable与Snapshot。
- 可被其他reactor消费的model POM必须flatten，不得要求consumer解析repository root parent或`${revision}`。
- Release commit包含已验证的Stable POM与文档。Tag分别为`analyzer-vX.Y.Z`、`dependency-evidence-plugin-vX.Y.Z`、`jdk-models-vX.Y.Z`与`jdk8-models-vX.Y.Z`。
- Dev build不要求Git commit或tag。Stable release才要求commit/tag；本规则不增加clean worktree fingerprint gate。

版本变化语义：

- `MAJOR`：CLI、Schema、Plugin goal、model公共API或catalog contract的breaking change。
- `MINOR`：向后兼容能力、新summary template/state slot或新增modeled method。
- `PATCH`：既有语义修复、可靠性、内部重构与build修复。

## Applies To

- 修改任一artifact version、跨artifact dependency version、consumer packaging或release操作时适用。
- 公共engine与JDK 8 model虽都从`0.1.0-SNAPSHOT`开始，但后续独立bump，不要求保持相同version。
- Maven Dependency Plugin`3.6.1`与Apache Maven`3.6.3`是runtime dependency version，不使用本项目tag namespace。

## Verification

默认SemVer、Java和JDK 8文件约束由Maven Enforcer在`validate`阶段执行。Stable release按dependency顺序执行：

```sh
mvn -f models/jdk/pom.xml -Prelease clean install
mvn -f models/jdk8/pom.xml -Prelease clean install
mvn -f plugins/pom.xml -Prelease clean install
mvn -Prelease clean verify
```

JDK 8路径通过`-Dtest.jdk8.home=/absolute/path/to/jdk8`覆盖。发布model前检查`target/flattened-pom.xml`已解析parent、`${revision}`和dependency version。

Version修改使用Versions Maven Plugin或直接修改对应module的唯一version source；禁止引入新的project-owned version ledger。公共engine release后，JDK 8 model必须显式更新`jdk-models.version`才能引用新coordinate。

Git record verification：

```sh
git show analyzer-vX.Y.Z
git show dependency-evidence-plugin-vX.Y.Z
git show jdk-models-vX.Y.Z
git show jdk8-models-vX.Y.Z
```

## Non-Goals

- 不为日常 Snapshot 自测要求每次 bump、commit 或创建 tag。
- 不维护 Maven POM 与 Git annotated tag 之外的 version ledger 或 fingerprint record。
