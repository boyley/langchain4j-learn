package com.example.knowledge.source;

import com.example.knowledge.entity.KnowledgeDocument;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识源接口 - 抽象不同的知识来源
 *
 * 核心设计思想:
 * - 统一接口，不同实现
 * - 业务层无需关心知识来自哪里
 * - 新增知识源只需实现此接口
 *
 * 已实现的知识源:
 * - DatabaseKnowledgeSource: 从数据库读取 (MySQL/PostgreSQL)
 * - FileSystemKnowledgeSource: 从文件系统读取 (PDF/Word/TXT)
 * - ConfluenceKnowledgeSource: 从 Confluence API 读取
 * - NotionKnowledgeSource: 从 Notion API 读取 (可扩展)
 */
public interface KnowledgeSource {

    /**
     * 获取知识源名称
     * 用于标识和日志记录
     *
     * @return 知识源名称，如 "mysql", "file", "confluence"
     */
    String getName();

    /**
     * 获取知识源显示名称
     *
     * @return 显示名称，如 "MySQL数据库", "文件系统"
     */
    String getDisplayName();

    /**
     * 检查知识源是否可用
     * 用于健康检查和故障转移
     *
     * @return true 如果知识源可用
     */
    boolean isAvailable();

    /**
     * 全量获取所有知识文档
     *
     * 注意: 大数据量时应使用 fetchIncremental 或 fetchBatch
     *
     * @return 所有文档列表
     */
    List<KnowledgeDocument> fetchAll();

    /**
     * 增量获取 - 只获取指定时间后更新的文档
     *
     * 这是生产环境的核心方法:
     * - 首次同步: lastSyncTime = null，获取全部
     * - 后续同步: lastSyncTime = 上次同步时间，只获取增量
     *
     * @param lastSyncTime 上次同步时间，null 表示全量
     * @return 需要同步的文档列表
     */
    List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime);

    /**
     * 分批获取 - 用于大数据量场景
     *
     * 避免一次性加载过多数据导致内存溢出
     *
     * @param offset 偏移量
     * @param limit  每批数量
     * @return 文档列表
     */
    List<KnowledgeDocument> fetchBatch(int offset, int limit);

    /**
     * 获取文档总数
     *
     * @return 文档总数
     */
    long count();

    /**
     * 根据 ID 获取单个文档
     *
     * @param docId 文档ID
     * @return 文档，不存在返回 null
     */
    KnowledgeDocument fetchById(String docId);
}
