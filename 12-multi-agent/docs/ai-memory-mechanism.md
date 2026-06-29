# AI 记忆机制详解

> AI 没有记忆，"记忆"是你帮它实现的

---

## 核心结论

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   AI 有记忆吗？  →  没有！每次调用都是全新的、独立的          │
│                                                             │
│   那多轮对话怎么实现？                                       │
│   →  你把历史对话一起发给它，AI 看到历史就能"假装"记得        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 一、真相：AI 是"金鱼记忆"

每次调用 `model.generate()`，对 AI 来说都是第一次见面：

```java
// 第一次调用
model.generate("我叫张三");  // AI: "你好张三！"

// 第二次调用
model.generate("我叫什么？"); // AI: "抱歉，你还没告诉我你的名字"
                              //      ↑ AI 完全不记得上一轮！
```

---

## 二、解决方案：你来当 AI 的"记忆"

### 原理图

```
┌─────────────────────────────────────────────────────────────┐
│                     "记忆"的真相                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  你的程序                              AI 模型               │
│  ┌──────────────────┐                 ┌──────────────┐      │
│  │                  │                 │              │      │
│  │  history 列表    │ ──── 全部发送 ──→│   处理消息   │      │
│  │  ┌────────────┐  │                 │              │      │
│  │  │ User: 1+1? │  │                 │   生成回复   │      │
│  │  │ AI: 2      │  │                 │              │      │
│  │  │ User: 加1? │  │                 │  （处理完就  │      │
│  │  └────────────┘  │                 │    全忘了）  │      │
│  │                  │ ←── 返回回复 ────│              │      │
│  │  保存回复到历史  │                 │              │      │
│  │                  │                 │              │      │
│  └──────────────────┘                 └──────────────┘      │
│                                                             │
│  记忆在这边 ✓                          无状态 ✗             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 代码实现

```java
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;

public class MemoryDemo {

    public static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 你来维护的"记忆"
        List<ChatMessage> history = new ArrayList<>();

        // ========== 第一轮对话 ==========
        history.add(UserMessage.from("我叫张三，我是程序员"));

        // 发送: [User: "我叫张三，我是程序员"]
        Response<AiMessage> r1 = model.generate(history);
        System.out.println("AI: " + r1.content().text());

        // 保存 AI 回复到历史
        history.add(r1.content());

        // ========== 第二轮对话 ==========
        history.add(UserMessage.from("我叫什么名字？"));

        // 发送: [User: "我叫张三...", AI: "你好张三...", User: "我叫什么名字？"]
        //       ↑ 把完整历史都发过去，AI 就能"回忆"起来
        Response<AiMessage> r2 = model.generate(history);
        System.out.println("AI: " + r2.content().text());
        // 输出: "你叫张三"  ← AI "记住"了！

        history.add(r2.content());

        // ========== 第三轮对话 ==========
        history.add(UserMessage.from("我是做什么工作的？"));

        // 发送: [... 全部历史 ...]
        Response<AiMessage> r3 = model.generate(history);
        System.out.println("AI: " + r3.content().text());
        // 输出: "你是程序员"
    }
}
```

---

## 三、每轮发送的内容对比

```
第1轮发送:
┌─────────────────────────────┐
│ User: 我叫张三，我是程序员   │
└─────────────────────────────┘
                ↓
        AI 回复: "你好张三！很高兴认识你"


第2轮发送:
┌─────────────────────────────┐
│ User: 我叫张三，我是程序员   │  ← 历史
│ AI: 你好张三！很高兴认识你   │  ← 历史
│ User: 我叫什么名字？         │  ← 新问题
└─────────────────────────────┘
                ↓
        AI 回复: "你叫张三"


第3轮发送:
┌─────────────────────────────┐
│ User: 我叫张三，我是程序员   │  ← 历史
│ AI: 你好张三！很高兴认识你   │  ← 历史
│ User: 我叫什么名字？         │  ← 历史
│ AI: 你叫张三                 │  ← 历史
│ User: 我是做什么工作的？     │  ← 新问题
└─────────────────────────────┘
                ↓
        AI 回复: "你是程序员"
```

**关键点：每次都发送完整历史，AI 通过阅读历史来"理解"上下文**

---

## 四、问题：历史越来越长怎么办？

### 问题

```
聊了 100 轮后：
- 历史有 100 条消息
- 可能有 10000+ token
- 每次调用都要发这么多
- Token 要花钱！而且有上限！
```

### 解决方案

```java
// 方案1：只保留最近 N 条消息
ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

