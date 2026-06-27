package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识文档实体 (元数据) - 对应数据库 knowledge_documents 表
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【企业级设计】内容与元数据分离
 * ═══════════════════════════════════════════════════════════════════════════════
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
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【JPA 实体注解说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * @Entity: 标记为 JPA 实体类
 * @Table: 指定表名和索引
 * @Id: 主键字段
 * @GeneratedValue: 主键生成策略
 * @Column: 列属性 (名称、长度、约束)
 * @Transient: 不持久化的临时字段
 * @PrePersist: 插入前回调
 * @PreUpdate: 更新前回调
 */
/*
 * @Data - Lombok 注解
 *
 * 自动生成:
 * - 所有字段的 getter 和 setter
 * - toString() 方法
 * - equals() 和 hashCode() 方法
 * - 无参构造函数
 *
 * 注意:
 * - 对于 JPA 实体，equals/hashCode 应基于业务键 (如 docId)
 * - @Data 生成的是基于所有字段的，可能需要手动覆盖
 */
@Data
/*
 * @Entity - JPA 实体注解
 *
 * 作用:
 * - 标记此类为 JPA 管理的实体
 * - 对应数据库中的一张表
 * - 必须有无参构造函数
 * - 必须有 @Id 标注的主键
 *
 * 可选参数:
 * - name: 实体名称 (默认为类名)
 */
@Entity
/*
 * @Table - 表映射注解
 *
 * 参数说明:
 * - name: 表名 (不指定则使用类名，驼峰转下划线)
 * - schema: 数据库 schema
 * - catalog: 数据库 catalog
 * - indexes: 索引定义
 * - uniqueConstraints: 唯一约束
 *
 * @Index 子注解:
 * - name: 索引名称
 * - columnList: 列名，多列用逗号分隔
 * - unique: 是否唯一索引
 *
 * 等价 SQL:
 * CREATE TABLE knowledge_documents (...);
 * CREATE UNIQUE INDEX idx_doc_id ON knowledge_documents(doc_id);
 * CREATE INDEX idx_category ON knowledge_documents(category);
 * ...
 */
@Table(name = "knowledge_documents", indexes = {
    @Index(name = "idx_doc_id", columnList = "docId", unique = true),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_update_time", columnList = "updateTime"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_vector_status", columnList = "vectorStatus")
})
public class KnowledgeDocument {

    // ═══════════════════════════════════════════════════════════════════════════
    // 主键
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 自增主键
     *
     * @Id - 标记为主键字段
     *
     * @GeneratedValue - 主键生成策略
     * 策略选项:
     * - GenerationType.IDENTITY: 数据库自增 (MySQL 推荐)
     * - GenerationType.SEQUENCE: 序列 (Oracle/PostgreSQL)
     * - GenerationType.TABLE: 使用表模拟序列
     * - GenerationType.AUTO: 由 JPA 自动选择
     *
     * MySQL 等价: id BIGINT PRIMARY KEY AUTO_INCREMENT
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══════════════════════════════════════════════════════════════════════════
    // 业务字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档唯一标识 (业务主键)
     *
     * 格式: source:unique_id
     * 示例:
     * - db:123 (数据库来源，ID 为 123)
     * - file:/path/to/doc.pdf (文件系统来源)
     * - confluence:page-12345 (Confluence 来源)
     *
     * @Column 参数说明:
     * - name: 列名 (默认字段名，驼峰转下划线: docId → doc_id)
     * - nullable: 是否允许 NULL
     * - unique: 是否唯一
     * - length: 字符串长度 (默认 255)
     * - insertable: 是否可插入 (默认 true)
     * - updatable: 是否可更新 (默认 true)
     * - columnDefinition: 自定义 DDL
     *
     * 等价 SQL: doc_id VARCHAR(128) NOT NULL UNIQUE
     */
    @Column(nullable = false, unique = true, length = 128)
    private String docId;

    /**
     * 文档标题
     *
     * 等价 SQL: title VARCHAR(255) NOT NULL
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * 文档摘要 (前 500 字符)
     *
     * 用途:
     * - 列表页预览
     * - 搜索结果展示
     * - 不需要加载完整内容
     */
    @Column(length = 500)
    private String summary;

    /**
     * 文档分类
     *
     * 示例: "HR政策", "技术文档", "产品手册"
     *
     * 用途:
     * - 向量搜索时按分类过滤
     * - 管理界面分类展示
     */
    @Column(length = 64)
    private String category;

    /**
     * 文档版本
     *
     * 默认 "1.0"
     * 用于版本管理和变更追踪
     */
    @Column(length = 32)
    private String version = "1.0";

    /**
     * 文档来源
     *
     * 标识知识来自哪个知识源
     * 值: "database" / "file" / "confluence" / "notion" / "api"
     */
    @Column(length = 32)
    private String source;

