package com.example.knowledge.controller;

import com.example.knowledge.service.KnowledgeSearchService;
import com.example.knowledge.service.KnowledgeSearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识检索 API
 *
 * 提供知识搜索相关的 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class SearchController {

    private final KnowledgeSearchService searchService;

    /**
     * 知识搜索
     *
     * GET /api/knowledge/search?q=如何请假&limit=5&minScore=0.5&category=HR
     *
     * @param q        查询文本
     * @param limit    最大返回数量 (默认5)
     * @param minScore 最小相似度 (默认0.5)
     * @param category 分类过滤 (可选)
     * @return 搜索结果列表
     */
    @GetMapping("/search")
    public ApiResponse<List<SearchResult>> search(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "0.5") double minScore,
            @RequestParam(required = false) String category) {

        log.info("搜索请求: q={}, limit={}, minScore={}, category={}", q, limit, minScore, category);

        List<SearchResult> results = searchService.search(q, limit, minScore, category);

        log.info("搜索返回 {} 条结果", results.size());
        return ApiResponse.success(results);
    }

    /**
     * RAG 上下文检索
     *
     * GET /api/knowledge/context?q=如何请假&limit=3
     *
     * 返回适合作为 LLM 上下文的格式化文本
     *
     * @param q     查询文本
     * @param limit 最大返回数量
     * @return 格式化的上下文文本
     */
    @GetMapping("/context")
    public ApiResponse<String> retrieveContext(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "3") int limit) {

        log.info("上下文检索: q={}, limit={}", q, limit);

        String context = searchService.retrieveContext(q, limit);
        return ApiResponse.success(context);
    }

    /**
     * 统一响应格式
     */
    public record ApiResponse<T>(
            int code,
            String message,
            T data
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(0, "success", data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(-1, message, null);
        }
    }
}
