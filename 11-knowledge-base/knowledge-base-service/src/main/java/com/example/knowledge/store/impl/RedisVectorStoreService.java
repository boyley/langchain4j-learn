package com.example.knowledge.store.impl;

import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Redis 向量存储服务实现
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【什么是 Redis Stack?】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Redis Stack = Redis + 扩展模块:
 *
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │ Redis Stack                                                            │
 *   ├─────────────────────────────────────────────────────────────────────────┤
 *   │                                                                         │
 *   │  Redis Core        + RediSearch        + RedisJSON    + RedisGraph     │
 *   │  (缓存/KV存储)       (全文搜索+向量搜索)   (JSON文档)     (图数据库)      │
 *   │                                                                         │
 *   │  我们使用 RediSearch 的向量搜索功能:                                     │
 *   │  - VSS (Vector Similarity Search)                                      │
 *   │  - 支持 KNN 最近邻查询                                                  │
 *   │  - 支持 HNSW 算法 (高性能)                                              │
 *   │                                                                         │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【适用场景】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * - 已有 Redis 基础设施
 * - 需要快速部署 (Docker 一键启动)
 * - 数据规模 < 100万向量
 * - 需要亚毫秒级查询延迟
 * - 对数据持久化要求不高 (Redis 可能丢数据)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【快速启动】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Docker 一键启动:
 * ```bash
 * docker run -d --name redis-stack \
 *   -p 6379:6379 \     # Redis 端口
 *   -p 8001:8001 \     # RedisInsight Web UI
 *   redis/redis-stack:latest
 * ```
 *
 * 验证安装:
 * ```bash
 * redis-cli
 * > MODULE LIST
 * # 应该看到 search (RediSearch) 模块
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   vector-store:
 *     type: redis              # 启用 Redis 向量存储
 *     dimension: 1536          # 向量维度 (必须与嵌入模型匹配)
 *     redis:
 *       host: localhost
 *       port: 6379
 *       password:              # 可选
 *       index-name: knowledge_vectors
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【Redis 中的数据结构】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 每个向量存储为一个 Redis Hash:
 *
 * HGETALL embedding:abc123
 * 返回:
 * {
 *   "vector": "<binary vector data>",  // 1536 维向量
 *   "text": "这是原始文本片段...",
 *   "docId": "db:doc-001",
 *   "title": "员工休假管理办法",
 *   "category": "HR政策"
 * }
 *
 * 自动创建的索引:
 * FT.CREATE knowledge_vectors
 *   ON HASH PREFIX 1 embedding:
 *   SCHEMA
 *     vector VECTOR HNSW 6 DIM 1536 DISTANCE_METRIC COSINE
 *     text TEXT
 *     docId TAG
 *     title TEXT
 *     category TAG
 *
 * @see <a href="https://redis.io/docs/stack/search/reference/vectors/">Redis Vector Search</a>
 */
@Slf4j
@Service
/*
 * @ConditionalOnProperty - 条件装配
 *
 * 只有配置 knowledge.vector-store.type=redis 时才创建此 Bean
 * 实现了策略模式: 通过配置切换不同的向量存储实现
 */
@ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "redis")
public class RedisVectorStoreService implements VectorStoreService {

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置属性
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Redis 服务器地址
     *
     * 示例:
     * - localhost (本地开发)
     * - redis.example.com (生产环境)
     * - redis-master (Kubernetes 服务名)
     */
    @Value("${knowledge.vector-store.redis.host:localhost}")
    private String host;

    /**
     * Redis 端口
     *
     * 默认: 6379 (Redis 标准端口)
     * 注意: Redis Cluster 模式可能使用不同端口
     */
    @Value("${knowledge.vector-store.redis.port:6379}")
    private int port;

    /**
     * Redis 密码 (可选)
     *
     * 无密码时留空
     * 生产环境建议使用环境变量: ${REDIS_PASSWORD}
     */
    @Value("${knowledge.vector-store.redis.password:}")
    private String password;

    /**
     * 向量索引名称
     *
     * RediSearch 会创建一个全文索引，名称为此值
     * 可以通过 FT.INFO {indexName} 查看索引信息
     *
     * 示例: FT.INFO knowledge_vectors
     */
    @Value("${knowledge.vector-store.redis.index-name:knowledge_vectors}")
    private String indexName;

    /**
     * 向量维度
     *
     * ⚠️ 必须与嵌入模型的输出维度一致!
     *
     * 常见维度:
     * - text-embedding-3-small: 1536
     * - text-embedding-3-large: 3072
     * - 本地模型 (如 bge-small): 384/768
     */
    @Value("${knowledge.vector-store.dimension:1536}")
    private int dimension;

    /**
     * LangChain4j Redis 向量存储客户端
     *
     * 封装了 RediSearch 的向量操作:
     * - 自动创建索引
     * - 向量 CRUD
     * - KNN 搜索
     */
    private RedisEmbeddingStore store;

    // ═══════════════════════════════════════════════════════════════════════════
    // 初始化
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 初始化 Redis 连接和索引
     *
     * @PostConstruct: Bean 创建后自动调用
     *
     * 初始化流程:
     * 1. 连接 Redis
     * 2. 检查索引是否存在
     * 3. 不存在则创建索引 (FT.CREATE)
     */
    @PostConstruct
    public void init() {
        log.info("初始化 Redis 向量存储, host={}, port={}, index={}", host, port, indexName);

        /*
         * RedisEmbeddingStore.builder() - 构建器模式
         *
         * 必需参数:
         * - host: Redis 地址
         * - port: Redis 端口
         * - indexName: 索引名称
         * - dimension: 向量维度
         *
         * 重要参数:
         * - metadataKeys: 需要索引的元数据字段列表
         *   这些字段可以用于过滤搜索
         */
        var builder = RedisEmbeddingStore.builder()
                .host(host)              // Redis 地址
                .port(port)              // Redis 端口
                .indexName(indexName)    // 索引名称
                .dimension(dimension)    // 向量维度

                /*
                 * metadataKeys - 元数据字段列表
                 *
                 * 作用:
                 * 1. 这些字段会被添加到 RediSearch 索引
                 * 2. 可以用 filter() 按这些字段过滤搜索结果
                 *
                 * 示例: 搜索时只返回 category="HR政策" 的结果
                 *
                 * RediSearch 索引定义:
                 * SCHEMA
                 *   docId TAG        # TAG 类型，精确匹配
                 *   title TEXT       # TEXT 类型，全文搜索
                 *   category TAG     # TAG 类型，精确匹配
                 *   source TAG       # TAG 类型，精确匹配
                 */
                .metadataKeys(List.of("docId", "title", "category", "source"));

        // 如果有密码，设置密码
        if (password != null && !password.isEmpty()) {
            builder.password(password);
        }

        this.store = builder.build();

        log.info("Redis 向量存储初始化完成");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VectorStoreService 接口实现
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取存储类型
     */
    @Override
    public String getType() {
        return "redis";
    }

    /**
     * 检查服务可用性
     */
    @Override
    public boolean isAvailable() {
        try {
            return store != null;
        } catch (Exception e) {
            log.error("Redis 向量存储不可用", e);
            return false;
        }
    }

    /**
     * 存储单个向量
     *
     * @param embedding 向量 (float[1536])
     * @param segment   文本片段
     * @return 生成的 ID
     *
     * Redis 命令 (简化):
     * HSET embedding:{uuid}
     *   vector {binary}
     *   text "文本内容"
     *   docId "db:doc-001"
     *   ...
     */
    @Override
    public String store(Embedding embedding, TextSegment segment) {
        return store.add(embedding, segment);
    }

    /**
     * 批量存储向量
     *
     * @param embeddings 向量列表
     * @param segments   文本片段列表
     * @return ID 列表
     *
     * 使用 Redis Pipeline 批量写入，性能更高
     */
    @Override
    public List<String> storeBatch(List<Embedding> embeddings, List<TextSegment> segments) {
        return store.addAll(embeddings, segments);
    }

    /**
     * 向量相似度搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大返回数量 (Redis 中称为 K，即 KNN 的 K)
     * @param minScore       最低相似度阈值 (0-1)
     * @return 匹配结果列表
     *
     * Redis 命令 (简化):
     * FT.SEARCH knowledge_vectors
     *   "*=>[KNN 5 @vector $query_vector AS score]"
     *   PARAMS 2 query_vector {binary}
     *   SORTBY score
     *   LIMIT 0 5
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore) {
        /*
         * EmbeddingSearchRequest 参数说明:
         *
         * queryEmbedding: 查询向量
         *   - 用户问题经过 EmbeddingModel 转换后的向量
         *   - 必须与存储的向量维度一致
         *
         * maxResults: 最大返回数量
         *   - 对应 KNN 算法中的 K 值
         *   - 建议 1-20，太大会影响性能
         *
         * minScore: 最低相似度阈值
         *   - 范围 0.0 - 1.0
         *   - 0.5 表示至少 50% 相似才返回
         *   - 0.7 表示高相关性
         *   - 0.9 表示非常相关
         */
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        return result.matches();
    }

    /**
     * 带分类过滤的搜索
     *
     * @param category 分类过滤条件
     *
     * Redis 命令 (带过滤):
     * FT.SEARCH knowledge_vectors
     *   "(@category:{HR政策})=>[KNN 5 @vector $query_vector AS score]"
     *   ...
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore, String category) {
        EmbeddingSearchRequest request;

        if (category != null && !category.isEmpty()) {
            /*
             * filter() - 元数据过滤
             *
             * 只返回满足条件的结果
             * 相当于 SQL 的 WHERE 子句
             *
             * 注意: 过滤字段必须在 metadataKeys 中声明
             */
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(MetadataFilterBuilder
                            .metadataKey("category")
                            .isEqualTo(category))
                    .build();
        } else {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
        }

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        return result.matches();
    }

    /**
     * 按文档ID删除向量
     *
     * @param docId 文档唯一标识
     * @return 删除数量 (-1 表示未知)
     *
     * 场景: 文档更新时先删除旧向量
     *
     * 注意: Redis 删除是按 Hash Key 删除，
     * 会删除所有 docId 匹配的记录
     */
    @Override
    public int deleteByDocId(String docId) {
        try {
            store.removeAll(MetadataFilterBuilder
                    .metadataKey("docId")
                    .isEqualTo(docId));
            log.debug("已删除文档 {} 的向量", docId);
            return -1; // Redis 不返回删除数量
        } catch (Exception e) {
            log.error("删除文档向量失败: {}", docId, e);
            return 0;
        }
    }

    /**
     * 删除所有向量
     *
     * ⚠️ 危险: 清空所有数据!
     *
     * 实现方式:
     * 1. 删除索引 (FT.DROPINDEX)
     * 2. 删除所有 embedding:* 的 Key
     */
    @Override
    public void deleteAll() {
        log.warn("删除所有向量数据!");
        store.removeAll();
    }

    /**
     * 获取统计信息
     */
    @Override
    public StoreStats getStats() {
        return new StoreStats("redis", -1, isAvailable());
    }
}
