package com.example.mcp;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Demo03: 使用 langchain4j 的工具能力模拟 MCP 场景
 *
 * 虽然这不是真正的 MCP，但展示了类似的概念：
 * AI 模型通过工具与外部世界交互
 */
public class Demo03_McpToolProvider {

    // ========== 定义工具类 ==========

    /**
     * 时间工具 - 类似 MCP 的 Tool
     */
    static class TimeTools {

        @Tool("获取当前时间")
        public String getCurrentTime() {
            return LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        }

        @Tool("获取今天是星期几")
        public String getDayOfWeek() {
            String[] days = {"日", "一", "二", "三", "四", "五", "六"};
            int day = LocalDateTime.now().getDayOfWeek().getValue() % 7;
            return "今天是星期" + days[day];
        }
    }

    /**
     * 天气工具（模拟）
     */
    static class WeatherTools {

        @Tool("查询城市天气")
        public String getWeather(@P("城市名称") String city) {
            // 模拟天气数据
            String[] conditions = {"晴", "多云", "阴", "小雨"};
            int temp = 15 + new Random().nextInt(20);
            String condition = conditions[new Random().nextInt(conditions.length)];

            return String.format("%s天气：%s，气温 %d°C", city, condition, temp);
        }
    }

    /**
     * 计算工具
     */
    static class CalculatorTools {

        @Tool("计算数学表达式")
        public String calculate(
                @P("运算类型：add/subtract/multiply/divide") String operation,
                @P("第一个数") double a,
                @P("第二个数") double b) {

            double result = switch (operation.toLowerCase()) {
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> b != 0 ? a / b : Double.NaN;
                default -> Double.NaN;
            };

            if (Double.isNaN(result)) {
                return "计算错误";
            }

            return String.format("%.2f %s %.2f = %.2f",
                    a, getOperationSymbol(operation), b, result);
        }

        private String getOperationSymbol(String op) {
            return switch (op.toLowerCase()) {
                case "add" -> "+";
                case "subtract" -> "-";
                case "multiply" -> "×";
                case "divide" -> "÷";
                default -> "?";
            };
        }
    }

    // ========== AI Service 接口 ==========

    interface AssistantWithTools {
        String chat(String message);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo03: 工具调用演示（MCP 概念）");
        System.out.println("=".repeat(60));

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 创建带工具的 AI 助手
        AssistantWithTools assistant = AiServices.builder(AssistantWithTools.class)
                .chatLanguageModel(model)
                .tools(
                        new TimeTools(),
                        new WeatherTools(),
                        new CalculatorTools()
                )
                .build();

        // 测试对话
        String[] questions = {
                "现在几点了？",
                "北京今天天气怎么样？",
                "帮我算一下 123 乘以 456 等于多少？",
                "今天是星期几？上海天气如何？"
        };

        for (String question : questions) {
            System.out.println("\n" + "-".repeat(50));
            System.out.println("用户: " + question);
            System.out.println("-".repeat(50));

            String answer = assistant.chat(question);
            System.out.println("助手: " + answer);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("工具调用演示完成！");
        System.out.println("=".repeat(60));

        System.out.println("""

                【总结】
                这个 Demo 展示了 AI + 工具 的核心概念：

                1. 定义工具（@Tool 注解）
                   └─ 类似 MCP Server 提供的 Tools

                2. AI 自动判断何时调用工具
                   └─ 类似 MCP Client 的行为

                3. 工具执行后返回结果给 AI
                   └─ 类似 MCP 的响应流程

                真正的 MCP 优势：
                - 工具定义与 AI 应用解耦
                - 可以动态发现和连接工具
                - 标准协议，跨平台复用
                """);
    }
}
