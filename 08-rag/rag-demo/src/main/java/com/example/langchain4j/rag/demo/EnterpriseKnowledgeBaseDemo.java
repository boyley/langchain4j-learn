package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
 * 企业知识库 Demo - 多部门 + 权限控制
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 企业知识库架构
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *                        ┌─────────────────────────────────────┐
 *                        │          企业知识库                  │
 *                        └─────────────────────────────────────┘
 *                                       │
 *          ┌────────────────────────────┼────────────────────────────┐
 *          │                            │                            │
 *    ┌─────┴─────┐               ┌──────┴──────┐              ┌──────┴──────┐
 *    │  HR 部门   │               │  技术部门   │              │  财务部门   │
 *    │ (公开)    │               │ (内部)     │              │ (机密)     │
 *    └───────────┘               └─────────────┘              └─────────────┘
 *         │                            │                            │
 *    ┌────┴────┐                 ┌─────┴─────┐                ┌─────┴─────┐
 *    │员工手册 │                 │技术文档   │                │财务报表   │
 *    │入职流程 │                 │架构设计   │                │预算方案   │
 *    │福利政策 │                 │代码规范   │                │薪资数据   │
 *    └─────────┘                 └───────────┘                └───────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 权限控制方案
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 每个文档都有 Metadata（元数据）：
 *   - department: 所属部门
 *   - accessLevel: 访问级别 (public/internal/confidential)
 *   - tags: 标签
 *
 * 每个用户都有权限：
 *   - 所属部门
 *   - 可访问的其他部门
 *   - 最高访问级别
 *
 * 搜索时，根据用户权限过滤文档！
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 技术实现
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. 存储文档时，附加 Metadata
 * 2. 搜索时，使用 Filter 过滤
 * 3. 只返回用户有权限访问的文档
 *
 * 生产环境存储选择：
 * - Milvus: 高性能向量数据库，支持过滤
 * - Pinecone: 托管向量数据库
 * - Elasticsearch: 支持向量搜索 + 过滤
 * - PostgreSQL + pgvector: 关系数据库 + 向量扩展
 * - Redis: 支持向量搜索
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class EnterpriseKnowledgeBaseDemo {

    // 访问级别常量
    private static final String ACCESS_PUBLIC = "public";           // 公开：所有员工可见
    private static final String ACCESS_INTERNAL = "internal";       // 内部：部门内可见
    private static final String ACCESS_CONFIDENTIAL = "confidential"; // 机密：仅授权人员

    // 模拟用户信息
    static class User {
        String name;
        String department;              // 所属部门
        Set<String> accessDepartments;  // 可访问的部门
        String maxAccessLevel;          // 最高访问级别

        User(String name, String department, Set<String> accessDepartments, String maxAccessLevel) {
            this.name = name;
            this.department = department;
            this.accessDepartments = accessDepartments;
            this.maxAccessLevel = maxAccessLevel;
        }
    }

    public static void main(String[] args) {
        /**
         * 初始化模型
         *
         * OpenAiEmbeddingModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                           │
         * │ apiKey          │ API 密钥                                           │
         * │ modelName       │ 向量模型名称 (text-embedding-3-small: 1536 维)     │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址
                .apiKey("demo")                                    // API 密钥
                .modelName("text-embedding-3-small")               // 向量模型
                .build();

        /**
         * OpenAiChatModel.builder() 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ baseUrl         │ API 地址                                           │
         * │ apiKey          │ API 密钥                                           │
         * │ modelName       │ 聊天模型名称                                       │
         * └─────────────────┴────────────────────────────────────────────────────┘
         */
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")  // API 地址
                .apiKey("demo")                                    // API 密钥
                .modelName("gpt-4o-mini")                          // 聊天模型
                .build();

        /**
         * EmbeddingStore - 向量存储
         *
         * 生产环境存储选择：
         * ┌─────────────────────┬───────────────────────────────────────────────┐
         * │ 存储类型            │ 说明                                          │
         * ├─────────────────────┼───────────────────────────────────────────────┤
         * │ InMemoryEmbedding   │ 内存存储，重启丢失，适合测试/Demo              │
         * │ MilvusEmbedding     │ Milvus 向量数据库，高性能，生产推荐            │
         * │ PineconeEmbedding   │ Pinecone 托管服务                             │
         * │ ElasticsearchEmb    │ ES 向量搜索                                   │
         * │ PgVectorEmbedding   │ PostgreSQL + pgvector 扩展                    │
         * │ RedisEmbedding      │ Redis Stack 向量搜索                          │
         * └─────────────────────┴───────────────────────────────────────────────┘
         */
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        System.out.println("═".repeat(60));
        System.out.println("       企业知识库 Demo - 多部门 + 权限控制");
        System.out.println("═".repeat(60));

        // ═══════════════════════════════════════════════════════════════
        // 第一步：构建企业知识库（模拟各部门文档）
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第一步】构建企业知识库\n");
        buildKnowledgeBase(store, embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第二步：模拟不同权限用户的搜索
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第二步】测试不同用户的访问权限\n");

        // 用户1：普通员工（只能看公开内容）
        User normalEmployee = new User(
            "张三",
            "市场部",
            Set.of("市场部"),
            ACCESS_PUBLIC
        );

        // 用户2：技术部员工（可看技术部内部文档）
        User techEmployee = new User(
            "李四",
            "技术部",
            Set.of("技术部", "HR"),
            ACCESS_INTERNAL
        );

        // 用户3：财务总监（可看所有部门机密文档）
        User cfo = new User(
            "王总",
            "财务部",
            Set.of("财务部", "技术部", "HR"),
            ACCESS_CONFIDENTIAL
        );

        // 测试查询
        String query = "公司的薪资福利政策是什么";

        System.out.println("─".repeat(60));
        System.out.println("搜索问题: \"" + query + "\"");
        System.out.println("─".repeat(60));

        // 普通员工搜索
        System.out.println("\n>>> 用户: " + normalEmployee.name + " (市场部普通员工)");
        System.out.println("    权限: 只能访问公开文档");
        searchWithPermission(store, embeddingModel, query, normalEmployee);

        // 技术部员工搜索
        System.out.println("\n>>> 用户: " + techEmployee.name + " (技术部员工)");
        System.out.println("    权限: 可访问技术部和HR的内部文档");
        searchWithPermission(store, embeddingModel, query, techEmployee);

        // 财务总监搜索
        System.out.println("\n>>> 用户: " + cfo.name + " (财务总监)");
        System.out.println("    权限: 可访问所有部门的机密文档");
        searchWithPermission(store, embeddingModel, query, cfo);

        // ═══════════════════════════════════════════════════════════════
        // 第三步：结合 AI 生成回答
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【第三步】结合 AI 生成回答\n");
        System.out.println("─".repeat(60));

        System.out.println(">>> 财务总监询问: \"公司的薪资结构是怎样的？\"");
        String answer = askWithRAG(store, embeddingModel, chatModel,
                                   "公司的薪资结构是怎样的", cfo);
        System.out.println("\nAI 回答: " + answer);

        // ═══════════════════════════════════════════════════════════════
        // 架构总结
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n" + "═".repeat(60));
        System.out.println("【架构总结】企业知识库实现方案");
        System.out.println("═".repeat(60));
        System.out.println("""

            ┌──────────────────────────────────────────────────────────┐
            │                    企业知识库架构                         │
            ├──────────────────────────────────────────────────────────┤
            │                                                          │
            │   用户请求                                                │
            │      │                                                   │
            │      ▼                                                   │
            │   ┌─────────────┐                                        │
            │   │ 权限校验    │ ← 获取用户部门、角色、访问级别          │
            │   └──────┬──────┘                                        │
            │          │                                               │
            │          ▼                                               │
            │   ┌─────────────┐    ┌─────────────────────────────┐    │
            │   │ 向量搜索    │───▶│ 向量数据库                   │    │
            │   │ + 权限过滤  │    │ (Milvus/Pinecone/ES/PG...)  │    │
            │   └──────┬──────┘    │                             │    │
            │          │           │ 存储:                       │    │
            │          │           │ - 文档内容                   │    │
            │          │           │ - 向量 (Embedding)          │    │
            │          │           │ - 元数据 (部门/权限/标签)    │    │
            │          │           └─────────────────────────────┘    │
            │          ▼                                               │
            │   ┌─────────────┐                                        │
            │   │ LLM 生成    │ ← 基于检索到的文档生成回答             │
            │   └──────┬──────┘                                        │
            │          │                                               │
            │          ▼                                               │
            │       回答用户                                            │
            │                                                          │
            └──────────────────────────────────────────────────────────┘

            关键点：
            1. 文档存储时附加 Metadata（部门、权限级别、标签等）
            2. 搜索时根据用户权限构建 Filter
            3. Filter 过滤后只返回用户有权访问的文档
            4. 基于过滤后的文档让 AI 生成回答

            生产环境建议：
            - 向量数据库: Milvus、Pinecone、Elasticsearch
            - 权限系统: 集成公司 LDAP/SSO
            - 审计日志: 记录谁访问了什么
            """);
    }

    /**
     * 构建企业知识库 - 添加各部门文档
     */
    private static void buildKnowledgeBase(EmbeddingStore<TextSegment> store,
                                           EmbeddingModel embeddingModel) {
        // HR 部门文档
        addDocument(store, embeddingModel,
            "员工入职流程：1.提交资料 2.签订合同 3.领取工卡 4.参加培训",
            "HR", ACCESS_PUBLIC, "入职,流程");

        addDocument(store, embeddingModel,
            "公司福利：五险一金、年终奖、带薪年假15天、免费午餐、健身房",
            "HR", ACCESS_PUBLIC, "福利,政策");

        addDocument(store, embeddingModel,
            "内部晋升制度：每年两次晋升窗口，需满足绩效要求和工作年限",
            "HR", ACCESS_INTERNAL, "晋升,制度");

        addDocument(store, embeddingModel,
            "薪资结构（机密）：基本工资+绩效奖金+股权激励，具体比例见附件",
            "HR", ACCESS_CONFIDENTIAL, "薪资,机密");

        // 技术部文档
        addDocument(store, embeddingModel,
            "技术栈介绍：Java 17 + Spring Boot 3 + MySQL + Redis + Kubernetes",
            "技术部", ACCESS_PUBLIC, "技术,架构");

        addDocument(store, embeddingModel,
            "代码规范：遵循阿里巴巴Java开发手册，PR必须有2人Review",
            "技术部", ACCESS_INTERNAL, "规范,开发");

        addDocument(store, embeddingModel,
            "系统架构图（内部）：微服务架构，包含用户服务、订单服务、支付服务...",
            "技术部", ACCESS_INTERNAL, "架构,设计");

        addDocument(store, embeddingModel,
            "核心算法说明（机密）：推荐算法使用深度学习模型，训练数据来源...",
            "技术部", ACCESS_CONFIDENTIAL, "算法,机密");

        // 财务部文档
        addDocument(store, embeddingModel,
            "报销流程：钉钉提交申请，附发票照片，3个工作日内审批",
            "财务部", ACCESS_PUBLIC, "报销,流程");

        addDocument(store, embeddingModel,
            "部门预算（内部）：技术部年度预算500万，市场部300万，HR部100万",
            "财务部", ACCESS_INTERNAL, "预算,财务");

        addDocument(store, embeddingModel,
            "公司估值（机密）：最新一轮融资估值10亿美元，投资方包括...",
            "财务部", ACCESS_CONFIDENTIAL, "估值,融资,机密");

        System.out.println("    ✓ HR 部门: 4 篇文档（公开2/内部1/机密1）");
        System.out.println("    ✓ 技术部门: 4 篇文档（公开1/内部2/机密1）");
        System.out.println("    ✓ 财务部门: 3 篇文档（公开1/内部1/机密1）");
        System.out.println("    共计 11 篇文档已入库");
    }

    /**
     * 添加单个文档到知识库
     */
    private static void addDocument(EmbeddingStore<TextSegment> store,
                                    EmbeddingModel embeddingModel,
                                    String content,
                                    String department,
                                    String accessLevel,
                                    String tags) {
        // 构建元数据
        Metadata metadata = Metadata.from(Map.of(
            "department", department,
            "accessLevel", accessLevel,
            "tags", tags
        ));

        // 创建文本段
        TextSegment segment = TextSegment.from(content, metadata);

        // 生成向量并存储
        Embedding embedding = embeddingModel.embed(content).content();
        store.add(embedding, segment);
    }

    /**
     * 根据用户权限搜索
     *
     * 搜索流程：
     * 1. 将查询文本转为向量
     * 2. 根据用户权限构建过滤器
     * 3. 在向量库中搜索，同时应用过滤器
     * 4. 返回用户有权限访问的相关文档
     */
    private static void searchWithPermission(EmbeddingStore<TextSegment> store,
                                             EmbeddingModel embeddingModel,
                                             String query,
                                             User user) {
        // 生成查询向量 - 将问题转为向量用于相似度计算
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 构建权限过滤器 - 根据用户权限过滤文档
        Filter filter = buildPermissionFilter(user);

        /**
         * EmbeddingSearchRequest.builder() - 构建搜索请求
         *
         * 参数说明：
         * ┌─────────────────┬────────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                               │
         * ├─────────────────┼────────────────────────────────────────────────────┤
         * │ queryEmbedding  │ 必须：查询向量，由问题文本通过 EmbeddingModel 生成 │
         * │ filter          │ 可选：元数据过滤器，按条件过滤文档                  │
         * │                 │ 支持 AND/OR 组合多个条件                           │
         * │ maxResults      │ 可选：最多返回几个结果，默认 3                      │
         * │ minScore        │ 可选：最低相似度阈值 (0-1)，过滤低相关结果          │
         * └─────────────────┴────────────────────────────────────────────────────┘
         *
         * 权限过滤原理：
         *   filter = department IN [用户可访问部门]
         *            AND accessLevel IN [用户可访问级别]
         *
         * 执行结果：
         *   只返回同时满足"语义相似"和"权限允许"的文档
         */
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)  // 必须：查询向量
                .filter(filter)                   // 可选：权限过滤器
                .maxResults(3)                    // 可选：最多返回 3 个结果
                // .minScore(0.5)                 // 可选：相似度 >= 0.5 才返回
                .build();

        // 执行搜索 - 向量相似度 + 元数据过滤
        EmbeddingSearchResult<TextSegment> result = store.search(request);

        // 显示结果
        if (result.matches().isEmpty()) {
            System.out.println("    结果: 未找到有权限访问的相关文档");
        } else {
            System.out.println("    找到 " + result.matches().size() + " 个相关文档:");
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                TextSegment segment = match.embedded();
                String dept = segment.metadata().getString("department");
                String level = segment.metadata().getString("accessLevel");
                System.out.printf("    - [%s/%s] %s (相似度: %.2f)%n",
                    dept, level,
                    truncate(segment.text(), 40),
                    match.score());
            }
        }
    }

    /**
     * 构建权限过滤器
     *
     * 过滤逻辑：
     * 1. 部门必须在用户可访问的部门列表中
     * 2. 访问级别必须 <= 用户的最高访问级别
     *
     * MetadataFilterBuilder - 元数据过滤器构建器
     * ═══════════════════════════════════════════════════════════════════════════
     *
     * 支持的比较操作：
     * ┌───────────────────────┬──────────────────────────────────────────────┐
     * │ 方法                  │ 说明                                         │
     * ├───────────────────────┼──────────────────────────────────────────────┤
     * │ isEqualTo(value)      │ 精确匹配: department = "HR"                  │
     * │ isNotEqualTo(value)   │ 不等于: status != "deleted"                  │
     * │ isIn(collection)      │ 包含于: department IN ["HR", "技术部"]        │
     * │ isNotIn(collection)   │ 不包含: level NOT IN ["secret"]              │
     * │ isGreaterThan(value)  │ 大于: priority > 5                           │
     * │ isLessThan(value)     │ 小于: score < 0.5                            │
     * │ isGreaterThanOrEqual  │ 大于等于: level >= 2                          │
     * │ isLessThanOrEqual     │ 小于等于: year <= 2024                        │
     * └───────────────────────┴──────────────────────────────────────────────┘
     *
     * 逻辑组合：
     * ┌───────────────────────┬──────────────────────────────────────────────┐
     * │ 方法                  │ 说明                                         │
     * ├───────────────────────┼──────────────────────────────────────────────┤
     * │ filter1.and(filter2)  │ 同时满足两个条件                              │
     * │ filter1.or(filter2)   │ 满足其中一个条件                              │
     * │ filter.not()          │ 取反                                         │
     * └───────────────────────┴──────────────────────────────────────────────┘
     *
     * 示例:
     *   // 简单过滤: 部门 = HR
     *   MetadataFilterBuilder.metadataKey("department").isEqualTo("HR")
     *
     *   // 组合过滤: 部门 IN [HR, 技术] AND 级别 = public
     *   MetadataFilterBuilder.metadataKey("department").isIn(Set.of("HR", "技术"))
     *       .and(MetadataFilterBuilder.metadataKey("accessLevel").isEqualTo("public"))
     */
    private static Filter buildPermissionFilter(User user) {
        // 根据用户最高权限确定可访问的级别列表
        List<String> allowedLevels = new ArrayList<>();
        allowedLevels.add(ACCESS_PUBLIC);  // 所有人都能看公开
        if (user.maxAccessLevel.equals(ACCESS_INTERNAL) ||
            user.maxAccessLevel.equals(ACCESS_CONFIDENTIAL)) {
            allowedLevels.add(ACCESS_INTERNAL);
        }
        if (user.maxAccessLevel.equals(ACCESS_CONFIDENTIAL)) {
            allowedLevels.add(ACCESS_CONFIDENTIAL);
        }

        // 构建过滤器:
        // department IN [用户可访问部门] AND accessLevel IN [用户可访问级别]
        // 只有同时满足两个条件的文档才会被返回
        return MetadataFilterBuilder.metadataKey("department")
                    .isIn(user.accessDepartments)           // 部门过滤
                .and(MetadataFilterBuilder.metadataKey("accessLevel")
                    .isIn(allowedLevels));                  // 权限级别过滤
    }

    /**
     * RAG 问答：搜索 + AI 生成
     */
    private static String askWithRAG(EmbeddingStore<TextSegment> store,
                                     EmbeddingModel embeddingModel,
                                     ChatLanguageModel chatModel,
                                     String question,
                                     User user) {
        // 1. 搜索相关文档
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        Filter filter = buildPermissionFilter(user);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);

        // 2. 拼接上下文
        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            context.append("- ").append(match.embedded().text()).append("\n");
        }

        // 3. 构建提示词让 AI 回答
        String prompt = String.format("""
            你是企业知识库助手。请根据以下资料回答问题。
            如果资料中没有相关信息，请说"抱歉，在您有权限访问的文档中未找到相关信息"。

            参考资料：
            %s

            问题：%s

            请简洁回答：
            """, context, question);

        return chatModel.generate(prompt);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
