# Benchmarks

本目录保存可由 Git 复现的 benchmark fixture、脚本和使用手册。运行时生成的 nested Git repository、JAR、HTML report 与资源日志统一写入 `tmp-files/`，不进入版本控制。

- [`impact-medium/`](impact-medium/)：包含 42 个 direct dependencies 与 9 类 `ChangePointKind` 的 `impact` 中型 benchmark。
