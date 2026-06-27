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
 * 知识文档数据访问层
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【Spring Data JPA 基础】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * JpaRepository<Entity, ID> 继承层次:
 *
 *   Repository (标记接口)
 *       │
 *       ▼
 *   CrudRepository (基础 CRUD)
 *       │ - save(), findById(), delete(), count()
 *       ▼
 *   PagingAndSortingRepository (分页排序)
 *       │ - findAll(Pageable), findAll(Sort)
 *       ▼
 *   JpaRepository (JPA 特有功能)
 *         - flush(), saveAndFlush(), deleteInBatch()
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【查询方法命名规则】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Spring Data 根据方法名自动生成 SQL:
 *
 * | 方法名                    | 生成的 SQL                           |
 * |--------------------------|--------------------------------------|
 * | findByDocId              | WHERE doc_id = ?                     |
 * | findByStatus             | WHERE status = ?                     |
 * | findBySourceAndStatus    | WHERE source = ? AND status = ?      |
 * | findByUpdateTimeAfter    | WHERE update_time > ?                |
 * | countByStatus            | SELECT COUNT(*) WHERE status = ?     |
 * | deleteByDocId            | DELETE WHERE doc_id = ?              |
 *
 * 支持的关键词:
 * - And / Or: 条件组合
 * - Is, Equals: 等于
 * - Between: 范围
 * - LessThan / GreaterThan: 比较
 * - After / Before: 时间比较
 * - Like / Containing: 模糊查询
 * - In / NotIn: 列表包含
 * - OrderBy: 排序
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【自定义查询注解】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * @Query: 自定义 JPQL 或原生 SQL
 * @Modifying: 标记修改操作 (UPDATE/DELETE)
 * @Param: 命名参数绑定
 *
 * 示例:
 * ```java
 * @Query("SELECT d FROM KnowledgeDocument d WHERE d.category = :cat")
 * List<KnowledgeDocument> findByCategory(@Param("cat") String category);
 *
 * @Query(value = "SELECT * FROM documents WHERE MATCH(content) AGAINST(:keyword)", nativeQuery = true)
 * List<KnowledgeDocument> fullTextSearch(@Param("keyword") String keyword);
 * ```
 */
