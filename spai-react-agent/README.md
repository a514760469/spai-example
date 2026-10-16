# Spring AI Alibaba React Agent 示例

使用 Spring AI Alibaba Agent Framework 构建 ReAct Agent，并集成 DashScope、MCP Client 和 Studio 相关依赖。

## 运行

检查 `src/main/resources/application.yaml` 及环境配置，设置模型凭证和 MCP 服务信息后启动。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl spai-react-agent -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
