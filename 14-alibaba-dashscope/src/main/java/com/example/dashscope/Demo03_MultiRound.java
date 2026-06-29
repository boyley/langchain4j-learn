package com.example.dashscope;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo03: 多轮对话
 * 演示如何维护对话历史，实现上下文记忆
 */
public class Demo03_MultiRound {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo03: 阿里百炼 - 多轮对话");
        System.out.println("=".repeat(60));

        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        QwenChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();

        // 维护对话历史
        List<ChatMessage> history = new ArrayList<>();

        // 添加系统提示
        history.add(SystemMessage.from("你是一个友好的编程助手，回答要简洁明了。"));

        // ========== 第一轮 ==========
        System.out.println("\n--- 第一轮 ---");
        history.add(UserMessage.from("我叫小明，我想学 Java"));

        Response<AiMessage> r1 = model.generate(history);
        System.out.println("用户: 我叫小明，我想学 Java");
        System.out.println("AI: " + r1.content().text());

        history.add(r1.content());

        // ========== 第二轮 ==========
        System.out.println("\n--- 第二轮 ---");
        history.add(UserMessage.from("我叫什么名字？我想学什么？"));

        Response<AiMessage> r2 = model.generate(history);
        System.out.println("用户: 我叫什么名字？我想学什么？");
        System.out.println("AI: " + r2.content().text());

        history.add(r2.content());

        // ========== 第三轮 ==========
        System.out.println("\n--- 第三轮 ---");
        history.add(UserMessage.from("给我推荐一本入门书籍"));

        Response<AiMessage> r3 = model.generate(history);
        System.out.println("用户: 给我推荐一本入门书籍");
        System.out.println("AI: " + r3.content().text());

        System.out.println("\n" + "=".repeat(60));
        System.out.println("多轮对话完成！");
        System.out.println("AI 能记住你的名字和学习目标，因为历史都传给它了");
        System.out.println("=".repeat(60));
    }
}
