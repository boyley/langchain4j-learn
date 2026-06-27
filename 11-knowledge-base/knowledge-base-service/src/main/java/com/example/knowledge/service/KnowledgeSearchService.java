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
 * 提供向量相似性搜索功能:
 * - 基础搜索
 * - 带过滤器搜索
 * - RAG 上下文检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final EmbeddingModel embeddingModel;
    private final VectorStoreService vectorStore;

    @Value("${knowledge.search.default-max-results:5}")
    private int defaultMaxResults;

    @Value("${knowledge.search.default-min-score:0.5}")
    private double defaultMinScore;

    /**
     * 基础搜索
     *
     * @param query 查询文本
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query) {
        return search(query, defaultMaxResults, defaultMinScore, null);
    }

    /**
     * 带参数搜索
     *
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @param minScore   最小相似度
     * @param category   分类过滤 (可选)
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query, int maxResults, double minScore, String category) {
        log.debug("执行知识搜索, query={}, maxResults={}, minScore={}, category={}",
                query, maxResults, minScore, category);

        // 1. 将查询文本转为向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 执行向量搜索
        List<EmbeddingMatch<TextSegment>> matches;
        if (category != null && !category.isEmpty()) {
            matches = vectorStore.search(queryEmbedding, maxResults, minScore, category);
        } else {
            matches = vectorStore.search(queryEmbedding, maxResults, minScore);
        }

        log.debug("搜索返回 {} 条结果", matches.size());

        // 3. 转换结果
        return matches.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
    }

    /**
     * RAG 上下文检索
     *
     * 返回适合作为 LLM 上下文的格式
     *
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @return 上下文字符串
     */
    public String retrieveContext(String query, int maxResults) {
        List<SearchResult> results = search(query, maxResults, defaultMinScore, null);

        if (results.isEmpty()) {
            return "未找到相关知识。";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是相关的知识内容：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append("【知识 ").append(i + 1).append("】");
            if (result.title() != null && !result.title().isEmpty()) {
                context.append("(来源: ").append(result.title()).append(")");
            }
            context.append("\n");
            context.append(result.content()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * 转换搜索结果
     */
    private SearchResult toSearchResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        return new SearchResult(
                segment.text(),
                segment.metadata().getString("title"),
                segment.metadata().getString("category"),
                segment.metadata().getString("docId"),
                match.score()
        );
    }

    /**
     * 搜索结果
     */
    public record SearchResult(
            String content,    // 内容
            String title,      // 标题
            String category,   // 分类
            String docId,      // 文档ID
            double score       // 相似度分数
    ) {}
}
