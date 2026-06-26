package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) 检索增强生成示例
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * RAG 是什么？
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * RAG 让 AI 能够基于你的文档/知识库来回答问题，而不是只依赖训练数据。
 *
 * 场景举例：
 *   用户问："公司的请假制度是什么？"
 *   - 没有 RAG：AI 不知道你们公司的制度，只能泛泛而谈
 *   - 有 RAG：AI 先从你的《员工手册》中找到相关内容，再基于内容回答
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * RAG 工作流程
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
 *   │  准备文档    │───▶│  分割文档    │───▶│  向量化     │───▶│  存储向量    │
 *   │ (知识库)    │    │ (切成小块)   │    │ (Embedding) │    │ (向量数据库) │
 *   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
 *         │                                                          │
 *         │  准备阶段 ───────────────────────────────────────────────┘
 *         │
 *         │  查询阶段 ────────────────────────────────────────────────┐
 *         │                                                          │
 *   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌──────▼──────┐
 *   │  返回答案    │◀───│  AI 生成    │◀───│  检索相关   │◀───│  用户提问    │
 *   │             │    │  回答       │    │  文档片段   │    │             │
 *   └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RagDemo {

    interface AssistantWithRag {
        String answer(String question);
    }

    public static void main(String[] args) {
        System.out.println("=== RAG 检索增强生成示例 - Demo API (无需 API Key) ===\n");

        // 1. 创建模型 - 使用 LangChain4j 官方 Demo API
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")
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

        /**
         * 为什么要分割文档？
         * ─────────────────────────────────────────────────────────────
         * 1. LLM 有上下文长度限制（如 4K、8K tokens）
         * 2. 太长的文档向量化后语义会"稀释"
         * 3. 检索时需要定位到具体段落，而非整篇文档
         *
         * DocumentSplitters.recursive(maxSegmentSize, maxOverlap) 参数说明：
         * ┌────────────────┬─────────────────────────────────────────────────────┐
         * │ 参数           │ 说明                                                │
         * ├────────────────┼─────────────────────────────────────────────────────┤
         * │ maxSegmentSize │ 每个片段的最大字符数                                 │
         * │                │ - 太小：语义不完整，检索效果差                        │
         * │                │ - 太大：检索不精确，浪费 token                        │
         * │                │ - 推荐：200-1000 字符                                │
         * ├────────────────┼─────────────────────────────────────────────────────┤
         * │ maxOverlap     │ 相邻片段的重叠字符数                                 │
         * │                │ - 作用：避免在句子中间截断，保持语义连贯              │
         * │                │ - 太小：可能截断重要信息                              │
         * │                │ - 太大：片段间重复内容多                              │
         * │                │ - 推荐：maxSegmentSize 的 10-20%                     │
         * └────────────────┴─────────────────────────────────────────────────────┘
         *
         * 分割策略：recursive（递归分割）
         * - 优先按段落（\n\n）分割
         * - 段落太长则按句子（。！？）分割
         * - 句子太长则按逗号/空格分割
         * - 保证每个片段不超过 maxSegmentSize
         */
        DocumentSplitter splitter = DocumentSplitters.recursive(
                200,  // maxSegmentSize: 每个片段最多 200 个字符
                20    // maxOverlap: 相邻片段重叠 20 个字符（保持上下文连贯）
        );
        List<TextSegment> segments = splitter.split(document);

        System.out.println("文档被分割为 " + segments.size() + " 个片段");
        for (int i = 0; i < Math.min(3, segments.size()); i++) {
            System.out.println("片段 " + (i + 1) + ": " + segments.get(i).text().substring(0, Math.min(50, segments.get(i).text().length())) + "...");
        }

        // 4. 向量化并存储
        System.out.println("\n--- 步骤 3: 向量化并存储 ---\n");

        /**
         * EmbeddingStore - 向量存储
         * ─────────────────────────────────────────────────────────────
         * 作用：存储文档的向量和原文，支持相似度搜索
         *
         * 可选实现：
         * ┌─────────────────────┬───────────────────────────────────────┐
         * │ 实现类              │ 说明                                  │
         * ├─────────────────────┼───────────────────────────────────────┤
         * │ InMemoryEmbedding   │ 内存存储，重启丢失，适合测试/Demo      │
         * │ MilvusEmbedding     │ Milvus 向量数据库，生产推荐            │
         * │ PineconeEmbedding   │ Pinecone 云服务                       │
         * │ ElasticsearchEmb    │ ES 向量搜索                           │
         * │ PgVectorEmbedding   │ PostgreSQL + pgvector 扩展            │
         * │ RedisEmbedding      │ Redis Stack 向量搜索                  │
         * └─────────────────────┴───────────────────────────────────────┘
         */
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // 将每个文档片段向量化并存储
        for (TextSegment segment : segments) {
            // embed() 将文本转为向量（一组数字）
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            // add() 存储向量和原文（搜索时返回原文）
            embeddingStore.add(embedding, segment);
        }

        System.out.println("已存储 " + segments.size() + " 个向量");

        // 5. 创建 ContentRetriever
        /**
         * ContentRetriever - 内容检索器
         * ─────────────────────────────────────────────────────────────
         * 作用：根据用户问题，从向量库中检索相关文档片段
         *
         * EmbeddingStoreContentRetriever.builder() 参数说明：
         * ┌─────────────────┬───────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                              │
         * ├─────────────────┼───────────────────────────────────────────────────┤
         * │ embeddingStore  │ 必须：向量存储实例                                 │
         * │                 │ 作用：指定从哪个向量库检索                          │
         * ├─────────────────┼───────────────────────────────────────────────────┤
         * │ embeddingModel  │ 必须：向量模型                                     │
         * │                 │ 作用：将用户问题也转为向量，用于相似度计算           │
         * │                 │ 注意：必须和存储时用的模型一致！                     │
         * ├─────────────────┼───────────────────────────────────────────────────┤
         * │ maxResults      │ 可选：最多返回几个片段（默认 3）                    │
         * │                 │ - 太少：可能遗漏相关信息                           │
         * │                 │ - 太多：增加 token 消耗，可能引入噪音               │
         * │                 │ - 推荐：3-5 个                                    │
         * ├─────────────────┼───────────────────────────────────────────────────┤
         * │ minScore        │ 可选：最低相似度阈值（0-1）                         │
         * │                 │ 作用：过滤掉相似度太低的结果                        │
         * │                 │ - 设为 0.5：只返回相似度 > 50% 的片段              │
         * │                 │ - 设为 0：返回所有结果（不推荐）                    │
         * │                 │ - 推荐：0.5-0.7                                   │
         * ├─────────────────┼───────────────────────────────────────────────────┤
         * │ filter          │ 可选：元数据过滤器                                 │
         * │                 │ 作用：按部门、权限等过滤（见企业知识库示例）         │
         * └─────────────────┴───────────────────────────────────────────────────┘
         */
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)   // 指定向量存储
                .embeddingModel(embeddingModel)   // 指定向量模型（用于将问题向量化）
                .maxResults(3)                    // 最多返回 3 个相关片段
                .minScore(0.5)                    // 相似度至少 50% 才返回
                .build();

        // 6. 创建带 RAG 的助手
        System.out.println("\n--- 步骤 4: 创建 RAG 助手 ---\n");

        /**
         * AiServices 集成 RAG
         * ─────────────────────────────────────────────────────────────
         * 配置 contentRetriever 后，每次调用 assistant.answer() 时：
         * 1. 先将问题向量化
         * 2. 从向量库检索相关片段
         * 3. 将片段作为上下文，连同问题一起发给 LLM
         * 4. LLM 基于上下文生成回答
         *
         * 用户问："LangChain4j 需要什么版本的 Java？"
         *    ↓
         * 检索到片段："LangChain4j 要求 Java 17 或更高版本..."
         *    ↓
         * 发给 LLM：
         *   "根据以下信息回答问题：
         *    [LangChain4j 要求 Java 17 或更高版本...]
         *    问题：LangChain4j 需要什么版本的 Java？"
         *    ↓
         * LLM 回答："LangChain4j 需要 Java 17 或更高版本。"
         */
        AssistantWithRag assistant = AiServices.builder(AssistantWithRag.class)
                .chatLanguageModel(chatModel)         // 聊天模型（生成回答）
                .contentRetriever(contentRetriever)   // 内容检索器（RAG 核心）
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
