package com.example.langchain4j.streaming.demo;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

/**
 * LangChain4j Demo API 流式响应示例
 *
 * 使用 LangChain4j 官方提供的免费 Demo API，无需任何配置即可运行！
 *
 * Demo API 说明：
 * - 免费使用，无需注册
 * - 有配额限制，仅供学习演示
 * - 使用 gpt-4o-mini 模型
 * - 请求通过 LangChain4j 代理转发
 */
public class StreamingDemo {

    public static void main(String[] args) throws Exception {
        /**
         * 构建 StreamingChatLanguageModel - 流式聊天模型
         *
         * 与普通 ChatLanguageModel 的区别：
         * - ChatLanguageModel: 等待完整回复后一次性返回
         * - StreamingChatLanguageModel: 逐个 token 实时返回，用户体验更好
         *
         * OpenAiStreamingChatModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                           │
         * │ apiKey          │ API 密钥                                           │
         * │ modelName       │ 模型名称                                           │
         * │ temperature     │ 温度参数                                           │
         * │ maxTokens       │ 最大生成 token 数                                  │
         * │ timeout         │ 连接超时时间                                       │
         * │ logRequests     │ 打印请求日志                                       │
         * │ logResponses    │ 打印响应日志                                       │
         * └─────────────────┴────────────────────────────────────────────────────┘
         *
         * StreamingResponseHandler 回调方法：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 方法            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ onNext(token)   │ 每收到一个 token 时调用，用于实时显示              │
         * │ onComplete(resp)│ 所有 token 接收完毕时调用                          │
         * │ onError(error)  │ 发生错误时调用                                     │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址
                .apiKey("demo")                                    // API 密钥
                .modelName("gpt-4o-mini")                          // 模型名称
                // .temperature(0.7)                               // 可选：温度
                // .timeout(Duration.ofSeconds(60))                 // 可选：超时
                .build();

        System.out.println("=== 流式响应示例 - Demo API (无需 API Key) ===\n");

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
