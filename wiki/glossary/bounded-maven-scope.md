---
name: "Bounded Maven Scope"
type: glossary
---

## Definition

Bounded Maven Scope 是从用户指定的入口 POM 出发，结合 active ancestor aggregator 与 Maven activation context 得到的有限 Reactor 和 Module 集合。

## Usage

该范围决定 Maven command 的执行根、`-pl`/`-am` 参数和报告纳入的 Module；它防止 Analyzer 扫描或报告同一 repository 中与入口无关的 POM。

## Aliases

- `bounded scope`
- `bounded reactor`

## Distinctions

- Git repository：提供源码与 snapshot 边界；Bounded Maven Scope 只选择其中与入口 POM 相关的 Maven structure。
