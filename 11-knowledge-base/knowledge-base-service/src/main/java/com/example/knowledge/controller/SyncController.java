package com.example.knowledge.controller;

import com.example.knowledge.controller.SearchController.ApiResponse;
import com.example.knowledge.entity.SyncStatus;
import com.example.knowledge.pipeline.KnowledgePipeline;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import com.example.knowledge.repository.SyncStatusRepository;
import com.example.knowledge.source.KnowledgeSource;
import com.example.knowledge.store.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 同步管理 API
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【API 概览】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 方法 | 路径                        | 说明              |
 * |------|----------------------------|-------------------|
 * | POST | /api/knowledge/sync/trigger | 手动触发同步       |
 * | GET  | /api/knowledge/sync/status  | 查看同步状态       |
 * | GET  | /api/knowledge/sync/sources | 查看可用知识源     |
 * | GET  | /api/knowledge/sync/stats   | 查看统计信息       |
 * | GET  | /api/knowledge/sync/health  | 健康检查          |
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【调用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 触发增量同步:
 * ```bash
 * curl -X POST "http://localhost:8080/api/knowledge/sync/trigger?source=database&mode=incremental"
 * ```
 *
 * 触发全量同步:
 * ```bash
 * curl -X POST "http://localhost:8080/api/knowledge/sync/trigger?source=database&mode=full"
 * ```
 *
 * 查看同步状态:
 * ```bash
 * curl "http://localhost:8080/api/knowledge/sync/status"
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【同步模式】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 增量同步 (incremental):
 * - 只同步上次同步后有变化的文档
 * - 基于 lastModifiedTime 判断
 * - 适合定时任务
 *
 * 全量同步 (full):
 * - 重新同步所有文档
 * - 会删除旧向量，重新生成
 * - 适合首次同步或数据修复
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/sync")
@RequiredArgsConstructor
public class SyncController {

    // ═══════════════════════════════════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 知识处理管道
     *
     * 执行: 获取文档 → 分割 → 向量化 → 存储
     */
    private final KnowledgePipeline pipeline;

    /**
     * 所有知识源列表
     *
     * Spring 会自动注入所有 KnowledgeSource 实现类
     * 例如: DatabaseKnowledgeSource, FileSystemKnowledgeSource
     *
     * List<Interface> 注入说明:
     * - Spring 会找到所有实现该接口的 Bean
     * - 自动注入为 List
     * - 顺序可通过 @Order 注解控制
     */
    private final List<KnowledgeSource> sources;

    /**
     * 同步状态仓库
     *
     * 记录每个知识源的同步历史
     */
    private final SyncStatusRepository syncStatusRepository;

    /**
     * 文档仓库
     *
     * 用于统计文档数量
     */
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * 向量存储服务
     *
     * 用于获取向量存储状态
     */
    private final VectorStoreService vectorStore;

    // ═══════════════════════════════════════════════════════════════════════════
    // 同步操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 手动触发同步
     *
     * POST /api/knowledge/sync/trigger?source=database&mode=incremental
     *
     * @param source 知识源名称
     *               - database: 数据库知识源
     *               - file: 文件系统知识源
     *               - confluence: Confluence 知识源 (如已配置)
     *
     * @param mode   同步模式
     *               - incremental: 增量同步 (默认)
     *               - full: 全量同步
     *
     * @return 同步结果
     *
     * @PostMapping 注解说明:
     * - 等价于 @RequestMapping(method = RequestMethod.POST)
     * - 用于处理 POST 请求
     * - 通常用于创建资源或触发操作
     *
     * 响应示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": {
     *     "docCount": 10,
     *     "segmentCount": 45,
     *     "message": "成功"
     *   }
     * }
     * ```
     */
    @PostMapping("/trigger")
    public ApiResponse<KnowledgePipeline.SyncResult> triggerSync(
            @RequestParam String source,
            @RequestParam(defaultValue = "incremental") String mode) {

        log.info("手动触发同步: source={}, mode={}", source, mode);

        // 查找知识源
        // 从所有注册的知识源中，按名称查找匹配的
        KnowledgeSource knowledgeSource = sources.stream()
                .filter(s -> s.getName().equals(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的知识源: " + source));

        // 执行同步
        boolean fullSync = "full".equalsIgnoreCase(mode);
        KnowledgePipeline.SyncResult result = pipeline.sync(knowledgeSource, fullSync);

        return ApiResponse.success(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 状态查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查看同步状态
     *
     * GET /api/knowledge/sync/status
     *
     * @return 所有知识源的同步状态
     *
     * 响应示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": [
     *     {
     *       "sourceName": "database",
     *       "lastSyncTime": "2024-01-15T10:30:00",
     *       "lastSyncStatus": "SUCCESS",
     *       "lastSyncCount": 10,
     *       "totalSyncCount": 150
     *     }
     *   ]
     * }
     * ```
     */
    @GetMapping("/status")
    public ApiResponse<List<SyncStatus>> getSyncStatus() {
        List<SyncStatus> statuses = syncStatusRepository.findAll();
        return ApiResponse.success(statuses);
    }

    /**
     * 查看可用的知识源
     *
     * GET /api/knowledge/sync/sources
     *
     * @return 知识源列表
     *
     * 响应示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": [
     *     {
     *       "name": "database",
     *       "displayName": "数据库知识源",
     *       "available": true,
     *       "count": 100
     *     },
     *     {
     *       "name": "file",
     *       "displayName": "文件系统知识源",
     *       "available": true,
     *       "count": 50
     *     }
     *   ]
     * }
     * ```
     */
    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> getSources() {
        // 遍历所有知识源，收集信息
        List<Map<String, Object>> sourceList = sources.stream()
                .map(source -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", source.getName());             // 内部名称
                    info.put("displayName", source.getDisplayName()); // 显示名称
                    info.put("available", source.isAvailable());    // 是否可用
                    info.put("count", source.count());              // 文档数量
                    return info;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(sourceList);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查看知识库统计信息
     *
     * GET /api/knowledge/sync/stats
     *
     * @return 统计信息
     *
     * 响应示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": {
     *     "totalDocuments": 100,
     *     "vectorizedDocuments": 95,
     *     "pendingDocuments": 3,
     *     "failedDocuments": 2,
     *     "byCategory": {
     *       "HR政策": 30,
     *       "技术文档": 50,
     *       "产品手册": 20
     *     },
     *     "bySource": {
     *       "database": 60,
     *       "file": 40
     *     },
     *     "vectorStore": {
     *       "type": "redis",
     *       "vectorCount": 450,
     *       "connected": true
     *     }
     *   }
     * }
     * ```
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // ─────────────────────────────────────────────────────────────────────
        // 文档统计
        // ─────────────────────────────────────────────────────────────────────
        // status=1: 有效文档
        // vectorStatus: 0=待处理, 1=已完成, 2=失败
        stats.put("totalDocuments", documentRepository.countByStatus(1));
        stats.put("vectorizedDocuments", documentRepository.countByStatusAndVectorStatus(1, 1));
        stats.put("pendingDocuments", documentRepository.countByStatusAndVectorStatus(1, 0));
        stats.put("failedDocuments", documentRepository.countByStatusAndVectorStatus(1, 2));

        // ─────────────────────────────────────────────────────────────────────
        // 按分类统计
        // ─────────────────────────────────────────────────────────────────────
        // 返回 List<Object[]>，每个元素是 [category, count]
        List<Object[]> categoryStats = documentRepository.countByCategory();
        Map<String, Long> byCategory = new HashMap<>();
        for (Object[] row : categoryStats) {
            byCategory.put((String) row[0], (Long) row[1]);
        }
        stats.put("byCategory", byCategory);

        // ─────────────────────────────────────────────────────────────────────
        // 按来源统计
        // ─────────────────────────────────────────────────────────────────────
        List<Object[]> sourceStats = documentRepository.countBySource();
        Map<String, Long> bySource = new HashMap<>();
        for (Object[] row : sourceStats) {
            bySource.put((String) row[0], (Long) row[1]);
        }
        stats.put("bySource", bySource);

        // ─────────────────────────────────────────────────────────────────────
        // 向量存储统计
        // ─────────────────────────────────────────────────────────────────────
        stats.put("vectorStore", vectorStore.getStats());

        return ApiResponse.success(stats);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 健康检查
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 健康检查
     *
     * GET /api/knowledge/sync/health
     *
     * 检查各组件的可用性
     *
     * @return 健康状态
     *
     * 响应示例:
     * ```json
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": {
     *     "vectorStore": {
     *       "type": "redis",
     *       "available": true
     *     },
     *     "sources": [
     *       {"name": "database", "available": true},
     *       {"name": "file", "available": true}
     *     ]
     *   }
     * }
     * ```
     *
     * 使用场景:
     * - 运维监控
     * - Kubernetes liveness/readiness probe
     * - 故障排查
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        // 检查向量存储
        health.put("vectorStore", Map.of(
                "type", vectorStore.getType(),
                "available", vectorStore.isAvailable()
        ));

        // 检查各知识源
        List<Map<String, Object>> sourceHealth = sources.stream()
                .map(source -> Map.<String, Object>of(
                        "name", source.getName(),
                        "available", source.isAvailable()
                ))
                .collect(Collectors.toList());
        health.put("sources", sourceHealth);

        return ApiResponse.success(health);
    }
}
