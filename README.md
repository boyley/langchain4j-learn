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

**核心注解说明**:
| 注解 | 作用 | 示例 |
|------|------|------|
| `@SystemMessage` | 设置 AI 角色/行为 | `@SystemMessage("你是客服助手")` |
| `@UserMessage` | 定义提示词模板 | `@UserMessage("翻译: {{text}}")` |
| `@V("变量名")` | 绑定参数到模板变量 | `@V("text") String text` |
| 返回类型 | AI 自动解析为指定类型 | `Person`, `List<String>`, `Enum` |

**核心代码 - 基础用法**:
```java
public interface Assistant {
    @SystemMessage("你是一个友好的助手")
    String chat(String message);

    @UserMessage("将 {{text}} 翻译成 {{language}}")
    String translate(@V("text") String text, @V("language") String language);
}

Assistant assistant = AiServices.create(Assistant.class, model);
String result = assistant.chat("你好");
```

**核心代码 - 信息提取（结构化输出）**:
```java
// 1. 定义要提取的数据结构
record Person(String name, Integer age, String company) {}

// 2. 定义提取接口
interface Extractor {
    @UserMessage("从文本提取人员信息: {{text}}")
    Person extractPerson(@V("text") String text);

    @UserMessage("提取所有人名: {{text}}")
    List<String> extractNames(@V("text") String text);

    @UserMessage("分析情感: {{text}}")
    Sentiment analyzeSentiment(@V("text") String text);  // 返回枚举
}

// 3. 使用 - AI 自动将文本转为结构化对象
Person person = extractor.extractPerson("张三是阿里的工程师，32岁");
// person.name() = "张三", person.age() = 32, person.company() = "阿里"
```

**核心代码 - @SystemMessage 系统提示（角色设定）**:
```java
/**
 * @SystemMessage 作用：设定 AI 的角色、行为规则、回答风格
 * 用户看不到系统提示，但 AI 会始终遵循
 *
 * 使用场景：
 * - 客服机器人：限定只回答产品问题
 * - 代码专家：要求提供代码示例
 * - 翻译助手：只返回翻译结果，不加解释
 * - 儿童教育：使用简单语言
 */
@SystemMessage("""
    你是"智能科技公司"的客服机器人。
    职责：回答产品咨询、处理售后问题
    规则：不透露公司内部信息，不回答无关问题
    风格：简洁、友好、专业
    """)
interface CustomerServiceBot {
    String chat(String message);
}

// 使用
CustomerServiceBot bot = AiServices.create(CustomerServiceBot.class, model);
bot.chat("你们公司一年赚多少钱？");
// AI 回复：抱歉，我无法提供公司财务信息...
```

**Demo 运行**:
```bash
# 基础用法
mvn exec:java -pl 03-ai-services/ai-services-demo \
  -Dexec.mainClass="com.example.langchain4j.aiservices.demo.AiServicesDemo" -q

# 信息提取示例
mvn exec:java -pl 03-ai-services/ai-services-demo \
  -Dexec.mainClass="com.example.langchain4j.aiservices.demo.ExtractorDemo" -q

# 系统提示示例（角色设定：客服、专家、翻译、儿童教育等）
mvn exec:java -pl 03-ai-services/ai-services-demo \
  -Dexec.mainClass="com.example.langchain4j.aiservices.demo.SystemMessageDemo" -q
```

---

### 04-tools - 工具调用 (Function Calling)

**学习内容**: 使用 @Tool 注解定义工具方法，让 AI 自动选择并调用合适的工具。

**核心注解说明**:
| 注解 | 作用 | 示例 |
|------|------|------|
| `@Tool("描述")` | 告诉 AI 这个方法能做什么 | `@Tool("计算两数之和")` |
| `@P("描述")` | 告诉 AI 这个参数是什么 | `@P("第一个加数") double a` |
| 返回值 | 工具执行结果，AI 用它组织回答 | `return a + b;` |

**工作流程**:
```
用户: "帮我算一下 15 加 27"
  ↓
AI 分析: 需要加法运算 → 选择 add 方法
  ↓
AI 提取参数: a=15, b=27 (根据 @P 描述)
  ↓
LangChain4j 调用: add(15, 27) → 返回 42
  ↓
AI 组织回答: "15 + 27 的结果是 42"
```

