package com.example.langchain4j.prompt.deepseek;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek 提示词模板示例
 *
 * 演示如何使用 PromptTemplate 构建动态提示词。
 * PromptTemplate 允许你定义带有变量占位符的模板，运行时替换为实际值。
 * DeepSeek API 兼容 OpenAI 协议，因此使用 OpenAiChatModel 并配置自定义 baseUrl。
 */
public class PromptTemplateDeepSeek {

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DEEPSEEK_API_KEY 环境变量");
            System.exit(1);
        }

        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();

        System.out.println("=== 提示词模板示例 (DeepSeek) ===\n");

        // 示例 1: 单变量模板
        System.out.println("--- 示例 1: 单变量模板 ---\n");
        PromptTemplate simpleTemplate = PromptTemplate.from(
                "请用简单的语言解释什么是 {{topic}}，适合初学者理解。"
        );
        Prompt prompt1 = simpleTemplate.apply(Map.of("topic", "机器学习"));
        System.out.println("提示词: " + prompt1.text());
        System.out.println("AI: " + model.generate(prompt1.text()));

        // 示例 2: 多变量模板
        System.out.println("\n--- 示例 2: 多变量模板 ---\n");
        PromptTemplate multiVarTemplate = PromptTemplate.from(
                "请将以下 {{sourceLanguage}} 文本翻译成 {{targetLanguage}}:\n\n{{text}}"
        );
        Map<String, Object> variables = new HashMap<>();
        variables.put("sourceLanguage", "中文");
        variables.put("targetLanguage", "英文");
        variables.put("text", "今天天气真好，我们去公园散步吧。");
        Prompt prompt2 = multiVarTemplate.apply(variables);
        System.out.println("提示词: " + prompt2.text());
        System.out.println("AI: " + model.generate(prompt2.text()));

        // 示例 3: 角色扮演模板
        System.out.println("\n--- 示例 3: 角色扮演模板 ---\n");
        PromptTemplate roleTemplate = PromptTemplate.from(
                "你是一位 {{role}}。用户问: {{question}}\n请以你的专业身份回答。"
        );
        Prompt prompt3 = roleTemplate.apply(Map.of(
                "role", "资深 Java 架构师",
                "question", "微服务和单体架构如何选择？"
        ));
        System.out.println("提示词: " + prompt3.text());
        System.out.println("AI: " + model.generate(prompt3.text()));

        // 示例 4: 结构化输出模板
        System.out.println("\n--- 示例 4: 结构化输出模板 ---\n");
        PromptTemplate jsonTemplate = PromptTemplate.from("""
                分析以下产品评论的情感，返回 JSON 格式:

                评论: {{review}}

                请返回格式:
                {
                    "sentiment": "positive/negative/neutral",
                    "confidence": 0.0-1.0,
                    "keywords": ["关键词1", "关键词2"]
                }
                """);
        Prompt prompt4 = jsonTemplate.apply(Map.of(
                "review", "这款手机拍照效果很好，电池也耐用，就是价格有点贵。"
        ));
        System.out.println("提示词: " + prompt4.text());
        System.out.println("AI: " + model.generate(prompt4.text()));
    }
}