/*
 * @Repository - Spring 仓库注解
 *
 * 作用:
 * 1. 标记为数据访问层组件
 * 2. 将数据访问异常转换为 Spring DataAccessException
 * 3. 便于组件扫描和注入
 *
 * 对于 JpaRepository 接口，@Repository 可以省略
 * 因为 Spring Data JPA 会自动识别并创建代理实现
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    // ═══════════════════════════════════════════════════════════════════════════
    // 基础查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 根据 docId 查找文档
     *
     * @param docId 文档唯一标识
     * @return Optional 包装的文档对象
     *
     * 为什么返回 Optional:
     * - 明确表示可能不存在
     * - 避免空指针异常
     * - 强制调用方处理不存在的情况
     *
     * 使用方式:
     * ```java
     * Optional<KnowledgeDocument> doc = repository.findByDocId("db:001");
     * doc.ifPresent(d -> process(d));
     * // 或
     * KnowledgeDocument doc = repository.findByDocId("db:001")
     *     .orElseThrow(() -> new NotFoundException("文档不存在"));
     * ```
     *
     * 生成的 SQL: SELECT * FROM knowledge_documents WHERE doc_id = ?
     */
    Optional<KnowledgeDocument> findByDocId(String docId);

    // ═══════════════════════════════════════════════════════════════════════════
    // 增量同步查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查找指定时间后更新的文档 (用于增量同步)
     *
     * @param source     知识源名称，如 "database", "file"
     * @param updateTime 上次同步时间
     * @return 需要同步的文档列表
     *
     * @Query 注解说明:
     * - value: JPQL 查询语句 (不是 SQL)
     * - JPQL 使用实体类名和属性名，不是表名和列名
     * - :source 是命名参数，由 @Param("source") 绑定
     *
     * @Param 注解说明:
     * - 将方法参数绑定到查询中的命名参数
     * - @Param("source") 对应查询中的 :source
     *
     * JPQL vs SQL:
     * - JPQL: SELECT d FROM KnowledgeDocument d WHERE d.source = :source
     * - SQL:  SELECT * FROM knowledge_documents WHERE source = ?
     */
    @Query("SELECT d FROM KnowledgeDocument d WHERE d.source = :source AND d.status = 1 AND d.updateTime > :updateTime ORDER BY d.updateTime")
    List<KnowledgeDocument> findBySourceAndUpdateTimeAfter(
            @Param("source") String source,
            @Param("updateTime") LocalDateTime updateTime);

    // ═══════════════════════════════════════════════════════════════════════════
    // 全量同步查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查找指定来源的所有有效文档 (用于全量同步)
     *
     * @param source 知识源名称
     * @param status 文档状态 (1=有效)
     * @return 文档列表
     *
     * 方法名解析规则:
     * - findBy: 查询
     * - Source: 按 source 字段
     * - And: 多条件组合
     * - Status: 按 status 字段
     *
     * 生成的 SQL: SELECT * FROM knowledge_documents WHERE source = ? AND status = ?
     */
    List<KnowledgeDocument> findBySourceAndStatus(String source, Integer status);

    /**
     * 分页查找指定来源的文档 (用于批量处理)
     *
     * @param source   知识源名称
     * @param status   文档状态
     * @param pageable 分页参数
     * @return 分页结果
     *
     * Pageable 参数说明:
     * - 包含 pageNumber (页码) 和 pageSize (每页大小)
     * - 可包含排序信息
     *
     * 创建 Pageable:
     * ```java
     * Pageable pageable = PageRequest.of(0, 100); // 第一页，每页100条
     * Pageable pageable = PageRequest.of(0, 100, Sort.by("updateTime").descending());
     * ```
     *
     * Page 返回值:
     * - getContent(): 当前页数据
     * - getTotalElements(): 总记录数
     * - getTotalPages(): 总页数
     * - hasNext(): 是否有下一页
     */
    Page<KnowledgeDocument> findBySourceAndStatus(String source, Integer status, Pageable pageable);

    // ═══════════════════════════════════════════════════════════════════════════
    // 向量化相关
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查找需要向量化的文档
     *
     * @param pageable 分页参数 (限制每次处理数量)
     * @return 待处理文档列表
     *
     * 条件:
     * - status = 1 (有效文档)
     * - vectorStatus = 0 (未向量化)
     *
     * 按 updateTime 排序: 先处理旧文档
     */
    @Query("SELECT d FROM KnowledgeDocument d WHERE d.status = 1 AND d.vectorStatus = 0 ORDER BY d.updateTime")
    List<KnowledgeDocument> findPendingVectorization(Pageable pageable);

    /**
     * 更新向量化状态
     *
     * @param docId        文档ID
     * @param status       向量状态 (0=未处理, 1=成功, 2=失败)
     * @param vectorTime   向量化时间
     * @param segmentCount 生成的片段数量
     *
     * @Modifying 注解说明:
     * - 标记此方法会修改数据 (UPDATE/DELETE)
     * - 必须在事务中执行
     * - 执行后会清空持久化上下文 (默认)
     *
     * 可选参数:
     * - @Modifying(clearAutomatically = true): 执行后清空缓存
     * - @Modifying(flushAutomatically = true): 执行前刷新缓存
     *
     * 使用注意:
     * - 调用方必须有 @Transactional
     * - 否则抛出 TransactionRequiredException
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.vectorStatus = :status, d.vectorTime = :vectorTime, d.segmentCount = :segmentCount WHERE d.docId = :docId")
    void updateVectorStatus(@Param("docId") String docId,
                            @Param("status") Integer status,
                            @Param("vectorTime") LocalDateTime vectorTime,
                            @Param("segmentCount") Integer segmentCount);

    // ═══════════════════════════════════════════════════════════════════════════
    // 通用查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 查找所有有效文档 (不限来源)
     *
     * @param status 状态
     * @return 文档列表
     */
    List<KnowledgeDocument> findByStatus(Integer status);

    /**
     * 查找指定时间后更新的有效文档 (不限来源)
     *
     * @param status     状态
     * @param updateTime 更新时间
     * @return 文档列表
     *
     * 方法名: findByStatusAndUpdateTimeAfter
     * - findBy: 查询
     * - Status: 按 status 字段
     * - And: 组合条件
     * - UpdateTime: 按 updateTime 字段
     * - After: 大于指定时间 (>)
     */
    List<KnowledgeDocument> findByStatusAndUpdateTimeAfter(Integer status, LocalDateTime updateTime);

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 按分类统计文档数量
     *
     * @return 分类统计结果，每个元素是 [category, count]
     *
     * JPQL 聚合函数:
     * - COUNT(): 计数
     * - SUM(): 求和
     * - AVG(): 平均
     * - MAX()/MIN(): 最大/最小
     *
     * GROUP BY: 分组
     *
     * 返回 List<Object[]> 说明:
     * - 每个 Object[] 对应一行结果
     * - row[0] = category (String)
     * - row[1] = count (Long)
     *
     * 使用方式:
     * ```java
     * List<Object[]> stats = repository.countByCategory();
     * for (Object[] row : stats) {
     *     String category = (String) row[0];
     *     Long count = (Long) row[1];
     * }
     * ```
     */
    @Query("SELECT d.category, COUNT(d) FROM KnowledgeDocument d WHERE d.status = 1 GROUP BY d.category")
    List<Object[]> countByCategory();

    /**
     * 按来源统计文档数量
     *
     * @return 来源统计结果
     */
    @Query("SELECT d.source, COUNT(d) FROM KnowledgeDocument d WHERE d.status = 1 GROUP BY d.source")
    List<Object[]> countBySource();

    /**
     * 统计有效文档总数
     *
     * @param status 状态
     * @return 文档数量
     *
     * 方法名规则: countBy + 属性名
     * 生成 SQL: SELECT COUNT(*) FROM knowledge_documents WHERE status = ?
     */
    long countByStatus(Integer status);

    /**
     * 统计指定状态的文档数
     *
     * @param status       文档状态
     * @param vectorStatus 向量状态
     * @return 文档数量
     *
     * 用途:
     * - countByStatusAndVectorStatus(1, 1): 已向量化的文档数
     * - countByStatusAndVectorStatus(1, 0): 待向量化的文档数
     * - countByStatusAndVectorStatus(1, 2): 向量化失败的文档数
     */
    long countByStatusAndVectorStatus(Integer status, Integer vectorStatus);

    // ═══════════════════════════════════════════════════════════════════════════
    // 删除操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 软删除文档
     *
     * @param docId 文档ID
     *
     * 软删除 vs 硬删除:
     * - 软删除: 设置 status = 0，数据保留
     * - 硬删除: DELETE，数据物理删除
     *
     * 为什么用软删除:
     * - 可恢复
     * - 保留历史
     * - 关联数据不会断裂
     *
     * CURRENT_TIMESTAMP: JPA 函数，返回当前时间
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.status = 0, d.updateTime = CURRENT_TIMESTAMP WHERE d.docId = :docId")
    void softDelete(@Param("docId") String docId);

    /**
     * 批量软删除
     *
     * @param docIds 文档ID列表
     *
     * IN 子句: 匹配列表中的任意值
     * 比循环调用 softDelete 效率高
     */
    @Modifying
    @Query("UPDATE KnowledgeDocument d SET d.status = 0, d.updateTime = CURRENT_TIMESTAMP WHERE d.docId IN :docIds")
    void softDeleteBatch(@Param("docIds") List<String> docIds);
}
