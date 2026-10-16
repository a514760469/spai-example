# LangChain4j 入门示例

使用 LangChain4j Spring Boot Starter 定义 AI 服务，并通过 Web 控制器提供聊天示例。

## 运行

检查 `src/main/resources/application.yaml` 的模型参数与密钥后，启动 `LangChain4j01Application`。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl example-langchain4j-01 -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
