---
name: "User Manual Maintenance"
type: rule
---

## Overview

本规则要求 `docs/user-manual.md` 成为可与 [Dependency Analyzer CLI](../c4/containers/dependency-analyzer-cli.md) executable JAR 独立交付的用户任务闭包。

## Scope

- 修改 CLI command、option、runtime prerequisite、analysis behavior、Report、status、exit code、failure mode 或 third-party distribution。
- 直接编辑 `docs/user-manual.md`。

## Rules

- **必须**让用户只持有 JAR、target project 与手册时，仍可准备环境、执行三个 Use Case、判断成功、解释限制并处理常见故障。
- **必须**在同一次用户可见行为变更中同步 required/default/repeatable/enum、runtime、Report layout、status、exit code 与 troubleshooting。
- **必须**让 tutorial 包含前置条件、最小 command、success evidence、output 与下一步；reference 保存精确 option 与 status。
- **必须**以最终 packaged JAR 的 `--help` 验证 CLI contract，并以实际 output 验证非-help behavior。
- **必须**默认使用中文；command、option、field、status、enum 与既有技术标识符保持原样且术语一致。
- **禁止**用 repository-relative link、Wiki、source path、README 或 benchmark 文档补足用户完成任务所需的信息。
- **禁止**把 internal Schema、class、cache、shard、Document Object Model（DOM）或 build process 因出现在 Wiki 而自动写入手册。

## Verification

- 先按 [Build Test and Package](../runbooks/build-test-and-package.md) 产出 executable artifact，再核对 root、`impact`、`tree`、`tree analyze` 与 `tree diff` help。
- 检查 fenced code block、internal anchors、required/default values、Report statuses 与 troubleshooting structure。
- 确认手册不依赖 source checkout，且不把文档检查解释为 benchmark authorization。

## Non-Goals

- 用户手册不承担 source build、release、benchmark 或内部 architecture 说明。
