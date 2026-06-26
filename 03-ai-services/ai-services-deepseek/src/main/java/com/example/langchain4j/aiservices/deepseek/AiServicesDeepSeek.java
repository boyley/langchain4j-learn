package com.example.langchain4j.aiservices.deepseek;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * DeepSeek AI Services 示例
 *
 * 演示如何使用 @AiService 声明式接口。
 * DeepSeek API 兼容 OpenAI 协议，因此使用 OpenAiChatModel 并配置自定义 baseUrl。
 */
public class AiServicesDeepSeek {
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

        System.out.println("=== AI Services 示例 (DeepSeek) ===\n");

        // Assistant 示例
        System.out.println("--- 示例 1: Assistant 接口 ---\n");
        Assistant assistant = AiServices.create(Assistant.class, model);

        String response1 = assistant.chat("什么是 LangChain4j？");
        System.out.println("chat(): " + response1);

        String response2 = assistant.chatWithSystemPrompt("用一句话介绍 Java");
        System.out.println("\nchatWithSystemPrompt(): " + response2);

        String response3 = assistant.translate("今天天气真好", "英文");
        System.out.println("\ntranslate(): " + response3);

        // SentimentAnalyzer 示例
        System.out.println("\n--- 示例 2: SentimentAnalyzer 接口 ---\n");
        SentimentAnalyzer analyzer = AiServices.create(SentimentAnalyzer.class, model);

        String review1 = "这家餐厅的菜品非常美味，服务也很周到！";
        String review2 = "产品质量太差了，完全不值这个价格。";
        String review3 = "东西收到了，包装完好。";

        System.out.println("文本: " + review1);
        System.out.println("情感: " + analyzer.analyzeSentiment(review1));

        System.out.println("\n文本: " + review2);
        System.out.println("情感: " + analyzer.analyzeSentiment(review2));

        System.out.println("\n文本: " + review3);
        System.out.println("情感: " + analyzer.analyzeSentiment(review3));

        System.out.println("\n是否正面评价: " + analyzer.isPositive(review1));

        String multiOpinion = "这款手机拍照很好，但电池不耐用，价格也偏贵，不过客服态度还不错。";
        System.out.println("\n文本: " + multiOpinion);
        System.out.println("观点数量: " + analyzer.countOpinions(multiOpinion));
    }
}
