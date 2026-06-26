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
        /**
         * 构建 ChatLanguageModel - OpenAI 兼容模型
         *
         * OpenAiChatModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址，默认 OpenAI 官方，可改为代理或兼容服务     │
         * │ apiKey          │ API 密钥，用于身份认证                              │
         * │ modelName       │ 模型名称，如 gpt-4o-mini, gpt-4, gpt-3.5-turbo     │
         * │ temperature     │ 温度 0-2，越高回答越随机/创意，越低越确定/保守       │
         * │ maxTokens       │ 最大生成 token 数，控制回答长度                     │
         * │ topP            │ 核采样参数 0-1，与 temperature 二选一使用           │
         * │ timeout         │ 请求超时时间                                       │
         * │ maxRetries      │ 失败重试次数                                       │
         * │ logRequests     │ 是否打印请求日志 (调试用)                           │
         * │ logResponses    │ 是否打印响应日志 (调试用)                           │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址 (Demo 代理)
                .apiKey("demo")                                    // API 密钥 (Demo 免费)
                .modelName("gpt-4o-mini")                          // 使用的模型
                // .temperature(0.7)                               // 可选：温度，默认 0.7
                // .maxTokens(1000)                                 // 可选：最大 token 数
                // .timeout(Duration.ofSeconds(60))                 // 可选：超时时间
                // .logRequests(true)                               // 可选：打印请求
                // .logResponses(true)                              // 可选：打印响应
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
