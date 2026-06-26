package com.example.langchain4j.tools.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * LangChain4j Demo API 工具调用示例
 *
 * 使用 LangChain4j 官方提供的免费 Demo API，无需任何配置即可运行！
 *
 * Demo API 说明：
 * - 免费使用，无需注册
 * - 有配额限制，仅供学习演示
 * - 使用 gpt-4o-mini 模型
 * - 请求通过 LangChain4j 代理转发
 */
public class ToolsDemo {

    public static void main(String[] args) {
        // 使用 LangChain4j 官方 Demo API - 无需 API Key！
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== 工具调用示例 - Demo API (无需 API Key) ===\n");

        // 创建工具实例 (包含 @Tool 和 @P 注解的方法)
        Calculator calculator = new Calculator();
        WeatherService weatherService = new WeatherService();

        /**
         * 带工具的 AiServices 构建
         *
         * tools() 参数说明：
         * ┌───────────────────┬─────────────────────────────────────────────────┐
         * │ 传入内容          │ 说明                                            │
         * ├───────────────────┼─────────────────────────────────────────────────┤
         * │ 工具类实例        │ 包含 @Tool 注解方法的对象，可传多个              │
         * │ ToolProvider      │ 动态提供工具的接口，用于运行时决定可用工具       │
         * │ ToolSpecification │ 工具规格定义，用于自定义工具描述                 │
         * └───────────────────┴─────────────────────────────────────────────────┘
         *
         * 工具调用流程：
         * 1. LangChain4j 扫描工具实例中的 @Tool 方法
         * 2. 生成工具描述 JSON 发送给 AI
         * 3. AI 根据用户输入选择合适的工具
         * 4. AI 从用户输入中提取参数 (根据 @P 描述)
         * 5. LangChain4j 调用对应的 Java 方法
         * 6. 将返回值传回 AI
         * 7. AI 组织最终回答
         */
        AssistantWithTools assistant = AiServices.builder(AssistantWithTools.class)
                .chatLanguageModel(model)               // 必须：聊天模型
                .tools(calculator, weatherService)      // 必须：工具实例 (可传多个)
                // .chatMemory(memory)                  // 可选：会话记忆
                .build();

        // 测试计算器
        System.out.println("--- 测试 1: 数学计算 ---\n");
        String question1 = "请帮我计算 (15 + 27) * 3 的结果";
        System.out.println("用户: " + question1);
        String response1 = assistant.chat(question1);
        System.out.println("AI: " + response1);

        // 测试天气查询
        System.out.println("\n--- 测试 2: 天气查询 ---\n");
        String question2 = "北京和上海今天的天气怎么样？";
        System.out.println("用户: " + question2);
        String response2 = assistant.chat(question2);
        System.out.println("AI: " + response2);

        // 测试混合场景
        System.out.println("\n--- 测试 3: 混合场景 ---\n");
        String question3 = "深圳的温度是多少？如果比30度高，计算超出多少度。";
        System.out.println("用户: " + question3);
        String response3 = assistant.chat(question3);
        System.out.println("AI: " + response3);
    }
}
