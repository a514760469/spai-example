# spai-example

Spring AI / Spring AI Alibaba 与 LangChain4j 的 Java 示例集合，采用 Maven 多模块结构，覆盖对话、多模态、Agent、MCP 和 RAG 等主题。各模块相互独立，可按需运行。

## 环境要求

- JDK 21
- Maven 3.8 或更高版本
- 按所选模块准备相应模型服务、API Key、数据库、向量存储或 MCP 服务

## 模块列表

| 模块 | 内容 |
| --- | --- |
| [`chat-example/`](./chat-example/README.md) | Spring AI 对话示例：展示通过 Spring AI 调用 DashScope、DeepSeek、OpenAI 及 Ollama 等模型的 Web 对话接口，并包含图像模型相关配置。 |
| [`chat-memory/`](./chat-memory/README.md) | 对话记忆示例：展示 Spring AI Alibaba 对话记忆能力，包含 Redis 记忆、Mem0 集成及自定义持久化相关代码。 |
| [`example-langchain4j-01/`](./example-langchain4j-01/README.md) | LangChain4j 入门示例：使用 LangChain4j Spring Boot Starter 定义 AI 服务，并通过 Web 控制器提供聊天示例。 |
| [`mcp-auth-client/`](./mcp-auth-client/README.md) | MCP 鉴权客户端示例：使用 Spring AI MCP Client 连接 MCP 服务，包含客户端 HTTP 配置、处理器及 MCP 服务配置文件。 |
| [`mcp-auth-server/`](./mcp-auth-server/README.md) | MCP 鉴权服务端示例：使用 Spring AI MCP WebFlux Server 提供 MCP 工具，包含请求过滤器、用户信息上下文及 Open-Meteo 示例服务。 |
| [`mcp-baidu-mcp-server/`](./mcp-baidu-mcp-server/README.md) | 百度地图 MCP 示例：结合 Spring AI MCP Server、MCP Client 与 DashScope，提供百度地图 MCP 相关服务和 LLM 配置示例。 |
| [`mcp-client-web/`](./mcp-client-web/README.md) | MCP 客户端 Web 配置示例：提供 MCP Router 相关依赖以及管理 MCP 配置的 Web 控制器；包含 `configRequests.http` 请求示例。 |
| [`mcp-filesystem/`](./mcp-filesystem/README.md) | MCP 文件系统客户端示例：使用 Spring AI MCP Client 与 DashScope，并包含文件系统和 GitHub 工具相关示例代码。 |
| [`mcp-gateway/`](./mcp-gateway/README.md) | MCP Gateway 示例：使用 Spring AI Alibaba MCP Gateway 聚合 MCP 服务，并提供 Gateway 控制器示例。 |
| [`mcp-nacos-server/`](./mcp-nacos-server/README.md) | Nacos MCP 服务示例：基于 Spring AI MCP Server 与 Nacos，提供 Nacos 配置查询相关 MCP 工具。 |
| [`mcp-web-client/`](./mcp-web-client/README.md) | MCP Web 客户端模块（骨架）：当前模块仅包含 Maven 描述和基础配置，源码及依赖尚未添加。 |
| [`multi-model/`](./multi-model/README.md) | 多模态模型示例：基于 Spring AI OpenAI 模型 starter 提供多模态 Web 示例，并包含视频帧提取辅助代码。 |
| [`rag-example/`](./rag-example/README.md) | Spring AI RAG 示例：包含文档读取、向量存储和检索增强生成示例，依赖涵盖 Neo4j、Redis、PgVector、Elasticsearch、Qdrant 等向量存储集成。 |
| [`rag-my-knowledge/`](./rag-my-knowledge/README.md) | 个人知识库 RAG 示例：提供知识文档读取与 RAG 接口，包含 Markdown/Tika 文档读取、自定义 ETL 及 PgVector 向量存储配置。 |
| [`rag-saa-example/`](./rag-saa-example/README.md) | Spring AI Alibaba RAG 示例：基于 Spring AI Alibaba RAG 与 DashScope 的最小示例模块。 |
| [`spai-react-agent/`](./spai-react-agent/README.md) | Spring AI Alibaba React Agent 示例：使用 Spring AI Alibaba Agent Framework 构建 ReAct Agent，并集成 DashScope、MCP Client 和 Studio 相关依赖。 |

## 构建

在仓库根目录执行：

```bash
mvn clean package
```

仅构建一个模块及其所需的 reactor 依赖：

```bash
mvn -pl <模块目录> -am package
```

例如：

```bash
mvn -pl chat-example -am package
```

## 运行

各 Spring Boot 模块的配置位于对应 `src/main/resources/application.yml` 或 `application.yaml`。先配置所需密钥、服务地址和外部依赖，再从 IDE 启动模块的 `*Application` 类，或在模块目录执行：

```bash
mvn spring-boot:run
```

具体依赖与准备事项请查看各模块 README。不要将真实密钥提交到版本库。
