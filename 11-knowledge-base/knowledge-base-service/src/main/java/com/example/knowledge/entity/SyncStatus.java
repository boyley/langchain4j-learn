package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 同步状态实体 - 记录各知识源的同步状态
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【设计目的】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 实现增量同步的关键:
 *
 *   定时任务触发同步
 *         │
 *         ▼
 *   读取 SyncStatus 获取 lastSyncTime
 *         │
 *   ┌─────┴─────┐
 *   │           │
 *   ▼           ▼
 * 首次同步    增量同步
 * (全量)     (只有变化)
 *   │           │
 *   └─────┬─────┘
 *         ▼
 *   更新 SyncStatus
 *   - lastSyncTime = now()
 *   - lastSyncCount = 处理数量
 *   - lastSyncStatus = SUCCESS/FAILED
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【表结构】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * sync_status 表:
 * +------------------+-------------+----------------------------------+
 * | 字段              | 类型        | 说明                             |
 * +------------------+-------------+----------------------------------+
 * | id               | BIGINT      | 主键                             |
 * | source_name      | VARCHAR(64) | 知识源名称 (唯一)                 |
 * | last_sync_time   | DATETIME    | 上次同步时间                      |
 * | last_sync_count  | INT         | 上次同步文档数                    |
 * | total_sync_count | BIGINT      | 累计同步总数                      |
 * | last_sync_status | VARCHAR(32) | 状态: SUCCESS/FAILED/RUNNING     |
 * | last_error_msg   | VARCHAR(500)| 错误信息                         |
 * | create_time      | DATETIME    | 创建时间                         |
 * | update_time      | DATETIME    | 更新时间                         |
 * +------------------+-------------+----------------------------------+
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【状态说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * lastSyncStatus 状态值:
 *
 *   RUNNING ─────┬────→ SUCCESS (同步成功)
 *   (同步中)      │
 *                └────→ FAILED (同步失败，记录错误信息)
 *
 * 状态流转:
 * 1. 同步开始: status = RUNNING
 * 2. 同步成功: status = SUCCESS, lastSyncTime = now()
 * 3. 同步失败: status = FAILED, lastErrorMessage = e.getMessage()
 */
@Data
@Entity
@Table(name = "sync_status")
public class SyncStatus {

    // ═══════════════════════════════════════════════════════════════════════════
    // 主键
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 自增主键
     *
     * @Id + @GeneratedValue(IDENTITY)
     * 等价 SQL: id BIGINT PRIMARY KEY AUTO_INCREMENT
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══════════════════════════════════════════════════════════════════════════
    // 核心字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 知识源名称 (唯一标识)
     *
     * 值: "database", "file", "confluence" 等
     * 每个知识源只有一条同步状态记录
     *
     * unique = true: 确保每个知识源只有一条记录
     */
    @Column(nullable = false, unique = true, length = 64)
    private String sourceName;

    /**
     * 上次同步时间
     *
     * 用于增量同步:
     * - null: 从未同步过，需要全量同步
     * - 有值: 只获取该时间之后更新的文档
     *
     * 使用方式:
     * ```java
     * LocalDateTime lastSync = status.getLastSyncTime();
     * List<KnowledgeDocument> docs = source.fetchIncremental(lastSync);
     * ```
     */
    private LocalDateTime lastSyncTime;

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 上次同步的文档数量
     *
     * 记录最近一次同步处理了多少文档
     * 用于监控和报表
     */
    private Integer lastSyncCount = 0;

    /**
     * 累计同步的文档总数
     *
     * 每次同步累加
     * 用于长期统计
     */
    private Long totalSyncCount = 0L;

    // ═══════════════════════════════════════════════════════════════════════════
    // 状态字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 上次同步状态
     *
     * 值:
     * - "RUNNING": 同步进行中
     * - "SUCCESS": 同步成功
     * - "FAILED": 同步失败
     *
     * 用途:
     * - 监控同步健康状态
     * - 避免并发同步 (检查是否 RUNNING)
     */
    @Column(length = 32)
    private String lastSyncStatus;

    /**
     * 上次同步失败的错误信息
     *
     * 只在 lastSyncStatus = "FAILED" 时有值
     * 用于问题排查
     *
     * 示例: "Connection refused", "Timeout", "Auth failed"
     */
    @Column(length = 500)
    private String lastErrorMessage;

    // ═══════════════════════════════════════════════════════════════════════════
    // 时间戳
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 记录创建时间
     *
     * updatable = false: 只在创建时设置，不会被更新
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     *
     * 每次同步后更新
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    // ═══════════════════════════════════════════════════════════════════════════
    // JPA 生命周期回调
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 插入前自动设置时间
     *
     * @PrePersist: 在 INSERT 执行前调用
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 更新前自动设置时间
     *
     * @PreUpdate: 在 UPDATE 执行前调用
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
