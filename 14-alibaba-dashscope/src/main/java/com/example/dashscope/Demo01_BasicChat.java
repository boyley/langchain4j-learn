package com.example.dashscope;

import dev.langchain4j.model.dashscope.QwenChatModel;

/**
 * Demo01: 基础对话
 * 最简单的通义千问调用示例
 */
public class Demo01_BasicChat {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo01: 阿里百炼 - 基础对话");
        System.out.println("=".repeat(60));

        // 从环境变量获取 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            System.err.println("export DASHSCOPE_API_KEY=sk-xxxxxxxx");
            return;
        }

        // 创建通义千问模型
        QwenChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")  // 推荐使用 qwen-plus
                .build();

        // 简单对话
        System.out.println("\n【问题】什么是 Java？");
        String answer = model.generate("用简洁的语言介绍一下 Java 编程语言");
        System.out.println("\n【回答】");
        System.out.println(answer);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("基础对话完成！");
        System.out.println("=".repeat(60));
    }
}
