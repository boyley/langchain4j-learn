package com.example.dashscope;

import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

/**
 * Demo02: 流式输出
 * 像 ChatGPT 一样，一个字一个字地输出
 */
public class Demo02_Streaming {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Demo02: 阿里百炼 - 流式输出");
        System.out.println("=".repeat(60));

        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        // 创建流式模型
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();

        System.out.println("\n【问题】写一首关于编程的诗\n");
        System.out.println("【回答】（流式输出）");

        CompletableFuture<Void> future = new CompletableFuture<>();

        // 流式调用
        model.generate("写一首关于编程的现代诗，要有意境", new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                // 每生成一个 token 就输出
                System.out.print(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("\n");
                System.out.println("-".repeat(40));
                System.out.println("生成完成！");
                System.out.println("总 Token: " + response.tokenUsage().totalTokenCount());
                future.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("出错了: " + error.getMessage());
                future.completeExceptionally(error);
            }
        });

        // 等待完成
        future.get();

        System.out.println("=".repeat(60));
    }
}
