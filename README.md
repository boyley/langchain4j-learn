# LangChain4j 学习项目

按功能领域划分的 LangChain4j 学习项目，每个功能下包含多种 LLM 提供商的示例。

## 支持的 LLM 提供商

- **OpenAI** - GPT-3.5/GPT-4 系列模型
- **Ollama** - 本地运行的开源模型 (Llama, Qwen 等)
- **通义千问 (Qwen)** - 阿里云通义千问模型
- **DeepSeek** - DeepSeek 系列模型 (deepseek-chat, deepseek-coder)
- **阿里百炼 (Bailian)** - 阿里云百炼平台 (qwen-plus, qwen-turbo, qwen-max)

## 环境要求

- Java 17+
- Maven 3.8+
- (可选) Ollama - 本地运行 LLM

## 快速开始

1. 复制环境变量模板并配置:
   ```bash
   cp .env.example .env
   # 编辑 .env 填入你的 API Key
   ```

2. 编译项目:
   ```bash
   mvn clean compile
   ```

3. 运行示例 (以 basic-chat-ollama 为例):
   ```bash
   cd 01-basic-chat/basic-chat-ollama
   mvn exec:java -Dexec.mainClass="com.example.langchain4j.chat.ollama.BasicChatOllama"
   ```

## 模块说明

| 模块 | 说明 |
|------|------|
| 01-basic-chat | 基础对话 - ChatLanguageModel 基本使用 |
| 02-prompt-template | 提示词模板 - PromptTemplate 动态构建 |
| 03-ai-services | AI 服务 - @AiService 声明式接口 |
| 04-tools | 工具调用 - @Tool 函数调用 |
| 05-memory | 会话记忆 - ChatMemory 多轮对话 |
| 06-streaming | 流式响应 - StreamingChatLanguageModel |
| 07-embedding | 文本向量 - EmbeddingModel 向量化 |
| 08-rag | 检索增强 - RAG 知识库问答 |
| 09-agent | 智能代理 - Agent 自主决策 |

## 学习路径

建议按模块编号顺序学习，从基础到高级。
