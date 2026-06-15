# Change Impact Analyze

变更影响分析工具。

## 前置条件

- Java 17+
- Maven 3.6.1+

## 构建

```bash
mvn clean package
```

## 测试

```bash
# 单元测试
mvn test

# 集成测试 + 全量验证
mvn verify
```

## 运行

```bash
# 查看帮助
java -jar target/change-impact-analyze.jar --help
```

## 项目结构

```
src/main/java/         # 主代码
src/test/java/         # 单元测试
src/integration-test/  # 集成测试
```
