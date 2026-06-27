package com.example.knowledge.repository;

import com.example.knowledge.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 同步状态数据访问层
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【功能说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 管理各知识源的同步状态记录
 *
 * 表结构:
 * +------------------+-------------+----------------------------------+
 * | id               | source_name | last_sync_time | last_sync_status|
 * +------------------+-------------+----------------+-----------------+
 * | 1                | database    | 2024-01-15 10:30| SUCCESS        |
 * | 2                | file        | 2024-01-15 09:00| SUCCESS        |
 * | 3                | confluence  | 2024-01-14 23:00| FAILED         |
 * +------------------+-------------+----------------+-----------------+
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用场景】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 增量同步流程:
 *
 *   1. 获取同步状态
 *      SyncStatus status = repository.findBySourceName("database")
 *          .orElseGet(() -> createNewStatus("database"));
 *
 *   2. 获取上次同步时间
 *      LocalDateTime lastSync = status.getLastSyncTime(); // 可能为 null
 *
 *   3. 执行增量同步
 *      List<Document> docs = source.fetchIncremental(lastSync);
 *
 *   4. 更新同步状态
 *      status.setLastSyncTime(LocalDateTime.now());
 *      status.setLastSyncStatus("SUCCESS");
 *      repository.save(status);
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【JpaRepository 继承的方法】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 基础 CRUD:
 * - save(entity): 保存或更新
 * - findById(id): 按主键查找
 * - findAll(): 查找所有
 * - delete(entity): 删除
 * - count(): 计数
 *
 * 分页排序:
 * - findAll(Pageable): 分页查找
 * - findAll(Sort): 排序查找
 *
 * 批量操作:
 * - saveAll(entities): 批量保存
 * - deleteAll(): 删除所有
 * - deleteAllInBatch(): 批量删除 (更高效)
 */
@Repository
public interface SyncStatusRepository extends JpaRepository<SyncStatus, Long> {

    /**
     * 根据知识源名称查找同步状态
     *
     * @param sourceName 知识源名称，如 "database", "file", "confluence"
     * @return Optional 包装的同步状态对象
     *
     * 方法命名规则:
     * - findBy: 查询方法前缀
     * - SourceName: 按 sourceName 字段查询
     *
     * 生成的 SQL:
     * SELECT * FROM sync_status WHERE source_name = ?
     *
     * 使用示例:
     * ```java
     * // 获取或创建同步状态
     * SyncStatus status = repository.findBySourceName("database")
     *     .orElseGet(() -> {
     *         SyncStatus newStatus = new SyncStatus();
     *         newStatus.setSourceName("database");
     *         return repository.save(newStatus);
     *     });
     *
     * // 获取上次同步时间
     * LocalDateTime lastSync = status.getLastSyncTime();
     * if (lastSync == null) {
     *     // 首次同步，执行全量
     * } else {
     *     // 增量同步
     * }
     * ```
     */
    Optional<SyncStatus> findBySourceName(String sourceName);
}
