# model.generate() API 详解

> langchain4j 中调用 AI 模型的核心方法

---

## 一句话理解

```
model.generate("问题") → AI 返回 "回答"
```

就像打电话问客服，你说问题，客服给你答案。

---

## 基本用法

### 1. 最简单的调用

```java
// 创建模型
OpenAiChatModel model = OpenAiChatModel.builder()
    .apiKey("your-api-key")
    .modelName("gpt-3.5-turbo")
    .build();

// 调用生成
String answer = model.generate("什么是 Java？");
System.out.println(answer);
// 输出: Java 是一种面向对象的编程语言...
```

### 2. 带角色设定

```java
import dev.langchain4j.data.message.*;

// SystemMessage = 设定 AI 的角色和行为
// UserMessage = 用户的问题

Response<AiMessage> response = model.generate(
    SystemMessage.from("你是一个 Java 专家，回答要简洁专业"),
    UserMessage.from("什么是多态？")
);

String answer = response.content().text();
```

### 3. 多轮对话

```java
import java.util.List;

// 把历史对话都传进去，AI 就能理解上下文
List<ChatMessage> messages = List.of(
    SystemMessage.from("你是数学老师"),
    UserMessage.from("1+1等于几？"),
    AiMessage.from("1+1=2"),           // 上一轮 AI 的回答
    UserMessage.from("再加1呢？")       // 新问题
);

Response<AiMessage> response = model.generate(messages);
// AI 会理解上下文，回答 "2+1=3"
```

---

## 方法签名

```java
public interface ChatLanguageModel {

    // 最简单：传字符串，返字符串
    String generate(String userMessage);

    // 传单条消息
    Response<AiMessage> generate(ChatMessage... messages);

    // 传消息列表
    Response<AiMessage> generate(List<ChatMessage> messages);

    // 带工具调用
    Response<AiMessage> generate(List<ChatMessage> messages,
                                  List<ToolSpecification> tools);
}
```

---

## 消息类型

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatMessage 类型                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  SystemMessage    → 系统提示，设定 AI 角色和规则              │
│                     "你是一个专业的翻译官"                    │
│                                                             │
│  UserMessage      → 用户消息，你问的问题                      │
│                     "请把这段话翻译成英文"                    │
│                                                             │
│  AiMessage        → AI 回复，模型生成的内容                   │
│                     "Here is the translation..."            │
│                                                             │
│  ToolExecutionResultMessage → 工具执行结果                   │
│                     "天气查询结果：北京，晴，25°C"             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 返回值解析

```java
Response<AiMessage> response = model.generate(messages);

// 获取回复文本
String text = response.content().text();

// 获取 Token 使用量
TokenUsage usage = response.tokenUsage();
int inputTokens = usage.inputTokenCount();   // 输入消耗
int outputTokens = usage.outputTokenCount(); // 输出消耗
int totalTokens = usage.totalTokenCount();   // 总计

// 获取结束原因
FinishReason reason = response.finishReason();
// STOP = 正常结束
// LENGTH = 达到最大长度
// TOOL_EXECUTION = 需要执行工具
```

---

## 完整示例

```java
package com.example.multiagent;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

public class GenerateApiDemo {

    public static void main(String[] args) {
        // 1. 创建模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)        // 创造性 0-1
                .maxTokens(500)          // 最大输出长度
                .build();

        // 2. 简单调用
        System.out.println("=== 简单调用 ===");
        String simple = model.generate("用一句话介绍 Java");
        System.out.println(simple);

        // 3. 带角色设定
        System.out.println("\n=== 带角色设定 ===");
        Response<AiMessage> response = model.generate(
                SystemMessage.from("你是一个幽默的程序员，回答要带点调侃"),
                UserMessage.from("为什么程序员喜欢用暗色主题？")
        );
        System.out.println(response.content().text());

        // 4. 多轮对话
        System.out.println("\n=== 多轮对话 ===");
        List<ChatMessage> history = new ArrayList<>();
        history.add(SystemMessage.from("你是数学老师"));

        // 第一轮
        history.add(UserMessage.from("1+1=?"));
        Response<AiMessage> r1 = model.generate(history);
        System.out.println("Q: 1+1=?");
        System.out.println("A: " + r1.content().text());
        history.add(r1.content());  // 把 AI 回复加入历史

        // 第二轮（AI 会记住上下文）
        history.add(UserMessage.from("再乘以10呢？"));
        Response<AiMessage> r2 = model.generate(history);
        System.out.println("Q: 再乘以10呢？");
        System.out.println("A: " + r2.content().text());

        // 5. 查看 Token 消耗
        System.out.println("\n=== Token 使用 ===");
        System.out.println("输入: " + r2.tokenUsage().inputTokenCount());
        System.out.println("输出: " + r2.tokenUsage().outputTokenCount());
        System.out.println("总计: " + r2.tokenUsage().totalTokenCount());
    }
}
```

---

## generate() vs AI Service

| 特性 | model.generate() | AI Service |
|------|------------------|------------|
| 写法 | 直接调用方法 | 定义接口 + 注解 |
| 灵活性 | 高，完全控制 | 中，框架封装 |
| 代码量 | 较多 | 较少 |
| 可维护性 | 一般 | 好 |
| 适用场景 | 简单调用、学习原理 | 生产项目 |

```java
// 方式1: model.generate() - 底层方式
String answer = model.generate("问题");

// 方式2: AI Service - 推荐方式
interface Assistant {
    String chat(String message);
}
Assistant assistant = AiServices.create(Assistant.class, model);
String answer = assistant.chat("问题");

// AI Service 底层也是调用 model.generate()
// 但它帮你处理了消息构建、历史管理等细节
```

---

## 常用配置参数

```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .apiKey("sk-xxx")                    // API 密钥
    .modelName("gpt-3.5-turbo")          // 模型名称
    .baseUrl("https://api.openai.com")   // API 地址（可换国内代理）
    .temperature(0.7)                    // 创造性 0-2，越高越随机
    .maxTokens(1000)                     // 最大输出 Token
    .timeout(Duration.ofSeconds(60))     // 超时时间
    .logRequests(true)                   // 打印请求日志
    .logResponses(true)                  // 打印响应日志
    .build();
```

---

## 流程图

```
┌──────────────────────────────────────────────────────────────┐
│                    model.generate() 流程                      │
└──────────────────────────────────────────────────────────────┘

    你的代码                    网络                 OpenAI/Claude
       │                        │                        │
       │  model.generate(msg)   │                        │
       │───────────────────────→│                        │
       │                        │   HTTP POST /chat      │
       │                        │───────────────────────→│
       │                        │                        │
       │                        │                        │ AI 思考
       │                        │                        │ 生成回复
       │                        │                        │
       │                        │   返回 JSON            │
       │                        │←───────────────────────│
       │  Response<AiMessage>   │                        │
       │←───────────────────────│                        │
       │                        │                        │
       │  response.content()    │                        │
       │  .text()               │                        │
       │                        │                        │
       ▼                        ▼                        ▼
    得到回复文本
```

---

## 总结

```
model.generate() = 把问题发给 AI，拿到回复

核心要点：
1. 传入：字符串 或 消息列表
2. 返回：AI 生成的回复
3. 底层：调用 OpenAI/Claude 等 API
4. 进阶：用 AI Service 更优雅
```
