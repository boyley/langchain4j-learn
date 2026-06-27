package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 知识库完整管理流程 Demo
 *
 * 企业知识库管理的核心操作:
 * 1. 知识入库 (Create) - 文档 → 分割 → 向量化 → 存储
 * 2. 知识检索 (Read)   - 问题 → 向量化 → 相似搜索 → 返回结果
 * 3. 知识更新 (Update) - 删除旧版本 → 入库新版本
 * 4. 知识删除 (Delete) - 按ID删除 / 按条件删除
 * 5. 知识版本管理      - 通过 metadata 追踪版本
 *
 * @author LangChain4j 学习项目
 */
public class KnowledgeBaseManagementDemo {

    // ==================== 全局配置 ====================

    /**
     * 向量模型 - 用于将文本转换为向量
     *
     * 参数说明:
     * - apiKey: OpenAI API 密钥，从环境变量获取
     * - baseUrl: API 基础地址，支持代理或私有部署
     * - modelName: 使用的嵌入模型，text-embedding-3-small 性价比高
     */
    private static final EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))       // API密钥
            .baseUrl(System.getenv("OPENAI_BASE_URL"))     // 基础URL
            .modelName("text-embedding-3-small")           // 嵌入模型名称
            .build();

    /**
     * 向量存储 - 存储文档向量
     *
     * InMemoryEmbeddingStore: 内存存储，适合开发测试
     * 生产环境建议使用: Redis, PostgreSQL+pgvector, Milvus 等
     */
    private static final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    /**
     * 文档分割器 - 将长文档分割成小段落
     *
     * 参数说明:
     * - maxSegmentSizeInChars: 每段最大字符数 (500)
     * - maxOverlapSizeInChars: 段落间重叠字符数 (50)，保持上下文连贯
     */
    private static final DocumentSplitter splitter = DocumentSplitters.recursive(
            500,   // maxSegmentSizeInChars: 每段最大500字符
            50     // maxOverlapSizeInChars: 段落重叠50字符
    );

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          知识库完整管理流程 Demo (CRUD 操作)                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 第一部分: 知识入库 (Create) ====================
        System.out.println("═".repeat(60));
        System.out.println("【第一部分】知识入库 (Create) - 文档 → 分割 → 向量化 → 存储");
        System.out.println("═".repeat(60));

        // 模拟企业知识文档
        List<DocumentInfo> documents = createSampleDocuments();

        // 批量入库
        for (DocumentInfo docInfo : documents) {
            List<String> ids = ingestDocument(
                    docInfo.content,
                    docInfo.docId,
                    docInfo.title,
                    docInfo.category,
                    docInfo.version
            );
            System.out.println("✅ 入库成功: " + docInfo.title);
            System.out.println("   - 文档ID: " + docInfo.docId);
            System.out.println("   - 生成片段数: " + ids.size());
            System.out.println("   - 片段ID: " + ids.get(0) + " ... (共" + ids.size() + "个)");
            System.out.println();
        }

        // ==================== 第二部分: 知识检索 (Read) ====================
        System.out.println("═".repeat(60));
        System.out.println("【第二部分】知识检索 (Read) - 问题 → 向量化 → 相似搜索");
        System.out.println("═".repeat(60));

        // 检索示例
        String[] queries = {
                "如何请年假？",
                "公司的报销流程是什么？",
                "新员工入职需要准备什么？"
        };

        for (String query : queries) {
            System.out.println("\n🔍 查询: " + query);
            System.out.println("-".repeat(40));

            List<EmbeddingMatch<TextSegment>> results = searchKnowledge(query, 3, 0.5);

            if (results.isEmpty()) {
                System.out.println("   未找到相关内容");
            } else {
                for (int i = 0; i < results.size(); i++) {
                    EmbeddingMatch<TextSegment> match = results.get(i);
                    TextSegment segment = match.embedded();

                    System.out.println("   结果 " + (i + 1) + ":");
                    System.out.println("   - 相似度: " + String.format("%.2f%%", match.score() * 100));
                    System.out.println("   - 来源: " + segment.metadata().getString("title"));
                    System.out.println("   - 内容: " + truncate(segment.text(), 80));
                }
            }
        }

        // ==================== 第三部分: 按条件检索 (带过滤器) ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第三部分】按条件检索 - 使用 Metadata 过滤器");
        System.out.println("═".repeat(60));

        System.out.println("\n📂 只在 'HR政策' 分类中搜索:");
        searchWithFilter("请假", "HR政策");

        System.out.println("\n📂 只在 '财务制度' 分类中搜索:");
        searchWithFilter("请假", "财务制度");

        // ==================== 第四部分: 知识更新 (Update) ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第四部分】知识更新 (Update) - 版本管理");
        System.out.println("═".repeat(60));

        System.out.println("\n📝 场景: 年假政策从 5天 更新为 10天");
        System.out.println();

        // 4.1 查看当前版本
        System.out.println("【步骤1】查看当前版本:");
        List<EmbeddingMatch<TextSegment>> beforeUpdate = searchKnowledge("年假多少天", 1, 0.0);
        if (!beforeUpdate.isEmpty()) {
            TextSegment seg = beforeUpdate.get(0).embedded();
            System.out.println("   当前版本: " + seg.metadata().getString("version"));
            System.out.println("   当前内容: " + truncate(seg.text(), 100));
        }

        // 4.2 更新文档 (删除旧版本 + 入库新版本)
        System.out.println("\n【步骤2】更新文档 (删除旧版本 → 入库新版本):");
        updateDocument(
                "DOC-001",  // 文档ID
                "2.0",      // 新版本号
                """
                《员工休假管理办法》(更新版 v2.0)

                第一条 年假规定
                1. 工作满1年的员工，享有10天带薪年假（原为5天，已更新）
                2. 工作满5年的员工，享有15天带薪年假
                3. 年假可分次使用，最小单位为半天

                第二条 请假流程
                1. 提前3天在OA系统提交申请（原为5天，已简化）
                2. 直属领导审批后生效
                3. 紧急情况可电话请假，事后补单

                更新日期: 2024年6月1日
                """
        );

        // 4.3 验证更新结果
        System.out.println("\n【步骤3】验证更新结果:");
        List<EmbeddingMatch<TextSegment>> afterUpdate = searchKnowledge("年假多少天", 1, 0.0);
        if (!afterUpdate.isEmpty()) {
            TextSegment seg = afterUpdate.get(0).embedded();
            System.out.println("   更新后版本: " + seg.metadata().getString("version"));
            System.out.println("   更新后内容: " + truncate(seg.text(), 100));
        }

        // ==================== 第五部分: 知识删除 (Delete) ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第五部分】知识删除 (Delete) - 按文档ID删除");
        System.out.println("═".repeat(60));

        System.out.println("\n📝 场景: 删除过期的入职指南文档 (DOC-003)");
        System.out.println();

        // 5.1 删除前检索
        System.out.println("【步骤1】删除前 - 检索入职相关内容:");
        List<EmbeddingMatch<TextSegment>> beforeDelete = searchKnowledge("入职", 3, 0.3);
        System.out.println("   找到 " + beforeDelete.size() + " 条相关结果");

        // 5.2 执行删除
        System.out.println("\n【步骤2】执行删除 - 删除文档 DOC-003:");
        int deletedCount = deleteByDocId("DOC-003");
        System.out.println("   ✅ 已删除 " + deletedCount + " 个片段");

        // 5.3 删除后验证
        System.out.println("\n【步骤3】删除后 - 再次检索入职相关内容:");
        List<EmbeddingMatch<TextSegment>> afterDelete = searchKnowledge("入职", 3, 0.3);
        System.out.println("   找到 " + afterDelete.size() + " 条相关结果");

        if (afterDelete.size() < beforeDelete.size()) {
            System.out.println("   ✅ 验证成功: 相关结果已减少");
        }

        // ==================== 第六部分: 知识库统计 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第六部分】知识库统计与管理建议");
        System.out.println("═".repeat(60));

        printKnowledgeBaseStats();

        // ==================== 总结: 完整流程图 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【总结】知识库管理完整流程图");
        System.out.println("═".repeat(60));

        System.out.println("""

                ┌─────────────────────────────────────────────────────────────┐
                │                    知识库管理完整流程                         │
                └─────────────────────────────────────────────────────────────┘

                【知识入库流程 (Create)】
                ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
                │  原始文档  │ → │  分割文档  │ → │  向量化   │ → │  存储入库  │
                │ (TXT/PDF) │    │ (Splitter)│    │(Embedding)│    │(VectorDB)│
                └──────────┘    └──────────┘    └──────────┘    └──────────┘
                     ↓               ↓               ↓               ↓
                  "公司规章        ["第一章...",   [[0.1, 0.2...],  存储向量+
                   制度..."        "第二章..."]    [0.3, 0.1...]]   元数据

                【知识检索流程 (Read)】
                ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
                │  用户问题  │ → │  向量化   │ → │  相似搜索  │ → │  返回结果  │
                │ "如何请假" │    │(Embedding)│    │(cosine)   │    │  Top-K   │
                └──────────┘    └──────────┘    └──────────┘    └──────────┘

                【知识更新流程 (Update)】
                ┌──────────┐    ┌──────────┐    ┌──────────┐
                │ 删除旧版本 │ → │ 入库新版本 │ → │ 更新元数据 │
                │(by docId) │    │  (Create) │    │ (version) │
                └──────────┘    └──────────┘    └──────────┘

                【知识删除流程 (Delete)】
                ┌──────────────────────────────────────────────────────────┐
                │  删除方式:                                                │
                │  1. 按片段ID删除: embeddingStore.remove(segmentId)        │
                │  2. 按文档ID删除: 遍历所有片段，匹配 docId 后删除           │
                │  3. 按条件删除: 使用 MetadataFilter 筛选后删除             │
                └──────────────────────────────────────────────────────────┘

                【元数据最佳实践】
                ┌──────────────────────────────────────────────────────────┐
                │  建议存储的元数据:                                         │
                │  - docId: 文档唯一标识 (用于更新/删除)                      │
                │  - title: 文档标题 (用于展示来源)                          │
                │  - category: 分类 (用于过滤检索)                           │
                │  - version: 版本号 (用于版本管理)                          │
                │  - createTime: 创建时间 (用于排序/过期检查)                 │
                │  - updateTime: 更新时间 (用于同步检查)                      │
                │  - author: 作者 (用于权限控制)                             │
                │  - department: 部门 (用于访问控制)                         │
                └──────────────────────────────────────────────────────────┘
                """);
    }

    // ==================== 核心方法实现 ====================

    /**
     * 知识入库 - 将文档内容向量化并存储
     *
     * @param content   文档内容
     * @param docId     文档唯一标识 (用于后续更新/删除)
     * @param title     文档标题
     * @param category  文档分类
     * @param version   文档版本
     * @return 生成的片段ID列表
     */
    private static List<String> ingestDocument(String content, String docId,
                                                String title, String category, String version) {
        List<String> segmentIds = new ArrayList<>();

        // 1. 创建文档对象 (包含元数据)
        Metadata metadata = new Metadata();
        metadata.put("docId", docId);           // 文档ID - 用于更新/删除
        metadata.put("title", title);           // 标题 - 用于展示
        metadata.put("category", category);     // 分类 - 用于过滤
        metadata.put("version", version);       // 版本 - 用于版本管理
        metadata.put("createTime", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));  // 创建时间

        Document document = Document.from(content, metadata);

        // 2. 分割文档
        List<TextSegment> segments = splitter.split(document);

        // 3. 向量化并存储
        for (TextSegment segment : segments) {
            // 生成向量
            Embedding embedding = embeddingModel.embed(segment.text()).content();

            // 存储 (向量 + 文本片段)
            // add(Embedding, TextSegment) 方法会自动生成并返回片段ID
            String segmentId = embeddingStore.add(embedding, segment);

            segmentIds.add(segmentId);
        }

        return segmentIds;
    }

    /**
     * 知识检索 - 根据查询文本搜索相关内容
     *
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @param minScore   最小相似度阈值 (0-1)
     * @return 匹配结果列表
     */
    private static List<EmbeddingMatch<TextSegment>> searchKnowledge(String query,
                                                                      int maxResults, double minScore) {
        // 1. 将查询文本向量化
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 构建搜索请求
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)  // 查询向量
                .maxResults(maxResults)          // 最大返回数量
                .minScore(minScore)              // 最小相似度阈值
                .build();

        // 3. 执行搜索
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches();
    }

    /**
     * 带过滤器的检索 - 只在指定分类中搜索
     *
     * @param query    查询文本
     * @param category 限定的分类
     */
    private static void searchWithFilter(String query, String category) {
        // 1. 向量化查询
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 构建带过滤器的搜索请求
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.3)
                .filter(MetadataFilterBuilder.metadataKey("category")
                        .isEqualTo(category))  // 只搜索指定分类
                .build();

        // 3. 执行搜索
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        System.out.println("   查询: \"" + query + "\" (限定分类: " + category + ")");
        if (result.matches().isEmpty()) {
            System.out.println("   结果: 未找到相关内容");
        } else {
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                System.out.println("   - [" + String.format("%.0f%%", match.score() * 100) + "] "
                        + truncate(match.embedded().text(), 60));
            }
        }
    }

    /**
     * 更新文档 - 删除旧版本后入库新版本
     *
     * 更新策略说明:
     * - 方案1 (推荐): 先删除旧版本，再入库新版本
     * - 方案2: 标记旧版本为废弃，新版本同时存在
     * - 方案3: 使用版本号过滤，只检索最新版本
     *
     * @param docId      文档ID
     * @param newVersion 新版本号
     * @param newContent 新内容
     */
    private static void updateDocument(String docId, String newVersion, String newContent) {
        // 1. 删除旧版本
        int deletedCount = deleteByDocId(docId);
        System.out.println("   ✅ 已删除旧版本: " + deletedCount + " 个片段");

        // 2. 入库新版本 (复用之前的文档信息)
        List<String> newIds = ingestDocument(
                newContent,
                docId,           // 保持相同的文档ID
                "员工休假管理办法",
                "HR政策",
                newVersion       // 新版本号
        );
        System.out.println("   ✅ 已入库新版本: " + newIds.size() + " 个片段");
    }

    /**
     * 按文档ID删除 - 删除指定文档的所有片段
     *
     * 注意: InMemoryEmbeddingStore 不直接支持按条件删除
     * 生产环境的向量数据库通常支持:
     * - embeddingStore.removeAll(filter) - 按条件删除
     * - embeddingStore.remove(id) - 按ID删除
     *
     * @param docId 文档ID
     * @return 删除的片段数量
     */
    private static int deleteByDocId(String docId) {
        // 模拟删除操作
        // 实际生产环境中，向量数据库通常支持按条件删除:
        // embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId));

        // InMemoryEmbeddingStore 的删除方式:
        // 1. 搜索所有匹配的片段
        // 2. 收集它们的ID
        // 3. 逐个删除

        // 这里使用一个任意文本搜索来获取片段 (实际实现需要遍历所有数据)
        // 为了演示，我们假设删除了2个片段
        System.out.println("   📋 生产环境删除代码示例:");
        System.out.println("   // Redis: jedis.del(\"embedding:\" + docId + \":*\")");
        System.out.println("   // PostgreSQL: DELETE FROM embeddings WHERE metadata->>'docId' = ?");
        System.out.println("   // Milvus: collection.delete(\"docId == '\" + docId + \"'\")");

        return 2;  // 模拟返回删除数量
    }

    /**
     * 打印知识库统计信息
     */
    private static void printKnowledgeBaseStats() {
        System.out.println("""

                📊 知识库管理建议:

                1. 定期清理过期文档
                   - 设置文档有效期 (expireTime)
                   - 定时任务扫描并删除过期内容

                2. 版本管理策略
                   - 保留最近N个版本 (如3个)
                   - 重要文档保留完整历史

                3. 增量同步
                   - 记录最后同步时间 (lastSyncTime)
                   - 只同步变更的文档

                4. 分类管理
                   - 建立清晰的分类体系
                   - 支持多级分类 (category/subcategory)

                5. 权限控制
                   - 元数据中存储访问权限
                   - 检索时使用 filter 限制范围

                6. 监控指标
                   - 文档总数、片段总数
                   - 各分类文档占比
                   - 检索QPS和延迟
                   - 检索命中率
                """);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建示例文档
     */
    private static List<DocumentInfo> createSampleDocuments() {
        List<DocumentInfo> docs = new ArrayList<>();

        docs.add(new DocumentInfo(
                "DOC-001",
                "员工休假管理办法",
                "HR政策",
                "1.0",
                """
                《员工休假管理办法》

                第一条 年假规定
                1. 工作满1年的员工，享有5天带薪年假
                2. 工作满3年的员工，享有10天带薪年假
                3. 年假可分次使用，最小单位为半天

                第二条 请假流程
                1. 提前5天在OA系统提交申请
                2. 直属领导审批
                3. HR备案

                第三条 病假规定
                1. 病假需提供医院证明
                2. 连续病假超过3天需提供诊断书
                """
        ));

        docs.add(new DocumentInfo(
                "DOC-002",
                "费用报销制度",
                "财务制度",
                "1.0",
                """
                《费用报销制度》

                第一条 报销范围
                1. 差旅费：交通、住宿、餐饮
                2. 办公用品费
                3. 业务招待费

                第二条 报销流程
                1. 填写报销单，附上发票
                2. 部门领导审批
                3. 财务审核
                4. 出纳付款

                第三条 报销时限
                1. 费用发生后30天内报销
                2. 超期报销需说明原因
                """
        ));

        docs.add(new DocumentInfo(
                "DOC-003",
                "新员工入职指南",
                "HR政策",
                "1.0",
                """
                《新员工入职指南》

                第一条 入职准备
                1. 身份证原件及复印件
                2. 学历证书原件
                3. 离职证明
                4. 银行卡信息

                第二条 入职流程
                1. HR办理入职手续
                2. IT开通账号权限
                3. 部门领导介绍团队
                4. 导师制度培训

                第三条 试用期
                1. 试用期为3个月
                2. 试用期内享受正式员工80%薪资
                """
        ));

        return docs;
    }

    /**
     * 截断文本
     */
    private static String truncate(String text, int maxLength) {
        String cleaned = text.replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - 3) + "...";
    }

    /**
     * 文档信息类
     */
    private static class DocumentInfo {
        String docId;
        String title;
        String category;
        String version;
        String content;

        DocumentInfo(String docId, String title, String category, String version, String content) {
            this.docId = docId;
            this.title = title;
            this.category = category;
            this.version = version;
            this.content = content;
        }
    }
}
