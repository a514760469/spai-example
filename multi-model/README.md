# 多模态模型示例

基于 Spring AI OpenAI 模型 starter 提供多模态 Web 示例，并包含视频帧提取辅助代码。

## 运行

检查 `src/main/resources/application.yaml` 中的模型服务配置后启动应用。

本模块继承仓库根目录的 Maven 父工程，建议从仓库根目录构建：

```bash
mvn -pl multi-model -am package
```

若为 Spring Boot 应用，可在本模块目录使用 `mvn spring-boot:run` 启动。请先按需检查并修改配置文件；不要提交真实密钥或口令。

更多项目说明见[仓库根 README](../README.md)。
