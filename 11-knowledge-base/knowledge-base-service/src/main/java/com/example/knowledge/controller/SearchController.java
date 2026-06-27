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
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【API 概览】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 方法 | 路径                      | 说明              |
 * |------|--------------------------|-------------------|
 * | GET  | /api/knowledge/search    | 知识搜索           |
 * | GET  | /api/knowledge/context   | RAG 上下文检索     |
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【调用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 基础搜索:
 * ```bash
 * curl "http://localhost:8080/api/knowledge/search?q=如何请假"
 * ```
 *
 * 带参数搜索:
 * ```bash
 * curl "http://localhost:8080/api/knowledge/search?q=如何请假&limit=10&minScore=0.7&category=HR政策"
 * ```
 *
 * RAG 上下文:
 * ```bash
 * curl "http://localhost:8080/api/knowledge/context?q=如何请假&limit=3"
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【响应格式】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 成功响应:
 * ```json
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": [
 *     {
 *       "content": "年假规定: 入职满一年可享受5天年假...",
 *       "title": "员工手册",
 *       "category": "HR政策",
 *       "docId": "db:doc-001",
 *       "score": 0.89
 *     }
 *   ]
 * }
 * ```
 *
 * 错误响应:
 * ```json
 * {
 *   "code": -1,
 *   "message": "错误信息",
 *   "data": null
 * }
 * ```
 */
@Slf4j
/*
 * @RestController - Spring MVC REST 控制器注解
 *
 * 作用:
 * 1. 标记此类为 REST API 控制器
 * 2. 等价于 @Controller + @ResponseBody
 * 3. 方法返回值直接序列化为 JSON (默认使用 Jackson)
 *
 * 与 @Controller 的区别:
 * - @Controller: 返回视图名称，需要 @ResponseBody 才返回 JSON
 * - @RestController: 直接返回 JSON，适合 REST API
 *
 * 示例:
 * ```java
 * @Controller
 * public class ViewController {
 *     @GetMapping("/page")
 *     public String page() {
 *         return "page"; // 返回视图名称
 *     }
 *
 *     @GetMapping("/data")
 *     @ResponseBody
 *     public Data data() {
 *         return new Data(); // 返回 JSON
 *     }
 * }
 *
 * @RestController
 * public class ApiController {
 *     @GetMapping("/data")
 *     public Data data() {
 *         return new Data(); // 直接返回 JSON
 *     }
 * }
 * ```
 */
@RestController
/*
 * @RequestMapping - 请求映射注解
 *
 * 作用:
 * 1. 定义此控制器的基础 URL 路径
 * 2. 所有方法的路径都会加上此前缀
 *
 * 参数说明:
 * - value/path: URL 路径
 * - method: HTTP 方法 (GET, POST, PUT, DELETE 等)
 * - consumes: 接受的 Content-Type
 * - produces: 返回的 Content-Type
 * - headers: 请求头条件
 * - params: 请求参数条件
 *
 * 示例:
 * ```java
 * @RequestMapping("/api/knowledge")
 * public class SearchController {
 *     @GetMapping("/search")  // 完整路径: /api/knowledge/search
 *     public Result search() { ... }
 * }
 * ```
 */
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class SearchController {

    /**
     * 知识搜索服务
     *
     * 通过构造器注入 (@RequiredArgsConstructor)
     */
    private final KnowledgeSearchService searchService;

    // ═══════════════════════════════════════════════════════════════════════════
    // 搜索接口
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 知识搜索
     *
     * GET /api/knowledge/search?q=如何请假&limit=5&minScore=0.5&category=HR
     *
     * @param q        查询文本 (必需)
     * @param limit    最大返回数量 (默认5)
     * @param minScore 最小相似度 (默认0.5)
     * @param category 分类过滤 (可选)
     * @return 搜索结果列表
     *
     * @GetMapping 注解说明:
     * - 等价于 @RequestMapping(method = RequestMethod.GET)
     * - value/path: 请求路径 (相对于类级别的 @RequestMapping)
     * - 完整路径: /api/knowledge/search
     *
     * @RequestParam 注解说明:
     * - 绑定 URL 查询参数到方法参数
     * - value/name: 参数名 (默认与变量名相同)
     * - required: 是否必需 (默认 true)
     * - defaultValue: 默认值 (设置后 required 自动变为 false)
     *
     * 参数示例:
     * - @RequestParam("q") String q
     *   URL: ?q=xxx → q = "xxx"
     *
     * - @RequestParam(defaultValue = "5") int limit
     *   URL: ?limit=10 → limit = 10
     *   URL: (无参数) → limit = 5
     *
     * - @RequestParam(required = false) String category
     *   URL: ?category=HR → category = "HR"
     *   URL: (无参数) → category = null
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

    // ═══════════════════════════════════════════════════════════════════════════
    // RAG 支持
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * RAG 上下文检索
     *
     * GET /api/knowledge/context?q=如何请假&limit=3
     *
     * 返回适合作为 LLM 上下文的格式化文本
     *
     * @param q     查询文本 (必需)
     * @param limit 最大返回数量 (默认3)
     * @return 格式化的上下文文本
     *
     * 返回示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": "以下是相关的知识内容：\n\n【知识 1】(来源: 员工手册)\n年假规定..."
     * }
     * ```
     *
     * 使用场景:
     * 前端获取上下文后，组装 Prompt 调用 LLM:
     * ```javascript
     * const context = await fetch('/api/knowledge/context?q=如何请假').then(r => r.json());
     * const prompt = `根据以下知识回答问题:\n${context.data}\n问题: 如何请假?`;
     * const answer = await callLLM(prompt);
     * ```
     */
    @GetMapping("/context")
    public ApiResponse<String> retrieveContext(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "3") int limit) {

        log.info("上下文检索: q={}, limit={}", q, limit);

        String context = searchService.retrieveContext(q, limit);
        return ApiResponse.success(context);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 响应格式
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 统一响应格式
     *
     * 标准化 API 响应结构，便于前端统一处理
     *
     * @param <T> 数据类型
     *
     * 结构:
     * - code: 状态码 (0 成功, 非0 失败)
     * - message: 状态描述
     * - data: 业务数据
     *
     * 为什么用 record:
     * - 自动生成构造函数、getter、equals 等
     * - 不可变，线程安全
     * - 代码简洁
     *
     * JSON 序列化:
     * Spring Boot 默认使用 Jackson 序列化
     * record 的字段名就是 JSON 的 key
     */
    public record ApiResponse<T>(
            int code,
            String message,
            T data
    ) {
        /**
         * 成功响应
         *
         * @param data 业务数据
         * @return ApiResponse
         *
         * 示例:
         * ```java
         * return ApiResponse.success(results);
         * // {"code":0,"message":"success","data":[...]}
         * ```
         */
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(0, "success", data);
        }

        /**
         * 错误响应
         *
         * @param message 错误信息
         * @return ApiResponse
         *
         * 示例:
         * ```java
         * return ApiResponse.error("参数错误");
         * // {"code":-1,"message":"参数错误","data":null}
         * ```
         */
        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(-1, message, null);
        }
    }
}
