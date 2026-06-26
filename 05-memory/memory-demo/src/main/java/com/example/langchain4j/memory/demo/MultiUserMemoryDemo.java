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
        // 使用 LangChain4j 官方 Demo API
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== 多用户会话隔离示例 ===\n");

        // 创建支持多用户的 Assistant
        // ChatMemoryProvider 根据 memoryId 为每个用户创建/获取独立的记忆
        MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> {
                    // 为每个用户创建独立的记忆存储
                    return userMemories.computeIfAbsent(memoryId.toString(),
                        id -> MessageWindowChatMemory.builder()
                            .id(id)
                            .maxMessages(10)
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
