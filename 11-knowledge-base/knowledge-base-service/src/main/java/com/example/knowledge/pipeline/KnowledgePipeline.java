package com.example.knowledge.pipeline;

import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.entity.SyncStatus;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import com.example.knowledge.repository.SyncStatusRepository;
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
 * 核心处理流程:
 * 知识源 → 分割 → 向量化 → 存储
 *
 * 关键设计:
 * 1. 增量同步 - 只处理变化的文档
 * 2. 批量处理 - 避免内存溢出
 * 3. 失败重试 - 记录失败状态，支持重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipeline {

    private final EmbeddingModel embeddingModel;
    private final VectorStoreService vectorStore;
    private final KnowledgeDocumentRepository documentRepository;
    private final SyncStatusRepository syncStatusRepository;

    @Value("${knowledge.pipeline.batch-size:100}")
    private int batchSize;

    @Value("${knowledge.pipeline.embedding-batch-size:20}")
    private int embeddingBatchSize;

    @Value("${knowledge.pipeline.segment-max-size:500}")
    private int segmentMaxSize;

    @Value("${knowledge.pipeline.segment-overlap:50}")
    private int segmentOverlap;

    // 文档分割器 (延迟初始化)
    private DocumentSplitter splitter;

    private DocumentSplitter getSplitter() {
        if (splitter == null) {
            splitter = DocumentSplitters.recursive(segmentMaxSize, segmentOverlap);
        }
        return splitter;
    }

    /**
     * 执行知识同步
     *
     * @param source   知识源
     * @param fullSync 是否全量同步
     * @return 同步结果
     */
    @Transactional
    public SyncResult sync(KnowledgeSource source, boolean fullSync) {
        String sourceName = source.getName();
        log.info("开始同步知识源: {}, 模式: {}", sourceName, fullSync ? "全量" : "增量");

        // 1. 获取上次同步时间
        SyncStatus syncStatus = getOrCreateSyncStatus(sourceName);
        LocalDateTime lastSyncTime = fullSync ? null : syncStatus.getLastSyncTime();

        // 2. 更新状态为运行中
        syncStatus.setLastSyncStatus("RUNNING");
        syncStatusRepository.save(syncStatus);

        try {
            // 3. 从知识源获取文档
            List<KnowledgeDocument> documents = source.fetchIncremental(lastSyncTime);
            log.info("从知识源 {} 获取到 {} 篇文档", sourceName, documents.size());

            if (documents.isEmpty()) {
                updateSyncStatus(syncStatus, "SUCCESS", 0, null);
                return new SyncResult(0, 0, "无需同步");
            }

            // 4. 处理文档
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

            // 5. 更新同步状态
            updateSyncStatus(syncStatus, "SUCCESS", docCount, null);

            log.info("同步完成: {} 篇文档, {} 个片段", docCount, segmentCount);
            return new SyncResult(docCount, segmentCount, "成功");

        } catch (Exception e) {
            log.error("同步失败: {}", sourceName, e);
            updateSyncStatus(syncStatus, "FAILED", 0, e.getMessage());
            throw new RuntimeException("同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理单个文档
     *
     * @param doc 文档
     * @return 生成的片段数
     */
    public int processDocument(KnowledgeDocument doc) {
        log.debug("处理文档: {}", doc.getDocId());

        try {
            // 1. 删除旧向量 (如果存在)
            vectorStore.deleteByDocId(doc.getDocId());

            // 2. 构建 Document 对象
            Metadata metadata = new Metadata();
            metadata.put("docId", doc.getDocId());
            metadata.put("title", doc.getTitle());
            metadata.put("category", doc.getCategory() != null ? doc.getCategory() : "");
            metadata.put("source", doc.getSource() != null ? doc.getSource() : "");

            Document document = Document.from(doc.getContent(), metadata);

            // 3. 分割文档
            List<TextSegment> segments = getSplitter().split(document);
            log.debug("文档 {} 分割为 {} 个片段", doc.getDocId(), segments.size());

            // 4. 批量向量化并存储
            for (int i = 0; i < segments.size(); i += embeddingBatchSize) {
                int end = Math.min(i + embeddingBatchSize, segments.size());
                List<TextSegment> batch = segments.subList(i, end);

                // 向量化 (逐个处理)
                List<Embedding> embeddings = new ArrayList<>();
                for (TextSegment segment : batch) {
                    Embedding embedding = embeddingModel.embed(segment.text()).content();
                    embeddings.add(embedding);
                }

                // 存储
                vectorStore.storeBatch(embeddings, batch);
            }

            // 5. 更新文档的向量化状态
            documentRepository.updateVectorStatus(doc.getDocId(), 1, LocalDateTime.now());

            return segments.size();

        } catch (Exception e) {
            log.error("处理文档失败: {}", doc.getDocId(), e);
            documentRepository.updateVectorStatus(doc.getDocId(), 2, LocalDateTime.now());
            throw e;
        }
    }

    /**
     * 处理一批文档
     */
    private BatchResult processBatch(List<KnowledgeDocument> docs) {
        int docCount = 0;
        int segmentCount = 0;

        for (KnowledgeDocument doc : docs) {
            try {
                // 先保存到数据库 (如果是外部来源)
                if (doc.getId() == null) {
                    KnowledgeDocument existing = documentRepository.findByDocId(doc.getDocId()).orElse(null);
                    if (existing != null) {
                        // 更新
                        existing.setContent(doc.getContent());
                        existing.setTitle(doc.getTitle());
                        existing.setCategory(doc.getCategory());
                        existing.setVersion(doc.getVersion());
                        existing.setVectorStatus(0);
                        documentRepository.save(existing);
                        doc = existing;
                    } else {
                        // 新增
                        doc = documentRepository.save(doc);
                    }
                }

                // 处理文档
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

    /**
     * 获取或创建同步状态
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
     */
    private void updateSyncStatus(SyncStatus status, String result, int count, String errorMessage) {
        status.setLastSyncStatus(result);
        status.setLastSyncTime(LocalDateTime.now());
        status.setLastSyncCount(count);
        status.setTotalSyncCount(status.getTotalSyncCount() + count);
        status.setLastErrorMessage(errorMessage);
        syncStatusRepository.save(status);
    }

    /**
     * 同步结果
     */
    public record SyncResult(int docCount, int segmentCount, String message) {}

    /**
     * 批处理结果
     */
    private record BatchResult(int docCount, int segmentCount) {}
}
