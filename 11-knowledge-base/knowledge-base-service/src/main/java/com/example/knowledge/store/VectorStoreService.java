package com.example.knowledge.store;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * 向量存储服务接口
 *
 * 统一抽象不同的向量数据库:
 * - Redis + RediSearch
 * - PostgreSQL + pgvector
 * - Milvus
 * - Elasticsearch
 *
 * 核心设计:
 * - 向量数据持久化存储，应用重启不丢失
 * - 支持增量更新 (先删后加)
 * - 支持元数据过滤
 */
public interface VectorStoreService {

    /**
     * 获取存储类型名称
     */
    String getType();

    /**
     * 检查存储是否可用
     */
    boolean isAvailable();

    /**
     * 存储单条向量
     *
     * @param embedding 向量
     * @param segment   文本片段 (包含元数据)
     * @return 存储ID
     */
    String store(Embedding embedding, TextSegment segment);

    /**
     * 批量存储向量
     *
     * @param embeddings 向量列表
     * @param segments   文本片段列表
     * @return 存储ID列表
     */
    List<String> storeBatch(List<Embedding> embeddings, List<TextSegment> segments);

    /**
     * 相似性搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回数量
     * @param minScore       最小相似度 (0-1)
     * @return 匹配结果列表
     */
    List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore);

    /**
     * 带过滤器的相似性搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回数量
     * @param minScore       最小相似度
     * @param category       分类过滤 (可选)
     * @return 匹配结果列表
     */
    List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore, String category);

    /**
     * 按文档ID删除所有相关向量
     *
     * 用于文档更新时:
     * 1. 先删除旧版本的所有向量
     * 2. 再存储新版本的向量
     *
     * @param docId 文档ID
     * @return 删除的数量 (部分实现可能返回 -1 表示不支持)
     */
    int deleteByDocId(String docId);

    /**
     * 删除所有数据
     * 谨慎使用!
     */
    void deleteAll();

    /**
     * 获取统计信息
     */
    StoreStats getStats();

    /**
     * 存储统计信息
     */
    record StoreStats(
            String type,           // 存储类型
            long vectorCount,      // 向量数量 (-1 表示不支持统计)
            boolean connected      // 是否连接
    ) {}
}
