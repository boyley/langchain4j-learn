package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 同步状态实体 - 记录各知识源的同步状态
 *
 * 用于增量同步:
 * - 记录每个知识源上次同步的时间点
 * - 下次同步时只获取该时间点之后更新的文档
 */
@Data
@Entity
@Table(name = "sync_status")
public class SyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 知识源名称
     * 如: mysql, file, confluence
     */
    @Column(nullable = false, unique = true, length = 64)
    private String sourceName;

    /**
     * 上次同步时间
     * null 表示从未同步过，需要全量同步
     */
    private LocalDateTime lastSyncTime;

    /**
     * 上次同步的文档数量
     */
    private Integer lastSyncCount = 0;

    /**
     * 累计同步的文档总数
     */
    private Long totalSyncCount = 0L;

    /**
     * 上次同步状态
     * SUCCESS / FAILED / RUNNING
     */
    @Column(length = 32)
    private String lastSyncStatus;

    /**
     * 上次同步失败的错误信息
     */
    @Column(length = 500)
    private String lastErrorMessage;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
