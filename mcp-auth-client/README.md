# MCP 鉴权客户端示例

使用 Spring AI MCP Client 连接 MCP 服务，包含客户端 HTTP 配置、处理器及 MCP 服务配置文件。

## 运行

检查 `src/main/resources/application.yml`、`mcp-servers-config.json` 和 `mcp-server.json5`，按需设置凭证及服务地址后启动应用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-auth-client -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
