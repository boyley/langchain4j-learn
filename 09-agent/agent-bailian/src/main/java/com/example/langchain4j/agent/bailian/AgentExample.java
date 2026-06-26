package com.example.langchain4j.agent.bailian;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

/**
 * 阿里百炼平台 Agent 智能代理示例
 *
 * 演示如何创建一个可以自主决策使用工具的 Agent。
 * 阿里百炼平台支持 OpenAI 兼容协议，因此使用 OpenAiChatModel 并配置自定义 baseUrl。
 */
public class AgentExample {

    @SystemMessage("""
        你是一个智能助手 Agent，可以自主决策使用哪些工具来完成任务。
        你有以下能力：
        1. 搜索互联网获取最新信息
        2. 进行数学计算
        3. 获取当前时间

        根据用户的问题，选择合适的工具来回答。
        如果需要多个步骤，请逐步完成。
        """)
    interface Agent {
        String chat(String userMessage);
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DASHSCOPE_API_KEY 环境变量");
            System.exit(1);
        }

        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();

        System.out.println("=== Agent 智能代理示例 (阿里百炼) ===\n");

        // 创建工具
        SearchTool searchTool = new SearchTool();
        CalculatorTool calculatorTool = new CalculatorTool();

        // 创建 Agent
        Agent agent = AiServices.builder(Agent.class)
                .chatLanguageModel(model)
                .tools(searchTool, calculatorTool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        // 测试场景
        System.out.println("--- 场景 1: 信息搜索 ---\n");
        String q1 = "LangChain4j 是什么？最新版本是多少？";
        System.out.println("用户: " + q1);
        String a1 = agent.chat(q1);
        System.out.println("Agent: " + a1);

        System.out.println("\n--- 场景 2: 数学计算 ---\n");
        String q2 = "计算 (100 + 50) * 2 - 75 的结果";
        System.out.println("用户: " + q2);
        String a2 = agent.chat(q2);
        System.out.println("Agent: " + a2);

        System.out.println("\n--- 场景 3: 组合任务 ---\n");
        String q3 = "现在几点了？顺便帮我算一下 365 * 24 等于多少（一年有多少小时）";
        System.out.println("用户: " + q3);
        String a3 = agent.chat(q3);
        System.out.println("Agent: " + a3);

        System.out.println("\n--- 场景 4: 多轮对话 ---\n");
        String q4 = "搜索一下 Java 最新版本";
        System.out.println("用户: " + q4);
        String a4 = agent.chat(q4);
        System.out.println("Agent: " + a4);

        String q5 = "根据你刚才搜索的结果，Java 21 是什么时候发布的？";
        System.out.println("\n用户: " + q5);
        String a5 = agent.chat(q5);
        System.out.println("Agent: " + a5);
    }
}
