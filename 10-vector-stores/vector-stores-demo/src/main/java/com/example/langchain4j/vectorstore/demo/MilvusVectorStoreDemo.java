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
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;

import java.util.List;

/**
 * Milvus 向量存储 Demo
 *
 * 【Milvus 简介】
 * Milvus 是一个专业的开源向量数据库，专为 AI 应用设计。
 * 支持万亿级向量搜索，毫秒级响应。
 *
 * 【适用场景】
 * - 大规模生产环境 (百万~千万级向量)
 * - 需要高性能向量搜索
 * - 需要分布式部署
 * - AI/ML 应用场景
 *
 * 【优点】
 * - 专为向量搜索优化，性能极佳
 * - 支持多种索引类型 (FLAT, IVF, HNSW, DiskANN)
 * - 支持分布式集群
 * - 支持 GPU 加速
 * - 丰富的向量搜索功能
 *
 * 【缺点】
 * - 部署复杂度较高
 * - 学习曲线较陡
 * - 需要额外运维
 *
 * @author LangChain4j 学习项目
 */
public class MilvusVectorStoreDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Milvus 向量存储 Demo (大规模生产推荐)                 ║");
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

        System.out.println("✅ 向量模型创建成功 (1536维)\n");

        // ==================== 第二步: 创建 Milvus 向量存储 ====================
        System.out.println("【第二步】创建 Milvus 向量存储");
        System.out.println("─".repeat(50));

        /**
         * MilvusEmbeddingStore 参数详解:
         *
         * 【连接配置】
         * @param host    - Milvus 服务地址
         * @param port    - gRPC 端口，默认 19530
         * @param token   - 认证 token (Milvus Cloud 或开启认证时需要)
         *
         * 【集合配置】
         * @param collectionName   - 集合名称 (类似于数据库表)
         * @param dimension        - 向量维度，必须与模型一致
         * @param consistencyLevel - 一致性级别:
         *                           STRONG: 强一致 (实时可见，性能略低)
         *                           BOUNDED: 有界一致 (几秒延迟)
         *                           EVENTUALLY: 最终一致 (性能最高)
         *                           SESSION: 会话一致
         *
         * 【索引配置】
         * @param indexType   - 索引类型 (详见下方说明)
         * @param metricType  - 距离度量方式:
         *                      COSINE: 余弦相似度 (推荐文本)
         *                      L2: 欧氏距离
         *                      IP: 内积
         */
        /**
         * MilvusEmbeddingStore 参数详解:
         *
         * 【连接配置】
         * @param host    - Milvus 服务地址
         * @param port    - gRPC 端口，默认 19530
         * @param token   - 认证 token (Milvus Cloud 或开启认证时需要)
         *
         * 【集合配置】
         * @param collectionName   - 集合名称 (类似于数据库表)
         * @param dimension        - 向量维度，必须与模型一致
         *
         * 注意: 索引类型和度量方式由 Milvus 服务端配置
         */
        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                // 连接配置
                .host(getEnvOrDefault("MILVUS_HOST", "localhost"))
                .port(Integer.parseInt(getEnvOrDefault("MILVUS_PORT", "19530")))
                // .token("your-token")  // Milvus Cloud 或认证时需要

                // 集合配置
                .collectionName("knowledge_vectors")  // 集合名称
                .dimension(1536)                      // 向量维度

                .build();

        System.out.println("✅ Milvus 向量存储创建成功");
        System.out.println("   集合: knowledge_vectors");
        System.out.println("   维度: 1536");
        System.out.println("   索引: IVF_FLAT");
        System.out.println("   度量: COSINE");
        System.out.println();

        // ==================== 第三步: 添加知识数据 ====================
        System.out.println("【第三步】添加知识数据");
        System.out.println("─".repeat(50));

        String[][] knowledgeData = {
                {"Milvus 是一个开源的向量数据库，专为 AI 应用设计。", "milvus"},
                {"Milvus 支持多种索引类型，包括 IVF、HNSW、DiskANN 等。", "milvus"},
                {"LangChain4j 是 Java 版的 LangChain 框架。", "langchain4j"},
                {"向量相似性搜索是 RAG 应用的核心技术。", "rag"},
                {"知识库管理需要考虑增量同步和版本控制。", "knowledge"},
        };

        for (String[] data : knowledgeData) {
            String text = data[0];
            String topic = data[1];

            Metadata metadata = new Metadata();
            metadata.put("topic", topic);

            TextSegment segment = TextSegment.from(text, metadata);
            Embedding embedding = embeddingModel.embed(text).content();
            embeddingStore.add(embedding, segment);

            System.out.println("✅ [" + topic + "] " + truncate(text, 45));
        }
        System.out.println("\n共入库 " + knowledgeData.length + " 条知识\n");

        // ==================== 第四步: 向量检索 ====================
        System.out.println("【第四步】向量检索");
        System.out.println("─".repeat(50));

        String query = "什么是向量数据库？";
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
            System.out.println("   [" + String.format("%.0f%%", match.score() * 100) + "] "
                    + match.embedded().text());
        }

        // ==================== 第五步: 索引类型说明 ====================
        System.out.println("\n【第五步】Milvus 索引类型详解");
        System.out.println("─".repeat(50));

        System.out.println("""

                ┌───────────────────────────────────────────────────────────────┐
                │                    Milvus 索引类型对比                         │
                ├─────────────┬─────────────┬─────────────┬─────────────────────┤
                │   索引类型   │   查询速度   │   精确度    │        适用场景       │
                ├─────────────┼─────────────┼─────────────┼─────────────────────┤
                │   FLAT      │    慢       │   100%      │  小数据量 (<10万)    │
                │   IVF_FLAT  │    中       │   高        │  中等数据量，均衡     │
                │   IVF_SQ8   │    快       │   中        │  大数据量，节省内存   │
                │   IVF_PQ    │    快       │   中        │  超大数据量，压缩存储 │
                │   HNSW      │    极快     │   高        │  高性能查询 (推荐)   │
                │   DISKANN   │    快       │   高        │  超大规模，磁盘存储   │
                └─────────────┴─────────────┴─────────────┴─────────────────────┘

                【索引选择建议】

                1. FLAT (精确搜索)
                   - 数据量 < 10万
                   - 需要 100% 精确结果
                   - 不需要构建索引时间

                2. IVF_FLAT (推荐入门)
                   - 数据量 10-100万
                   - 精确度和速度均衡
                   - 参数: nlist (聚类数)，建议 sqrt(数据量)

                3. HNSW (推荐生产)
                   - 数据量 100万+
                   - 追求极致查询速度
                   - 内存占用较高
                   - 参数: M (边数), efConstruction (构建精度)

                4. DISKANN (超大规模)
                   - 数据量 1000万+
                   - 内存有限，使用磁盘存储
                   - 查询速度仍然很快
                """);

        // ==================== 第六步: Milvus 管理命令 ====================
        System.out.println("【第六步】Milvus 管理命令 (Python SDK 示例)");
        System.out.println("─".repeat(50));

        System.out.println("""
                # 安装 Python SDK
                pip install pymilvus

                # Python 管理示例
                from pymilvus import connections, Collection, utility

                # 连接 Milvus
                connections.connect("default", host="localhost", port="19530")

                # 列出所有集合
                print(utility.list_collections())

                # 获取集合信息
                collection = Collection("knowledge_vectors")
                print(collection.schema)
                print(collection.num_entities)  # 数据量

                # 删除集合 (谨慎!)
                # utility.drop_collection("knowledge_vectors")

                # 查看索引信息
                print(collection.indexes)

                # 手动搜索
                results = collection.search(
                    data=[[0.1, 0.2, ...]],  # 查询向量
                    anns_field="embedding",   # 向量字段
                    param={"metric_type": "COSINE", "params": {"nprobe": 10}},
                    limit=5
                )
                """);

        // ==================== 总结 ====================
        printSummary();
    }

    private static void printPrerequisites() {
        System.out.println("""
                ┌────────────────────────────────────────────────────────────┐
                │                      前置条件                               │
                └────────────────────────────────────────────────────────────┘

                方式1: Docker Compose 部署 (推荐开发测试)
                ─────────────────────────────────────────────────────────────
                # docker-compose.yml
                version: '3.5'
                services:
                  etcd:
                    image: quay.io/coreos/etcd:v3.5.0
                    environment:
                      - ETCD_AUTO_COMPACTION_MODE=revision
                      - ETCD_AUTO_COMPACTION_RETENTION=1000
                    command: etcd -listen-client-urls=http://0.0.0.0:2379

                  minio:
                    image: minio/minio:latest
                    environment:
                      MINIO_ACCESS_KEY: minioadmin
                      MINIO_SECRET_KEY: minioadmin
                    command: minio server /data

                  standalone:
                    image: milvusdb/milvus:latest
                    ports:
                      - "19530:19530"
                      - "9091:9091"
                    environment:
                      ETCD_ENDPOINTS: etcd:2379
                      MINIO_ADDRESS: minio:9000
                    depends_on:
                      - etcd
                      - minio

                # 启动
                docker-compose up -d

                方式2: Docker 单机快速启动
                ─────────────────────────────────────────────────────────────
                docker run -d --name milvus-standalone \\
                  -p 19530:19530 \\
                  -p 9091:9091 \\
                  milvusdb/milvus:latest

                方式3: Milvus Cloud (生产推荐)
                ─────────────────────────────────────────────────────────────
                访问 https://cloud.zilliz.com 创建托管实例

                环境变量配置:
                export MILVUS_HOST=localhost
                export MILVUS_PORT=19530

                ─────────────────────────────────────────────────────────────
                """);
    }

    private static void printSummary() {
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【总结】Milvus 向量存储");
        System.out.println("═".repeat(60));

        System.out.println("""

                ┌────────────────────────────────────────────────────────────┐
                │                  Milvus 选型指南                            │
                ├────────────────────────────────────────────────────────────┤
                │  数据规模: 百万~万亿级向量                                   │
                │  查询延迟: < 10ms (HNSW)                                   │
                │  部署方式: 单机 / 分布式集群 / 云托管                        │
                ├────────────────────────────────────────────────────────────┤
                │  ✅ 适合场景:                                               │
                │     - 大规模生产环境                                        │
                │     - AI/ML 应用                                           │
                │     - 需要高性能向量搜索                                    │
                │     - 需要分布式部署                                        │
                │     - 有向量数据库运维能力                                  │
                ├────────────────────────────────────────────────────────────┤
                │  ❌ 不适合场景:                                             │
                │     - 小规模数据 (< 10万)                                   │
                │     - 简单的 POC 验证                                       │
                │     - 无法投入运维资源                                      │
                └────────────────────────────────────────────────────────────┘

                【各方案对比】
                ┌──────────────┬────────────┬────────────┬────────────┐
                │              │  InMemory  │   Redis    │   Milvus   │
                ├──────────────┼────────────┼────────────┼────────────┤
                │  数据规模    │   < 1万    │  < 100万   │   亿级+    │
                │  查询速度    │    快      │    快      │    极快    │
                │  持久化      │    无      │    有      │    有      │
                │  分布式      │    无      │    有限    │    完整    │
                │  部署复杂度  │    无      │    低      │    中-高   │
                │  运维成本    │    无      │    低      │    中-高   │
                └──────────────┴────────────┴────────────┴────────────┘

                下一步: 了解 Elasticsearch → ElasticsearchVectorStoreDemo.java
                        或查看完整架构 → KnowledgeArchitectureDemo.java
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
