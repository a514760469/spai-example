# Spring AI 对话示例

展示通过 Spring AI 调用 DashScope、DeepSeek、OpenAI 及 Ollama 等模型的 Web 对话接口，并包含图像模型相关配置。

## 运行

检查 `src/main/resources/application.yml` 中的模型配置和密钥，再启动 `ai.lifo.spai.chat.ChatApplication`。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl chat-example -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
