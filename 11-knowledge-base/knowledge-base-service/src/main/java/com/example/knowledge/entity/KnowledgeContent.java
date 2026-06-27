package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识文档内容实体 - 分离存储大内容
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【设计说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 为什么分离存储:
 *
 *   场景: 批量查询 1000 篇文档用于同步
 *
 *   旧方案 (内容在主表):
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │ SELECT * FROM knowledge_documents WHERE status = 1                 │
 *   │                                                                     │
 *   │ 返回: [{id, title, content(1MB), ...}, ...]  × 1000 条              │
 *   │ 内存: 1000 × 1MB = 1GB                       ← OOM!                │
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 *   新方案 (内容分表):
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │ Step 1: SELECT id, docId, title FROM knowledge_documents           │
 *   │         返回: 1000 条元数据，约 100KB                                │
 *   │                                                                     │
 *   │ Step 2: 逐个处理时才查内容                                           │
 *   │         SELECT content FROM knowledge_content WHERE docId = ?       │
 *   │         每次只加载一篇文档的内容                                      │
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【MySQL 文本类型对比】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 类型       | 最大长度       | 说明                    |
 * |-----------|---------------|------------------------|
 * | TINYTEXT  | 255 字节       | 约 255 字符             |
 * | TEXT      | 65,535 字节    | 约 64 KB               |
 * | MEDIUMTEXT| 16,777,215 字节| 约 16 MB               |
 * | LONGTEXT  | 4,294,967,295 | 约 4 GB                |
 *
 * 本实体使用 LONGTEXT 以支持大型文档
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【关联关系】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   KnowledgeDocument (元数据)          KnowledgeContent (内容)
 *   ┌─────────────────────┐            ┌─────────────────────┐
 *   │ docId = "db:001"    │────────────│ docId = "db:001"    │
 *   │ title = "员工手册"   │    1 : 1   │ content = "..."     │
 *   │ contentSize = 51200 │            │ contentSize = 51200 │
 *   │ contentHash = "xxx" │            │ contentHash = "xxx" │
 *   └─────────────────────┘            └─────────────────────┘
 *
 * 通过 docId 关联，不使用 JPA @OneToOne:
 * - 避免自动懒加载带来的 N+1 问题
 * - 明确控制何时加载内容
 */
@Data
@Entity
/*
 * @Table 参数说明:
 * - name: 表名
 * - indexes: 索引定义
 *   - idx_content_doc_id: docId 的唯一索引，用于快速查找
 */
@Table(name = "knowledge_content", indexes = {
    @Index(name = "idx_content_doc_id", columnList = "docId", unique = true)
})
public class KnowledgeContent {

    // ═══════════════════════════════════════════════════════════════════════════
    // 主键
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 自增主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══════════════════════════════════════════════════════════════════════════
    // 关联字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 关联的文档ID
     *
     * 与 KnowledgeDocument.docId 对应
     * 通过业务主键关联而非外键，便于数据管理
     *
     * unique = true: 一篇文档只有一条内容记录
     */
    @Column(nullable = false, unique = true, length = 128)
    private String docId;

    // ═══════════════════════════════════════════════════════════════════════════
    // 内容字段
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档完整内容
     *
     * @Lob 注解说明:
     * - 标记为大对象 (Large Object)
     * - 对于 String 类型，映射为 TEXT/CLOB
     * - 对于 byte[] 类型，映射为 BLOB
     *
     * columnDefinition = "LONGTEXT":
     * - 显式指定 MySQL 列类型
     * - LONGTEXT 支持最大 4GB
     * - 如果不指定，JPA 默认可能使用 TEXT (只有 64KB)
     *
     * 等价 SQL: content LONGTEXT NOT NULL
     */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 内容大小 (字节)
     *
     * 冗余存储，避免每次都计算
     * 由 @PrePersist/@PreUpdate 自动设置
     */
    @Column
    private Long contentSize;

    /**
     * 内容哈希 (MD5)
     *
     * 用途:
     * - 检测内容是否变化
     * - 避免重复处理相同内容
     * - 去重检测
     *
     * 生成方式: MD5(content)
     */
    @Column(length = 64)
    private String contentHash;

    // ═══════════════════════════════════════════════════════════════════════════
    // 时间戳
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // JPA 生命周期回调
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 插入前回调
     *
     * 自动设置:
     * - 创建时间
     * - 更新时间
     * - 内容大小 (如果有内容)
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (content != null) {
            contentSize = (long) content.getBytes().length;
        }
    }

    /**
     * 更新前回调
     *
     * 自动设置:
     * - 更新时间
     * - 内容大小 (如果有内容)
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (content != null) {
            contentSize = (long) content.getBytes().length;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 静态工厂方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 创建内容实体 (静态工厂方法)
     *
     * @param docId   文档ID
     * @param content 文档内容
     * @return KnowledgeContent 实体
     *
     * 使用工厂方法的好处:
     * - 自动计算 contentSize 和 contentHash
     * - 避免忘记设置必要字段
     * - 语义更清晰
     *
     * 使用示例:
     * ```java
     * KnowledgeContent content = KnowledgeContent.of("db:001", "文档内容...");
     * repository.save(content);
     * ```
     */
    public static KnowledgeContent of(String docId, String content) {
        KnowledgeContent entity = new KnowledgeContent();
        entity.setDocId(docId);
        entity.setContent(content);
        entity.setContentSize((long) content.getBytes().length);
        entity.setContentHash(computeHash(content));
        return entity;
    }

    /**
     * 计算内容哈希
     *
     * 使用 MD5 算法 (速度快，32 字符)
     * 生产环境如需更强安全性可改用 SHA256
     *
     * @param content 内容
     * @return 32 字符的 MD5 哈希值
     *
     * 示例: "hello" → "5d41402abc4b2a76b9719d911017c592"
     */
    private static String computeHash(String content) {
        try {
            // 获取 MD5 摘要算法实例
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");

            // 计算哈希
            byte[] hash = md.digest(content.getBytes());

            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 降级方案: 使用 Java hashCode
            return String.valueOf(content.hashCode());
        }
    }
}
