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
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;

import java.util.List;

/**
 * Redis 向量存储 Demo
 *
 * 【适用场景】
 * - 中小规模生产环境 (< 100万条)
 * - 需要持久化存储
 * - 需要高性能查询 (< 10ms)
 * - 已有 Redis 基础设施
 *
 * 【优点】
 * - 查询速度快 (毫秒级)
 * - 支持持久化
 * - 支持元数据过滤
 * - 运维成熟，生态丰富
 *
 * 【缺点】
 * - 需要 Redis Stack (含 RediSearch)
 * - 内存消耗较大
 * - 大规模数据性能下降
 *
 * 【前置条件】
 * 启动 Redis Stack:
 * docker run -d --name redis-stack -p 6379:6379 redis/redis-stack:latest
 *
 * @author LangChain4j 学习项目
 */
public class RedisVectorStoreDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           Redis 向量存储 Demo (中小规模推荐)                   ║");
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

        // ==================== 第二步: 创建 Redis 向量存储 ====================
        System.out.println("【第二步】创建 Redis 向量存储");
        System.out.println("─".repeat(50));

        /**
         * RedisEmbeddingStore 参数详解:
         *
         * 【连接配置】
         * @param host          - Redis 服务器地址，默认 localhost
         * @param port          - Redis 端口，默认 6379
         * @param user          - 用户名 (Redis 6.0+ ACL)，默认 default
         * @param password      - 密码，无密码时可不设置
         *
         * 【索引配置】
         * @param indexName     - 向量索引名称，建议按业务命名如 "knowledge_vectors"
         * @param prefix        - key 前缀，默认 "embedding:"，存储格式: embedding:uuid
         * @param dimension     - 向量维度，必须与 Embedding 模型输出维度一致!
         *                        text-embedding-3-small = 1536
         *                        text-embedding-3-large = 3072
         *
         * 【元数据配置】
         * @param metadataKeys  - 需要索引的元数据字段，用于过滤查询
         *                        只有在此列表中的字段才能用于 filter
         *
         * 【高级配置】
         * @param vectorAlgorithm - 向量搜索算法:
         *                          FLAT: 精确搜索，适合小数据量
         *                          HNSW: 近似搜索，适合大数据量
         */
        EmbeddingStore<TextSegment> embeddingStore = RedisEmbeddingStore.builder()
                // 连接配置
                .host(getEnvOrDefault("REDIS_HOST", "localhost"))  // Redis 地址
                .port(Integer.parseInt(getEnvOrDefault("REDIS_PORT", "6379")))  // 端口
                // .user("default")           // 用户名 (可选)
                // .password("your-password") // 密码 (可选)

                // 索引配置
                .indexName("knowledge_vectors")   // 索引名称
                .dimension(1536)                  // 向量维度 (必须匹配模型!)

                // 元数据配置 - 指定哪些字段可用于过滤
                .metadataKeys(List.of(
                        "docId",       // 文档ID - 用于更新/删除
                        "category",    // 分类 - 用于分类过滤
                        "title",       // 标题 - 用于展示
                        "version"      // 版本 - 用于版本管理
                ))
                .build();

        System.out.println("✅ Redis 向量存储创建成功");
        System.out.println("   索引名: knowledge_vectors");
        System.out.println("   维度: 1536");
        System.out.println("   可过滤字段: docId, category, title, version");
        System.out.println();

        // ==================== 第三步: 添加知识数据 ====================
        System.out.println("【第三步】添加知识数据到 Redis");
        System.out.println("─".repeat(50));

        // 清空旧数据 (演示用)
        // embeddingStore.removeAll();

        // 准备知识数据
        String[][] knowledgeData = {
                {"员工每年享有5天带薪年假，工作满3年后增加到10天。", "DOC-001", "HR政策", "休假管理办法"},
                {"请假需要提前3天在OA系统提交申请，紧急情况可电话请假。", "DOC-001", "HR政策", "休假管理办法"},
                {"差旅费报销需要在30天内提交，附上发票原件和行程单。", "DOC-002", "财务制度", "费用报销制度"},
                {"新员工入职需准备身份证、学历证书、离职证明、银行卡信息。", "DOC-003", "HR政策", "入职指南"},
                {"Python 开发规范：使用 4 空格缩进，类名使用大驼峰命名。", "DOC-004", "技术规范", "开发规范"},
                {"API 接口返回格式统一为 JSON，包含 code、message、data 字段。", "DOC-004", "技术规范", "开发规范"},
        };

        for (int i = 0; i < knowledgeData.length; i++) {
            String text = knowledgeData[i][0];
            String docId = knowledgeData[i][1];
            String category = knowledgeData[i][2];
            String title = knowledgeData[i][3];

            // 创建元数据
            Metadata metadata = new Metadata();
            metadata.put("docId", docId);
            metadata.put("category", category);
            metadata.put("title", title);
            metadata.put("version", "1.0");

            // 创建文本片段
            TextSegment segment = TextSegment.from(text, metadata);

            // 向量化
            Embedding embedding = embeddingModel.embed(text).content();

            // 存储到 Redis
            String id = embeddingStore.add(embedding, segment);

            System.out.println("✅ #" + (i + 1) + " [" + category + "] " + truncate(text, 40));
        }
        System.out.println("\n共入库 " + knowledgeData.length + " 条知识\n");

        // ==================== 第四步: 基础检索 ====================
        System.out.println("【第四步】基础向量检索");
        System.out.println("─".repeat(50));

        String query1 = "怎么请假？";
        System.out.println("🔍 查询: " + query1);

        Embedding queryEmbedding1 = embeddingModel.embed(query1).content();
        EmbeddingSearchResult<TextSegment> result1 = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding1)
                        .maxResults(3)
                        .minScore(0.5)
                        .build()
        );

        for (EmbeddingMatch<TextSegment> match : result1.matches()) {
            System.out.println("   [" + String.format("%.0f%%", match.score() * 100) + "] "
                    + match.embedded().text());
        }
        System.out.println();

        // ==================== 第五步: 带过滤器检索 ====================
        System.out.println("【第五步】带元数据过滤的检索");
        System.out.println("─".repeat(50));

        String query2 = "有什么规范？";

        // 5.1 不带过滤器
        System.out.println("🔍 查询: " + query2 + " (不限分类)");
        searchAndPrint(embeddingStore, embeddingModel, query2, null);

        // 5.2 只搜索技术规范
        System.out.println("\n🔍 查询: " + query2 + " (限定: 技术规范)");
        searchAndPrint(embeddingStore, embeddingModel, query2, "技术规范");

        // 5.3 只搜索 HR 政策
        System.out.println("\n🔍 查询: " + query2 + " (限定: HR政策)");
        searchAndPrint(embeddingStore, embeddingModel, query2, "HR政策");

        // ==================== 第六步: 删除操作 ====================
        System.out.println("\n【第六步】删除操作");
        System.out.println("─".repeat(50));

        System.out.println("""
                Redis 向量存储支持多种删除方式:

                // 方式1: 按 ID 删除单条
                embeddingStore.remove("uuid-xxx");

                // 方式2: 按 ID 列表批量删除
                embeddingStore.removeAll(List.of("id1", "id2", "id3"));

                // 方式3: 按条件删除 (推荐用于文档更新)
                embeddingStore.removeAll(
                    MetadataFilterBuilder.metadataKey("docId").isEqualTo("DOC-001")
                );

                // 方式4: 删除所有数据 (谨慎!)
                embeddingStore.removeAll();
                """);

        // ==================== 第七步: Redis 管理命令 ====================
        System.out.println("【第七步】Redis 管理命令");
        System.out.println("─".repeat(50));

        System.out.println("""
                # 连接 Redis
                redis-cli

                # 查看所有向量索引
                FT._LIST

                # 查看索引信息
                FT.INFO knowledge_vectors

                # 查看存储的 keys
                KEYS embedding:*

                # 查看某条数据的详情
                HGETALL embedding:xxx-uuid-xxx

                # 手动删除索引 (会删除所有数据!)
                FT.DROPINDEX knowledge_vectors DD

                # 监控 Redis 内存使用
                INFO memory
                """);

        // ==================== 总结 ====================
        printSummary();
    }

    /**
     * 执行搜索并打印结果
     */
    private static void searchAndPrint(EmbeddingStore<TextSegment> store,
                                       EmbeddingModel model,
                                       String query,
                                       String categoryFilter) {
        Embedding queryEmbedding = model.embed(query).content();

        EmbeddingSearchRequest request;
        if (categoryFilter != null) {
            /**
             * MetadataFilterBuilder 过滤器用法:
             *
             * 等于: metadataKey("category").isEqualTo("HR政策")
             * 不等于: metadataKey("category").isNotEqualTo("财务")
             * 包含: metadataKey("category").isIn(List.of("HR", "财务"))
             * 大于: metadataKey("version").isGreaterThan(1.0)
             * 组合: filter1.and(filter2) 或 filter1.or(filter2)
             */
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.3)
                    .filter(MetadataFilterBuilder.metadataKey("category")
                            .isEqualTo(categoryFilter))
                    .build();
        } else {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.3)
                    .build();
        }

        EmbeddingSearchResult<TextSegment> result = store.search(request);

        if (result.matches().isEmpty()) {
            System.out.println("   未找到相关内容");
        } else {
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                TextSegment seg = match.embedded();
                System.out.println("   [" + String.format("%.0f%%", match.score() * 100) + "] ["
                        + seg.metadata().getString("category") + "] "
                        + truncate(seg.text(), 50));
            }
        }
    }

    private static void printPrerequisites() {
        System.out.println("""
                ┌────────────────────────────────────────────────────────────┐
                │                      前置条件                               │
                └────────────────────────────────────────────────────────────┘

                1. 启动 Redis Stack (包含 RediSearch 向量搜索模块)

                   # Docker 一键启动 (推荐)
                   docker run -d \\
                     --name redis-stack \\
                     -p 6379:6379 \\
                     -p 8001:8001 \\
                     redis/redis-stack:latest

                   # 端口说明:
                   # 6379 - Redis 服务端口
                   # 8001 - RedisInsight Web 管理界面

                2. 验证 RediSearch 模块已加载

                   redis-cli
                   > MODULE LIST
                   # 应该看到 "search" 模块

                3. 设置环境变量 (可选)

                   export REDIS_HOST=localhost
                   export REDIS_PORT=6379
                   export REDIS_PASSWORD=your-password

                ─────────────────────────────────────────────────────────────
                """);
    }

    private static void printSummary() {
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【总结】Redis 向量存储");
        System.out.println("═".repeat(60));

        System.out.println("""

                ┌────────────────────────────────────────────────────────────┐
                │                  Redis 向量存储选型指南                     │
                ├────────────────────────────────────────────────────────────┤
                │  数据规模: < 100万条向量                                    │
                │  查询延迟: < 10ms                                          │
                │  内存需求: 约 1GB / 10万条 (1536维)                         │
                ├────────────────────────────────────────────────────────────┤
                │  ✅ 适合场景:                                               │
                │     - 中小规模知识库                                        │
                │     - 已有 Redis 基础设施                                   │
                │     - 需要快速响应                                          │
                │     - 需要元数据过滤                                        │
                ├────────────────────────────────────────────────────────────┤
                │  ❌ 不适合场景:                                             │
                │     - 超大规模数据 (> 100万)                                │
                │     - 内存受限环境                                          │
                │     - 需要复杂的向量运算                                    │
                └────────────────────────────────────────────────────────────┘

                下一步: 了解 PostgreSQL 向量存储 → PostgresVectorStoreDemo.java
                """);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
