# Nacos MCP 服务示例

基于 Spring AI MCP Server 与 Nacos，提供 Nacos 配置查询相关 MCP 工具。

## 运行

检查 `src/main/resources/application.yaml` 中的 Nacos 地址和连接配置，确保 Nacos 可访问后启动。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-nacos-server -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
