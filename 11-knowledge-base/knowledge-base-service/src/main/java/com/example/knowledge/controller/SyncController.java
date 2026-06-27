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
 * 提供知识同步相关的管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/sync")
@RequiredArgsConstructor
public class SyncController {

    private final KnowledgePipeline pipeline;
    private final List<KnowledgeSource> sources;
    private final SyncStatusRepository syncStatusRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStoreService vectorStore;

    /**
     * 手动触发同步
     *
     * POST /api/knowledge/sync/trigger?source=database&mode=incremental
     *
     * @param source 知识源名称 (database/file/confluence)
     * @param mode   同步模式 (incremental/full)
     * @return 同步结果
     */
    @PostMapping("/trigger")
    public ApiResponse<KnowledgePipeline.SyncResult> triggerSync(
            @RequestParam String source,
            @RequestParam(defaultValue = "incremental") String mode) {

        log.info("手动触发同步: source={}, mode={}", source, mode);

        // 查找知识源
        KnowledgeSource knowledgeSource = sources.stream()
                .filter(s -> s.getName().equals(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的知识源: " + source));

        // 执行同步
        boolean fullSync = "full".equalsIgnoreCase(mode);
        KnowledgePipeline.SyncResult result = pipeline.sync(knowledgeSource, fullSync);

        return ApiResponse.success(result);
    }

    /**
     * 查看同步状态
     *
     * GET /api/knowledge/sync/status
     *
     * @return 所有知识源的同步状态
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
     */
    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> getSources() {
        List<Map<String, Object>> sourceList = sources.stream()
                .map(source -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", source.getName());
                    info.put("displayName", source.getDisplayName());
                    info.put("available", source.isAvailable());
                    info.put("count", source.count());
                    return info;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(sourceList);
    }

    /**
     * 查看知识库统计信息
     *
     * GET /api/knowledge/sync/stats
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 文档统计
        stats.put("totalDocuments", documentRepository.countByStatus(1));
        stats.put("vectorizedDocuments", documentRepository.countByStatusAndVectorStatus(1, 1));
        stats.put("pendingDocuments", documentRepository.countByStatusAndVectorStatus(1, 0));
        stats.put("failedDocuments", documentRepository.countByStatusAndVectorStatus(1, 2));

        // 按分类统计
        List<Object[]> categoryStats = documentRepository.countByCategory();
        Map<String, Long> byCategory = new HashMap<>();
        for (Object[] row : categoryStats) {
            byCategory.put((String) row[0], (Long) row[1]);
        }
        stats.put("byCategory", byCategory);

        // 按来源统计
        List<Object[]> sourceStats = documentRepository.countBySource();
        Map<String, Long> bySource = new HashMap<>();
        for (Object[] row : sourceStats) {
            bySource.put((String) row[0], (Long) row[1]);
        }
        stats.put("bySource", bySource);

        // 向量存储统计
        stats.put("vectorStore", vectorStore.getStats());

        return ApiResponse.success(stats);
    }

    /**
     * 健康检查
     *
     * GET /api/knowledge/sync/health
     *
     * @return 健康状态
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
