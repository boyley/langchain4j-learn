package com.example.knowledge.source;

import com.example.knowledge.entity.KnowledgeDocument;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识源接口 - 抽象不同的知识来源
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【设计模式: 策略模式】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 核心设计思想:
 * - 统一接口，不同实现
 * - 业务层无需关心知识来自哪里
 * - 新增知识源只需实现此接口
 *
 *   业务层代码
 *       │
 *       ▼
 *   KnowledgeSource (接口)
 *       │
 *   ┌───┴───┬───────────┬───────────────┐
 *   │       │           │               │
 *   ▼       ▼           ▼               ▼
 * Database FileSystem Confluence     Notion
 *  实现     实现        实现          实现
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【已实现的知识源】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 实现类                     | 来源               | 适用场景              |
 * |---------------------------|--------------------|---------------------|
 * | DatabaseKnowledgeSource   | 数据库              | 知识后台管理、系统集成  |
 * | FileSystemKnowledgeSource | 文件系统            | PDF/Word/TXT 文档    |
 * | ConfluenceKnowledgeSource | Confluence API     | 企业 Wiki 文档        |
 * | NotionKnowledgeSource     | Notion API         | Notion 知识库        |
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【扩展新知识源】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 步骤:
 * 1. 实现 KnowledgeSource 接口
 * 2. 添加 @Component 注解
 * 3. 添加 @ConditionalOnProperty 配置开关
 * 4. 在 application.yml 启用
 *
 * 示例:
 * ```java
 * @Component
 * @ConditionalOnProperty(name = "knowledge.source.notion.enabled", havingValue = "true")
 * public class NotionKnowledgeSource implements KnowledgeSource {
 *     // 实现所有方法...
 * }
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心方法说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 生产环境推荐使用流程:
 *
 *   首次同步:
 *     fetchIncremental(null) → 返回所有文档
 *              │
 *              ▼
 *         处理并记录同步时间
 *
 *   后续同步:
 *     fetchIncremental(lastSyncTime) → 只返回变化的文档
 *              │
 *              ▼
 *         增量处理，效率高
 *
 *   大数据量:
 *     fetchBatch(0, 100) → 第一批
 *     fetchBatch(100, 100) → 第二批
 *     fetchBatch(200, 100) → 第三批
 *     ...
 */
public interface KnowledgeSource {

    // ═══════════════════════════════════════════════════════════════════════════
    // 基础信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取知识源名称
     *
     * 用于:
     * - 日志记录
     * - 同步状态表的 source_name 字段
     * - API 接口的知识源标识
     *
     * @return 知识源名称
     *
     * 命名规范:
     * - 小写字母
     * - 无空格，用下划线分隔
     * - 简短且有意义
     *
     * 示例: "database", "file", "confluence", "notion"
     */
    String getName();

    /**
     * 获取知识源显示名称
     *
     * 用于:
     * - 管理界面展示
     * - 用户友好的描述
     *
     * @return 显示名称
     *
     * 示例: "数据库", "文件系统", "Confluence Wiki"
     */
    String getDisplayName();

    /**
     * 检查知识源是否可用
     *
     * 用于:
     * - 健康检查 (Health Check)
     * - 同步前的可用性验证
     * - 故障转移判断
     *
     * @return true 如果知识源可用，可以进行同步
     *
     * 检查内容 (根据知识源类型):
     * - Database: 数据库连接是否正常
     * - FileSystem: 目录是否存在且可读
     * - Confluence: API 是否可访问
     */
    boolean isAvailable();

    // ═══════════════════════════════════════════════════════════════════════════
    // 文档获取
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 全量获取所有知识文档
     *
     * @return 所有文档列表
     *
     * ⚠️ 注意:
     * - 大数据量时会占用大量内存
     * - 生产环境应使用 fetchIncremental 或 fetchBatch
     * - 主要用于开发测试或小数据量场景
     *
     * 使用建议:
     * - 文档数 < 1000: 可以使用
     * - 文档数 > 1000: 使用 fetchBatch 分批获取
     */
    List<KnowledgeDocument> fetchAll();

    /**
     * 增量获取 - 只获取指定时间后更新的文档
     *
     * ⭐ 这是生产环境的核心方法
     *
     * @param lastSyncTime 上次同步时间
     *                     - null: 表示首次同步，获取全部文档
     *                     - 有值: 只获取该时间之后更新的文档
     *
     * @return 需要同步的文档列表
     *
     * 工作流程:
     * ```
     *   定时任务触发
     *        │
     *        ▼
     *   读取 sync_status 表获取 lastSyncTime
     *        │
     *        ▼
     *   fetchIncremental(lastSyncTime)
     *        │
     *   ┌────┴────┐
     *   │         │
     *   ▼         ▼
     * 首次同步  增量同步
     * (全量)   (只有变化的)
     *   │         │
     *   └────┬────┘
     *        ▼
     *   处理文档并向量化
     *        │
     *        ▼
     *   更新 lastSyncTime = now()
     * ```
     *
     * 增量判断依据:
     * - update_time > lastSyncTime
     * - 或 create_time > lastSyncTime
     */
    List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime);

    /**
     * 分批获取 - 用于大数据量场景
     *
     * @param offset 偏移量 (跳过的记录数)
     * @param limit  每批数量 (返回的记录数)
     *
     * @return 文档列表
     *
     * 避免一次性加载过多数据导致内存溢出 (OOM)
     *
     * 使用示例:
     * ```java
     * int batchSize = 100;
     * long total = source.count();
     *
     * for (int offset = 0; offset < total; offset += batchSize) {
     *     List<KnowledgeDocument> batch = source.fetchBatch(offset, batchSize);
     *     processBatch(batch);
     * }
     * ```
     *
     * 与分页的区别:
     * - 分页 (pageNumber, pageSize): 页码从 0 或 1 开始
     * - 偏移 (offset, limit): 偏移量，更直观
     *
     * 两者等价:
     * - fetchBatch(100, 50) ≈ Page.of(2, 50) (第3页，每页50条)
     */
    List<KnowledgeDocument> fetchBatch(int offset, int limit);

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计与查询
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取文档总数
     *
     * @return 文档总数
     *
     * 用于:
     * - 进度计算 (已处理/总数)
     * - 分批处理的循环条件
     * - 统计报表
     */
    long count();

    /**
     * 根据 ID 获取单个文档
     *
     * @param docId 文档唯一标识
     *              - 格式: "{source}:{id}"
     *              - 例如: "db:doc-001", "file:readme.md"
     *
     * @return 文档对象，不存在返回 null
     *
     * 使用场景:
     * - 单文档更新/重新向量化
     * - 文档详情查询
     * - 调试和排查
     */
    KnowledgeDocument fetchById(String docId);
}
