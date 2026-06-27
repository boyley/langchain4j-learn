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
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【设计说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 与 KnowledgeDocumentRepository 配合使用，实现内容分离存储:
 *
 *   KnowledgeDocumentRepository          KnowledgeContentRepository
 *   ┌──────────────────────────┐        ┌──────────────────────────┐
 *   │ 查询元数据 (轻量)         │        │ 查询内容 (按需)          │
 *   │                          │        │                          │
 *   │ findByDocId()            │        │ findByDocId()            │
 *   │ findByStatus()           │        │ findContentByDocId()     │
 *   │ 返回: 不含 content        │        │ 返回: 完整 content       │
 *   └──────────────────────────┘        └──────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用场景】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 批量查询文档列表 (不需要内容):
 *    ```java
 *    List<KnowledgeDocument> docs = documentRepository.findByStatus(1);
 *    // 只查询元数据，内存占用小
 *    ```
 *
 * 2. 处理单个文档时加载内容:
 *    ```java
 *    for (KnowledgeDocument doc : docs) {
 *        String content = contentRepository.findContentByDocId(doc.getDocId())
 *            .orElseThrow(() -> new NotFoundException("内容不存在"));
 *        // 处理内容...
 *    }
 *    // 每次只加载一篇文档的内容
 *    ```
 *
 * 3. 检查内容是否变化:
 *    ```java
 *    String existingHash = contentRepository.findContentHashByDocId(docId).orElse(null);
 *    String newHash = computeHash(newContent);
 *    if (!newHash.equals(existingHash)) {
 *        // 内容有变化，需要重新向量化
 *    }
 *    ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【性能优化】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * findContentByDocId vs findByDocId:
 *
 *   findByDocId():
 *   - 加载整个实体 (id, docId, content, contentSize, contentHash, ...)
 *   - 适合需要多个字段的场景
 *
 *   findContentByDocId():
 *   - 只加载 content 字段
 *   - 减少网络传输
 *   - 适合只需要内容的场景
 */
@Repository
public interface KnowledgeContentRepository extends JpaRepository<KnowledgeContent, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // 基础查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 根据文档ID查找完整内容实体
     *
     * @param docId 文档唯一标识
     * @return Optional 包装的内容实体
     *
     * 返回完整的 KnowledgeContent 实体，包含:
     * - id, docId, content, contentSize, contentHash, createTime, updateTime
     *
     * 适用场景:
     * - 需要修改内容
     * - 需要多个字段
     */
    Optional<KnowledgeContent> findByDocId(String docId);

    /**
     * 判断内容是否存在
     *
     * @param docId 文档ID
     * @return true 如果内容存在
     *
     * 用途:
     * - 保存前检查是插入还是更新
     * - 验证数据完整性
     *
     * 生成 SQL: SELECT COUNT(*) > 0 FROM knowledge_content WHERE doc_id = ?
     * 比 findByDocId 更高效 (不需要加载内容)
     */
    boolean existsByDocId(String docId);

    // ═══════════════════════════════════════════════════════════════════════════
    // 删除操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 根据文档ID删除内容
     *
     * @param docId 文档ID
     * @return 删除的记录数 (0 或 1)
     *
     * @Modifying: 标记为修改操作
     *
     * 用途:
     * - 文档删除时同步删除内容
     * - 文档更新时先删除旧内容
     *
     * 注意: 需要在事务中调用
     */
    @Modifying
    @Query("DELETE FROM KnowledgeContent c WHERE c.docId = :docId")
    int deleteByDocId(@Param("docId") String docId);

    // ═══════════════════════════════════════════════════════════════════════════
    // 投影查询 (只查询特定字段)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 只查询内容文本 (不加载整个实体)
     *
     * @param docId 文档ID
     * @return Optional 包装的内容字符串
     *
     * 投影查询的好处:
     * - 只传输需要的字段
     * - 减少内存占用
     * - 提高查询效率
     *
     * 对比:
     * - findByDocId(): 加载整个实体 (id, docId, content, ...)
     * - findContentByDocId(): 只加载 content 字段
     *
     * 生成 SQL: SELECT content FROM knowledge_content WHERE doc_id = ?
     */
    @Query("SELECT c.content FROM KnowledgeContent c WHERE c.docId = :docId")
    Optional<String> findContentByDocId(@Param("docId") String docId);

    /**
     * 查询内容哈希 (用于变更检测)
     *
     * @param docId 文档ID
     * @return Optional 包装的哈希值
     *
     * 用途: 检查内容是否变化
     *
     * 比较逻辑:
     * ```java
     * String existingHash = repository.findContentHashByDocId(docId).orElse(null);
     * String newHash = computeHash(newContent);
     *
     * if (!Objects.equals(existingHash, newHash)) {
     *     // 内容已变化，需要更新
     * } else {
     *     // 内容未变化，跳过处理
     * }
     * ```
     *
     * 优点:
     * - 不需要加载完整内容就能判断变化
     * - 对于大文档，节省大量网络和内存开销
     */
    @Query("SELECT c.contentHash FROM KnowledgeContent c WHERE c.docId = :docId")
    Optional<String> findContentHashByDocId(@Param("docId") String docId);
}
