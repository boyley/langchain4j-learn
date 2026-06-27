package com.example.knowledge.service;

import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识检索服务
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心功能】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 提供向量相似性搜索功能:
 *
 *   用户查询                    向量化                    向量搜索
 *   "如何请假" ────→ EmbeddingModel ────→ [0.12,...] ────→ VectorStore
 *                                                              │
 *                                                              ▼
 *                                                        最相似的 K 条结果
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【搜索流程】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 查询向量化
 *    - 将用户的自然语言查询转换为向量
 *    - 使用与索引时相同的 EmbeddingModel
 *
 * 2. 向量检索
 *    - 在向量存储中执行 KNN (K-最近邻) 搜索
 *    - 返回相似度最高的 K 条结果
 *
 * 3. 结果过滤
 *    - 根据 minScore 阈值过滤低相关结果
 *    - 根据 category 字段过滤特定分类
 *
 * 4. 结果转换
 *    - 将 EmbeddingMatch 转换为业务对象 SearchResult
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【RAG 集成】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * RAG (Retrieval Augmented Generation) 流程:
 *
 *   用户问题
 *       │
 *       ▼
 *   KnowledgeSearchService.retrieveContext() ──→ 检索相关知识
 *       │
 *       ▼
 *   组装 Prompt: "根据以下知识回答问题: {context} 问题: {query}"
 *       │
 *       ▼
 *   LLM 生成回答
 *
 * retrieveContext() 方法返回格式化的上下文文本，可直接用于 LLM Prompt。
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ```java
 * // 基础搜索
 * List<SearchResult> results = searchService.search("如何请假");
 *
 * // 带参数搜索
 * List<SearchResult> results = searchService.search(
 *     "如何请假",  // 查询文本
 *     5,           // 最大返回 5 条
 *     0.7,         // 相似度至少 0.7
 *     "HR政策"     // 只搜索 HR政策 分类
 * );
 *
 * // RAG 上下文检索
 * String context = searchService.retrieveContext("如何请假", 3);
 * String prompt = "根据以下知识回答问题:\n" + context + "\n问题: 如何请假?";
 * String answer = chatModel.generate(prompt);
 * ```
 */
@Slf4j
/*
 * @Service - Spring 服务层注解
 *
 * 作用:
 * 1. 标记此类为服务层组件
 * 2. 自动注册为 Spring Bean
 * 3. 语义上表示"业务逻辑层"
 *
 * 与其他注解的区别:
 * - @Controller: 表现层 (处理 HTTP 请求)
 * - @Service: 业务逻辑层 (处理业务规则)
 * - @Repository: 数据访问层 (处理数据库操作)
 * - @Component: 通用组件 (无特定语义)
 *
 * 这四个注解功能相同，区别仅在于语义，便于代码阅读和维护。
 */
@Service
/*
 * @RequiredArgsConstructor - Lombok 注解
 *
 * 作用:
 * 1. 生成包含所有 final 字段的构造函数
 * 2. 配合 Spring 实现构造器注入
 *
 * 等价于:
 * ```java
 * public KnowledgeSearchService(
 *         EmbeddingModel embeddingModel,
 *         VectorStoreService vectorStore) {
 *     this.embeddingModel = embeddingModel;
 *     this.vectorStore = vectorStore;
 * }
 * ```
 *
 * 为什么推荐构造器注入:
 * - 字段可以声明为 final，保证不可变
 * - 依赖关系明确，便于测试
 * - 避免循环依赖 (编译时检查)
 */
@RequiredArgsConstructor
public class KnowledgeSearchService {

    // ═══════════════════════════════════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 嵌入模型
     *
     * 用于将查询文本转换为向量
     * 必须与索引时使用的模型相同，否则搜索结果不准确
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 向量存储服务
     *
     * 执行实际的向量搜索操作
     * 可能是 Redis、PostgreSQL 或其他实现
     */
    private final VectorStoreService vectorStore;

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置参数
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 默认最大返回数量
     *
     * 配置: knowledge.search.default-max-results
     * 默认: 5
     *
     * 建议:
     * - RAG 场景: 3-5 条足够
     * - 搜索展示: 10-20 条
     * - 太多会增加 LLM Token 消耗
     */
    @Value("${knowledge.search.default-max-results:5}")
    private int defaultMaxResults;

    /**
     * 默认最小相似度阈值
     *
     * 配置: knowledge.search.default-min-score
     * 默认: 0.5
     *
     * 相似度范围: 0.0 ~ 1.0
     * - 0.3: 宽松匹配，可能包含不相关结果
     * - 0.5: 中等匹配，推荐起始值
     * - 0.7: 严格匹配，只返回高相关结果
     * - 0.9: 非常严格，可能漏掉相关结果
     *
     * 调优建议:
     * - 召回率低: 降低阈值
     * - 噪声多: 提高阈值
     */
    @Value("${knowledge.search.default-min-score:0.5}")
    private double defaultMinScore;

    // ═══════════════════════════════════════════════════════════════════════════
    // 搜索方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 基础搜索 (使用默认参数)
     *
     * @param query 查询文本 - 用户输入的自然语言问题
     * @return 搜索结果列表，按相似度降序排列
     *
     * 使用默认参数:
     * - maxResults: 由 knowledge.search.default-max-results 配置
     * - minScore: 由 knowledge.search.default-min-score 配置
     * - category: 无过滤，搜索所有分类
     */
    public List<SearchResult> search(String query) {
        return search(query, defaultMaxResults, defaultMinScore, null);
    }

