# 对话记忆示例

展示 Spring AI Alibaba 对话记忆能力，包含 Redis 记忆、Mem0 集成及自定义持久化相关代码。

## 运行

先配置 `src/main/resources/application.yml` 中的模型、Redis 和数据库连接，再启动应用。按需准备对应服务。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl chat-memory -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
