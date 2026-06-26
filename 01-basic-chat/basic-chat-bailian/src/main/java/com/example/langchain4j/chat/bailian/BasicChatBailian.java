package com.example.langchain4j.chat.bailian;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 阿里百炼平台基础对话示例
 *
 * 演示如何使用 LangChain4j 与阿里百炼平台进行简单对话。
 * 阿里百炼平台支持 OpenAI 兼容 API，因此使用 OpenAiChatModel 并配置自定义 baseUrl。
 *
 * 运行前请设置环境变量:
 * - DASHSCOPE_API_KEY: 你的阿里云 DashScope API 密钥
 */
public class BasicChatBailian {

    public static void main(String[] args) {
        // 1. 从环境变量获取配置
        String apiKey = System.getenv("DASHSCOPE_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DASHSCOPE_API_KEY 环境变量");
            System.exit(1);
        }

        // 2. 创建 ChatLanguageModel 实例
        // 阿里百炼平台支持 OpenAI 兼容协议，使用 OpenAiChatModel
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .modelName("qwen-plus")  // 可选: qwen-turbo, qwen-max
                .temperature(0.7)        // 控制回复的随机性，0-2，越高越随机
                .build();

        // 3. 发送消息并获取回复
        System.out.println("=== 阿里百炼平台基础对话示例 ===\n");

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
