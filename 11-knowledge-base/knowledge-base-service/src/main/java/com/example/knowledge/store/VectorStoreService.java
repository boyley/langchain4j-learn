package com.example.knowledge.store;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * 向量存储服务接口
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【接口说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 这是向量存储的统一抽象接口，屏蔽不同向量数据库的差异。
 *
 *   应用层代码
 *       │
 *       ▼
 *   VectorStoreService (接口)
 *       │
 *   ┌───┴───┬───────────┬───────────────┐
 *   │       │           │               │
 *   ▼       ▼           ▼               ▼
 *  Redis   pgvector   Milvus    Elasticsearch
 *  实现     实现        实现           实现
 *
 * 好处:
 * - 业务代码不依赖具体实现
 * - 通过配置切换存储类型
 * - 方便测试 (可 Mock)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心概念】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Embedding (向量):
 *   - 文本经过嵌入模型转换后的数字表示
 *   - 通常是 1536 维的浮点数组
 *   - 语义相似的文本，向量距离近
 *
 * TextSegment (文本片段):
 *   - 包含原始文本 (text)
 *   - 包含元数据 (metadata): docId, title, category 等
 *   - 搜索时返回，用于展示结果
 *
 * EmbeddingMatch (匹配结果):
 *   - 包含向量 (embedding)
 *   - 包含文本片段 (embedded)
 *   - 包含相似度分数 (score)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ```java
 * // 存储向量
 * Embedding embedding = embeddingModel.embed("这是一段文本").content();
 * TextSegment segment = TextSegment.from("这是一段文本",
 *     Metadata.from("docId", "doc-001"));
 * vectorStore.store(embedding, segment);
 *
 * // 搜索向量
 * Embedding query = embeddingModel.embed("查询文本").content();
 * List<EmbeddingMatch<TextSegment>> results =
 *     vectorStore.search(query, 5, 0.5);
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【实现类】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 实现类                    | 存储后端            | 适用场景              |
 * |--------------------------|--------------------|--------------------|
 * | RedisVectorStoreService  | Redis + RediSearch | 中小规模，快速部署     |
 * | PgVectorStoreService     | PostgreSQL+pgvector| 已有PG，需要ACID      |
 * | MilvusVectorStoreService | Milvus             | 大规模，亿级向量      |
 *
 * 通过配置切换:
 * ```yaml
 * knowledge.vector-store.type: redis    # 或 pgvector, milvus
 * ```
 */
public interface VectorStoreService {

    // ═══════════════════════════════════════════════════════════════════════════
    // 基础信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取存储类型名称
     *
     * @return 类型标识，如 "redis", "pgvector", "milvus"
     */
    String getType();

    /**
     * 检查存储服务是否可用
     *
     * @return true 表示服务正常，可以执行操作
     *
     * 检查内容通常包括:
     * - 网络连接是否正常
     * - 认证是否成功
     * - 索引/表是否存在
     */
    boolean isAvailable();

    // ═══════════════════════════════════════════════════════════════════════════
    // 存储操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 存储单条向量
     *
     * @param embedding 向量数据
     *                  - 通常是 float[1536]
     *                  - 来自 EmbeddingModel.embed() 的结果
     *
     * @param segment   文本片段
     *                  - text(): 原始文本内容
     *                  - metadata(): 元数据 Map，包含:
     *                    - docId: 文档唯一标识 (用于更新时删除旧向量)
     *                    - title: 文档标题
     *                    - category: 分类 (用于过滤搜索)
     *                    - source: 来源
     *
     * @return 生成的存储ID (通常是 UUID)
     *
     * 示例:
     * ```java
     * Metadata meta = new Metadata();
     * meta.put("docId", "doc-001");
     * meta.put("title", "员工手册");
     *
     * TextSegment segment = TextSegment.from("年假规定...", meta);
     * Embedding embedding = model.embed(segment.text()).content();
     *
     * String id = vectorStore.store(embedding, segment);
     * ```
     */
    String store(Embedding embedding, TextSegment segment);

    /**
     * 批量存储向量
     *
     * @param embeddings 向量列表
     * @param segments   文本片段列表 (必须与 embeddings 一一对应)
     * @return 存储ID列表
     *
     * 性能提示:
     * - 批量存储比循环单条存储快 10-100 倍
     * - 建议每批 50-200 条
     *
     * 注意:
     * - embeddings.size() 必须等于 segments.size()
     * - embeddings[i] 对应 segments[i]
     */
    List<String> storeBatch(List<Embedding> embeddings, List<TextSegment> segments);

    // ═══════════════════════════════════════════════════════════════════════════
    // 搜索操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 向量相似度搜索
     *
     * @param queryEmbedding 查询向量
     *                       - 用户问题经过 EmbeddingModel 转换后的向量
     *                       - 维度必须与存储的向量一致
     *
     * @param maxResults     最大返回数量
     *                       - 对应 KNN 中的 K 值
     *                       - 建议 1-20，太大影响性能
     *
     * @param minScore       最低相似度阈值
     *                       - 范围: 0.0 ~ 1.0
     *                       - 0.5: 中等相关 (推荐起始值)
     *                       - 0.7: 较高相关
     *                       - 0.9: 非常相关
     *                       - 低于此阈值的结果会被过滤
     *
     * @return 匹配结果列表，按相似度降序排列
     *
     * 返回的 EmbeddingMatch 包含:
     * - embeddingId(): 向量记录的唯一ID
     * - embedding(): 向量数据
     * - embedded(): TextSegment (原文 + 元数据)
     * - score(): 相似度分数 (0-1)
     *
     * 示例:
     * ```java
     * Embedding query = model.embed("如何请假").content();
     * List<EmbeddingMatch<TextSegment>> results =
     *     vectorStore.search(query, 5, 0.5);
     *
     * for (EmbeddingMatch<TextSegment> match : results) {
     *     System.out.println("分数: " + match.score());
     *     System.out.println("内容: " + match.embedded().text());
     *     System.out.println("标题: " + match.embedded().metadata().getString("title"));
     * }
     * ```
     */
    List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore);

    /**
     * 带分类过滤的向量搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回数量
     * @param minScore       最低相似度
     * @param category       分类过滤条件
     *                       - 只返回指定分类的结果
     *                       - 为 null 或空时不过滤
     *
     * @return 匹配结果列表
     *
     * 场景示例:
     * - 只搜索 "HR政策" 分类的文档
     * - 只搜索 "技术文档" 分类的文档
     *
     * ```java
     * // 只在 HR政策 分类中搜索
     * List<EmbeddingMatch<TextSegment>> results =
     *     vectorStore.search(query, 5, 0.5, "HR政策");
     * ```
     */
    List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore, String category);

    // ═══════════════════════════════════════════════════════════════════════════
    // 删除操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 按文档ID删除所有相关向量
     *
     * @param docId 文档唯一标识 (与存储时 metadata 中的 docId 对应)
     * @return 删除的向量数量
     *         - 具体数值: 实际删除数量
     *         - -1: 删除成功但不支持统计数量 (如 Redis)
     *         - 0: 删除失败
     *
     * 使用场景:
     * 文档更新时，需要先删除旧向量，再存储新向量
     *
     * 流程:
     * ```
     * 1. 删除旧向量: vectorStore.deleteByDocId("doc-001")
     * 2. 重新分割文档
     * 3. 重新向量化
     * 4. 存储新向量: vectorStore.storeBatch(...)
     * ```
     *
     * 为什么需要删除:
     * - 如果直接存储新向量，旧向量不会被覆盖
     * - 会导致同一文档有多份向量，搜索结果重复
     */
    int deleteByDocId(String docId);

    /**
     * 删除所有向量数据
     *
     * ⚠️ 危险操作!
     * - 会清空整个向量存储
     * - 不可恢复
     * - 生产环境慎用
     *
     * 使用场景:
     * - 开发/测试环境重置数据
     * - 完全重建向量库
     */
    void deleteAll();

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取存储统计信息
     *
     * @return 统计信息对象
     */
    StoreStats getStats();

    /**
     * 存储统计信息
     *
     * @param type        存储类型 ("redis", "pgvector", "milvus")
     * @param vectorCount 向量数量 (-1 表示不支持统计)
     * @param connected   是否已连接
     */
    record StoreStats(
            String type,
            long vectorCount,
            boolean connected
    ) {}
}
