package com.example.langchain4j.aiservices.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * LangChain4j Demo API AI Services 示例
 *
 * 使用 LangChain4j 官方提供的免费 Demo API，无需任何配置即可运行！
 *
 * Demo API 说明：
 * - 免费使用，无需注册
 * - 有配额限制，仅供学习演示
 * - 使用 gpt-4o-mini 模型
 * - 请求通过 LangChain4j 代理转发
 */
public class AiServicesDemo {
    public static void main(String[] args) {
        // 使用 LangChain4j 官方 Demo API - 无需 API Key！
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== AI Services 示例 - Demo API (无需 API Key) ===\n");

        /**
         * 构建 AI Service - 两种方式
         *
         * 方式1: 简单创建 (只需 model)
         *   AiServices.create(Assistant.class, model);
         *
         * 方式2: Builder 模式 (完整配置)
         *   AiServices.builder(Assistant.class)
         *       .chatLanguageModel(model)      // 必须：聊天模型
         *       .chatMemory(memory)            // 可选：会话记忆，实现多轮对话
         *       .chatMemoryProvider(provider)  // 可选：多用户记忆提供者
         *       .tools(tool1, tool2)           // 可选：工具类实例，让 AI 调用方法
         *       .contentRetriever(retriever)   // 可选：内容检索器，用于 RAG
         *       .retrievalAugmentor(augmentor) // 可选：检索增强器，高级 RAG 配置
         *       .moderationModel(modModel)     // 可选：内容审核模型
         *       .build();
         *
         * AiServices.builder() 参数说明：
         * ┌──────────────────────┬──────────────────────────────────────────────┐
         * │ 参数                 │ 说明                                         │
         * ├──────────────────────┼──────────────────────────────────────────────┤
         * │ chatLanguageModel    │ 聊天模型，必须，处理对话请求                  │
         * │ streamingModel       │ 流式模型，用于流式输出场景                    │
         * │ chatMemory           │ 单用户会话记忆，保存对话历史                  │
         * │ chatMemoryProvider   │ 多用户记忆提供者，根据 @MemoryId 隔离用户     │
         * │ tools                │ 工具实例，AI 可调用的 @Tool 方法              │
         * │ contentRetriever     │ 内容检索器，从知识库检索相关内容 (RAG)        │
         * │ retrievalAugmentor   │ 检索增强器，自定义 RAG 流程                  │
         * │ moderationModel      │ 内容审核模型，过滤不当内容                    │
         * └──────────────────────┴──────────────────────────────────────────────┘
         */

        // Assistant 示例
        System.out.println("--- 示例 1: Assistant 接口 ---\n");

        // 简单创建方式
        Assistant assistant = AiServices.create(Assistant.class, model);

        // 等价于 Builder 方式:
        // Assistant assistant = AiServices.builder(Assistant.class)
        //         .chatLanguageModel(model)
        //         .build();

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
