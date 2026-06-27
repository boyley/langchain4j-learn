package com.example.knowledge.source.impl;

import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import com.example.knowledge.source.KnowledgeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库知识源实现
 *
 * 从数据库 knowledge_documents 表读取知识文档。
 *
 * 适用场景:
 * - 知识通过管理后台录入
 * - 知识来自其他系统同步到数据库
 * - 需要结构化管理的知识
 *
 * 配置启用:
 * knowledge.source.database.enabled=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowledge.source.database.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseKnowledgeSource implements KnowledgeSource {

    private final KnowledgeDocumentRepository repository;

    @Override
    public String getName() {
        return "database";
    }

    @Override
    public String getDisplayName() {
        return "数据库";
    }

    @Override
    public boolean isAvailable() {
        try {
            repository.count();
            return true;
        } catch (Exception e) {
            log.error("数据库知识源不可用", e);
            return false;
        }
    }

    @Override
    public List<KnowledgeDocument> fetchAll() {
        log.info("开始全量获取数据库知识文档...");
        List<KnowledgeDocument> docs = repository.findBySourceAndStatus("database", 1);
        log.info("获取到 {} 篇文档", docs.size());
        return docs;
    }

    @Override
    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
        if (lastSyncTime == null) {
            log.info("首次同步，执行全量获取");
            return fetchAll();
        }

        log.info("增量获取数据库知识文档, lastSyncTime={}", lastSyncTime);
        List<KnowledgeDocument> docs = repository.findBySourceAndUpdateTimeAfter("database", lastSyncTime);
        log.info("增量获取到 {} 篇文档", docs.size());
        return docs;
    }

    @Override
    public List<KnowledgeDocument> fetchBatch(int offset, int limit) {
        log.debug("分批获取数据库知识文档, offset={}, limit={}", offset, limit);
        return repository.findBySourceAndStatus("database", 1, PageRequest.of(offset / limit, limit))
                .getContent();
    }

    @Override
    public long count() {
        return repository.countByStatus(1);
    }

    @Override
    public KnowledgeDocument fetchById(String docId) {
        return repository.findByDocId(docId).orElse(null);
    }
}
