package com.example.langchain4j.chat.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * OpenAI 基础对话示例
 *
 * 演示如何使用 LangChain4j 与 OpenAI 进行简单对话。
 *
 * 运行前请设置环境变量:
 * - OPENAI_API_KEY: 你的 OpenAI API 密钥
 * - OPENAI_BASE_URL: (可选) API 地址，用于代理
 */
public class BasicChatOpenAI {

    public static void main(String[] args) {
        // 1. 从环境变量获取配置
        String apiKey = System.getenv("OPENAI_API_KEY");
        String baseUrl = System.getenv("OPENAI_BASE_URL");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 OPENAI_API_KEY 环境变量");
            System.exit(1);
        }

        // 2. 创建 ChatLanguageModel 实例
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-3.5-turbo")  // 可选: gpt-4, gpt-4-turbo
                .temperature(0.7);            // 控制回复的随机性，0-2，越高越随机

        // 如果设置了自定义 API 地址，使用它
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        ChatLanguageModel model = builder.build();

        // 3. 发送消息并获取回复
        System.out.println("=== OpenAI 基础对话示例 ===\n");

        // 简单对话
        String question = "你好，请用一句话介绍一下你自己。";
        System.out.println("用户: " + question);

        String answer = model.generate(question);
        System.out.println("AI: " + answer);

        System.out.println("\n--- 第二轮对话 ---\n");

        // 另一个问题
        String question2 = "Java 和 Python 哪个更适合做 AI 开发？请简要说明。";
        System.out.println("用户: " + question2);

        String answer2 = model.generate(question2);
        System.out.println("AI: " + answer2);
    }
}
