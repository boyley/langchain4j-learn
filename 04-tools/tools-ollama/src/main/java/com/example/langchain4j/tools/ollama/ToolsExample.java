package com.example.langchain4j.tools.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

public class ToolsExample {

    public static void main(String[] args) {
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:11434";
        }

        String modelName = System.getenv("OLLAMA_MODEL");
        if (modelName == null || modelName.isEmpty()) {
            modelName = "qwen2:7b";
        }

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();

        System.out.println("=== 工具调用示例 (Ollama) ===\n");
        System.out.println("使用模型: " + modelName + "\n");

        // 创建工具实例
        Calculator calculator = new Calculator();
        WeatherService weatherService = new WeatherService();

        // 创建带工具的助手
        AssistantWithTools assistant = AiServices.builder(AssistantWithTools.class)
                .chatLanguageModel(model)
                .tools(calculator, weatherService)
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
