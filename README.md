# Spring AI 示例项目

这是一个基于 Spring AI 的综合示例项目，展示了如何使用 Spring AI 及其生态系统构建 AI 应用。

## 项目概述

本项目包含多个子模块，涵盖了 Spring AI 与不同 AI 服务提供商的集成示例，以及 MCP（Model Context Protocol）相关的完整实践。

## 技术栈

- **Java 21**
- **Spring Boot 3.5.9**
- **Spring AI 1.1.2**
- **Spring AI Alibaba 1.1.2.0**
- **LangChain4j 1.0.1**
- **Maven**

## 子模块列表

### 基础示例

| 模块 | 说明 |
|------|------|
| `example-dashscope-01` | 使用阿里云 DashScope SDK 直接调用通义千问模型 |
| `example-spring-ai-alibaba-01` | Spring AI Alibaba 集成示例，使用 DashScope 作为模型提供商 |
| `example-spring-ai-open-ai-01` | Spring AI OpenAI 集成示例，兼容 DeepSeek 等 OpenAI 协议的模型 |
| `example-spring-ai-ollama-01` | Spring AI Ollama 集成示例，支持本地运行的开源模型 |
| `example-spring-ai-mcp-01` | Spring AI MCP 客户端基础示例 |
| `example-spring-ai-rag-01` | Spring AI RAG（检索增强生成）示例，集成 Elasticsearch 向量存储 |
| `example-langchain4j-01` | LangChain4j 框架集成示例 |

### MCP 相关模块

| 模块 | 说明 |
|------|------|
| `mcp-auth-server` | MCP 服务端示例，提供时间、天气、股票等工具，支持 Nacos 服务注册 |
| `mcp-auth-client` | MCP 客户端示例，通过 Streamable HTTP 连接 MCP 服务端 |
| `mcp-client-web` | MCP Web 客户端，支持多种服务发现方式（文件、数据库、Nacos） |
| `mcp-gateway` | MCP 网关服务，聚合多个 MCP Server 的工具 |
| `mcp-filesystem` | MCP 文件系统工具集成，演示如何通过 MCP 访问本地文件 |
| `mcp-nacos-server` | MCP Nacos 配置中心服务端，提供 Nacos 配置读取和搜索工具 |

### Agent 与多模态

| 模块 | 说明 |
|------|------|
| `spai-react-agent` | 基于 Spring AI Alibaba 的 ReAct Agent 示例，实现天气查询智能体 |
| `multi-model` | 多模态模型示例，支持图像和视频帧提取，集成火山引擎方舟平台 |

## 环境要求

- JDK 21+
- Maven 3.8+
- 各模块可能需要额外的环境变量（如 API Key），请参考各模块的 README 或配置文件

## 快速开始

1. 克隆项目
```bash
git clone <repository-url>
cd spai-example
```

2. 配置环境变量

根据使用的模块，配置相应的 API Key：
```bash
export AI_DASHSCOPE_API_KEY=your_dashscope_api_key
export AI_DEEPSEEK_API_KEY=your_deepseek_api_key
export ARK_API_KEY=your_ark_api_key
```

3. 构建项目
```bash
mvn clean install
```

4. 运行示例

进入具体的子模块目录，参考其 README 或运行 Spring Boot 应用：
```bash
cd example-spring-ai-alibaba-01
mvn spring-boot:run
```

## 项目结构

```
spai-example/
├── example-dashscope-01/          # DashScope SDK 直接调用
├── example-spring-ai-alibaba-01/  # Spring AI Alibaba 集成
├── example-spring-ai-open-ai-01/  # Spring AI OpenAI 协议集成
├── example-spring-ai-ollama-01/   # Spring AI Ollama 本地模型
├── example-spring-ai-mcp-01/      # Spring AI MCP 基础示例
├── example-spring-ai-rag-01/      # Spring AI RAG 示例
├── example-langchain4j-01/        # LangChain4j 集成
├── mcp-auth-server/               # MCP 服务端
├── mcp-auth-client/               # MCP 客户端
├── mcp-client-web/                # MCP Web 客户端
├── mcp-gateway/                   # MCP 网关
├── mcp-filesystem/                # MCP 文件系统工具
├── mcp-nacos-server/              # MCP Nacos 配置服务
├── spai-react-agent/              # ReAct Agent 示例
└── multi-model/                   # 多模态模型示例
```

## 许可证

本项目仅供学习和参考使用。
