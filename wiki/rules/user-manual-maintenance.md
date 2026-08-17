---
title: "User Manual Maintenance"
type: rule
relations:
  - path: "wiki/project/dependency-analyzer.md"
    desc: "用户手册是Dependency Analyzer面向分析执行者的独立交付文档"
  - path: "wiki/features/cli-preflight-diagnostics.md"
    desc: "CLI option、Preflight、Diagnostic和exit code是手册命令参考的行为来源"
  - path: "wiki/features/maven-runtime.md"
    desc: "Java、Maven、config dir和project dependency resolution是手册环境要求的行为来源"
  - path: "wiki/features/report-generator.md"
    desc: "impact页面、状态、筛选与离线使用是手册报告参考的行为来源"
  - path: "wiki/features/repository-dependency-tree-report.md"
    desc: "tree范围、冲突口径、增量发布与报告状态是手册tree章节的行为来源"
code_refs:
  - path: "docs/user-manual.md"
    desc: "面向用户的自包含操作、参考、解释与排障文档"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/cli/DependencyAnalyzerCli.java"
    desc: "Root CLI option与subcommand入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/impact/ImpactCommand.java"
    desc: "impact public option、validation与exit code入口"
  - path: "analyzer/src/main/java/io/github/dependencyanalysis/tree/TreeCommand.java"
    desc: "tree public option入口"
---

# Rule: User Manual Maintenance

## Summary

`docs/user-manual.md`必须是可以与已构建Analyzer JAR独立交付的用户任务闭包。用户不读取source repository、wiki、README或其他工程文档，也能够准备环境、运行`impact`与`tree`、解释报告和状态，并处理常见故障。

## Rules

### Self-Contained User Boundary

- 手册从用户已经获得可运行Analyzer JAR开始，不承担source build、release或Benchmark流程。
- 手册完成用户任务所需的前置条件、command、option、default、validation、output、status、limit和troubleshooting必须在文件内完整定义。
- 手册不得通过repository-relative link、wiki link、source path、Benchmark说明或其他工程文档补足必要信息。
- 手册不得依赖固定source checkout布局。JAR、JDK、Maven和用户project path使用可替换的语义占位符或用户环境示例。
- 目录内部anchor可以使用。第三方网页不能成为完成用户任务的必要依赖。

### Documentation Responsibilities

- Tutorial引导首次用户完成一次可验证的`impact`或`tree`执行；必须包含前置条件、最小command、success判断、output和下一步。
- How-to guide围绕一个实际目标提供步骤，不混入与该目标无关的内部实现说明。
- Reference集中记录精确事实，包括option、enum、default、Glob、status、exit code和report layout。
- Explanation只保留用户选择参数、判断可信度或理解限制所需的背景；内部Schema、class、cache、shard、DOM和build packaging不进入手册。
- Troubleshooting使用“现象、检查、处理、影响”结构，并说明失败是否保留旧report、是否影响其他Module或Reactor，以及对应exit code。

### Synchronization Triggers

以下用户可见变化必须在同一次交付中更新手册：

- CLI command、option、alias、required/default/repeatable规则、合法值、组合约束或validation。
- Java、JDK、Maven、Git、settings、config dir或runtime selection要求。
- `impact`或`tree`的输入范围、data semantics、analysis scope、Coverage limitation或failure isolation。
- Report文件结构、页面、字段、筛选、status、incremental publication或offline contract。
- Preflight status/decision、Diagnostic可见性、exit code或旧report保留语义。
- 用户可操作的troubleshooting入口，以及手册列出的第三方component/version/license。

纯内部重构仅在改变上述用户决策或可观察行为时更新手册。实现细节不能因为存在于wiki而自动进入用户手册。

### Sources of Truth

- CLI展示契约以最终打包JAR的root、`impact`和`tree` `--help`为准。
- 非help行为以对应feature contract和最终artifact实际行为为准。
- Wiki可为维护者提供工程事实，但用户手册必须重新表达必要事实，不能链接wiki代替正文。
- Artifact、source behavior与手册不一致时不得将手册视为已验证。

### Language and Terminology

- 文档默认使用中文；command、option、field、status、enum和既有技术标识符保持原样。
- 常见技术缩写可直接使用。项目或上下文专有缩写首次出现时写出完整名称与缩写，后续保持同一写法。
- 同一概念在tutorial、how-to、reference、explanation和troubleshooting中使用统一术语。
- 示例必须明确区分Analyzer JAR、用户target repository、Analyzer Java 17 runtime与`impact` target JDK 8。
- Fenced code block统一使用三个反引号，并按内容标注`sh`、`text`等info string；不得使用波浪号围栏。

## Applies To

- 所有新增或修改Dependency Analyzer CLI、runtime prerequisite、analysis behavior、Report、status、failure mode和第三方distribution信息的任务。
- 所有直接编辑`docs/user-manual.md`的文档任务。
- Code review、release review和artifact验收中对用户文档完整性的检查。

## Verification

涉及code change时，先按repository build规则产出最终artifact，再核对help：

```sh
java -jar target/dependency-analyzer.jar --help
java -jar target/dependency-analyzer.jar impact --help
java -jar target/dependency-analyzer.jar tree --help
```

完成前还必须确认：

- 一个只持有Analyzer JAR、target Git/Maven project和手册的用户能够完成两条quick-start路径。
- 全部fenced code block使用成对的三反引号，且不存在波浪号围栏。
- 手册不含repository-relative link，不以source/wiki path作为用户instruction。
- Required/default/alias/repeatable/enum、report status和exit code与最终artifact一致。
- 每个Coverage limitation和handled failure都能从手册判断结果可信度与后续动作。
- Benchmark只在用户明确授权时执行；手册验证本身不构成Benchmark授权。
