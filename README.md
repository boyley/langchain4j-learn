# LangChain4j 学习项目

按功能领域划分的 LangChain4j 学习项目，每个功能下包含多种 LLM 提供商的示例。

## 支持的 LLM 提供商

| 提供商 | 说明 | 环境变量 |
|--------|------|----------|
| **Demo** | LangChain4j 官方免费 API，无需配置 | 无需配置 |
| **OpenAI** | GPT-3.5/GPT-4 系列模型 | `OPENAI_API_KEY` |
| **Ollama** | 本地运行的开源模型 (Llama, Qwen 等) | `OLLAMA_BASE_URL` (可选) |
| **通义千问 (Qwen)** | 阿里云通义千问模型 | `DASHSCOPE_API_KEY` |
| **DeepSeek** | DeepSeek 系列模型 | `DEEPSEEK_API_KEY` |
| **阿里百炼 (Bailian)** | 阿里云百炼平台 | `DASHSCOPE_API_KEY` |

## 环境要求

- Java 17+
- Maven 3.8+
- (可选) Ollama - 本地运行 LLM

## 快速开始 - 免费 Demo

**无需任何 API Key，直接运行！**

```bash
cd /Users/admin/工作/demo/langchain4j-learn

# 运行基础对话 Demo
mvn exec:java -pl 01-basic-chat/basic-chat-demo \
  -Dexec.mainClass="com.example.langchain4j.chat.demo.BasicChatDemo" -q
```

---

## 模块详细说明

### 01-basic-chat - 基础对话

**学习内容**: ChatLanguageModel 的基本使用，发送消息并获取回复。

**核心代码**:
```java
ChatLanguageModel model = OpenAiChatModel.builder()
    .apiKey("your-api-key")
    .modelName("gpt-3.5-turbo")
    .build();

String answer = model.generate("你好");
```

**Demo 运行**:
```bash
mvn exec:java -pl 01-basic-chat/basic-chat-demo \
  -Dexec.mainClass="com.example.langchain4j.chat.demo.BasicChatDemo" -q
```

---

### 02-prompt-template - 提示词模板

**学习内容**: 使用 PromptTemplate 构建动态提示词，支持变量替换。

**核心代码**:
```java
PromptTemplate template = PromptTemplate.from(
    "请将 {{text}} 翻译成 {{language}}"
);
Prompt prompt = template.apply(Map.of("text", "你好", "language", "英文"));
```

**Demo 运行**:
```bash
mvn exec:java -pl 02-prompt-template/prompt-demo \
  -Dexec.mainClass="com.example.langchain4j.prompt.demo.PromptTemplateDemo" -q
```

---

### 03-ai-services - AI 服务 (声明式接口)

**学习内容**: 使用 @AiService 注解定义接口，框架自动生成实现。支持返回枚举、布尔值、数字等类型。

**核心代码**:
```java
public interface Assistant {
    @SystemMessage("你是一个友好的助手")
    String chat(String message);

    @UserMessage("将 {{text}} 翻译成 {{language}}")
    String translate(String text, String language);
}

Assistant assistant = AiServices.create(Assistant.class, model);
String result = assistant.chat("你好");
```

**Demo 运行**:
```bash
mvn exec:java -pl 03-ai-services/ai-services-demo \
  -Dexec.mainClass="com.example.langchain4j.aiservices.demo.AiServicesDemo" -q
```

---

### 04-tools - 工具调用 (Function Calling)

**学习内容**: 使用 @Tool 注解定义工具方法，让 AI 自动选择并调用合适的工具。

**核心代码**:
```java
public class Calculator {
    @Tool("计算两个数的和")
    public double add(double a, double b) {
        return a + b;
    }
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(model)
    .tools(new Calculator())
    .build();
```

**Demo 运行**:
```bash
mvn exec:java -pl 04-tools/tools-demo \
  -Dexec.mainClass="com.example.langchain4j.tools.demo.ToolsDemo" -q
```

---

### 05-memory - 会话记忆

**学习内容**: 使用 ChatMemory 实现多轮对话，AI 能记住之前的对话内容。

**核心代码**:
```java
ChatMemory memory = MessageWindowChatMemory.builder()
    .maxMessages(10)  // 保留最近10条消息
    .build();

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(model)
    .chatMemory(memory)
    .build();
```

**Demo 运行**:
```bash
mvn exec:java -pl 05-memory/memory-demo \
  -Dexec.mainClass="com.example.langchain4j.memory.demo.MemoryDemo" -q
```

---

### 06-streaming - 流式响应

**学习内容**: 使用 StreamingChatLanguageModel 实现实时输出，逐个 Token 显示。

**核心代码**:
```java
StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
    .apiKey("your-api-key")
    .build();

model.generate("讲个笑话", new StreamingResponseHandler<>() {
    @Override
    public void onNext(String token) {
        System.out.print(token);  // 实时输出每个 token
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        System.out.println("\n完成!");
    }
});
```

**Demo 运行**:
```bash
mvn exec:java -pl 06-streaming/streaming-demo \
  -Dexec.mainClass="com.example.langchain4j.streaming.demo.StreamingDemo" -q
```

---

### 07-embedding - 文本向量化

**学习内容**: 使用 EmbeddingModel 将文本转换为向量，计算文本相似度。

