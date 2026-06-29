package com.example.multiagent;

import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Demo01: 链式调用
 * 最基础的多 Agent 模式：把两个模型调用串联起来
 *
 * 流程：用户输入 → 模型A生成 → 模型B润色 → 输出
 */
public class Demo01_ChainCall {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo01: 链式调用（Chain Call）");
        System.out.println("=".repeat(60));

        // 初始化模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        String topic = "远程办公的利与弊";
        System.out.println("\n输入主题: " + topic);

        // ========== Step 1: 生成初稿 ==========
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【Step 1】生成初稿...");
        System.out.println("-".repeat(40));

        String draft = model.generate(
                "请写一段关于「" + topic + "」的简短分析，约150字"
        );
        System.out.println(draft);

        // ========== Step 2: 润色优化 ==========
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【Step 2】润色优化...");
        System.out.println("-".repeat(40));

        String polished = model.generate(
                "请润色以下文章，使其更加专业和有说服力：\n\n" + draft
        );
        System.out.println(polished);

        // ========== Step 3: 生成总结 ==========
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【Step 3】生成一句话总结...");
        System.out.println("-".repeat(40));

        String summary = model.generate(
                "用一句话总结以下内容的核心观点：\n\n" + polished
        );
        System.out.println(summary);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("链式调用完成！");
        System.out.println("=".repeat(60));
    }
}
