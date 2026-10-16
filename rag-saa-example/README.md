# Spring AI Alibaba RAG 示例

基于 Spring AI Alibaba RAG 与 DashScope 的最小示例模块。

## 运行

检查应用配置中的模型密钥后启动 `RagSaaApplication`。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl rag-saa-example -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
