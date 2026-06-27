package com.example.knowledge.store.impl;

import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * PostgreSQL + pgvector 向量存储服务实现
 *
 * 适合已有 PostgreSQL 基础设施的企业。
 *
 * 配置:
 * knowledge.vector-store.type=pgvector
 * knowledge.vector-store.pgvector.host=localhost
 * knowledge.vector-store.pgvector.port=5432
 * knowledge.vector-store.pgvector.database=knowledge_db
 *
 * 前置条件:
 * 1. PostgreSQL 12+
 * 2. 安装 pgvector 扩展: CREATE EXTENSION vector;
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "pgvector")
public class PgVectorStoreService implements VectorStoreService {

    @Value("${knowledge.vector-store.pgvector.host:localhost}")
    private String host;

    @Value("${knowledge.vector-store.pgvector.port:5432}")
    private int port;

    @Value("${knowledge.vector-store.pgvector.database:knowledge_db}")
    private String database;

    @Value("${knowledge.vector-store.pgvector.user:postgres}")
    private String user;

    @Value("${knowledge.vector-store.pgvector.password:}")
    private String password;

    @Value("${knowledge.vector-store.pgvector.table:knowledge_embeddings}")
    private String table;

    @Value("${knowledge.vector-store.dimension:1536}")
    private int dimension;

    private PgVectorEmbeddingStore store;

    @PostConstruct
    public void init() {
        log.info("初始化 PostgreSQL pgvector 向量存储, host={}, database={}, table={}", host, database, table);

        this.store = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)  // 自动创建表
                .dropTableFirst(false)  // 不删除已有数据
                .useIndex(true)  // 创建向量索引
                .build();

        log.info("PostgreSQL pgvector 向量存储初始化完成");
    }

    @Override
    public String getType() {
        return "pgvector";
    }

    @Override
    public boolean isAvailable() {
        return store != null;
    }

    @Override
    public String store(Embedding embedding, TextSegment segment) {
        return store.add(embedding, segment);
    }

    @Override
    public List<String> storeBatch(List<Embedding> embeddings, List<TextSegment> segments) {
        return store.addAll(embeddings, segments);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        return result.matches();
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults, double minScore, String category) {
        EmbeddingSearchRequest request;
        if (category != null && !category.isEmpty()) {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo(category))
                    .build();
        } else {
            request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
        }

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        return result.matches();
    }

    @Override
    public int deleteByDocId(String docId) {
        try {
            store.removeAll(MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId));
            return -1;
        } catch (Exception e) {
            log.error("删除文档向量失败: {}", docId, e);
            return 0;
        }
    }

    @Override
    public void deleteAll() {
        log.warn("删除所有向量数据!");
        store.removeAll();
    }

    @Override
    public StoreStats getStats() {
        return new StoreStats("pgvector", -1, isAvailable());
    }
}