**核心代码**:
```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .apiKey("your-api-key")
    .modelName("text-embedding-3-small")
    .build();

Embedding embedding = model.embed("Java 编程").content();
float[] vector = embedding.vector();  // 获取向量
```

**Demo 运行**:
```bash
mvn exec:java -pl 07-embedding/embedding-demo \
  -Dexec.mainClass="com.example.langchain4j.embedding.demo.EmbeddingDemo" -q
```

---

### 08-rag - 检索增强生成 (RAG)

**学习内容**: 结合知识库进行问答。流程: 文档加载 -> 分割 -> 向量化 -> 存储 -> 检索 -> 生成回答。

**核心代码**:
```java
// 1. 分割文档
DocumentSplitter splitter = DocumentSplitters.recursive(200, 20);
List<TextSegment> segments = splitter.split(document);

// 2. 向量化并存储
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
for (TextSegment segment : segments) {
    Embedding emb = embeddingModel.embed(segment.text()).content();
    store.add(emb, segment);
}

// 3. 创建检索器
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(embeddingModel)
    .maxResults(3)
    .build();

// 4. 创建 RAG 助手
Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(chatModel)
    .contentRetriever(retriever)
    .build();
```

**Demo 运行**:
```bash
mvn exec:java -pl 08-rag/rag-demo \
  -Dexec.mainClass="com.example.langchain4j.rag.demo.RagDemo" -q
```

---

### 09-agent - 智能代理

**学习内容**: 创建自主决策的 Agent，能够根据任务自动选择和组合多个工具。

**核心代码**:
```java
@SystemMessage("你是一个智能助手，可以搜索信息和进行计算")
interface Agent {
    String chat(String message);
}

Agent agent = AiServices.builder(Agent.class)
    .chatLanguageModel(model)
    .tools(new SearchTool(), new CalculatorTool())
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .build();

// Agent 会自动决定使用哪些工具
String answer = agent.chat("搜索 Java 最新版本，然后计算 21 * 365");
```

**Demo 运行**:
```bash
mvn exec:java -pl 09-agent/agent-demo \
  -Dexec.mainClass="com.example.langchain4j.agent.demo.AgentDemo" -q
```

---

## 所有 Demo 运行命令汇总

```bash
cd /Users/admin/工作/demo/langchain4j-learn

# 01 基础对话
mvn exec:java -pl 01-basic-chat/basic-chat-demo -Dexec.mainClass="com.example.langchain4j.chat.demo.BasicChatDemo" -q

# 02 提示词模板
mvn exec:java -pl 02-prompt-template/prompt-demo -Dexec.mainClass="com.example.langchain4j.prompt.demo.PromptTemplateDemo" -q

# 03 AI Services
mvn exec:java -pl 03-ai-services/ai-services-demo -Dexec.mainClass="com.example.langchain4j.aiservices.demo.AiServicesDemo" -q

# 04 工具调用
mvn exec:java -pl 04-tools/tools-demo -Dexec.mainClass="com.example.langchain4j.tools.demo.ToolsDemo" -q

# 05 会话记忆
mvn exec:java -pl 05-memory/memory-demo -Dexec.mainClass="com.example.langchain4j.memory.demo.MemoryDemo" -q

# 06 流式输出
mvn exec:java -pl 06-streaming/streaming-demo -Dexec.mainClass="com.example.langchain4j.streaming.demo.StreamingDemo" -q

# 07 文本向量
mvn exec:java -pl 07-embedding/embedding-demo -Dexec.mainClass="com.example.langchain4j.embedding.demo.EmbeddingDemo" -q

# 08 RAG 检索增强
mvn exec:java -pl 08-rag/rag-demo -Dexec.mainClass="com.example.langchain4j.rag.demo.RagDemo" -q

# 09 智能代理
mvn exec:java -pl 09-agent/agent-demo -Dexec.mainClass="com.example.langchain4j.agent.demo.AgentDemo" -q
```

---

## 学习路径

建议按模块编号顺序学习：

1. **01-basic-chat** - 了解基本概念，成功调用 LLM
2. **02-prompt-template** - 掌握提示词工程基础
3. **03-ai-services** - 学习更优雅的声明式写法
4. **04-tools** - 让 AI 调用外部工具
5. **05-memory** - 实现多轮对话
6. **06-streaming** - 优化用户体验
7. **07-embedding** - 理解向量概念
8. **08-rag** - 构建知识库问答
9. **09-agent** - 探索自主智能体

---

## 配置其他提供商

如果要使用非 Demo 的提供商，需要配置环境变量：

```bash
# 复制模板
cp .env.example .env

# 编辑 .env 填入你的 API Key
# OPENAI_API_KEY=sk-xxx
# DEEPSEEK_API_KEY=sk-xxx
# DASHSCOPE_API_KEY=sk-xxx

# 加载环境变量
source .env

# 运行其他提供商示例
mvn exec:java -pl 01-basic-chat/basic-chat-deepseek \
  -Dexec.mainClass="com.example.langchain4j.chat.deepseek.BasicChatDeepSeek" -q
```

## 项目统计

- **9 个功能模块**
- **6 个 LLM 提供商** (Demo, OpenAI, Ollama, Qwen, DeepSeek, 百炼)
- **80+ 个 Java 示例文件**
