package com.example.langchain4j.embedding.demo;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.*;

/**
 * 向量相似性检索完整教程
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 第一章：什么是向量相似性检索？
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 传统搜索 vs 向量搜索：
 *
 *   传统关键词搜索：
 *     搜索 "苹果手机" → 只能找到包含 "苹果手机" 这几个字的文档
 *     搜索 "iPhone"  → 找不到 "苹果手机" 相关内容（关键词不匹配）
 *
 *   向量相似性搜索：
 *     搜索 "苹果手机" → 能找到 "iPhone"、"iOS设备"、"Apple手机" 等
 *     因为它们的 语义向量 相近！
 *
 * 工作原理：
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │  1. 文档入库时：文本 → Embedding 模型 → 向量 → 存入向量数据库           │
 *   │  2. 搜索时：    查询 → Embedding 模型 → 向量 → 在数据库中找相似向量     │
 *   │  3. 返回结果：  相似度最高的 N 个文档                                   │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 第二章：相似度计算方法
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 常用的三种相似度计算方法：
 *
 * ┌──────────────────┬────────────────────────────────────────────────────────┐
 * │ 方法             │ 说明                                                   │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ 余弦相似度       │ 计算两个向量的夹角余弦值                               │
 * │ (Cosine)         │ 范围: -1 到 1，1 表示完全相同                          │
 * │                  │ 特点: 不受向量长度影响，只看方向                        │
 * │                  │ 适用: 文本相似度（最常用）                              │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ 欧氏距离         │ 计算两个向量的直线距离                                 │
 * │ (Euclidean)      │ 范围: 0 到 ∞，0 表示完全相同                           │
 * │                  │ 特点: 受向量长度影响                                   │
 * │                  │ 适用: 图像、空间位置                                   │
 * ├──────────────────┼────────────────────────────────────────────────────────┤
 * │ 点积             │ 计算两个向量的点积                                     │
 * │ (Dot Product)    │ 范围: -∞ 到 +∞                                         │
 * │                  │ 特点: 结合方向和大小                                   │
 * │                  │ 适用: 推荐系统                                         │
 * └──────────────────┴────────────────────────────────────────────────────────┘
 *
 * LangChain4j 默认使用余弦相似度，返回值范围 0-1（已归一化）
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 第三章：EmbeddingStore 向量存储
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * EmbeddingStore 是向量数据库的抽象接口：
 *
 * 主要方法：
 * ┌────────────────────────────────────┬──────────────────────────────────────┐
 * │ 方法                               │ 说明                                 │
 * ├────────────────────────────────────┼──────────────────────────────────────┤
 * │ add(embedding)                     │ 添加单个向量（无原文）               │
 * │ add(embedding, textSegment)        │ 添加向量和原文                       │
 * │ add(id, embedding)                 │ 添加向量并指定 ID                    │
 * │ addAll(embeddings)                 │ 批量添加向量                         │
 * │ addAll(embeddings, textSegments)   │ 批量添加向量和原文                   │
 * ├────────────────────────────────────┼──────────────────────────────────────┤
 * │ search(request)                    │ 搜索相似向量（推荐）                 │
 * │ findRelevant(embedding, maxResults)│ 简单搜索（旧 API）                   │
 * ├────────────────────────────────────┼──────────────────────────────────────┤
 * │ remove(id)                         │ 删除指定 ID 的向量                   │
 * │ removeAll(ids)                     │ 批量删除                             │
 * │ removeAll(filter)                  │ 按条件删除                           │
 * │ removeAll()                        │ 清空所有                             │
 * └────────────────────────────────────┴──────────────────────────────────────┘
 *
 * 可用实现：
 * ┌─────────────────────┬──────────────────────────────────────────────────────┐
 * │ 实现类              │ 说明                                                 │
 * ├─────────────────────┼──────────────────────────────────────────────────────┤
 * │ InMemoryEmbedding   │ 内存存储，重启丢失，适合测试/Demo                    │
 * │ MilvusEmbedding     │ Milvus 向量数据库，高性能，生产推荐                  │
 * │ PineconeEmbedding   │ Pinecone 托管服务，免运维                            │
 * │ QdrantEmbedding     │ Qdrant 向量数据库，Rust 实现，高性能                 │
 * │ WeaviateEmbedding   │ Weaviate 向量数据库，支持混合搜索                    │
 * │ ElasticsearchEmb    │ Elasticsearch 向量搜索                               │
 * │ PgVectorEmbedding   │ PostgreSQL + pgvector 扩展                           │
 * │ RedisEmbedding      │ Redis Stack 向量搜索                                 │
 * │ ChromaEmbedding     │ Chroma 向量数据库                                    │
 * │ Neo4jEmbedding      │ Neo4j 图数据库向量搜索                               │
 * └─────────────────────┴──────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 第四章：EmbeddingSearchRequest 搜索请求
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * EmbeddingSearchRequest.builder() 参数详解：
 *
 * ┌─────────────────┬──────────────────────────────────────────────────────────┐
 * │ 参数            │ 说明                                                     │
 * ├─────────────────┼──────────────────────────────────────────────────────────┤
 * │ queryEmbedding  │ 必须：查询向量                                           │
 * │                 │ 由查询文本通过 EmbeddingModel.embed() 生成               │
 * ├─────────────────┼──────────────────────────────────────────────────────────┤
 * │ maxResults      │ 可选：最多返回几个结果，默认 3                           │
 * │                 │ - 太少：可能遗漏相关内容                                 │
 * │                 │ - 太多：增加后续处理负担                                 │
 * │                 │ - 推荐：3-10 个                                          │
 * ├─────────────────┼──────────────────────────────────────────────────────────┤
 * │ minScore        │ 可选：最低相似度阈值 (0-1)                               │
 * │                 │ - 0.9+：非常相似（几乎相同）                             │
 * │                 │ - 0.7-0.9：高度相关                                      │
 * │                 │ - 0.5-0.7：中度相关                                      │
 * │                 │ - <0.5：可能不相关                                       │
 * │                 │ - 推荐：0.5-0.7，根据场景调整                            │
 * ├─────────────────┼──────────────────────────────────────────────────────────┤
 * │ filter          │ 可选：元数据过滤器                                       │
 * │                 │ 先按元数据过滤，再在过滤后的结果中搜索                   │
 * │                 │ 支持 AND/OR/NOT 组合                                     │
 * └─────────────────┴──────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 第五章：应用场景
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 语义搜索 - 搜索意思相近的内容，不只是关键词匹配
 * 2. 相似推荐 - "看了这篇文章的人还喜欢..."
 * 3. 问答系统 - 从知识库中找到最相关的答案
 * 4. 去重检测 - 找出语义相似的重复内容
 * 5. 聚类分析 - 将相似内容归类
 * 6. 异常检测 - 找出与正常样本差异大的数据
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class VectorSimilaritySearchDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(70));
        System.out.println("          向量相似性检索完整教程");
        System.out.println("═".repeat(70));

        /**
         * 初始化 EmbeddingModel
         *
         * OpenAiEmbeddingModel.builder() 参数：
         * ┌─────────────────┬────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                           │
         * ├─────────────────┼────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                       │
         * │ apiKey          │ API 密钥                                       │
         * │ modelName       │ 模型名称                                       │
         * │                 │ - text-embedding-3-small: 1536 维，性价比高    │
         * │                 │ - text-embedding-3-large: 3072 维，精度更高    │
         * └─────────────────┴────────────────────────────────────────────────┘
         */
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")
                .build();

        /**
         * 初始化 EmbeddingStore
         *
         * InMemoryEmbeddingStore 特点：
         * - 数据存储在内存中，重启后丢失
         * - 适合开发测试和小规模数据
         * - 生产环境建议使用 Milvus/Pinecone 等
         */
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // ═══════════════════════════════════════════════════════════════
        // 第一部分：基础操作 - 添加和搜索
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第一部分】基础操作 - 添加和搜索");
        System.out.println("─".repeat(70));

        demoBasicOperations(embeddingStore, embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第二部分：相似度分数详解
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第二部分】相似度分数详解");
        System.out.println("─".repeat(70));

        demoSimilarityScores(embeddingStore, embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第三部分：minScore 阈值过滤
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第三部分】minScore 阈值过滤");
        System.out.println("─".repeat(70));

        demoMinScoreFilter(embeddingStore, embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第四部分：Metadata 元数据过滤
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第四部分】Metadata 元数据过滤");
        System.out.println("─".repeat(70));

        demoMetadataFilter(embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第五部分：实际应用场景
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第五部分】实际应用场景");
        System.out.println("─".repeat(70));

        demoRealWorldScenarios(embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第六部分：相似度计算原理
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第六部分】相似度计算原理（手动实现）");
        System.out.println("─".repeat(70));

        demoSimilarityCalculation(embeddingModel);

        // 总结
        printSummary();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第一部分：基础操作
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoBasicOperations(EmbeddingStore<TextSegment> store,
                                            EmbeddingModel model) {
        System.out.println("\n>>> 1.1 添加文档到向量库\n");

        // 准备测试数据
        String[] documents = {
            "Java 是一种面向对象的编程语言",
            "Python 是一种解释型的编程语言",
            "今天北京的天气晴朗",
            "Spring Boot 是 Java 的 Web 框架",
            "机器学习需要大量的训练数据"
        };

        System.out.println("添加以下文档：");
        for (int i = 0; i < documents.length; i++) {
            // 1. 将文本转为向量
            Embedding embedding = model.embed(documents[i]).content();

            // 2. 创建 TextSegment（包含原文）
            TextSegment segment = TextSegment.from(documents[i]);

            // 3. 存入向量库
            // add() 方法返回自动生成的 ID
            String id = store.add(embedding, segment);

            System.out.printf("  [%d] %s (ID: %s, 维度: %d)%n",
                    i + 1, documents[i], id.substring(0, 8) + "...", embedding.dimension());
        }

        System.out.println("\n>>> 1.2 搜索相似文档\n");

        String query = "Java 编程";
        System.out.println("搜索: \"" + query + "\"\n");

        // 1. 将查询转为向量
        Embedding queryEmbedding = model.embed(query).content();

        /**
         * EmbeddingSearchRequest.builder() 参数说明：
         *
         * ┌─────────────────┬─────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                            │
         * ├─────────────────┼─────────────────────────────────────────────────┤
         * │ queryEmbedding  │ 必须：查询向量                                  │
         * │ maxResults      │ 可选：返回结果数量，默认 3                      │
         * │ minScore        │ 可选：最低相似度阈值                            │
         * │ filter          │ 可选：元数据过滤条件                            │
         * └─────────────────┴─────────────────────────────────────────────────┘
         */
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)  // 查询向量
                .maxResults(3)                   // 返回前 3 个结果
                .build();

        // 2. 执行搜索
        EmbeddingSearchResult<TextSegment> result = store.search(request);

        // 3. 处理结果
        System.out.println("搜索结果（按相似度排序）：");
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            System.out.printf("  相似度: %.4f | %s%n",
                    match.score(),
                    match.embedded().text());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第二部分：相似度分数详解
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoSimilarityScores(EmbeddingStore<TextSegment> store,
                                             EmbeddingModel model) {
        System.out.println("""

            相似度分数范围（归一化后）：
            ┌────────────────┬────────────────────────────────────────────────┐
            │ 分数范围       │ 含义                                           │
            ├────────────────┼────────────────────────────────────────────────┤
            │ 0.90 - 1.00    │ 几乎相同（可能是重复内容）                     │
            │ 0.80 - 0.90    │ 非常相似                                       │
            │ 0.70 - 0.80    │ 高度相关                                       │
            │ 0.60 - 0.70    │ 中度相关                                       │
            │ 0.50 - 0.60    │ 轻度相关                                       │
            │ < 0.50         │ 可能不相关                                     │
            └────────────────┴────────────────────────────────────────────────┘
            """);

        // 测试不同查询的相似度
        String[] testQueries = {
            "Java 编程语言",      // 应该与 Java 文档高度相似
            "Web 开发框架",       // 应该与 Spring Boot 相关
            "北京今天天气怎么样", // 应该与天气相关
            "如何炒股赚钱"        // 与所有文档都不太相关
        };

        for (String query : testQueries) {
            Embedding queryEmb = model.embed(query).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(1)
                    .build();

            EmbeddingSearchResult<TextSegment> res = store.search(req);
            if (!res.matches().isEmpty()) {
                EmbeddingMatch<TextSegment> top = res.matches().get(0);
                String relevance = getRelevanceLevel(top.score());
                System.out.printf("查询: \"%s\"%n", query);
                System.out.printf("  → 最相似: %s%n", top.embedded().text());
                System.out.printf("  → 相似度: %.4f (%s)%n%n", top.score(), relevance);
            }
        }
    }

    private static String getRelevanceLevel(double score) {
        if (score >= 0.90) return "几乎相同";
        if (score >= 0.80) return "非常相似";
        if (score >= 0.70) return "高度相关";
        if (score >= 0.60) return "中度相关";
        if (score >= 0.50) return "轻度相关";
        return "可能不相关";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第三部分：minScore 阈值过滤
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoMinScoreFilter(EmbeddingStore<TextSegment> store,
                                           EmbeddingModel model) {
        System.out.println("""

            minScore 参数作用：
            - 过滤掉相似度低于阈值的结果
            - 避免返回不相关的内容
            - 提高结果质量

            使用场景建议：
            ┌────────────────────────┬────────────────────────────────────────┐
            │ 场景                   │ 推荐 minScore                          │
            ├────────────────────────┼────────────────────────────────────────┤
            │ 精确匹配（去重检测）   │ 0.85 - 0.95                            │
            │ 问答系统               │ 0.70 - 0.80                            │
            │ 语义搜索               │ 0.60 - 0.70                            │
            │ 相似推荐               │ 0.50 - 0.60                            │
            │ 探索性搜索             │ 0.40 - 0.50                            │
            └────────────────────────┴────────────────────────────────────────┘
            """);

        String query = "如何炒股票投资理财";
        Embedding queryEmb = model.embed(query).content();

        System.out.println("查询: \"" + query + "\" (与知识库内容不相关)\n");

        // 不设置 minScore
        System.out.println(">>> 不设置 minScore（返回所有结果）：");
        EmbeddingSearchRequest noMinScore = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(3)
                .build();
        printSearchResults(store.search(noMinScore));

        // 设置 minScore = 0.5
        System.out.println("\n>>> 设置 minScore = 0.5（过滤低相关度）：");
        EmbeddingSearchRequest withMinScore = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(3)
                .minScore(0.5)  // 只返回相似度 >= 0.5 的结果
                .build();
        printSearchResults(store.search(withMinScore));

        // 设置 minScore = 0.7
        System.out.println("\n>>> 设置 minScore = 0.7（严格过滤）：");
        EmbeddingSearchRequest strictMinScore = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(3)
                .minScore(0.7)  // 只返回相似度 >= 0.7 的结果
                .build();
        printSearchResults(store.search(strictMinScore));
    }

    private static void printSearchResults(EmbeddingSearchResult<TextSegment> result) {
        if (result.matches().isEmpty()) {
            System.out.println("  没有找到符合条件的结果");
        } else {
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                System.out.printf("  [%.4f] %s%n", match.score(), match.embedded().text());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第四部分：Metadata 元数据过滤
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoMetadataFilter(EmbeddingModel model) {
        System.out.println("""

            Metadata（元数据）过滤：
            - 先按元数据条件过滤，再搜索相似向量
            - 实现分类、权限控制、时间范围等功能
            - 比先搜索再过滤更高效

            MetadataFilterBuilder 常用方法：
            ┌───────────────────────────┬────────────────────────────────────────┐
            │ 方法                      │ 示例                                   │
            ├───────────────────────────┼────────────────────────────────────────┤
            │ isEqualTo(value)          │ category = "技术"                      │
            │ isNotEqualTo(value)       │ status != "deleted"                    │
            │ isIn(collection)          │ department IN ["技术", "产品"]         │
            │ isNotIn(collection)       │ level NOT IN ["secret"]                │
            │ isGreaterThan(value)      │ year > 2020                            │
            │ isLessThan(value)         │ price < 100                            │
            │ isGreaterThanOrEqualTo    │ score >= 60                            │
            │ isLessThanOrEqualTo       │ priority <= 3                          │
            ├───────────────────────────┼────────────────────────────────────────┤
            │ filter1.and(filter2)      │ 同时满足                               │
            │ filter1.or(filter2)       │ 满足其一                               │
            │ filter.not()              │ 取反                                   │
            └───────────────────────────┴────────────────────────────────────────┘
            """);

        // 创建新的向量库，存储带元数据的文档
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // 添加带元数据的文档
        System.out.println(">>> 添加带元数据的文档：\n");

        addDocWithMetadata(store, model,
                "Java 多线程编程指南",
                Map.of("category", "技术", "level", "高级", "year", 2023));

        addDocWithMetadata(store, model,
                "Python 入门教程",
                Map.of("category", "技术", "level", "入门", "year", 2024));

        addDocWithMetadata(store, model,
                "产品经理必读：用户需求分析",
                Map.of("category", "产品", "level", "中级", "year", 2023));

        addDocWithMetadata(store, model,
                "Spring Boot 微服务架构",
                Map.of("category", "技术", "level", "高级", "year", 2024));

        addDocWithMetadata(store, model,
                "市场营销策略入门",
                Map.of("category", "市场", "level", "入门", "year", 2022));

        // 测试不同的过滤条件
        String query = "编程教程";
        Embedding queryEmb = model.embed(query).content();

        System.out.println("\n查询: \"" + query + "\"\n");

        // 无过滤
        System.out.println(">>> 无过滤（返回所有相关结果）：");
        searchWithFilter(store, queryEmb, null);

        // 只看技术类
        System.out.println("\n>>> 过滤: category = '技术'：");
        Filter techFilter = MetadataFilterBuilder.metadataKey("category")
                .isEqualTo("技术");
        searchWithFilter(store, queryEmb, techFilter);

        // 只看高级内容
        System.out.println("\n>>> 过滤: level = '高级'：");
        Filter advancedFilter = MetadataFilterBuilder.metadataKey("level")
                .isEqualTo("高级");
        searchWithFilter(store, queryEmb, advancedFilter);

        // 组合过滤：技术类 + 2024年
        System.out.println("\n>>> 过滤: category = '技术' AND year = 2024：");
        Filter combinedFilter = MetadataFilterBuilder.metadataKey("category")
                .isEqualTo("技术")
                .and(MetadataFilterBuilder.metadataKey("year").isEqualTo(2024));
        searchWithFilter(store, queryEmb, combinedFilter);

        // 入门或中级
        System.out.println("\n>>> 过滤: level IN ['入门', '中级']：");
        Filter levelFilter = MetadataFilterBuilder.metadataKey("level")
                .isIn(Set.of("入门", "中级"));
        searchWithFilter(store, queryEmb, levelFilter);
    }

    private static void addDocWithMetadata(EmbeddingStore<TextSegment> store,
                                           EmbeddingModel model,
                                           String text,
                                           Map<String, Object> metadata) {
        Embedding embedding = model.embed(text).content();
        TextSegment segment = TextSegment.from(text, Metadata.from(metadata));
        store.add(embedding, segment);
        System.out.printf("  添加: %s | 元数据: %s%n", text, metadata);
    }

    private static void searchWithFilter(EmbeddingStore<TextSegment> store,
                                         Embedding queryEmb,
                                         Filter filter) {
        EmbeddingSearchRequest request;
        if (filter != null) {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(5)
                    .filter(filter)
                    .build();
        } else {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(5)
                    .build();
        }

        EmbeddingSearchResult<TextSegment> result = store.search(request);

        if (result.matches().isEmpty()) {
            System.out.println("  没有找到符合条件的结果");
        } else {
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                TextSegment seg = match.embedded();
                System.out.printf("  [%.4f] %s | %s%n",
                        match.score(), seg.text(), seg.metadata().toMap());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第五部分：实际应用场景
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoRealWorldScenarios(EmbeddingModel model) {
        System.out.println("""

            ┌─────────────────────────────────────────────────────────────────────┐
            │                     实际应用场景演示                                 │
            └─────────────────────────────────────────────────────────────────────┘
            """);

        // 场景 1: 智能客服 FAQ 匹配
        System.out.println(">>> 场景 1: 智能客服 - FAQ 匹配\n");
        demoFAQMatching(model);

        // 场景 2: 相似文章推荐
        System.out.println("\n>>> 场景 2: 相似文章推荐\n");
        demoSimilarRecommendation(model);

        // 场景 3: 重复内容检测
        System.out.println("\n>>> 场景 3: 重复内容检测\n");
        demoDuplicateDetection(model);
    }

    private static void demoFAQMatching(EmbeddingModel model) {
        // 构建 FAQ 知识库
        EmbeddingStore<TextSegment> faqStore = new InMemoryEmbeddingStore<>();

        String[][] faqs = {
            {"如何重置密码", "点击登录页面的「忘记密码」，输入手机号验证即可重置。"},
            {"怎么申请退款", "在订单详情页点击「申请退款」，填写原因后提交，3个工作日内处理。"},
            {"运费怎么算", "满99元包邮，不满99元收取10元运费。偏远地区另计。"},
            {"发票怎么开", "订单完成后，在「我的订单」中点击「申请发票」，支持电子发票。"},
            {"会员有什么权益", "会员享受9折优惠、积分双倍、专属客服、生日礼包等权益。"}
        };

        // 存储 FAQ
        for (String[] faq : faqs) {
            Embedding emb = model.embed(faq[0]).content();
            Metadata meta = Metadata.from(Map.of("answer", faq[1]));
            faqStore.add(emb, TextSegment.from(faq[0], meta));
        }

        // 用户各种问法
        String[] userQuestions = {
            "密码忘了怎么办",
            "我想退货",
            "邮费多少钱"
        };

        for (String question : userQuestions) {
            Embedding queryEmb = model.embed(question).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmb)
                    .maxResults(1)
                    .minScore(0.6)
                    .build();

            EmbeddingSearchResult<TextSegment> result = faqStore.search(req);
            System.out.println("用户问: " + question);
            if (!result.matches().isEmpty()) {
                EmbeddingMatch<TextSegment> match = result.matches().get(0);
                System.out.printf("  匹配FAQ: %s (相似度: %.2f)%n",
                        match.embedded().text(), match.score());
                System.out.println("  回答: " + match.embedded().metadata().getString("answer"));
            } else {
                System.out.println("  未找到匹配的FAQ，转人工客服");
            }
            System.out.println();
        }
    }

    private static void demoSimilarRecommendation(EmbeddingModel model) {
        // 构建文章库
        EmbeddingStore<TextSegment> articleStore = new InMemoryEmbeddingStore<>();

        String[] articles = {
            "深入理解 Java 虚拟机：JVM 高级特性与最佳实践",
            "Spring Boot 实战：从入门到精通",
            "Python 数据分析实战：Pandas 与 NumPy",
            "微服务架构设计模式：构建可扩展系统",
            "MySQL 性能优化：查询优化与索引设计",
            "Docker 容器技术：入门与实践",
            "Kubernetes 实战：集群部署与管理"
        };

        for (String article : articles) {
            Embedding emb = model.embed(article).content();
            articleStore.add(emb, TextSegment.from(article));
        }

        // 用户正在看的文章
        String currentArticle = "Java 并发编程实战";
        System.out.println("用户正在阅读: 《" + currentArticle + "》");
        System.out.println("推荐相似文章:\n");

        Embedding currentEmb = model.embed(currentArticle).content();
        EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                .queryEmbedding(currentEmb)
                .maxResults(3)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> result = articleStore.search(req);
        int rank = 1;
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            System.out.printf("  %d. 《%s》 (相关度: %.0f%%)%n",
                    rank++, match.embedded().text(), match.score() * 100);
        }
    }

    private static void demoDuplicateDetection(EmbeddingModel model) {
        // 待检测的内容
        String[] contents = {
            "今天天气真好，阳光明媚",
            "阳光灿烂，今天的天气太棒了",  // 与第1条语义相似
            "Java 是一种编程语言",
            "Python 是一种解释型语言",
            "今天的天气非常好，晴空万里"   // 与第1条语义相似
        };

        System.out.println("检测以下内容中的重复项（相似度 > 0.8）：\n");
        for (int i = 0; i < contents.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, contents[i]);
        }
        System.out.println();

        // 两两比较
        System.out.println("检测结果：\n");
        for (int i = 0; i < contents.length; i++) {
            for (int j = i + 1; j < contents.length; j++) {
                Embedding emb1 = model.embed(contents[i]).content();
                Embedding emb2 = model.embed(contents[j]).content();
                double similarity = cosineSimilarity(emb1.vector(), emb2.vector());

                if (similarity > 0.8) {
                    System.out.printf("  ⚠️ 疑似重复: [%d] 和 [%d] (相似度: %.2f)%n",
                            i + 1, j + 1, similarity);
                    System.out.printf("     \"%s\"%n", contents[i]);
                    System.out.printf("     \"%s\"%n%n", contents[j]);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 第六部分：相似度计算原理
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoSimilarityCalculation(EmbeddingModel model) {
        System.out.println("""

            手动实现相似度计算，理解底层原理：

            余弦相似度公式：
                         A · B           Σ(Ai × Bi)
            cos(θ) = ─────────── = ─────────────────────
                      ||A|| ||B||    √Σ(Ai²) × √Σ(Bi²)

            其中：
            - A · B 是两个向量的点积
            - ||A|| 和 ||B|| 是向量的模（长度）
            """);

        String text1 = "我喜欢吃苹果";
        String text2 = "我爱吃水果";
        String text3 = "今天天气不错";

        Embedding emb1 = model.embed(text1).content();
        Embedding emb2 = model.embed(text2).content();
        Embedding emb3 = model.embed(text3).content();

        System.out.println("计算三个文本的相似度：\n");
        System.out.println("  文本 A: \"" + text1 + "\"");
        System.out.println("  文本 B: \"" + text2 + "\"");
        System.out.println("  文本 C: \"" + text3 + "\"\n");

        double simAB = cosineSimilarity(emb1.vector(), emb2.vector());
        double simAC = cosineSimilarity(emb1.vector(), emb3.vector());
        double simBC = cosineSimilarity(emb2.vector(), emb3.vector());

        System.out.printf("  A 和 B 的余弦相似度: %.4f (语义相近：都是关于食物)%n", simAB);
        System.out.printf("  A 和 C 的余弦相似度: %.4f (语义不同)%n", simAC);
        System.out.printf("  B 和 C 的余弦相似度: %.4f (语义不同)%n", simBC);

        System.out.println("\n>>> 欧氏距离（作为对比）：\n");

        double distAB = euclideanDistance(emb1.vector(), emb2.vector());
        double distAC = euclideanDistance(emb1.vector(), emb3.vector());
        double distBC = euclideanDistance(emb2.vector(), emb3.vector());

        System.out.printf("  A 和 B 的欧氏距离: %.4f (越小越相似)%n", distAB);
        System.out.printf("  A 和 C 的欧氏距离: %.4f%n", distAC);
        System.out.printf("  B 和 C 的欧氏距离: %.4f%n", distBC);
    }

    /**
     * 计算余弦相似度
     *
     * 公式: cos(θ) = (A · B) / (||A|| × ||B||)
     *
     * @return 相似度值，范围 [-1, 1]，1 表示完全相同
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;  // 点积
        double normA = 0.0;       // A 的模
        double normB = 0.0;       // B 的模

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算欧氏距离
     *
     * 公式: d = √Σ(Ai - Bi)²
     *
     * @return 距离值，范围 [0, ∞)，0 表示完全相同
     */
    private static double euclideanDistance(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 总结
    // ═══════════════════════════════════════════════════════════════════════════

    private static void printSummary() {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("                         总  结");
        System.out.println("═".repeat(70));
        System.out.println("""

            向量相似性检索核心要点：

            1. 基本流程
               ┌─────────────────────────────────────────────────────────────┐
               │ 文本 → EmbeddingModel → 向量 → EmbeddingStore → 相似搜索   │
               └─────────────────────────────────────────────────────────────┘

            2. 关键参数调优
               ┌────────────────┬──────────────────────────────────────────┐
               │ 参数           │ 建议                                     │
               ├────────────────┼──────────────────────────────────────────┤
               │ maxResults     │ 3-10，根据下游处理能力调整               │
               │ minScore       │ 0.5-0.7，根据场景精度要求调整            │
               │ filter         │ 用于分类、权限控制、时间范围等           │
               └────────────────┴──────────────────────────────────────────┘

            3. 应用场景选择
               ┌────────────────┬─────────────┬────────────────────────────┐
               │ 场景           │ minScore    │ 特殊考虑                   │
               ├────────────────┼─────────────┼────────────────────────────┤
               │ 精确匹配/去重  │ 0.85+       │ 用于检测重复内容           │
               │ FAQ/知识问答   │ 0.70-0.80   │ 无匹配时转人工             │
               │ 语义搜索       │ 0.60-0.70   │ 平衡召回率和准确率         │
               │ 相似推荐       │ 0.50-0.60   │ 宽松匹配，多样化推荐       │
               └────────────────┴─────────────┴────────────────────────────┘

            4. 生产环境建议
               - 使用专业向量数据库（Milvus/Pinecone/Qdrant）
               - 建立合适的索引（HNSW/IVF）
               - 批量操作提升性能
               - 监控搜索延迟和召回率

            5. 下一步学习
               - 08-rag 模块：结合 LLM 实现智能问答
               - DocumentSplitter：文档分割策略
               - EnterpriseKnowledgeBase：企业级权限控制
            """);
    }
}
