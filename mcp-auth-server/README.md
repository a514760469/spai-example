# MCP 鉴权服务端示例

使用 Spring AI MCP WebFlux Server 提供 MCP 工具，包含请求过滤器、用户信息上下文及 Open-Meteo 示例服务。

## 运行

检查 `src/main/resources/application.yml` 的端口、鉴权及注册配置后启动应用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-auth-server -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
