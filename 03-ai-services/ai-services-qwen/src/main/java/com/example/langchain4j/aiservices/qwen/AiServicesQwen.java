package com.example.langchain4j.aiservices.qwen;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;

public class AiServicesQwen {
    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DASHSCOPE_API_KEY 环境变量");
            System.exit(1);
        }

        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")
                .build();

        System.out.println("=== AI Services 示例 (Qwen) ===\n");

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
