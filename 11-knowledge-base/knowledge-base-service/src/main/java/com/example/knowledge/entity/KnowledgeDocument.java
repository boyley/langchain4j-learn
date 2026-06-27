package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识文档实体 (元数据) - 对应数据库 knowledge_documents 表
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【企业级设计】内容与元数据分离
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 问题场景:
 * - 一篇文档可能有 1MB~100MB 的内容 (大型 PDF/Word)
 * - 批量查询 1000 篇文档 = 加载 1GB~100GB 到内存 → OOM
 *
 * 解决方案:
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │                                                                      │
 * │  KnowledgeDocument (元数据)         KnowledgeContent (内容)           │
 * │  ┌─────────────────────────┐       ┌─────────────────────────┐      │
 * │  │ id                      │       │ id                      │      │
 * │  │ docId                   │◄──────│ docId (FK)              │      │
 * │  │ title                   │   1:1 │ content (LONGTEXT)      │      │
 * │  │ category                │       │ contentSize             │      │
 * │  │ contentSize (元数据)    │       │ contentHash             │      │
 * │  │ vectorStatus            │       └─────────────────────────┘      │
 * │  │ updateTime              │                                        │
 * │  └─────────────────────────┘       查询元数据时不会加载内容           │
 * │                                    需要内容时单独查询                 │
 * │                                                                      │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * 使用方式:
 * - 批量查询: 只查 KnowledgeDocument (不含内容)
 * - 处理文档: 通过 docId 单独获取 KnowledgeContent
 * - 流式处理: 大内容可以分块读取
 */
@Data
@Entity
@Table(name = "knowledge_documents", indexes = {
    @Index(name = "idx_doc_id", columnList = "docId", unique = true),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_update_time", columnList = "updateTime"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_vector_status", columnList = "vectorStatus")
})
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文档唯一标识
     * 格式: source:unique_id, 如 db:123, file:/path/to/doc.pdf
     */
    @Column(nullable = false, unique = true, length = 128)
    private String docId;

    /**
     * 文档标题
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * 文档摘要 (前 500 字符)
     * 用于预览，不需要加载完整内容
     */
    @Column(length = 500)
    private String summary;

    /**
     * 文档分类
     */
    @Column(length = 64)
    private String category;

    /**
     * 文档版本
     */
    @Column(length = 32)
    private String version = "1.0";

    /**
     * 文档来源: database / file / confluence / notion / api
     */
    @Column(length = 32)
    private String source;

    // ═══════════════════════════════════════════════════════════════════
    // 内容元信息 (不存储实际内容)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 内容大小 (字节)
     * 用于判断是否是大文件，决定处理策略
     */
    @Column
    private Long contentSize;

    /**
     * 内容哈希 (MD5/SHA256)
     * 用于判断内容是否变化，避免重复处理
     */
    @Column(length = 64)
    private String contentHash;

    /**
     * 内容存储位置
     * - database: 存在 knowledge_content 表
     * - oss: 存在对象存储，值为 URL
     */
    @Column(length = 32)
    private String contentLocation = "database";

    /**
     * 如果存在对象存储，记录 URL
     */
    @Column(length = 500)
    private String contentUrl;

    // ═══════════════════════════════════════════════════════════════════
    // 状态字段
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 文档状态: 1=有效, 0=已删除
     */
    @Column(nullable = false)
    private Integer status = 1;

    /**
     * 向量化状态: 0=未处理, 1=已向量化, 2=失败
     */
    @Column(nullable = false)
    private Integer vectorStatus = 0;

    /**
     * 向量化时间
     */
    private LocalDateTime vectorTime;

    /**
     * 向量化生成的片段数量
     */
    private Integer segmentCount;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间 (用于增量同步)
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    // ═══════════════════════════════════════════════════════════════════
    // 临时字段 (不持久化)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 临时内容字段 - 用于知识源返回数据时传递
     * 不会存入数据库，只在内存中使用
     *
     * 使用场景:
     * - FileSystemKnowledgeSource 解析文件后设置此字段
     * - KnowledgePipeline 处理时读取并保存到 knowledge_content 表
     */
    @Transient
    private String content;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 便捷方法
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 判断是否是大文件 (超过 1MB)
     */
    public boolean isLargeContent() {
        return contentSize != null && contentSize > 1024 * 1024;
    }

    /**
     * 获取内容大小的可读格式
     */
    public String getContentSizeReadable() {
        if (contentSize == null) return "未知";
        if (contentSize < 1024) return contentSize + " B";
        if (contentSize < 1024 * 1024) return (contentSize / 1024) + " KB";
        return (contentSize / 1024 / 1024) + " MB";
    }
}
