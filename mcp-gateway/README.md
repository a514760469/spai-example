# MCP Gateway 示例

使用 Spring AI Alibaba MCP Gateway 聚合 MCP 服务，并提供 Gateway 控制器示例。

## 运行

检查 `src/main/resources/application.yaml` 中的网关、上游 MCP 服务和模型配置后启动应用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-gateway -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
