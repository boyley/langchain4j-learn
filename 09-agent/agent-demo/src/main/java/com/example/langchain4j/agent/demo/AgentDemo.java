package com.example.langchain4j.agent.demo;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

/**
 * LangChain4j Demo API Agent 智能代理示例
 *
 * 使用 LangChain4j 官方提供的免费 Demo API，无需任何配置即可运行！
 *
 * Demo API 说明：
 * - 免费使用，无需注册
 * - 有配额限制，仅供学习演示
 * - 使用 gpt-4o-mini 模型
 * - 请求通过 LangChain4j 代理转发
 */
public class AgentDemo {

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
        /**
         * 构建 ChatLanguageModel
         *
         * OpenAiChatModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                           │
         * │ apiKey          │ API 密钥                                           │
         * │ modelName       │ 模型名称                                           │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址
                .apiKey("demo")                                    // API 密钥
                .modelName("gpt-4o-mini")                          // 模型名称
                .build();

        System.out.println("=== Agent 智能代理示例 - Demo API (无需 API Key) ===\n");

        // 创建工具实例 - 包含 @Tool 注解方法的类
        SearchTool searchTool = new SearchTool();
        CalculatorTool calculatorTool = new CalculatorTool();

        /**
         * 创建 Agent - 带工具和记忆的 AI 助手
         *
         * Agent vs 普通 Assistant:
         * ┌────────────────────────┬───────────────────────────────────────────┐
         * │ 普通 Assistant          │ Agent                                    │
         * ├────────────────────────┼───────────────────────────────────────────┤
         * │ 只能对话                │ 可以调用工具执行任务                       │
         * │ 无记忆                  │ 有会话记忆，支持多轮对话                   │
         * │ 单一功能                │ 自主决策使用哪个工具                       │
         * └────────────────────────┴───────────────────────────────────────────┘
         *
         * AiServices.builder() 参数说明：
         * ┌──────────────────────┬──────────────────────────────────────────────┐
         * │ 参数                 │ 说明                                         │
         * ├──────────────────────┼──────────────────────────────────────────────┤
         * │ chatLanguageModel    │ 必须：聊天模型，处理对话和决策                │
         * │ tools                │ 可选：工具类实例，AI 可调用的 @Tool 方法      │
         * │                      │ 可传入多个工具类，用逗号分隔                  │
         * │ chatMemory           │ 可选：会话记忆，保存对话历史                  │
         * │                      │ 让 Agent 能记住之前的对话内容                │
         * │ chatMemoryProvider   │ 可选：多用户记忆提供者                       │
         * │ contentRetriever     │ 可选：内容检索器 (RAG)                       │
         * └──────────────────────┴──────────────────────────────────────────────┘
         *
         * MessageWindowChatMemory.withMaxMessages(n) 参数：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ maxMessages     │ 最大消息数，超出后自动删除最早的消息                 │
         * │                 │ 10 条消息 ≈ 5 轮对话（用户+AI各1条）               │
         * └─────────────────┴────────────────────────────────────────────────────┘
         *
         * Agent 工作流程：
         * 1. 用户提问
         * 2. AI 分析问题，决定是否需要使用工具
         * 3. 如需工具，调用对应的 @Tool 方法并获取结果
         * 4. AI 整合工具结果，生成最终回答
         * 5. 对话记录保存到 chatMemory
         */
        Agent agent = AiServices.builder(Agent.class)
                .chatLanguageModel(model)                          // 必须：聊天模型
                .tools(searchTool, calculatorTool)                 // 工具实例列表
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))  // 会话记忆
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
