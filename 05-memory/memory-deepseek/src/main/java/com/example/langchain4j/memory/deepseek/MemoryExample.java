package com.example.langchain4j.memory.deepseek;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * DeepSeek 会话记忆示例
 *
 * 演示如何使用 ChatMemory 实现多轮对话记忆。
 * DeepSeek API 兼容 OpenAI 协议，因此使用 OpenAiChatModel 并配置自定义 baseUrl。
 */
public class MemoryExample {

    interface AssistantWithMemory {
        String chat(String userMessage);
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DEEPSEEK_API_KEY 环境变量");
            System.exit(1);
        }

        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();

        System.out.println("=== 会话记忆示例 (DeepSeek) ===\n");

        // 示例 1: MessageWindowChatMemory - 保留最近 N 条消息
        System.out.println("--- 示例 1: MessageWindowChatMemory ---\n");

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)  // 保留最近10条消息
                .build();

        AssistantWithMemory assistant = AiServices.builder(AssistantWithMemory.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();

        // 多轮对话演示
        System.out.println("用户: 我叫张三，今年25岁。");
        String response1 = assistant.chat("我叫张三，今年25岁。");
        System.out.println("AI: " + response1);

        System.out.println("\n用户: 我喜欢编程和打篮球。");
        String response2 = assistant.chat("我喜欢编程和打篮球。");
        System.out.println("AI: " + response2);

        System.out.println("\n用户: 你还记得我叫什么名字吗？");
        String response3 = assistant.chat("你还记得我叫什么名字吗？");
        System.out.println("AI: " + response3);

        System.out.println("\n用户: 我多大了？有什么爱好？");
        String response4 = assistant.chat("我多大了？有什么爱好？");
        System.out.println("AI: " + response4);

        // 示例 2: 新的对话 - 独立的记忆
        System.out.println("\n--- 示例 2: 独立会话记忆 ---\n");

        ChatMemory chatMemory2 = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();

        AssistantWithMemory assistant2 = AiServices.builder(AssistantWithMemory.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory2)
                .build();

        System.out.println("用户: 我叫李四。");
        String response5 = assistant2.chat("我叫李四。");
        System.out.println("AI: " + response5);

        System.out.println("\n用户: 你知道张三吗？");
        String response6 = assistant2.chat("你知道张三吗？");
        System.out.println("AI: " + response6);
        System.out.println("(注意: 这个会话不知道张三，因为是独立的记忆)");

        // 示例 3: 验证原会话记忆仍然有效
        System.out.println("\n--- 示例 3: 验证原会话记忆 ---\n");
        System.out.println("用户: (使用第一个助手) 请总结一下我的信息。");
        String response7 = assistant.chat("请总结一下我的信息。");
        System.out.println("AI: " + response7);
    }
}
