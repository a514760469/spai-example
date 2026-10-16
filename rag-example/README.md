# Spring AI RAG 示例

包含文档读取、向量存储和检索增强生成示例，依赖涵盖 Neo4j、Redis、PgVector、Elasticsearch、Qdrant 等向量存储集成。

## 运行

先阅读 `src/main/resources/application.yaml` 并配置所选模型、向量存储及连接服务；示例文档位于 `src/main/resources/data/`。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl rag-example -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
