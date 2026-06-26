package com.example.langchain4j.chat.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

/**
 * Ollama 基础对话示例
 *
 * 演示如何使用 LangChain4j 与本地 Ollama 模型进行对话。
 * Ollama 是一个本地运行的 LLM 服务，免费无限制，非常适合学习和开发。
 *
 * 使用前请确保:
 * 1. 已安装 Ollama: https://ollama.ai
 * 2. 已下载模型: ollama pull llama3 (或其他模型)
 * 3. Ollama 服务正在运行: ollama serve
 */
public class BasicChatOllama {

    public static void main(String[] args) {
        // 1. 获取 Ollama 服务地址，默认 localhost:11434
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:11434";
        }

        // 2. 创建 ChatLanguageModel 实例
        // 常用模型: llama3, llama2, mistral, qwen2, gemma
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("llama3")        // 使用的模型名称
                .temperature(0.7)           // 控制回复的随机性
                .build();

        // 3. 发送消息并获取回复
        System.out.println("=== Ollama 基础对话示例 ===\n");
        System.out.println("使用模型: llama3");
        System.out.println("服务地址: " + baseUrl + "\n");

        // 简单对话
        String question = "你好，请用一句话介绍一下你自己。";
        System.out.println("用户: " + question);

        String answer = model.generate(question);
        System.out.println("AI: " + answer);

        System.out.println("\n--- 第二轮对话 ---\n");

        // 另一个问题
        String question2 = "Java 和 Python 哪个更适合做 AI 开发？请简要说明。";
        System.out.println("用户: " + question2);

        String answer2 = model.generate(question2);
        System.out.println("AI: " + answer2);
    }
}
