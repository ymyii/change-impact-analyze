---
name: "Command Control"
type: component
parent: "[[c4/containers/dependency-analyzer-cli]]"
relations:
  - target: "[[c4/components/dependency-analyzer-cli-workspace-scope]]"
    description: "下发已校验的代码库与快照选择。"
  - target: "[[c4/components/dependency-analyzer-cli-maven-runtime]]"
    description: "请求本次命令共用且已校验的 Maven runtime。"
  - target: "[[c4/components/dependency-analyzer-cli-impact-tracing]]"
    description: "启动依赖升级影响分析并接收冻结结果。"
  - target: "[[c4/components/dependency-analyzer-cli-dependency-trees]]"
    description: "启动单侧或双侧依赖树分析并接收结果状态。"
---

## Overview

Command Control 定义公共 CLI、按命令执行的 Preflight、Diagnostic context、Stage lifecycle 和 Runtime Metrics 边界。

## Responsibilities

- 在分析副作用前验证 option 组合、filesystem、Git、JDK、Maven 与 output contract。
- 以稳定 Stage、substage 和 identity 输出低成本 Diagnostic，并按 verbosity 控制高成本 evidence。
- 将 handled Module failure 与 command-level failure 分离。

## Technology

- Picocli 4.7.6：定义 CLI command、option 与参数校验边界。
- Java structured diagnostics：输出可关联的运行阶段、进度与失败证据。

## Interfaces

- Root 与 subcommand contract：参数解析失败或 Preflight 阻断返回 `1`，不启动 pipeline。
- Diagnostic contract：每个物理行使用稳定五段 prefix；敏感 settings、credential 与未过滤参数不得进入输出。

## State and Data

Runtime Metrics scheduler 与 Diagnostic context 只属于当前 command；关闭 command 时终止，不影响结果状态。

## Boundaries

该 Component 负责控制流入口和运行证据；不拥有 Git worktree、Maven execution、分析算法或 HTML schema。
