# MCP 文件系统客户端示例

使用 Spring AI MCP Client 与 DashScope，并包含文件系统和 GitHub 工具相关示例代码。

## 运行

检查 `src/main/resources/application.yaml` 与 `mcp-servers-config.json` 中的 MCP 服务命令、路径和模型参数后启动。外部 MCP 服务需先可用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-filesystem -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