    // ═══════════════════════════════════════════════════════════════════════════
    // 内容元信息 (不存储实际内容)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 内容大小 (字节)
     *
     * 用途:
     * - 判断是否是大文件
     * - 决定处理策略 (流式 vs 全量)
     * - 展示文件大小
     *
     * 判断大文件: contentSize > 1MB
     */
    @Column
    private Long contentSize;

    /**
     * 内容哈希 (MD5/SHA256)
     *
     * 用途:
     * - 判断内容是否变化
     * - 避免重复处理未变化的文档
     * - 去重检测
     *
     * 示例: "e10adc3949ba59abbe56e057f20f883e"
     */
    @Column(length = 64)
    private String contentHash;

    /**
     * 内容存储位置
     *
     * 值:
     * - "database": 存在 knowledge_content 表
     * - "oss": 存在对象存储 (阿里云 OSS / AWS S3)
     * - "local": 本地文件系统
     */
    @Column(length = 32)
    private String contentLocation = "database";

    /**
     * 对象存储 URL (如果存在 OSS)
     *
     * 示例: "https://bucket.oss.com/docs/xxx.pdf"
     */
    @Column(length = 500)
    private String contentUrl;

    // ═══════════════════════════════════════════════════════════════════════════
    // 状态字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档状态
     *
     * 值:
     * - 1: 有效 (正常使用)
     * - 0: 已删除 (软删除)
     *
     * 为什么用软删除:
     * - 可恢复
     * - 保留历史记录
     * - 关联数据不会失效
     */
    @Column(nullable = false)
    private Integer status = 1;

    /**
     * 向量化状态
     *
     * 值:
     * - 0: 未处理 (待向量化)
     * - 1: 已向量化 (成功)
     * - 2: 失败 (需要排查)
     *
     * 状态机:
     *   0 (未处理) → 处理中 → 1 (成功)
     *                    ↓
     *                    2 (失败) → 重试 → 1 (成功)
     */
    @Column(nullable = false)
    private Integer vectorStatus = 0;

    /**
     * 向量化完成时间
     *
     * 用于:
     * - 统计处理时间
     * - 排查问题
     */
    private LocalDateTime vectorTime;

    /**
     * 向量化生成的片段数量
     *
     * 一篇文档会被分割成多个片段 (TextSegment)
     * 每个片段独立向量化并存储
     */
    private Integer segmentCount;

    /**
     * 创建时间
     *
     * updatable = false: 创建后不允许修改
     * 由 @PrePersist 自动设置
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     *
     * 用于增量同步:
     * - 每次修改自动更新 (@PreUpdate)
     * - 增量同步时查询 updateTime > lastSyncTime
     */
    @Column(nullable = false)
    private LocalDateTime updateTime;

    // ═══════════════════════════════════════════════════════════════════════════
    // 临时字段 (不持久化)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 临时内容字段 - 用于知识源返回数据时传递
     *
     * @Transient 注解说明:
     * - 标记此字段不持久化到数据库
     * - JPA 不会为此字段生成列
     * - 只存在于 Java 对象内存中
     *
     * 使用场景:
     * 1. FileSystemKnowledgeSource 解析文件后:
     *    doc.setContent(parsedContent);
     *
     * 2. KnowledgePipeline 处理时:
     *    String content = doc.getContent();
     *    if (content != null) {
     *        contentService.save(doc.getDocId(), content);
     *    }
     *
     * 3. 处理完成后清空 (帮助 GC):
     *    doc.setContent(null);
     */
    @Transient
    private String content;

    // ═══════════════════════════════════════════════════════════════════════════
    // JPA 生命周期回调
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 插入前回调
     *
     * @PrePersist 注解说明:
     * - 在 INSERT 语句执行前调用
     * - 用于设置默认值
     * - 常用于设置创建时间、更新时间
     *
     * 调用时机:
     * entityManager.persist(entity) 或 repository.save(newEntity)
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 更新前回调
     *
     * @PreUpdate 注解说明:
     * - 在 UPDATE 语句执行前调用
     * - 用于自动更新字段
     * - 常用于更新 updateTime
     *
     * 调用时机:
     * 实体字段有变化并执行 flush 时
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 便捷方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 判断是否是大文件 (超过 1MB)
     *
     * @return true 如果内容大于 1MB
     *
     * 大文件处理策略:
     * - 流式读取
     * - 分块处理
     * - 异步处理
     */
    public boolean isLargeContent() {
        return contentSize != null && contentSize > 1024 * 1024;
    }

    /**
     * 获取内容大小的可读格式
     *
     * @return 人类可读的大小，如 "1.5 MB"
     *
     * 示例:
     * - 512 → "512 B"
     * - 1536 → "1 KB"
     * - 1572864 → "1 MB"
     */
    public String getContentSizeReadable() {
        if (contentSize == null) return "未知";
        if (contentSize < 1024) return contentSize + " B";
        if (contentSize < 1024 * 1024) return (contentSize / 1024) + " KB";
        return (contentSize / 1024 / 1024) + " MB";
    }
}
