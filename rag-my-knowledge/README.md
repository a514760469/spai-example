# 个人知识库 RAG 示例

提供知识文档读取与 RAG 接口，包含 Markdown/Tika 文档读取、自定义 ETL 及 PgVector 向量存储配置。

## 运行

检查 `src/main/resources/application.yaml`，配置模型、Embedding、PostgreSQL/PgVector 后启动应用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl rag-my-knowledge -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
