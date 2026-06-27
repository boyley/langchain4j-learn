package com.example.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识文档内容实体 - 分离存储大内容
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【设计说明】
 * ═══════════════════════════════════════════════════════════════════════════
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
 * 使用 LONGTEXT:
 * - MySQL TEXT: 最大 65KB
 * - MySQL MEDIUMTEXT: 最大 16MB
 * - MySQL LONGTEXT: 最大 4GB
 */
@Data
@Entity
@Table(name = "knowledge_content", indexes = {
    @Index(name = "idx_content_doc_id", columnList = "docId", unique = true)
})
public class KnowledgeContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的文档ID (与 KnowledgeDocument.docId 对应)
     */
    @Column(nullable = false, unique = true, length = 128)
    private String docId;

    /**
     * 文档完整内容
     * 使用 LONGTEXT 支持最大 4GB
     * 使用 @Lob 标注大对象
     */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 内容大小 (字节)
     */
    @Column
    private Long contentSize;

    /**
     * 内容哈希 (用于变更检测)
     */
    @Column(length = 64)
    private String contentHash;

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
        if (content != null) {
            contentSize = (long) content.getBytes().length;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (content != null) {
            contentSize = (long) content.getBytes().length;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 静态工厂方法
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 创建内容实体
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
     * 计算内容哈希 (简单实现，生产环境用 SHA256)
     */
    private static String computeHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
