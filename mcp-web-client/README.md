# MCP Web 客户端模块（骨架）

当前模块仅包含 Maven 描述和基础配置，源码及依赖尚未添加。

## 运行

模块目前没有可启动的应用入口。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl mcp-web-client -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