**核心代码**:
```java
public class Calculator {
    /**
     * @Tool - AI 根据描述决定是否调用此方法
     * @P   - AI 从用户输入中提取参数值
     * 返回值 - 执行结果，AI 用它来回答用户
     */
    @Tool("计算两个数的和，用于加法运算")
    public double add(
            @P("第一个加数") double a,
            @P("第二个加数") double b) {
        return a + b;  // 返回 42，AI 回答"结果是42"
    }
}

// 注册工具
Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(model)
    .tools(new Calculator(), new WeatherService())  // 可注册多个工具
    .build();
```

**Demo 运行**:
```bash
mvn exec:java -pl 04-tools/tools-demo \
  -Dexec.mainClass="com.example.langchain4j.tools.demo.ToolsDemo" -q
```

---

### 05-memory - 会话记忆

**学习内容**: 使用 ChatMemory 实现多轮对话，AI 能记住之前的对话内容。支持多用户会话隔离。

**核心代码 - 单用户**:
```java
ChatMemory memory = MessageWindowChatMemory.builder()
    .maxMessages(10)  // 保留最近10条消息
    .build();

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(model)
    .chatMemory(memory)
    .build();
```

**核心代码 - 多用户隔离**:
```java
// 使用 @MemoryId 标识不同用户
interface MultiUserAssistant {
    String chat(@MemoryId String memoryId, @UserMessage String message);
}

// 使用 ChatMemoryProvider 为每个用户创建独立记忆
MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
    .chatLanguageModel(model)
    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
        .id(memoryId.toString())
        .maxMessages(10)
        .build())
    .build();

// 不同用户的对话互相隔离
assistant.chat("user_001", "我叫张三");  // 用户A的会话
assistant.chat("user_002", "我叫李四");  // 用户B的会话（不知道张三）
```

**核心代码 - 持久化存储 (Redis/数据库)**:
```java
// 实现 ChatMemoryStore 接口
class RedisChatMemoryStore implements ChatMemoryStore {

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 【SELECT】 redisTemplate.opsForValue().get(key)
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 【INSERT/UPDATE】 redisTemplate.opsForValue().set(key, json)
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 【DELETE】 redisTemplate.delete(key)
    }
}

// 注入自定义存储
MessageWindowChatMemory.builder()
    .chatMemoryStore(new RedisChatMemoryStore())  // 或 DatabaseChatMemoryStore
    .build();
```

**Demo 运行**:
```bash
# 单用户记忆
mvn exec:java -pl 05-memory/memory-demo \
  -Dexec.mainClass="com.example.langchain4j.memory.demo.MemoryDemo" -q

# 多用户隔离
mvn exec:java -pl 05-memory/memory-demo \
  -Dexec.mainClass="com.example.langchain4j.memory.demo.MultiUserMemoryDemo" -q

# 持久化存储 (Redis/数据库模拟)
mvn exec:java -pl 05-memory/memory-demo \
  -Dexec.mainClass="com.example.langchain4j.memory.demo.PersistentMemoryDemo" -q
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

### 07-embedding - 文本向量化 (Embedding)

**Embedding 是什么？** 把文字变成一组数字（向量），让计算机能"理解"语义。

**解决什么问题？** 传统关键词匹配无法理解语义，"密码忘了" 搜不到 "重置密码"。

**怎么解决？** 语义相近的文本 → 向量也相近 → 可以计算相似度。

```
"我喜欢苹果" → [0.12, -0.34, 0.56, ...]  ─┐
                                          ├─ 向量相似 → 意思相近！
"我爱吃苹果" → [0.11, -0.33, 0.55, ...]  ─┘

"今天下雨了" → [0.89, 0.23, -0.67, ...]  ← 向量差异大 → 意思不同
```

**应用场景**:
| 场景 | 说明 |
|------|------|
| 语义搜索 | 搜"苹果手机"能找到"iPhone" |
| 智能客服 | 用户问法不同也能匹配到答案 |
| 推荐系统 | 找相似的商品/文章/视频 |
| **RAG** | 让 AI 从文档中找信息再回答（最重要！见08-rag） |

**核心代码**:
```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://langchain4j.dev/demo/openai/v1")
    .apiKey("demo")
    .modelName("text-embedding-3-small")
    .build();

