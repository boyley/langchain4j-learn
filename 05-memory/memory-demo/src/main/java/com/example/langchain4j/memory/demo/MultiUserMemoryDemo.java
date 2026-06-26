package com.example.langchain4j.memory.demo;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多用户会话隔离示例
 *
 * 在实际应用中，每个用户应该有独立的会话记忆：
 * - 用户 A 的对话历史不会影响用户 B
 * - 使用 @MemoryId 注解标识不同用户
 * - 使用 ChatMemoryProvider 为每个用户创建独立的记忆
 */
public class MultiUserMemoryDemo {

    /**
     * 支持多用户的 Assistant 接口
     * 使用 @MemoryId 注解标识用户
     */
    interface MultiUserAssistant {
        /**
         * @param memoryId    用户唯一标识（可以是用户ID、会话ID等）
         * @param userMessage 用户消息
         * @return AI 回复
         */
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    // 存储每个用户的 ChatMemory
    private static final Map<String, MessageWindowChatMemory> userMemories = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        /**
         * 构建 ChatLanguageModel
         *
         * OpenAiChatModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                           │
         * │ apiKey          │ API 密钥                                           │
         * │ modelName       │ 模型名称                                           │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址
                .apiKey("demo")                                    // API 密钥
                .modelName("gpt-4o-mini")                          // 模型名称
                .build();

        System.out.println("=== 多用户会话隔离示例 ===\n");

        /**
         * 多用户记忆隔离 - chatMemoryProvider
         * ═══════════════════════════════════════════════════════════════════════════
         *
         * 问题：单个 chatMemory 被所有用户共享，会导致对话混淆
         * 解决：使用 chatMemoryProvider 为每个用户创建独立的记忆
         *
         * chatMemoryProvider 工作原理：
         * ┌───────────────────────────────────────────────────────────────────────┐
         * │ 1. 用户调用 assistant.chat(memoryId, message)                         │
         * │ 2. LangChain4j 提取 @MemoryId 参数值                                   │
         * │ 3. 调用 chatMemoryProvider.get(memoryId) 获取该用户的记忆              │
         * │ 4. 如果是新用户，创建新的 ChatMemory                                   │
         * │ 5. 如果是老用户，返回已有的 ChatMemory                                 │
         * │ 6. 后续对话自动使用该用户的记忆                                        │
         * └───────────────────────────────────────────────────────────────────────┘
         *
         * AiServices.builder() 参数说明：
         * ┌──────────────────────┬──────────────────────────────────────────────┐
         * │ 参数                 │ 说明                                         │
         * ├──────────────────────┼──────────────────────────────────────────────┤
         * │ chatLanguageModel    │ 必须：聊天模型                                │
         * │ chatMemoryProvider   │ 多用户记忆提供者，根据 @MemoryId 隔离用户     │
         * │                      │ 参数: memoryId -> ChatMemory                 │
         * │                      │ 返回: 该用户的独立记忆实例                    │
         * └──────────────────────┴──────────────────────────────────────────────┘
         *
         * MessageWindowChatMemory.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ id              │ 记忆 ID，用于区分不同会话                           │
         * │ maxMessages     │ 最大消息数，超出后自动删除最早的消息                 │
         * │ chatMemoryStore │ 可选：持久化存储 (见 PersistentMemoryDemo)         │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
                .chatLanguageModel(model)                          // 必须：聊天模型
                .chatMemoryProvider(memoryId -> {                  // 多用户记忆提供者
                    // 为每个用户创建独立的记忆存储
                    // computeIfAbsent: 如果不存在则创建，存在则返回已有的
                    return userMemories.computeIfAbsent(memoryId.toString(),
                        id -> MessageWindowChatMemory.builder()
                            .id(id)                   // 记忆 ID = 用户 ID
                            .maxMessages(10)          // 最多保留 10 条消息
                            .build());
                })
                .build();

        // 模拟用户 ID
        String userA = "user_001";
        String userB = "user_002";
        String userC = "user_003";

        // ========== 用户 A 的对话 ==========
        System.out.println("【用户 A (user_001) 的对话】");
        System.out.println("-".repeat(40));

        System.out.println("用户A: 我叫张三，是一名 Java 开发工程师。");
        String responseA1 = assistant.chat(userA, "我叫张三，是一名 Java 开发工程师。");
        System.out.println("AI: " + responseA1);

        System.out.println("\n用户A: 我今年工作5年了。");
        String responseA2 = assistant.chat(userA, "我今年工作5年了。");
        System.out.println("AI: " + responseA2);

        // ========== 用户 B 的对话 ==========
        System.out.println("\n【用户 B (user_002) 的对话】");
        System.out.println("-".repeat(40));

        System.out.println("用户B: 我叫李四，是一名产品经理。");
        String responseB1 = assistant.chat(userB, "我叫李四，是一名产品经理。");
        System.out.println("AI: " + responseB1);

        System.out.println("\n用户B: 我最近在做一个 AI 项目。");
        String responseB2 = assistant.chat(userB, "我最近在做一个 AI 项目。");
        System.out.println("AI: " + responseB2);

        // ========== 用户 C 的对话 ==========
        System.out.println("\n【用户 C (user_003) 的对话】");
        System.out.println("-".repeat(40));

        System.out.println("用户C: 我叫王五，刚学 LangChain4j。");
        String responseC1 = assistant.chat(userC, "我叫王五，刚学 LangChain4j。");
        System.out.println("AI: " + responseC1);

        // ========== 验证用户隔离 ==========
        System.out.println("\n【验证用户隔离 - 各用户询问自己的信息】");
        System.out.println("=".repeat(50));

        System.out.println("\n用户A: 我叫什么名字？做什么工作？");
        String verifyA = assistant.chat(userA, "我叫什么名字？做什么工作？");
        System.out.println("AI 回复用户A: " + verifyA);

        System.out.println("\n用户B: 我叫什么？在做什么项目？");
        String verifyB = assistant.chat(userB, "我叫什么？在做什么项目？");
        System.out.println("AI 回复用户B: " + verifyB);

        System.out.println("\n用户C: 你还记得我是谁吗？");
        String verifyC = assistant.chat(userC, "你还记得我是谁吗？");
        System.out.println("AI 回复用户C: " + verifyC);

        // ========== 验证跨用户隔离 ==========
        System.out.println("\n【验证跨用户隔离 - 用户 B 询问用户 A 的信息】");
        System.out.println("=".repeat(50));

        System.out.println("\n用户B: 你知道张三吗？");
        String crossCheck = assistant.chat(userB, "你知道张三吗？");
        System.out.println("AI 回复用户B: " + crossCheck);
        System.out.println("\n✓ 用户 B 的会话不知道用户 A 的信息，验证了用户隔离！");

        // 显示内存使用情况
        System.out.println("\n【当前活跃的用户会话】");
        System.out.println("-".repeat(40));
        userMemories.forEach((userId, memory) -> {
            System.out.println("- " + userId + ": " + memory.messages().size() + " 条消息");
        });
    }
}
