package com.example.langchain4j.chat.qwen;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;

/**
 * 通义千问基础对话示例
 *
 * 演示如何使用 LangChain4j 与阿里云通义千问进行对话。
 * 通义千问是阿里云提供的大语言模型服务，国内访问稳定，中文能力强。
 *
 * 运行前请设置环境变量:
 * - DASHSCOPE_API_KEY: 阿里云灵积平台的 API 密钥
 *   获取地址: https://dashscope.console.aliyun.com/
 */
public class BasicChatQwen {

    public static void main(String[] args) {
        // 1. 从环境变量获取 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DASHSCOPE_API_KEY 环境变量");
            System.err.println("获取地址: https://dashscope.console.aliyun.com/");
            System.exit(1);
        }

        // 2. 创建 ChatLanguageModel 实例
        // 可用模型: qwen-turbo, qwen-plus, qwen-max
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")    // 最快最便宜的模型
                .build();

        // 3. 发送消息并获取回复
        System.out.println("=== 通义千问基础对话示例 ===\n");
        System.out.println("使用模型: qwen-turbo\n");

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