    /**
     * 带参数搜索
     *
     * @param query      查询文本
     *                   - 用户输入的自然语言问题
     *                   - 会被转换为向量进行相似度搜索
     *
     * @param maxResults 最大返回数量
     *                   - 对应 KNN 中的 K 值
     *                   - 建议 1-20，太大影响性能
     *
     * @param minScore   最小相似度阈值
     *                   - 范围 0.0 ~ 1.0
     *                   - 低于此值的结果会被过滤
     *
     * @param category   分类过滤 (可选)
     *                   - null 或空: 不过滤
     *                   - 有值: 只返回该分类的结果
     *                   - 对应文档元数据中的 category 字段
     *
     * @return 搜索结果列表，按相似度降序排列
     *
     * 内部流程:
     * 1. query 文本 → EmbeddingModel.embed() → 查询向量
     * 2. 查询向量 → VectorStore.search() → 匹配结果
     * 3. 匹配结果 → toSearchResult() → SearchResult
     */
    public List<SearchResult> search(String query, int maxResults, double minScore, String category) {
        log.debug("执行知识搜索, query={}, maxResults={}, minScore={}, category={}",
                query, maxResults, minScore, category);

        // ─────────────────────────────────────────────────────────────────────
        // 1. 将查询文本转为向量
        // ─────────────────────────────────────────────────────────────────────
        // embeddingModel.embed() 返回 Response<Embedding>
        // .content() 获取实际的 Embedding 对象
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // ─────────────────────────────────────────────────────────────────────
        // 2. 执行向量搜索
        // ─────────────────────────────────────────────────────────────────────
        // 根据是否有分类过滤，调用不同的搜索方法
        List<EmbeddingMatch<TextSegment>> matches;
        if (category != null && !category.isEmpty()) {
            // 带分类过滤的搜索
            matches = vectorStore.search(queryEmbedding, maxResults, minScore, category);
        } else {
            // 无过滤的搜索
            matches = vectorStore.search(queryEmbedding, maxResults, minScore);
        }

        log.debug("搜索返回 {} 条结果", matches.size());

        // ─────────────────────────────────────────────────────────────────────
        // 3. 转换结果
        // ─────────────────────────────────────────────────────────────────────
        // 将 LangChain4j 的 EmbeddingMatch 转换为业务对象 SearchResult
        return matches.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RAG 支持
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * RAG 上下文检索
     *
     * 返回适合作为 LLM 上下文的格式化文本
     *
     * @param query      查询文本 - 用户的问题
     * @param maxResults 最大返回数量 - 控制上下文长度
     * @return 格式化的上下文字符串，可直接放入 Prompt
     *
     * 返回格式示例:
     * ```
     * 以下是相关的知识内容：
     *
     * 【知识 1】(来源: 员工手册)
     * 年假规定: 入职满一年可享受5天年假...
     *
     * 【知识 2】(来源: 休假管理办法)
     * 请假流程: 1. 填写请假申请 2. 提交审批...
     * ```
     *
     * 使用场景:
     * ```java
     * String context = searchService.retrieveContext("如何请假", 3);
     * String prompt = """
     *     你是一个企业知识助手。请根据以下知识回答问题。
     *
     *     %s
     *
     *     问题: %s
     *     """.formatted(context, userQuestion);
     *
     * String answer = chatModel.generate(prompt);
     * ```
     */
    public String retrieveContext(String query, int maxResults) {
        List<SearchResult> results = search(query, maxResults, defaultMinScore, null);

        if (results.isEmpty()) {
            return "未找到相关知识。";
        }

        // 构建格式化的上下文
        StringBuilder context = new StringBuilder();
        context.append("以下是相关的知识内容：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append("【知识 ").append(i + 1).append("】");

            // 如果有标题，添加来源信息
            if (result.title() != null && !result.title().isEmpty()) {
                context.append("(来源: ").append(result.title()).append(")");
            }
            context.append("\n");
            context.append(result.content()).append("\n\n");
        }

        return context.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 结果转换
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 转换搜索结果
     *
     * @param match LangChain4j 返回的匹配结果
     * @return 业务对象 SearchResult
     *
     * EmbeddingMatch 结构:
     * - embeddingId(): 向量记录的唯一 ID
     * - embedding(): 向量数据 (通常不需要返回给前端)
     * - embedded(): TextSegment (原文 + 元数据)
     * - score(): 相似度分数 (0-1)
     */
    private SearchResult toSearchResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();

        /*
         * TextSegment 结构:
         * - text(): 原始文本内容
         * - metadata(): 元数据 Map
         *   - getString("key"): 获取字符串值
         *   - getInteger("key"): 获取整数值
         *   - 等等...
         */
        return new SearchResult(
                segment.text(),                              // 文本内容
                segment.metadata().getString("title"),       // 文档标题
                segment.metadata().getString("category"),    // 分类
                segment.metadata().getString("docId"),       // 文档 ID
                match.score()                                // 相似度分数
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 数据结构
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 搜索结果
     *
     * 使用 Java 17 record 语法:
     * - 自动生成构造函数、getter、equals、hashCode、toString
     * - 不可变对象
     * - 简洁的数据载体
     *
     * @param content  内容 - 匹配的文本片段
     * @param title    标题 - 文档标题
     * @param category 分类 - 文档分类
     * @param docId    文档ID - 关联的文档唯一标识
     * @param score    相似度分数 - 0.0 ~ 1.0，越高越相关
     */
    public record SearchResult(
            String content,    // 内容 (匹配的文本片段)
            String title,      // 标题 (来自文档元数据)
            String category,   // 分类 (用于分类展示)
            String docId,      // 文档ID (可用于跳转原文)
            double score       // 相似度分数 (0-1)
    ) {}
}
