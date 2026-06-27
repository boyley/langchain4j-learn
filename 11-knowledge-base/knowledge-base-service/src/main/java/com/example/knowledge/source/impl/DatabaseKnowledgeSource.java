package com.example.knowledge.source.impl;

import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import com.example.knowledge.source.KnowledgeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库知识源实现
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【功能概述】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 从数据库 knowledge_documents 表读取知识文档。
 *
 * 数据流:
 *   管理后台/其他系统 → knowledge_documents 表 → DatabaseKnowledgeSource → 向量化
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【适用场景】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 知识通过管理后台录入
 *    - CMS 系统
 *    - FAQ 管理系统
 *
 * 2. 知识来自其他系统同步到数据库
 *    - ETL 数据同步
 *    - 消息队列消费
 *
 * 3. 需要结构化管理的知识
 *    - 带状态管理 (草稿/发布/下线)
 *    - 带权限控制
 *    - 带版本历史
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   source:
 *     database:
 *       enabled: true    # 启用数据库知识源 (默认 true)
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【数据库表结构】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * knowledge_documents 表:
 * - doc_id: 文档唯一标识 (如 "db:doc-001")
 * - title: 标题
 * - category: 分类
 * - source: 来源 (固定为 "database")
 * - status: 状态 (1=有效, 0=删除)
 * - update_time: 更新时间 (用于增量同步)
 *
 * knowledge_content 表:
 * - doc_id: 关联文档
 * - content: 大文本内容 (LONGTEXT)
 */
@Slf4j
/*
 * @Component - Spring 组件注解
 *
 * 将此类注册为 Spring Bean，可被自动注入
 */
@Component
@RequiredArgsConstructor
/*
 * @ConditionalOnProperty - 条件装配注解
 *
 * 作用:
 * 只有当配置属性满足条件时，才创建此 Bean
 *
 * 参数说明:
 * - name: 配置属性名
 * - havingValue: 期望的值
 * - matchIfMissing: 配置不存在时是否匹配 (默认 false)
 *
 * 此处配置:
 * - name = "knowledge.source.database.enabled"
 * - havingValue = "true"
 * - matchIfMissing = true (配置不存在时默认启用)
 *
 * 效果:
 * - knowledge.source.database.enabled=true → 创建 Bean
 * - knowledge.source.database.enabled=false → 不创建
 * - 未配置 → 创建 Bean (matchIfMissing=true)
 *
 * 其他条件注解:
 * - @ConditionalOnClass: 类存在时创建
 * - @ConditionalOnMissingBean: Bean 不存在时创建
 * - @ConditionalOnExpression: SpEL 表达式为真时创建
 */
@ConditionalOnProperty(name = "knowledge.source.database.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseKnowledgeSource implements KnowledgeSource {

    /**
     * 知识文档仓库
     *
     * Spring Data JPA 自动生成实现
     * 提供 CRUD 操作和自定义查询
     */
    private final KnowledgeDocumentRepository repository;

    // ═══════════════════════════════════════════════════════════════════════════
    // 基础信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取知识源名称
     *
     * @return "database"
     *
     * 用于:
     * - 同步状态表的 source_name 字段
     * - 日志记录
     * - API 接口标识
     */
    @Override
    public String getName() {
        return "database";
    }

    /**
     * 获取显示名称
     *
     * @return "数据库"
     *
     * 用于管理界面展示
     */
    @Override
    public String getDisplayName() {
        return "数据库";
    }

    /**
     * 检查数据库是否可用
     *
     * 通过执行简单查询验证数据库连接
     *
     * @return true 如果数据库连接正常
     */
    @Override
    public boolean isAvailable() {
        try {
            // 执行简单的 count 查询验证连接
            repository.count();
            return true;
        } catch (Exception e) {
            log.error("数据库知识源不可用", e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 文档获取
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 全量获取所有文档
     *
     * 条件:
     * - source = "database" (只获取本知识源的文档)
     * - status = 1 (只获取有效文档)
     *
     * @return 所有有效文档列表
     *
     * 注意:
     * - 此方法返回的是元数据，不含 content 字段
     * - content 在处理时通过 ContentService 单独加载
     * - 这样设计避免大数据量时 OOM
     */
    @Override
    public List<KnowledgeDocument> fetchAll() {
        log.info("开始全量获取数据库知识文档...");
        List<KnowledgeDocument> docs = repository.findBySourceAndStatus("database", 1);
        log.info("获取到 {} 篇文档", docs.size());
        return docs;
    }

    /**
     * 增量获取文档
     *
     * @param lastSyncTime 上次同步时间
     *                     - null: 首次同步，返回全部
     *                     - 有值: 只返回该时间之后更新的
     *
     * @return 需要同步的文档列表
     *
     * SQL 等价:
     * ```sql
     * -- lastSyncTime = null (全量)
     * SELECT * FROM knowledge_documents
     * WHERE source = 'database' AND status = 1
     *
     * -- lastSyncTime 有值 (增量)
     * SELECT * FROM knowledge_documents
     * WHERE source = 'database' AND update_time > :lastSyncTime
     * ```
     */
    @Override
    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
        // 首次同步，执行全量获取
        if (lastSyncTime == null) {
            log.info("首次同步，执行全量获取");
            return fetchAll();
        }

        // 增量获取
        log.info("增量获取数据库知识文档, lastSyncTime={}", lastSyncTime);
        List<KnowledgeDocument> docs = repository.findBySourceAndUpdateTimeAfter("database", lastSyncTime);
        log.info("增量获取到 {} 篇文档", docs.size());
        return docs;
    }

    /**
     * 分批获取文档
     *
     * @param offset 偏移量 (跳过的记录数)
     * @param limit  每批数量
     *
     * @return 文档列表
     *
     * PageRequest.of(pageNumber, pageSize) 说明:
     * - pageNumber: 页码 (从 0 开始)
     * - pageSize: 每页大小
     *
     * 转换关系:
     * - offset=0, limit=100 → PageRequest.of(0, 100) → 第1页
     * - offset=100, limit=100 → PageRequest.of(1, 100) → 第2页
     * - offset=200, limit=100 → PageRequest.of(2, 100) → 第3页
     *
     * 计算公式: pageNumber = offset / limit
     */
    @Override
    public List<KnowledgeDocument> fetchBatch(int offset, int limit) {
        log.debug("分批获取数据库知识文档, offset={}, limit={}", offset, limit);

        // offset / limit 得到页码
        // 例如: offset=100, limit=100 → page=1
        int pageNumber = offset / limit;

        return repository.findBySourceAndStatus("database", 1, PageRequest.of(pageNumber, limit))
                .getContent(); // Page.getContent() 返回当前页的数据列表
    }

    /**
     * 获取文档总数
     *
     * 只统计有效文档 (status = 1)
     *
     * @return 文档总数
     */
    @Override
    public long count() {
        return repository.countByStatus(1);
    }

    /**
     * 根据 ID 获取单个文档
     *
     * @param docId 文档唯一标识
     *
     * @return 文档，不存在返回 null
     *
     * 注意: 返回的文档不含 content 字段
     */
    @Override
    public KnowledgeDocument fetchById(String docId) {
        return repository.findByDocId(docId).orElse(null);
    }
}
