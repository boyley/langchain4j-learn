package com.example.langchain4j.vectorstore.demo;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.util.List;

/**
 * PostgreSQL + pgvector 向量存储 Demo
 *
 * 【适用场景】
 * - 已有 PostgreSQL 数据库环境
 * - 中大规模数据 (< 500万条)
 * - 需要 SQL 兼容性
 * - 需要事务支持
 *
 * 【优点】
 * - 无需额外部署向量数据库
 * - 支持 SQL 查询，学习成本低
 * - 支持事务、备份、主从复制
 * - 可与业务数据存在同一数据库
 *
 * 【缺点】
 * - 大规模数据性能不如专业向量库
 * - 需要手动调优索引
 *
 * 【前置条件】
 * 1. PostgreSQL 12+ 数据库
 * 2. 安装 pgvector 扩展
 *
 * @author LangChain4j 学习项目
 */
public class PostgresVectorStoreDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║      PostgreSQL + pgvector 向量存储 Demo (已有PG环境推荐)      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 前置条件 ====================
        printPrerequisites();

        // ==================== 第一步: 创建向量模型 ====================
        System.out.println("【第一步】创建向量模型");
        System.out.println("─".repeat(50));

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .modelName("text-embedding-3-small")
                .build();

        System.out.println("✅ 向量模型创建成功 (text-embedding-3-small, 1536维)\n");

        // ==================== 第二步: 创建 PostgreSQL 向量存储 ====================
        System.out.println("【第二步】创建 PostgreSQL 向量存储");
        System.out.println("─".repeat(50));

        /**
         * PgVectorEmbeddingStore 参数详解:
         *
         * 【连接配置】
         * @param host      - PostgreSQL 地址，默认 localhost
         * @param port      - 端口，默认 5432
         * @param database  - 数据库名
         * @param user      - 用户名
         * @param password  - 密码
         *
         * 【表配置】
         * @param table       - 表名，默认 "embeddings"
         * @param dimension   - 向量维度，必须与模型一致!
         * @param createTable - 是否自动创建表，首次运行设为 true
         * @param dropTableFirst - 是否先删除旧表，开发测试时可设为 true
         *
         * 【索引配置】
         * @param useIndex      - 是否创建向量索引，大数据量时建议 true
         * @param indexListSize - IVFFlat 索引的 lists 参数
         *                        建议值: sqrt(行数)，如 100万行用 1000
         *
         * 自动创建的表结构:
         * CREATE TABLE embeddings (
         *     id UUID PRIMARY KEY,
         *     embedding vector(1536),
         *     text TEXT,
         *     metadata JSONB
         * );
         */
        EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
                // 连接配置
                .host(getEnvOrDefault("PG_HOST", "localhost"))
                .port(Integer.parseInt(getEnvOrDefault("PG_PORT", "5432")))
                .database(getEnvOrDefault("PG_DATABASE", "knowledge_db"))
                .user(getEnvOrDefault("PG_USER", "postgres"))
                .password(getEnvOrDefault("PG_PASSWORD", "postgres"))

                // 表配置
                .table("knowledge_embeddings")   // 自定义表名
                .dimension(1536)                 // 向量维度 (必须匹配!)
                .createTable(true)               // 自动创建表 (首次运行)
                .dropTableFirst(false)           // 不删除已有数据

                // 索引配置 (可选，大数据量时建议开启)
                .useIndex(true)                  // 创建向量索引
                .indexListSize(100)              // IVFFlat 索引参数

                .build();

        System.out.println("✅ PostgreSQL 向量存储创建成功");
        System.out.println("   表名: knowledge_embeddings");
        System.out.println("   维度: 1536");
        System.out.println("   索引: IVFFlat (lists=100)");
        System.out.println();

        // ==================== 第三步: 添加知识数据 ====================
        System.out.println("【第三步】添加知识数据");
        System.out.println("─".repeat(50));

        String[][] knowledgeData = {
                {"PostgreSQL 是一个强大的开源关系型数据库。", "DOC-001", "数据库"},
                {"pgvector 是 PostgreSQL 的向量扩展，支持向量相似性搜索。", "DOC-001", "数据库"},
                {"Java 是一种面向对象的编程语言，广泛用于企业级应用开发。", "DOC-002", "编程语言"},
                {"Spring Boot 简化了 Spring 应用的开发和部署。", "DOC-003", "框架"},
                {"LangChain4j 是 Java 版的 LangChain，用于构建 AI 应用。", "DOC-004", "AI框架"},
        };

        for (String[] data : knowledgeData) {
            String text = data[0];
            String docId = data[1];
            String category = data[2];

            Metadata metadata = new Metadata();
            metadata.put("docId", docId);
            metadata.put("category", category);

            TextSegment segment = TextSegment.from(text, metadata);
            Embedding embedding = embeddingModel.embed(text).content();
            embeddingStore.add(embedding, segment);

            System.out.println("✅ [" + category + "] " + truncate(text, 50));
        }
        System.out.println("\n共入库 " + knowledgeData.length + " 条知识\n");

        // ==================== 第四步: 向量检索 ====================
        System.out.println("【第四步】向量检索");
        System.out.println("─".repeat(50));

        String query = "向量数据库怎么搜索？";
        System.out.println("🔍 查询: " + query + "\n");

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .minScore(0.5)
                        .build()
        );

        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment seg = match.embedded();
            System.out.println("   [" + String.format("%.0f%%", match.score() * 100) + "] "
                    + seg.text());
        }

        // ==================== 第五步: 带过滤器检索 ====================
        System.out.println("\n【第五步】带元数据过滤的检索");
        System.out.println("─".repeat(50));

        System.out.println("🔍 查询: \"开发\" (限定分类: 数据库)\n");

        Embedding queryEmbedding2 = embeddingModel.embed("开发").content();
        EmbeddingSearchResult<TextSegment> result2 = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding2)
                        .maxResults(3)
                        .minScore(0.3)
                        .filter(MetadataFilterBuilder.metadataKey("category")
                                .isEqualTo("数据库"))
                        .build()
        );

        for (EmbeddingMatch<TextSegment> match : result2.matches()) {
            System.out.println("   [" + String.format("%.0f%%", match.score() * 100) + "] "
                    + match.embedded().text());
        }

        // ==================== 第六步: SQL 直接操作 ====================
        System.out.println("\n【第六步】PostgreSQL 直接操作向量表");
        System.out.println("─".repeat(50));

        System.out.println("""
                pgvector 的优势是可以直接用 SQL 操作!

                -- 查看表结构
                \\d knowledge_embeddings

                -- 查看所有数据
                SELECT id, text, metadata FROM knowledge_embeddings;

                -- 按元数据查询
                SELECT text, metadata->>'category' as category
                FROM knowledge_embeddings
                WHERE metadata->>'category' = '数据库';

                -- 手动向量搜索 (L2 距离)
                SELECT text, embedding <-> '[0.1, 0.2, ...]' as distance
                FROM knowledge_embeddings
                ORDER BY distance
                LIMIT 5;

                -- 手动向量搜索 (余弦相似度)
                SELECT text, 1 - (embedding <=> '[0.1, 0.2, ...]') as similarity
                FROM knowledge_embeddings
                ORDER BY similarity DESC
                LIMIT 5;

                -- 删除特定文档的所有向量
                DELETE FROM knowledge_embeddings
                WHERE metadata->>'docId' = 'DOC-001';

                -- 统计各分类的向量数量
                SELECT metadata->>'category' as category, COUNT(*)
                FROM knowledge_embeddings
                GROUP BY category;
                """);

        // ==================== 第七步: 索引优化 ====================
        System.out.println("【第七步】索引优化建议");
        System.out.println("─".repeat(50));

        System.out.println("""
                pgvector 支持两种索引类型:

                1. IVFFlat (倒排文件索引)
                   - 适合: 数据量大，内存有限
                   - 建议 lists 值: sqrt(行数)
                   - 查询时需要设置 probes: 通常为 lists/10

                   CREATE INDEX ON knowledge_embeddings
                   USING ivfflat (embedding vector_cosine_ops)
                   WITH (lists = 100);

                   SET ivfflat.probes = 10;

                2. HNSW (分层可导航小世界图)
                   - 适合: 追求查询速度
                   - 内存占用更高
                   - 构建时间更长

                   CREATE INDEX ON knowledge_embeddings
                   USING hnsw (embedding vector_cosine_ops)
                   WITH (m = 16, ef_construction = 64);

                   SET hnsw.ef_search = 40;

                【索引选择建议】
                - < 10万条: 可以不建索引
                - 10-100万条: 使用 IVFFlat
                - > 100万条: 使用 HNSW 或考虑 Milvus
                """);

        // ==================== 总结 ====================
        printSummary();
    }

    private static void printPrerequisites() {
        System.out.println("""
                ┌────────────────────────────────────────────────────────────┐
                │                      前置条件                               │
                └────────────────────────────────────────────────────────────┘

                1. Docker 启动 PostgreSQL + pgvector

                   docker run -d \\
                     --name postgres-vector \\
                     -p 5432:5432 \\
                     -e POSTGRES_PASSWORD=postgres \\
                     -e POSTGRES_DB=knowledge_db \\
                     pgvector/pgvector:pg16

                2. 或在现有 PostgreSQL 上安装 pgvector

                   # Ubuntu/Debian
                   sudo apt install postgresql-16-pgvector

                   # 在数据库中启用扩展
                   CREATE EXTENSION IF NOT EXISTS vector;

                3. 验证 pgvector 安装成功

                   psql -U postgres -d knowledge_db
                   > SELECT * FROM pg_extension WHERE extname = 'vector';

                4. 设置环境变量

                   export PG_HOST=localhost
                   export PG_PORT=5432
                   export PG_DATABASE=knowledge_db
                   export PG_USER=postgres
                   export PG_PASSWORD=postgres

                ─────────────────────────────────────────────────────────────
                """);
    }

    private static void printSummary() {
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【总结】PostgreSQL + pgvector 向量存储");
        System.out.println("═".repeat(60));

        System.out.println("""

                ┌────────────────────────────────────────────────────────────┐
                │            PostgreSQL + pgvector 选型指南                  │
                ├────────────────────────────────────────────────────────────┤
                │  数据规模: < 500万条向量                                    │
                │  查询延迟: 10-50ms (取决于索引)                            │
                │  存储方式: 磁盘存储，支持 SSD 加速                          │
                ├────────────────────────────────────────────────────────────┤
                │  ✅ 适合场景:                                               │
                │     - 已有 PostgreSQL 基础设施                              │
                │     - 需要 SQL 兼容性                                       │
                │     - 需要事务支持                                          │
                │     - 向量数据与业务数据在同一库                            │
                │     - 运维团队熟悉 PostgreSQL                               │
                ├────────────────────────────────────────────────────────────┤
                │  ❌ 不适合场景:                                             │
                │     - 超大规模数据 (> 500万)                                │
                │     - 需要亚毫秒级响应                                      │
                │     - 纯向量搜索场景 (无 SQL 需求)                          │
                └────────────────────────────────────────────────────────────┘

                【与 Redis 对比】
                ┌──────────────┬────────────────┬────────────────┐
                │              │     Redis      │   PostgreSQL   │
                ├──────────────┼────────────────┼────────────────┤
                │  查询速度    │     更快       │     中等       │
                │  存储方式    │     内存       │     磁盘       │
                │  数据持久化  │     可选       │     默认       │
                │  SQL 支持    │     无         │     完整       │
                │  事务支持    │     有限       │     完整       │
                │  学习成本    │     中等       │     低         │
                └──────────────┴────────────────┴────────────────┘

                下一步: 了解 Milvus 专业向量库 → MilvusVectorStoreDemo.java
                """);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
