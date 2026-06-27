package com.example.knowledge.pipeline;

import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.entity.SyncStatus;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import com.example.knowledge.repository.SyncStatusRepository;
import com.example.knowledge.service.ContentService;
import com.example.knowledge.source.KnowledgeSource;
import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识处理管道
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心处理流程】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   知识源                分割              向量化            存储
 *   ┌─────────┐      ┌─────────┐      ┌─────────┐      ┌─────────┐
 *   │ Database│      │ Document│      │ Embedding│      │  Vector │
 *   │ File    │ ───► │ Splitter│ ───► │  Model  │ ───► │  Store  │
 *   │ API     │      │         │      │         │      │         │
 *   └─────────┘      └─────────┘      └─────────┘      └─────────┘
 *      │                 │                 │                │
 *   获取文档         分割成片段        转换为向量        保存到向量库
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【企业级设计】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 增量同步
 *    - 基于 lastSyncTime 只处理变化的文档
 *    - 减少重复处理，提高效率
 *
 * 2. 批量处理
 *    - 分批获取和处理文档
 *    - 避免一次性加载大量数据导致 OOM
 *
 * 3. 内容分离
 *    - 元数据和内容分开存储
 *    - 批量查询时只加载元数据
 *    - 处理时按需加载内容
 *
 * 4. 失败重试
 *    - 记录处理失败的文档
 *    - 支持重新处理
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【文档分割原理】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 为什么要分割:
 * - 嵌入模型有 Token 限制 (如 8192)
 * - 长文档向量化效果差
 * - 分割后检索更精准
 *
 * 分割策略:
 *   ┌────────────────────────────────────────────────────────────────────┐
 *   │ 原始文档 (5000 字符)                                                │
 *   │ "第一章 概述...第二章 详解...第三章 总结..."                         │
 *   └────────────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼ DocumentSplitter.recursive(500, 50)
 *   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
 *   │ 片段 1       │  │ 片段 2       │  │ 片段 3       │  ...
 *   │ 第一章 概述...│  │ ...第二章...  │  │ ...第三章...  │
 *   │ (500 字符)   │  │ (500 字符)   │  │ (500 字符)   │
 *   └──────────────┘  └──────────────┘  └──────────────┘
 *         ↑                                    ↑
 *     重叠 50 字符 ─────────────────────────────┘
 *
 * 参数说明:
 * - maxSize (500): 每个片段最大字符数
 * - overlap (50): 片段之间的重叠字符数，保证上下文连贯
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ```java
 * // 执行同步
 * SyncResult result = pipeline.sync(databaseSource, false);  // 增量同步
 * SyncResult result = pipeline.sync(databaseSource, true);   // 全量同步
 *
 * // 处理单个文档
 * int segments = pipeline.processDocument(document);
 * ```
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipeline {

    // ═══════════════════════════════════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 嵌入模型
     *
     * 将文本转换为向量
     * 由 EmbeddingModelConfig 配置
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 向量存储服务
     *
     * 存储和检索向量
     * 可能是 Redis、PostgreSQL 或 Milvus 实现
     */
    private final VectorStoreService vectorStore;

    /**
     * 文档元数据仓库
     */
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * 同步状态仓库
     */
    private final SyncStatusRepository syncStatusRepository;

    /**
     * 内容服务
     *
     * 管理文档内容的读写
     * 实现内容与元数据分离存储
     */
    private final ContentService contentService;

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置参数
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档处理批次大小
     *
     * 每批处理的文档数量
     * 控制内存使用
     */
    @Value("${knowledge.pipeline.batch-size:100}")
    private int batchSize;

    /**
     * 向量化批次大小
     *
     * 每批向量化的片段数量
     * 控制 API 调用频率
     */
    @Value("${knowledge.pipeline.embedding-batch-size:20}")
    private int embeddingBatchSize;

    /**
     * 片段最大字符数
     *
     * 分割时每个片段的最大长度
     * 建议: 300-1000
     */
    @Value("${knowledge.pipeline.segment-max-size:500}")
    private int segmentMaxSize;

    /**
     * 片段重叠字符数
     *
     * 相邻片段的重叠长度
     * 保证上下文连贯性
     */
    @Value("${knowledge.pipeline.segment-overlap:50}")
    private int segmentOverlap;

    // ═══════════════════════════════════════════════════════════════════════════
    // 文档分割器
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档分割器 (延迟初始化)
     *
     * 使用懒加载: 第一次调用 getSplitter() 时创建
     * 这样可以使用 @Value 注入的配置值
     */
    private DocumentSplitter splitter;

    /**
     * 获取文档分割器
     *
     * @return DocumentSplitter 实例
     *
     * DocumentSplitters.recursive() 参数:
     * - maxSegmentSize: 每个片段最大字符数
     * - maxOverlapSize: 片段间重叠字符数
     *
     * recursive 策略:
     * 1. 先尝试按段落分割
     * 2. 段落太长则按句子分割
     * 3. 句子太长则按字符分割
     * 4. 保证每个片段不超过 maxSegmentSize
     */
    private DocumentSplitter getSplitter() {
        if (splitter == null) {
            splitter = DocumentSplitters.recursive(segmentMaxSize, segmentOverlap);
        }
        return splitter;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 同步操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 执行知识同步
     *
     * @param source   知识源 (Database/File/Confluence 等)
     * @param fullSync 是否全量同步
     *                 - true: 重新同步所有文档
     *                 - false: 只同步上次之后变化的文档
     *
     * @return 同步结果
     *
     * @Transactional 注解说明:
     * - 整个同步过程在一个事务中
     * - 发生异常时回滚状态更新
     * - 但向量存储的操作不在事务范围内 (最终一致性)
     */
    @Transactional
    public SyncResult sync(KnowledgeSource source, boolean fullSync) {
        String sourceName = source.getName();
        log.info("开始同步知识源: {}, 模式: {}", sourceName, fullSync ? "全量" : "增量");

        // ─────────────────────────────────────────────────────────────────────
        // 1. 获取同步状态
        // ─────────────────────────────────────────────────────────────────────
        SyncStatus syncStatus = getOrCreateSyncStatus(sourceName);
        LocalDateTime lastSyncTime = fullSync ? null : syncStatus.getLastSyncTime();

        // ─────────────────────────────────────────────────────────────────────
        // 2. 更新状态为运行中
        // ─────────────────────────────────────────────────────────────────────
        syncStatus.setLastSyncStatus("RUNNING");
        syncStatusRepository.save(syncStatus);

        try {
            // ─────────────────────────────────────────────────────────────────
            // 3. 从知识源获取文档
            // ─────────────────────────────────────────────────────────────────
            List<KnowledgeDocument> documents = source.fetchIncremental(lastSyncTime);
            log.info("从知识源 {} 获取到 {} 篇文档", sourceName, documents.size());

            if (documents.isEmpty()) {
                updateSyncStatus(syncStatus, "SUCCESS", 0, null);
                return new SyncResult(0, 0, "无需同步");
            }

            // ─────────────────────────────────────────────────────────────────
            // 4. 分批处理文档
            // ─────────────────────────────────────────────────────────────────
            int docCount = 0;
            int segmentCount = 0;

            for (int i = 0; i < documents.size(); i += batchSize) {
                int end = Math.min(i + batchSize, documents.size());
                List<KnowledgeDocument> batch = documents.subList(i, end);

                BatchResult batchResult = processBatch(batch);
                docCount += batchResult.docCount();
                segmentCount += batchResult.segmentCount();

                // 打印进度
                double progress = (double) (i + batch.size()) / documents.size() * 100;
                log.info("同步进度: {:.1f}% ({}/{})", progress, i + batch.size(), documents.size());
            }

            // ─────────────────────────────────────────────────────────────────
            // 5. 更新同步状态
            // ─────────────────────────────────────────────────────────────────
            updateSyncStatus(syncStatus, "SUCCESS", docCount, null);

            log.info("同步完成: {} 篇文档, {} 个片段", docCount, segmentCount);
            return new SyncResult(docCount, segmentCount, "成功");

        } catch (Exception e) {
            log.error("同步失败: {}", sourceName, e);
            updateSyncStatus(syncStatus, "FAILED", 0, e.getMessage());
            throw new RuntimeException("同步失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 文档处理
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 处理单个文档
     *
     * @param doc 文档 (可能含临时内容，或需要从数据库加载)
     * @return 生成的片段数
     *
     * 处理流程:
     * 1. 删除旧向量 (如果存在)
     * 2. 获取内容 (临时字段或数据库)
     * 3. 构建 LangChain4j Document
     * 4. 分割成片段
     * 5. 向量化并存储
     * 6. 更新处理状态
     */
    public int processDocument(KnowledgeDocument doc) {
        log.debug("处理文档: {}", doc.getDocId());

        try {
            // ─────────────────────────────────────────────────────────────────
            // 1. 删除旧向量 (如果存在)
            // ─────────────────────────────────────────────────────────────────
            // 文档更新时，需要先删除旧的向量数据
            // 否则会有重复数据
            vectorStore.deleteByDocId(doc.getDocId());

            // ─────────────────────────────────────────────────────────────────
            // 2. 获取内容
            // ─────────────────────────────────────────────────────────────────
            // 优先使用临时字段 (来自 FileSystemKnowledgeSource 等外部源)
            // 如果没有，则从数据库加载 (来自 DatabaseKnowledgeSource)
            String content = doc.getContent();
            if (content == null || content.isEmpty()) {
                content = contentService.getContent(doc.getDocId())
                        .orElseThrow(() -> new RuntimeException("文档内容不存在: " + doc.getDocId()));
                log.debug("从数据库加载内容: docId={}, size={}", doc.getDocId(), content.length());
            } else {
                // 外部来源的内容需要保存到数据库
                contentService.saveDocumentWithContent(doc, content);
                log.debug("保存外部内容到数据库: docId={}, size={}", doc.getDocId(), content.length());
            }

            // ─────────────────────────────────────────────────────────────────
            // 3. 构建 LangChain4j Document 对象
            // ─────────────────────────────────────────────────────────────────
            // Metadata: 元数据，会存储到向量库，可用于过滤
            Metadata metadata = new Metadata();
            metadata.put("docId", doc.getDocId());
            metadata.put("title", doc.getTitle());
            metadata.put("category", doc.getCategory() != null ? doc.getCategory() : "");
            metadata.put("source", doc.getSource() != null ? doc.getSource() : "");

            Document document = Document.from(content, metadata);

            // ─────────────────────────────────────────────────────────────────
            // 4. 分割文档
            // ─────────────────────────────────────────────────────────────────
            // 将长文档分割成小片段
            // 每个片段会继承 Document 的 metadata
            List<TextSegment> segments = getSplitter().split(document);
            log.debug("文档 {} 分割为 {} 个片段", doc.getDocId(), segments.size());

            // ─────────────────────────────────────────────────────────────────
            // 5. 批量向量化并存储
            // ─────────────────────────────────────────────────────────────────
            for (int i = 0; i < segments.size(); i += embeddingBatchSize) {
                int end = Math.min(i + embeddingBatchSize, segments.size());
                List<TextSegment> batch = segments.subList(i, end);

                // 向量化 (逐个处理，可改为批量调用 API)
                List<Embedding> embeddings = new ArrayList<>();
                for (TextSegment segment : batch) {
                    Embedding embedding = embeddingModel.embed(segment.text()).content();
                    embeddings.add(embedding);
                }

                // 批量存储到向量库
                vectorStore.storeBatch(embeddings, batch);
            }

            // ─────────────────────────────────────────────────────────────────
            // 6. 更新文档的向量化状态
            // ─────────────────────────────────────────────────────────────────
            documentRepository.updateVectorStatus(doc.getDocId(), 1, LocalDateTime.now(), segments.size());

            // ─────────────────────────────────────────────────────────────────
            // 7. 清空临时内容，帮助 GC
            // ─────────────────────────────────────────────────────────────────
            doc.setContent(null);

            return segments.size();

        } catch (Exception e) {
            log.error("处理文档失败: {}", doc.getDocId(), e);
            // 更新状态为失败
            documentRepository.updateVectorStatus(doc.getDocId(), 2, LocalDateTime.now(), 0);
            throw e;
        }
    }

    /**
     * 处理单个文档 (带内容参数，用于外部来源)
     *
     * @param doc     文档元数据
     * @param content 文档内容
     * @return 生成的片段数
     *
     * 使用场景:
     * - 外部 API 直接传入内容
     * - 不需要从数据库加载
     */
    public int processDocumentWithContent(KnowledgeDocument doc, String content) {
        // 先保存内容
        contentService.saveDocumentWithContent(doc, content);
        // 再处理向量化
        return processDocument(doc);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 批量处理
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 处理一批文档
     *
     * @param docs 文档列表
     * @return 批处理结果
     *
     * 单个文档处理失败不影响其他文档
     * 错误会被记录，继续处理下一个
     */
    private BatchResult processBatch(List<KnowledgeDocument> docs) {
        int docCount = 0;
        int segmentCount = 0;

        for (KnowledgeDocument doc : docs) {
            try {
                // 处理文档 (内容已经在知识源保存时写入)
                int segments = processDocument(doc);
                docCount++;
                segmentCount += segments;

            } catch (Exception e) {
                log.error("处理文档失败: {}", doc.getDocId(), e);
                // 继续处理其他文档
            }
        }

        return new BatchResult(docCount, segmentCount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 状态管理
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取或创建同步状态
     *
     * @param sourceName 知识源名称
     * @return SyncStatus 实体
     *
     * 如果不存在，创建新的状态记录
     */
    private SyncStatus getOrCreateSyncStatus(String sourceName) {
        return syncStatusRepository.findBySourceName(sourceName)
                .orElseGet(() -> {
                    SyncStatus status = new SyncStatus();
                    status.setSourceName(sourceName);
                    return syncStatusRepository.save(status);
                });
    }

    /**
     * 更新同步状态
     *
     * @param status       状态实体
     * @param result       结果: SUCCESS / FAILED
     * @param count        处理的文档数
     * @param errorMessage 错误信息 (仅失败时)
     */
    private void updateSyncStatus(SyncStatus status, String result, int count, String errorMessage) {
        status.setLastSyncStatus(result);
        status.setLastSyncTime(LocalDateTime.now());
        status.setLastSyncCount(count);
        status.setTotalSyncCount(status.getTotalSyncCount() + count);
        status.setLastErrorMessage(errorMessage);
        syncStatusRepository.save(status);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 数据结构
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 同步结果
     *
     * @param docCount     处理的文档数
     * @param segmentCount 生成的片段总数
     * @param message      结果消息
     */
    public record SyncResult(int docCount, int segmentCount, String message) {}

    /**
     * 批处理结果
     *
     * @param docCount     成功处理的文档数
     * @param segmentCount 生成的片段数
     */
    private record BatchResult(int docCount, int segmentCount) {}
}
