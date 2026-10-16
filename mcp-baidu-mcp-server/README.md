# 百度地图 MCP 示例

结合 Spring AI MCP Server、MCP Client 与 DashScope，提供百度地图 MCP 相关服务和 LLM 配置示例。

## 运行

检查 `src/main/resources/application.yml` 和 `mcp-server.json5`，配置所需服务地址与密钥后启动。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-baidu-mcp-server -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
