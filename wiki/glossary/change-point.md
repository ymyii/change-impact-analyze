---
name: "Change Point"
type: glossary
---

## Definition

Change Point 是依赖新旧版本间经过语义过滤后仍需追踪的稳定变化身份，可表示 class、member、method body、JVM access 或 ServiceLoader registration 的变化。

## Usage

Impact 分析把 Change Point 与来源 artifact、Module ownership 和可定位的结构证据绑定，再据此查询调用关系；物理 JAR 路径和源码行号不属于其稳定身份。

## Aliases

- `ChangePoint`

## Distinctions

- Dependency change：表示 Maven artifact 的版本或解析状态变化；一个 dependency change 可以产生零个或多个 Change Point。
