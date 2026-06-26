package com.example.langchain4j.chat.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * LangChain4j Demo API 基础对话示例
 *
 * 使用 LangChain4j 官方提供的免费 Demo API，无需任何配置即可运行！
 *
 * Demo API 说明：
 * - 免费使用，无需注册
 * - 有配额限制，仅供学习演示
 * - 使用 gpt-4o-mini 模型
 * - 请求通过 LangChain4j 代理转发
 */
public class BasicChatDemo {

    public static void main(String[] args) {
        // 使用 LangChain4j 官方 Demo API - 无需 API Key！
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== LangChain4j Demo API 基础对话示例 ===");
        System.out.println("(无需配置 API Key，直接运行！)\n");

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

        System.out.println("\n--- 第三轮对话 ---\n");

        // 测试中文能力
        String question3 = "写一首关于编程的五言绝句。";
        System.out.println("用户: " + question3);

        String answer3 = model.generate(question3);
        System.out.println("AI: " + answer3);
    }
}
