---
title: "Maven Build Runner Acceptance Criteria"
type: ac
---

# Acceptance Criteria: Maven Build Runner

本页验收 [Maven Build Runner](../../implementation/maven-build-runner.md)。

## Functional

1. 场景：构建 owned leaf
   - Given：入口 POM 被一个祖先 active module closure 拥有。
   - When：规划并执行 compile。
   - Then：
     - Mode 为 `OWNED_LEAF`。
     - Maven invocation 使用祖先 aggregator 与 `-pl <relative-path> -am`。

2. 场景：构建 standalone project
   - Given：没有祖先 active module closure 包含入口 POM。
   - When：规划 compile。
   - Then：
     - Mode 为 `STANDALONE`。
     - Invocation 不附加 `-pl` 或 `-am`。

## Failure

1. 场景：compile 失败
   - Given：Maven compile 返回非零 exit code。
   - When：Build Runner 收集 process result。
   - Then：
     - 后续 dependency evidence stage 不启动。
     - Diagnostic 保留 bounded process output tail。

## Non-Functional

- [ ] 当 repository 包含无关 POM 时，scope planning 访问范围不超过入口目录、入口祖先链与所选 active module closure。
