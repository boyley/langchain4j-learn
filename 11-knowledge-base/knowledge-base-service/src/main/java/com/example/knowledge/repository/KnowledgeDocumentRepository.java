package com.example.knowledge.repository;

import com.example.knowledge.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 知识文档 Repository
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * 根据 docId 查找文档
     */
    Optional<KnowledgeDocument> findByDocId(String docId);

    /**
     * 查找指定时间后更新的文档 (用于增量同步)
     *
     * @param source     知识源
     * @param updateTime 更新时间
     * @return 需要同步的文档列表
     */
    @Query("SELECT d FROM KnowledgeDocument d WHERE d.source = :source AND d.status = 1 AND d.updateTime > :updateTime ORDER BY d.updateTime")
    List<KnowledgeDocument> findBySourceAndUpdateTimeAfter(
            @Param("source") String source,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查找指定来源的所有有效文档 (用于全量同步)
     */
    List<KnowledgeDocument> findBySourceAndStatus(String source, Integer status);

    /**
     * 分页查找指定来源的文档 (用于批量处理)
     */
    Page<KnowledgeDocument> findBySourceAndStatus(String source, Integer status, Pageable pageable);

    /**
     * 查找需要向量化的文档
     */
    @Query("SELECT d FROM KnowledgeDocument d WHERE d.status = 1 AND d.vectorStatus = 0 ORDER BY d.updateTime")
    List<KnowledgeDocument> findPendingVectorization(Pageable pageable);

    /**
     * 更新向量化状态 (含片段数)
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.vectorStatus = :status, d.vectorTime = :vectorTime, d.segmentCount = :segmentCount WHERE d.docId = :docId")
    void updateVectorStatus(@Param("docId") String docId,
                            @Param("status") Integer status,
                            @Param("vectorTime") LocalDateTime vectorTime,
                            @Param("segmentCount") Integer segmentCount);

    /**
     * 查找所有有效文档 (不限来源)
     */
    List<KnowledgeDocument> findByStatus(Integer status);

    /**
     * 查找指定时间后更新的有效文档 (不限来源，用于增量同步)
     */
    List<KnowledgeDocument> findByStatusAndUpdateTimeAfter(Integer status, LocalDateTime updateTime);

    /**
     * 按分类统计文档数量
     */
    @Query("SELECT d.category, COUNT(d) FROM KnowledgeDocument d WHERE d.status = 1 GROUP BY d.category")
    List<Object[]> countByCategory();

    /**
     * 按来源统计文档数量
     */
    @Query("SELECT d.source, COUNT(d) FROM KnowledgeDocument d WHERE d.status = 1 GROUP BY d.source")
    List<Object[]> countBySource();

    /**
     * 统计有效文档总数
     */
    long countByStatus(Integer status);

    /**
     * 统计已向量化文档数
     */
    long countByStatusAndVectorStatus(Integer status, Integer vectorStatus);

    /**
     * 软删除文档
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.status = 0, d.updateTime = CURRENT_TIMESTAMP WHERE d.docId = :docId")
    void softDelete(@Param("docId") String docId);

    /**
     * 批量软删除
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.status = 0, d.updateTime = CURRENT_TIMESTAMP WHERE d.docId IN :docIds")
    void softDeleteBatch(@Param("docIds") List<String> docIds);
}
