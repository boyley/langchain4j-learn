package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识文档实体 - 对应数据库 knowledge_documents 表
 *
 * 这是知识库的核心实体，存储原始文档信息。
 * 向量化后的数据存储在向量数据库中，通过 docId 关联。
 */
@Data
@Entity
@Table(name = "knowledge_documents", indexes = {
    @Index(name = "idx_doc_id", columnList = "docId", unique = true),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_update_time", columnList = "updateTime"),
    @Index(name = "idx_source", columnList = "source")
})
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文档唯一标识
     * 用于向量存储中关联原始文档
     * 格式建议: source:unique_id, 如 mysql:123, file:/path/to/doc.pdf
     */
    @Column(nullable = false, unique = true, length = 128)
    private String docId;

    /**
     * 文档标题
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * 文档内容
     * 使用 TEXT 类型存储长文本
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 文档分类
     * 用于过滤检索，如: HR政策, 技术文档, 产品手册
     */
    @Column(length = 64)
    private String category;

    /**
     * 文档版本
     * 用于版本管理和增量更新
     */
    @Column(length = 32)
    private String version = "1.0";

    /**
     * 文档来源
     * mysql / file / confluence / notion / api
     */
    @Column(length = 32)
    private String source;

    /**
     * 文档状态
     * 1=有效, 0=已删除
     */
    @Column(nullable = false)
    private Integer status = 1;

    /**
     * 向量化状态
     * 0=未处理, 1=已向量化, 2=向量化失败
     */
    @Column(nullable = false)
    private Integer vectorStatus = 0;

    /**
     * 向量化时间
     */
    private LocalDateTime vectorTime;

    /**
     * 文档创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 文档更新时间
     * 用于增量同步判断
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
