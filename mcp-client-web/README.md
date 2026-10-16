# MCP 客户端 Web 配置示例

提供 MCP Router 相关依赖以及管理 MCP 配置的 Web 控制器；包含 `configRequests.http` 请求示例。

## 运行

检查 `src/main/resources/application.yml`，启动应用后可参考 `src/main/resources/configRequests.http` 调用接口。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-client-web -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
