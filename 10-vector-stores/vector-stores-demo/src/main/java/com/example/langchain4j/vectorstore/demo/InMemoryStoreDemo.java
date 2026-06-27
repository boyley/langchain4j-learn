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
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

/**
 * InMemory 向量存储 Demo
 *
 * 【适用场景】
 * - 开发测试环境
 * - 数据量 < 1万条
 * - 快速原型验证
 * - 不需要持久化
 *
 * 【优点】
 * - 零配置，开箱即用
 * - 速度最快（纯内存）
 * - 无外部依赖
 *
 * 【缺点】
 * - 程序重启数据丢失
 * - 大数据量会 OOM
 * - 不支持分布式
 *
 * @author LangChain4j 学习项目
 */
public class InMemoryStoreDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           InMemory 向量存储 Demo (开发测试用)                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 第一步: 创建向量模型 ====================
        System.out.println("【第一步】创建向量模型 (Embedding Model)");
        System.out.println("─".repeat(50));

        /**
         * OpenAiEmbeddingModel 参数说明:
         *
         * @param apiKey     - OpenAI API 密钥，从环境变量获取更安全
         * @param baseUrl    - API 基础地址，可以用代理地址
         * @param modelName  - 嵌入模型名称:
         *                     - text-embedding-3-small: 1536维，便宜，推荐
         *                     - text-embedding-3-large: 3072维，更精准
         *                     - text-embedding-ada-002: 1536维，旧版
         */
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))       // API密钥
                .baseUrl(System.getenv("OPENAI_BASE_URL"))     // 基础URL (可选)
                .modelName("text-embedding-3-small")           // 模型名称
                .build();

        System.out.println("✅ 向量模型创建成功");
        System.out.println("   模型: text-embedding-3-small");
        System.out.println("   维度: 1536");
        System.out.println();

        // ==================== 第二步: 创建向量存储 ====================
        System.out.println("【第二步】创建 InMemory 向量存储");
        System.out.println("─".repeat(50));

        /**
         * InMemoryEmbeddingStore 创建方式:
         *
         * 方式1: 默认构造 (推荐)
         * EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
         *
         * 方式2: 从 JSON 文件加载 (持久化)
         * InMemoryEmbeddingStore<TextSegment> store =
         *     InMemoryEmbeddingStore.fromFile("/path/to/store.json");
         */
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        System.out.println("✅ 内存向量存储创建成功");
        System.out.println("   类型: InMemoryEmbeddingStore");
        System.out.println("   特点: 数据存储在 JVM 堆内存中");
        System.out.println();

        // ==================== 第三步: 准备知识数据 ====================
        System.out.println("【第三步】准备知识数据并入库");
        System.out.println("─".repeat(50));

        // 模拟企业知识库
        String[][] knowledgeData = {
                // {文本内容, 文档ID, 分类}
                {"员工每年享有5天带薪年假，工作满3年后增加到10天。年假可以分次使用，最小单位为半天。", "DOC-001", "HR政策"},
                {"请假需要提前3天在OA系统提交申请，直属领导审批后生效。紧急情况可电话请假，事后补单。", "DOC-001", "HR政策"},
                {"差旅费报销需要在费用发生后30天内提交，需附上发票原件和行程单。", "DOC-002", "财务制度"},
                {"新员工入职需要准备身份证原件、学历证书、离职证明、银行卡信息。", "DOC-003", "HR政策"},
                {"公司提供免费午餐，用餐时间为11:30-13:00。加班到20:00后可申请晚餐补贴。", "DOC-004", "福利制度"},
        };

        System.out.println("准备入库 " + knowledgeData.length + " 条知识...\n");

        for (int i = 0; i < knowledgeData.length; i++) {
            String text = knowledgeData[i][0];
            String docId = knowledgeData[i][1];
            String category = knowledgeData[i][2];

            // 3.1 创建元数据
            Metadata metadata = new Metadata();
            metadata.put("docId", docId);         // 文档ID - 用于更新/删除
            metadata.put("category", category);   // 分类 - 用于过滤搜索

            // 3.2 创建文本片段
            TextSegment segment = TextSegment.from(text, metadata);

            // 3.3 向量化
            Embedding embedding = embeddingModel.embed(text).content();

            // 3.4 存储到向量库
            String id = embeddingStore.add(embedding, segment);

            System.out.println("✅ 入库成功 #" + (i + 1));
            System.out.println("   ID: " + id);
            System.out.println("   分类: " + category);
            System.out.println("   内容: " + truncate(text, 50));
            System.out.println();
        }

        // ==================== 第四步: 向量检索 ====================
        System.out.println("【第四步】向量相似性检索");
        System.out.println("─".repeat(50));

        String[] queries = {
                "如何请假？",
                "报销需要什么材料？",
                "新人入职要带什么？"
        };

        for (String query : queries) {
            System.out.println("\n🔍 查询: " + query);
            System.out.println("-".repeat(40));

            // 4.1 将查询文本转为向量
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 4.2 构建搜索请求
            /**
             * EmbeddingSearchRequest 参数说明:
             *
             * @param queryEmbedding - 查询向量 (必需)
             * @param maxResults     - 最大返回数量，默认 3
             * @param minScore       - 最小相似度阈值 (0-1)，低于此分数的不返回
             * @param filter         - 元数据过滤器，可按 category、docId 等过滤
             */
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)  // 查询向量
                    .maxResults(3)                   // 返回 Top 3
                    .minScore(0.5)                   // 相似度 > 50%
                    .build();

            // 4.3 执行搜索
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            // 4.4 展示结果
            if (matches.isEmpty()) {
                System.out.println("   未找到相关内容");
            } else {
                for (int i = 0; i < matches.size(); i++) {
                    EmbeddingMatch<TextSegment> match = matches.get(i);
                    TextSegment segment = match.embedded();
                    double score = match.score();

                    System.out.println("   结果 " + (i + 1) + ":");
                    System.out.println("   ├─ 相似度: " + String.format("%.1f%%", score * 100));
                    System.out.println("   ├─ 分类: " + segment.metadata().getString("category"));
                    System.out.println("   └─ 内容: " + truncate(segment.text(), 60));
                }
            }
        }

        // ==================== 第五步: 数据持久化 (可选) ====================
        System.out.println("\n");
        System.out.println("【第五步】数据持久化 (可选)");
        System.out.println("─".repeat(50));

        System.out.println("""
                InMemoryEmbeddingStore 支持序列化到 JSON 文件:

                // 保存到文件
                InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
                // ... 添加数据 ...
                store.serializeToFile("/path/to/store.json");

                // 从文件加载
                InMemoryEmbeddingStore<TextSegment> loadedStore =
                    InMemoryEmbeddingStore.fromFile("/path/to/store.json");

                注意: 这种方式适合小数据量，大数据量建议使用 Redis 或 PostgreSQL
                """);

        // ==================== 总结 ====================
        System.out.println("═".repeat(60));
        System.out.println("【总结】InMemory 向量存储");
        System.out.println("═".repeat(60));

        System.out.println("""

                ┌────────────────────────────────────────────────────────┐
                │                 InMemory 适用场景                       │
                ├────────────────────────────────────────────────────────┤
                │  ✅ 开发测试环境                                        │
                │  ✅ 快速原型验证                                        │
                │  ✅ 数据量 < 1万条                                      │
                │  ✅ 不需要持久化                                        │
                │  ✅ 单机应用                                            │
                ├────────────────────────────────────────────────────────┤
                │  ❌ 生产环境 (数据会丢失)                                │
                │  ❌ 大数据量 (会 OOM)                                   │
                │  ❌ 分布式部署 (不共享数据)                              │
                └────────────────────────────────────────────────────────┘

                下一步: 了解 Redis 向量存储 → RedisVectorStoreDemo.java
                """);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
