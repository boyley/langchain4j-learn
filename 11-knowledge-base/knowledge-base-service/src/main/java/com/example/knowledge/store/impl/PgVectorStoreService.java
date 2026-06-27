package com.example.knowledge.store.impl;

import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * PostgreSQL + pgvector 向量存储服务实现
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【什么是 pgvector?】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * pgvector 是 PostgreSQL 的向量扩展，让普通的 PostgreSQL 数据库具备向量搜索能力。
 *
 *   普通 PostgreSQL                      PostgreSQL + pgvector
 *   ┌────────────────────┐              ┌────────────────────────────┐
 *   │ 支持:               │              │ 额外支持:                   │
 *   │ - 文本、数字        │              │ - vector 类型 (存储向量)    │
 *   │ - JSON             │      +       │ - 向量相似度搜索            │
 *   │ - 地理位置          │   pgvector   │ - KNN 最近邻查询            │
 *   │ - 全文搜索          │      =       │ - 余弦/欧氏距离计算         │
 *   └────────────────────┘              └────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【适用场景】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * - 已有 PostgreSQL 基础设施，不想引入新的数据库
 * - 数据规模 < 500万向量
 * - 需要 ACID 事务支持
 * - 需要向量与业务数据在同一数据库
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【前置条件】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. PostgreSQL 12+ (推荐 15+)
 *
 * 2. 安装 pgvector 扩展:
 *    ```sql
 *    CREATE EXTENSION vector;
 *    ```
 *
 * 3. 或使用 Docker:
 *    ```bash
 *    docker run -d --name pgvector \
 *      -e POSTGRES_PASSWORD=postgres \
 *      -p 5432:5432 \
 *      pgvector/pgvector:pg16
 *    ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   vector-store:
 *     type: pgvector           # 启用 pgvector
 *     dimension: 1536          # 向量维度 (必须与嵌入模型匹配)
 *     pgvector:
 *       host: localhost
 *       port: 5432
 *       database: knowledge_db
 *       user: postgres
 *       password: postgres
 *       table: knowledge_embeddings
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【自动创建的表结构】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ```sql
 * CREATE TABLE knowledge_embeddings (
 *     embedding_id UUID PRIMARY KEY,           -- 向量记录ID
 *     embedding vector(1536),                  -- 向量数据 (1536维)
 *     text TEXT,                               -- 原始文本片段
 *     metadata JSONB                           -- 元数据 (docId, title, category等)
 * );
 *
 * -- 自动创建的向量索引 (加速搜索)
 * CREATE INDEX ON knowledge_embeddings
 *     USING ivfflat (embedding vector_cosine_ops)
 *     WITH (lists = 100);
 * ```
 *
 * @see <a href="https://github.com/pgvector/pgvector">pgvector GitHub</a>
 */
@Slf4j
@Service
/*
 * @ConditionalOnProperty - Spring Boot 条件装配注解
 *
 * 作用: 只有当配置文件中 knowledge.vector-store.type=pgvector 时，才创建这个 Bean
 *
 * 参数说明:
 * - name: 配置属性名
 * - havingValue: 期望的属性值
 *
 * 这样可以通过配置切换不同的向量存储实现:
 * - type=redis → 创建 RedisVectorStoreService
 * - type=pgvector → 创建 PgVectorStoreService (本类)
 * - type=milvus → 创建 MilvusVectorStoreService
 */
@ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "pgvector")
public class PgVectorStoreService implements VectorStoreService {

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置属性 - 通过 @Value 从 application.yml 注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * PostgreSQL 主机地址
     *
     * @Value 注解说明:
     * - ${...} 从配置文件读取
     * - :localhost 是默认值，配置文件没有时使用
     *
     * 示例: localhost, 192.168.1.100, pg.example.com
     */
    @Value("${knowledge.vector-store.pgvector.host:localhost}")
    private String host;

    /**
     * PostgreSQL 端口号
     *
     * 默认: 5432 (PostgreSQL 标准端口)
     */
    @Value("${knowledge.vector-store.pgvector.port:5432}")
    private int port;

    /**
     * 数据库名称
     *
     * pgvector 扩展需要在这个数据库中安装:
     * ```sql
     * \c knowledge_db
     * CREATE EXTENSION vector;
     * ```
     */
    @Value("${knowledge.vector-store.pgvector.database:knowledge_db}")
    private String database;

    /**
     * 数据库用户名
     *
     * 需要有创建表和索引的权限
     */
    @Value("${knowledge.vector-store.pgvector.user:postgres}")
    private String user;

    /**
     * 数据库密码
     *
     * 生产环境建议使用环境变量: ${PG_PASSWORD}
     */
    @Value("${knowledge.vector-store.pgvector.password:}")
    private String password;

    /**
     * 向量存储表名
     *
     * LangChain4j 会自动创建这个表，包含:
     * - embedding_id: UUID 主键
     * - embedding: vector(dimension) 向量列
     * - text: 原始文本
     * - metadata: JSONB 元数据
     */
    @Value("${knowledge.vector-store.pgvector.table:knowledge_embeddings}")
    private String table;

    /**
     * 向量维度
     *
     * ⚠️ 重要: 必须与嵌入模型的输出维度一致!
     *
     * 常见模型维度:
     * - text-embedding-3-small: 1536
     * - text-embedding-3-large: 3072
     * - text-embedding-ada-002: 1536
     *
     * 如果维度不匹配会报错:
     * "expected 1536 dimensions, not 3072"
     */
    @Value("${knowledge.vector-store.dimension:1536}")
    private int dimension;

    /**
     * LangChain4j 提供的 pgvector 存储客户端
     *
     * 封装了所有 pgvector 操作:
     * - 自动建表
     * - 向量 CRUD
     * - 相似度搜索
     */
    private PgVectorEmbeddingStore store;

    // ═══════════════════════════════════════════════════════════════════════════
    // 初始化方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 初始化 pgvector 连接
     *
     * @PostConstruct 注解说明:
     * - 在 Bean 创建后、属性注入完成后自动调用
     * - 用于执行初始化逻辑
     * - 只会执行一次
     *
     * 执行时机: Spring 容器启动时
     */
    @PostConstruct
    public void init() {
        log.info("初始化 PostgreSQL pgvector 向量存储, host={}, database={}, table={}", host, database, table);

        /*
         * PgVectorEmbeddingStore.builder() - 构建 pgvector 客户端
         *
         * 必需参数:
         * - host: 数据库地址
         * - port: 端口
         * - database: 数据库名
         * - user: 用户名
         * - password: 密码
         * - table: 表名
         * - dimension: 向量维度
         *
         * 可选参数:
         * - createTable: 是否自动创建表 (默认 false)
         * - dropTableFirst: 是否先删除已有表 (默认 false)
         * - useIndex: 是否创建向量索引 (默认 false)
         */
        this.store = PgVectorEmbeddingStore.builder()
                .host(host)                    // PostgreSQL 地址
                .port(port)                    // PostgreSQL 端口
                .database(database)            // 数据库名
                .user(user)                    // 用户名
                .password(password)            // 密码
                .table(table)                  // 表名
                .dimension(dimension)          // 向量维度 (必须与模型匹配!)

                /*
                 * createTable(true) - 自动创建表
                 *
                 * 如果表不存在，自动执行:
                 * CREATE TABLE IF NOT EXISTS {table} (
                 *     embedding_id UUID PRIMARY KEY,
                 *     embedding vector({dimension}),
                 *     text TEXT,
                 *     metadata JSONB
                 * );
                 */
                .createTable(true)

                /*
                 * dropTableFirst(false) - 是否先删除表
                 *
                 * - true: 每次启动都删除表重建 (会丢失数据!)
                 * - false: 保留已有数据 (生产环境必须用 false)
                 *
                 * ⚠️ 警告: 生产环境必须设为 false!
                 */
                .dropTableFirst(false)

                /*
                 * useIndex(true) - 创建向量索引
                 *
                 * 自动创建 IVFFlat 索引:
                 * CREATE INDEX ON {table}
                 *     USING ivfflat (embedding vector_cosine_ops)
                 *     WITH (lists = 100);
                 *
                 * 索引作用:
                 * - 加速向量搜索 (从秒级到毫秒级)
                 * - 使用近似最近邻 (ANN) 算法
                 *
                 * 注意: 数据量 < 1000 时索引效果不明显
                 */
                .useIndex(true)

                .build();

        log.info("PostgreSQL pgvector 向量存储初始化完成");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VectorStoreService 接口实现
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取存储类型标识
     *
     * @return "pgvector"
     */
    @Override
    public String getType() {
        return "pgvector";
    }

    /**
     * 检查服务是否可用
     *
     * @return true 表示已初始化成功
     */
    @Override
    public boolean isAvailable() {
        return store != null;
    }

    /**
     * 存储单个向量
     *
     * @param embedding 向量数据 (1536维浮点数组)
     * @param segment   文本片段 (包含原文和元数据)
     * @return 生成的唯一ID (UUID格式)
     *
     * 底层SQL:
     * INSERT INTO knowledge_embeddings (embedding_id, embedding, text, metadata)
     * VALUES (uuid, vector, text, jsonb);
     */
    @Override
    public String store(Embedding embedding, TextSegment segment) {
        return store.add(embedding, segment);
    }

    /**
     * 批量存储向量
     *
     * @param embeddings 向量列表
     * @param segments   文本片段列表 (与 embeddings 一一对应)
     * @return 生成的ID列表
     *
     * 性能: 批量插入比逐条插入快 10-100 倍
     */
    @Override
    public List<String> storeBatch(List<Embedding> embeddings, List<TextSegment> segments) {
        return store.addAll(embeddings, segments);
    }

    /**
     * 向量相似度搜索
     *
     * @param queryEmbedding 查询向量 (用户问题的向量化结果)
     * @param maxResults     最多返回几条结果
     * @param minScore       最低相似度阈值 (0-1, 低于此分数的结果会被过滤)
     * @return 匹配结果列表，按相似度降序排列
     *
     * 底层SQL (简化版):
     * SELECT *, 1 - (embedding <=> query_embedding) AS score
     * FROM knowledge_embeddings
     * WHERE 1 - (embedding <=> query_embedding) >= minScore
     * ORDER BY embedding <=> query_embedding
     * LIMIT maxResults;
     *
     * 说明:
     * - <=> 是 pgvector 的余弦距离运算符
     * - 1 - 距离 = 相似度
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore) {
        /*
         * EmbeddingSearchRequest - 搜索请求构建器
         *
         * 参数说明:
         * - queryEmbedding: 查询向量 (必需)
         * - maxResults: 最大返回数量 (必需)
         * - minScore: 最低分数阈值 (可选, 默认0)
         * - filter: 元数据过滤条件 (可选)
         */
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)  // 查询向量
                .maxResults(maxResults)          // 最多返回N条
                .minScore(minScore)              // 最低相似度
                .build();

        /*
         * store.search() 返回 EmbeddingSearchResult
         *
         * EmbeddingSearchResult 包含:
         * - matches(): 匹配结果列表
         *
         * 每个 EmbeddingMatch 包含:
         * - embeddingId(): 向量记录ID
         * - embedding(): 向量数据
         * - embedded(): TextSegment (原文+元数据)
         * - score(): 相似度分数 (0-1)
         */
        EmbeddingSearchResult<TextSegment> result = store.search(request);
        return result.matches();
    }

    /**
     * 带分类过滤的向量搜索
     *
     * @param queryEmbedding 查询向量
     * @param maxResults     最大结果数
     * @param minScore       最低分数
     * @param category       分类过滤 (只搜索指定分类的文档)
     * @return 匹配结果列表
     *
     * 底层SQL (带过滤):
     * SELECT * FROM knowledge_embeddings
     * WHERE metadata->>'category' = 'HR政策'
     *   AND 1 - (embedding <=> query_embedding) >= minScore
     * ORDER BY embedding <=> query_embedding
     * LIMIT maxResults;
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore, String category) {
        EmbeddingSearchRequest request;

        if (category != null && !category.isEmpty()) {
            /*
             * MetadataFilterBuilder - 元数据过滤器构建器
             *
             * 用法:
             * - metadataKey("字段名").isEqualTo(值) - 等于
             * - metadataKey("字段名").isNotEqualTo(值) - 不等于
             * - metadataKey("字段名").isIn(值列表) - 在列表中
             * - metadataKey("字段名").isGreaterThan(值) - 大于
             *
             * 组合:
             * - filter1.and(filter2) - 且
             * - filter1.or(filter2) - 或
             */
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(MetadataFilterBuilder
                            .metadataKey("category")  // 元数据字段名
                            .isEqualTo(category))     // 等于指定值
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
     * 根据文档ID删除所有相关向量
     *
     * @param docId 文档唯一标识
     * @return 删除数量 (-1 表示未知，pgvector 不返回删除数量)
     *
     * 场景: 文档更新时，先删除旧向量，再插入新向量
     *
     * 底层SQL:
     * DELETE FROM knowledge_embeddings
     * WHERE metadata->>'docId' = 'db:doc-001';
     */
    @Override
    public int deleteByDocId(String docId) {
        try {
            /*
             * removeAll(filter) - 按条件删除
             *
             * 删除所有 metadata.docId = docId 的记录
             */
            store.removeAll(MetadataFilterBuilder
                    .metadataKey("docId")
                    .isEqualTo(docId));
            return -1; // pgvector 不返回删除数量
        } catch (Exception e) {
            log.error("删除文档向量失败: {}", docId, e);
            return 0;
        }
    }

    /**
     * 删除所有向量数据
     *
     * ⚠️ 危险操作: 会清空整个向量表!
     *
     * 底层SQL:
     * DELETE FROM knowledge_embeddings;
     * -- 或
     * TRUNCATE TABLE knowledge_embeddings;
     */
    @Override
    public void deleteAll() {
        log.warn("删除所有向量数据!");
        store.removeAll();
    }

    /**
     * 获取存储统计信息
     *
     * @return 统计信息 (类型、数量、可用状态)
     *
     * 注意: pgvector 版本的 LangChain4j 不提供计数方法，
     * 所以 count 返回 -1
     */
    @Override
    public StoreStats getStats() {
        return new StoreStats(
                "pgvector",      // 存储类型
                -1,              // 向量数量 (pgvector 不支持直接获取)
                isAvailable()    // 是否可用
        );
    }
}