// 文本 → 向量
Embedding embedding = model.embed("Java 编程").content();
float[] vector = embedding.vector();  // 1536 个数字

// 计算相似度
double similarity = cosineSimilarity(vector1, vector2);  // 0~1，越大越相似
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

**企业知识库方案 - 多部门 + 权限控制**:
```java
// 1. 文档存储时附加 Metadata（元数据）
Metadata metadata = Metadata.from(Map.of(
    "department", "HR",           // 所属部门
    "accessLevel", "confidential", // 访问级别：public/internal/confidential
    "tags", "薪资,机密"            // 标签
));
TextSegment segment = TextSegment.from(content, metadata);
store.add(embedding, segment);

// 2. 搜索时根据用户权限过滤
Filter filter = MetadataFilterBuilder
    .metadataKey("department").isIn(user.accessDepartments)  // 部门过滤
    .and(MetadataFilterBuilder.metadataKey("accessLevel").isIn(allowedLevels)); // 权限过滤

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .filter(filter)  // 只返回用户有权限的文档
    .maxResults(3)
    .build();

// 3. 结果：不同用户搜索同一问题，看到的文档不同！
// 普通员工: 只看到公开文档
// 部门经理: 看到部门内部文档
// 高管: 看到所有机密文档
```

**Demo 运行**:
```bash
# 基础 RAG
mvn exec:java -pl 08-rag/rag-demo \
  -Dexec.mainClass="com.example.langchain4j.rag.demo.RagDemo" -q

# 企业知识库（多部门+权限控制）
mvn exec:java -pl 08-rag/rag-demo \
  -Dexec.mainClass="com.example.langchain4j.rag.demo.EnterpriseKnowledgeBaseDemo" -q
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

# 03 AI Services (基础)
mvn exec:java -pl 03-ai-services/ai-services-demo -Dexec.mainClass="com.example.langchain4j.aiservices.demo.AiServicesDemo" -q

# 03 AI Services (信息提取)
mvn exec:java -pl 03-ai-services/ai-services-demo -Dexec.mainClass="com.example.langchain4j.aiservices.demo.ExtractorDemo" -q

# 03 AI Services (系统提示 - 角色设定)
mvn exec:java -pl 03-ai-services/ai-services-demo -Dexec.mainClass="com.example.langchain4j.aiservices.demo.SystemMessageDemo" -q

# 04 工具调用
mvn exec:java -pl 04-tools/tools-demo -Dexec.mainClass="com.example.langchain4j.tools.demo.ToolsDemo" -q

# 05 会话记忆 (单用户)
mvn exec:java -pl 05-memory/memory-demo -Dexec.mainClass="com.example.langchain4j.memory.demo.MemoryDemo" -q

# 05 会话记忆 (多用户隔离)
mvn exec:java -pl 05-memory/memory-demo -Dexec.mainClass="com.example.langchain4j.memory.demo.MultiUserMemoryDemo" -q

# 05 会话记忆 (持久化存储 - Redis/数据库)
mvn exec:java -pl 05-memory/memory-demo -Dexec.mainClass="com.example.langchain4j.memory.demo.PersistentMemoryDemo" -q

# 06 流式输出
mvn exec:java -pl 06-streaming/streaming-demo -Dexec.mainClass="com.example.langchain4j.streaming.demo.StreamingDemo" -q

# 07 文本向量
mvn exec:java -pl 07-embedding/embedding-demo -Dexec.mainClass="com.example.langchain4j.embedding.demo.EmbeddingDemo" -q

# 08 RAG 检索增强 (基础)
mvn exec:java -pl 08-rag/rag-demo -Dexec.mainClass="com.example.langchain4j.rag.demo.RagDemo" -q

# 08 RAG 企业知识库 (多部门+权限控制)
mvn exec:java -pl 08-rag/rag-demo -Dexec.mainClass="com.example.langchain4j.rag.demo.EnterpriseKnowledgeBaseDemo" -q

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
