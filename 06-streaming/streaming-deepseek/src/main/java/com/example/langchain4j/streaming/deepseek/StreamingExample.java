package com.example.langchain4j.streaming.deepseek;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

/**
 * DeepSeek 流式响应示例
 *
 * 演示如何使用 StreamingChatLanguageModel 实现实时 Token 输出。
 * DeepSeek API 兼容 OpenAI 协议，因此使用 OpenAiStreamingChatModel 并配置自定义 baseUrl。
 */
public class StreamingExample {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 DEEPSEEK_API_KEY 环境变量");
            System.exit(1);
        }

        // 创建流式模型
        StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();

        System.out.println("=== 流式响应示例 (DeepSeek) ===\n");

        // 示例 1: 基础流式输出
        System.out.println("--- 示例 1: 基础流式输出 ---\n");
        System.out.println("用户: 请讲一个关于程序员的笑话。\n");
        System.out.print("AI: ");

        CompletableFuture<Response<AiMessage>> future1 = new CompletableFuture<>();

        model.generate("请讲一个关于程序员的笑话。", new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                // 每收到一个 token 就立即输出
                System.out.print(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("\n");
                future1.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("\n错误: " + error.getMessage());
                future1.completeExceptionally(error);
            }
        });

        // 等待完成
        future1.get();

        // 示例 2: 带统计信息的流式输出
        System.out.println("--- 示例 2: 带统计信息 ---\n");
        System.out.println("用户: 简要介绍 Java 的特点。\n");
        System.out.print("AI: ");

        CompletableFuture<Response<AiMessage>> future2 = new CompletableFuture<>();
        final int[] tokenCount = {0};
        final long startTime = System.currentTimeMillis();

        model.generate("简要介绍 Java 的特点。", new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                System.out.print(token);
                tokenCount[0]++;
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n");
                System.out.println("--- 统计信息 ---");
                System.out.println("Token 数量: " + tokenCount[0]);
                System.out.println("总耗时: " + duration + " ms");
                System.out.println("平均速度: " + String.format("%.2f", tokenCount[0] * 1000.0 / duration) + " tokens/s");
                future2.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("\n错误: " + error.getMessage());
                future2.completeExceptionally(error);
            }
        });

        future2.get();
    }
}
