package com.example.knowledge.repository;

import com.example.knowledge.entity.KnowledgeContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 知识内容数据访问层
 *
 * 与 KnowledgeDocumentRepository 配合使用:
 * - KnowledgeDocumentRepository: 查询元数据 (轻量)
 * - KnowledgeContentRepository: 查询内容 (按需)
 */
@Repository
public interface KnowledgeContentRepository extends JpaRepository<KnowledgeContent, Long> {

    /**
     * 根据文档ID查找内容
     */
    Optional<KnowledgeContent> findByDocId(String docId);

    /**
     * 判断内容是否存在
     */
    boolean existsByDocId(String docId);

    /**
     * 根据文档ID删除内容
     */
    @Modifying
    @Query("DELETE FROM KnowledgeContent c WHERE c.docId = :docId")
    int deleteByDocId(@Param("docId") String docId);

    /**
     * 只查询内容文本 (不加载整个实体)
     */
    @Query("SELECT c.content FROM KnowledgeContent c WHERE c.docId = :docId")
    Optional<String> findContentByDocId(@Param("docId") String docId);

    /**
     * 检查内容是否变化 (通过哈希比较)
     */
    @Query("SELECT c.contentHash FROM KnowledgeContent c WHERE c.docId = :docId")
    Optional<String> findContentHashByDocId(@Param("docId") String docId);
}
