package com.example.langchain4j.rag.ollama;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

public class RagExample {

    interface AssistantWithRag {
        String answer(String question);
    }

    public static void main(String[] args) {
        String ollamaHost = System.getenv("OLLAMA_HOST");
        if (ollamaHost == null || ollamaHost.isEmpty()) {
            ollamaHost = "http://localhost:11434";
        }

        System.out.println("=== RAG 检索增强生成示例 (Ollama) ===\n");
        System.out.println("Ollama 地址: " + ollamaHost);
        System.out.println("提示: 请确保 Ollama 服务已启动，且已拉取 llama3 和 nomic-embed-text 模型\n");

        // 1. 创建模型
        ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl(ollamaHost)
                .modelName("llama3")
                .build();

        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaHost)
                .modelName("nomic-embed-text")
                .build();

        // 2. 准备知识库文档
        System.out.println("--- 步骤 1: 准备知识库 ---\n");

        String knowledgeBase = """
            LangChain4j 是一个 Java 版本的 LangChain 框架，用于构建 AI 应用。
            它支持多种 LLM 提供商，包括 OpenAI、Ollama、阿里云通义千问等。

            LangChain4j 的主要特性包括：
            1. AI Services - 声明式 AI 服务，使用接口定义 AI 能力
            2. Tools - 工具调用，让 AI 可以执行函数
            3. Memory - 会话记忆，支持多轮对话
            4. RAG - 检索增强生成，结合知识库回答问题
            5. Streaming - 流式响应，实时输出

            LangChain4j 要求 Java 17 或更高版本。
            可以通过 Maven 或 Gradle 引入依赖。
            官方文档地址: https://docs.langchain4j.dev/

            使用 AiServices 可以快速创建 AI 助手：
            定义接口，然后使用 AiServices.create() 方法即可。
            支持 @SystemMessage 定义系统提示词，@UserMessage 定义用户消息模板。
            """;

        Document document = Document.from(knowledgeBase);

        // 3. 分割文档
        System.out.println("--- 步骤 2: 分割文档 ---\n");

        DocumentSplitter splitter = DocumentSplitters.recursive(200, 20);
        List<TextSegment> segments = splitter.split(document);

        System.out.println("文档被分割为 " + segments.size() + " 个片段");
        for (int i = 0; i < Math.min(3, segments.size()); i++) {
            System.out.println("片段 " + (i + 1) + ": " + segments.get(i).text().substring(0, Math.min(50, segments.get(i).text().length())) + "...");
        }

        // 4. 向量化并存储
        System.out.println("\n--- 步骤 3: 向量化并存储 ---\n");

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }

        System.out.println("已存储 " + segments.size() + " 个向量");

        // 5. 创建 ContentRetriever
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)  // 检索最相关的3个片段
                .minScore(0.5)  // 最低相似度阈值
                .build();

        // 6. 创建带 RAG 的助手
        System.out.println("\n--- 步骤 4: 创建 RAG 助手 ---\n");

        AssistantWithRag assistant = AiServices.builder(AssistantWithRag.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        // 7. 测试问答
        System.out.println("--- 步骤 5: 测试问答 ---\n");

        String[] questions = {
            "LangChain4j 是什么？",
            "LangChain4j 有哪些主要特性？",
            "如何创建 AI 助手？",
            "LangChain4j 需要什么版本的 Java？"
        };

        for (String question : questions) {
            System.out.println("问题: " + question);
            String answer = assistant.answer(question);
            System.out.println("回答: " + answer);
            System.out.println();
        }
    }
}