// 方案2：只保留最近 N 个 token
ChatMemory memory = TokenWindowChatMemory.withMaxTokens(2000);
```

### 窗口记忆示意图

```
配置：maxMessages = 3

消息历史演变：

初始：[]

+User1 → [User1]
+AI1   → [User1, AI1]
+User2 → [User1, AI1, User2]
+AI2   → [AI1, User2, AI2]      ← User1 被挤掉了
+User3 → [User2, AI2, User3]    ← AI1 被挤掉了

始终保持最近 3 条，老的自动丢弃
```

---

## 五、使用 ChatMemory 自动管理

手动管理 `List<ChatMessage>` 太麻烦，用框架的 `ChatMemory`：

```java
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.AiServices;

public class ChatMemoryDemo {

    interface Assistant {
        String chat(String message);
    }

    public static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 创建记忆组件：保留最近 20 条消息
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        // 创建带记忆的助手
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(memory)  // ← 绑定记忆
                .build();

        // 直接对话，框架自动管理历史
        System.out.println(assistant.chat("我叫张三"));
        System.out.println(assistant.chat("我是Java程序员"));
        System.out.println(assistant.chat("我叫什么？做什么的？"));
        // 输出: "你叫张三，是Java程序员"
    }
}
```

---

## 六、记忆类型对比

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `MessageWindowChatMemory` | 保留最近 N 条消息 | 一般对话 |
| `TokenWindowChatMemory` | 保留最近 N 个 token | 控制成本 |
| 自定义 `ChatMemory` | 自己实现存储逻辑 | 持久化到数据库 |

### MessageWindowChatMemory

```java
// 保留最近 10 条消息
ChatMemory memory = MessageWindowChatMemory.builder()
    .maxMessages(10)
    .build();
```

### TokenWindowChatMemory

```java
// 保留最近 2000 token
ChatMemory memory = TokenWindowChatMemory.builder()
    .maxTokens(2000)
    .tokenizer(new OpenAiTokenizer())  // 需要 tokenizer 来计数
    .build();
```

---

## 七、多用户场景

一个应用服务多个用户，每个用户要有独立的记忆：

```java
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class MultiUserDemo {

    interface Assistant {
        String chat(@MemoryId String oderId, @UserMessage String message);
    }

    public static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 为每个用户创建独立的记忆
        ChatMemoryProvider memoryProvider = userId ->
            MessageWindowChatMemory.builder()
                .id(userId)
                .maxMessages(20)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryProvider)
                .build();

        // 用户A 的对话
        assistant.chat("userA", "我叫张三");
        assistant.chat("userA", "我叫什么？");  // → "你叫张三"

        // 用户B 的对话（独立的记忆）
        assistant.chat("userB", "我叫李四");
        assistant.chat("userB", "我叫什么？");  // → "你叫李四"
    }
}
```

---

## 八、持久化记忆

默认记忆在内存中，程序重启就没了。可以存到数据库：

```java
// 伪代码示意
public class DatabaseChatMemory implements ChatMemory {

    private final String oderId;
    private final ChatMessageRepository repository;  // JPA Repository

    @Override
    public void add(ChatMessage message) {
        // 存入数据库
        repository.save(new ChatMessageEntity(userId, message));
    }

    @Override
    public List<ChatMessage> messages() {
        // 从数据库读取
        return repository.findByUserId(userId)
            .stream()
            .map(this::toMessage)
            .toList();
    }

    @Override
    public void clear() {
        repository.deleteByUserId(userId);
    }
}
```

---

## 九、总结

```
┌─────────────────────────────────────────────────────────────┐
│                        记忆机制总结                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. AI 本身无记忆                                            │
│     - 每次调用都是独立的                                     │
│     - 不会自动记住之前说过什么                                │
│                                                             │
│  2. "记忆"靠你实现                                           │
│     - 在你的代码中保存历史消息                                │
│     - 每次调用把历史一起发给 AI                               │
│                                                             │
│  3. 框架帮你简化                                             │
│     - ChatMemory 组件自动管理历史                            │
│     - 支持窗口限制、多用户、持久化                            │
│                                                             │
│  4. 注意成本                                                 │
│     - 历史越长，token 消耗越多                                │
│     - 使用窗口限制控制成本                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 十、相关文档

- [model.generate() API 详解](./model-generate-api.md)
- [langchain4j Memory 官方文档](https://docs.langchain4j.dev/tutorials/chat-memory)
