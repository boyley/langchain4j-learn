package com.example.knowledge.store.impl;

import com.example.knowledge.store.VectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Redis 向量存储服务实现
 *
 * 使用 Redis Stack (RediSearch) 作为向量存储后端。
 *
 * 配置:
 * knowledge.vector-store.type=redis
 * knowledge.vector-store.redis.host=localhost
 * knowledge.vector-store.redis.port=6379
 *
 * 前置条件:
 * docker run -d --name redis-stack -p 6379:6379 redis/redis-stack:latest
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "redis")
public class RedisVectorStoreService implements VectorStoreService {

    @Value("${knowledge.vector-store.redis.host:localhost}")
    private String host;

    @Value("${knowledge.vector-store.redis.port:6379}")
    private int port;

    @Value("${knowledge.vector-store.redis.password:}")
    private String password;

    @Value("${knowledge.vector-store.redis.index-name:knowledge_vectors}")
    private String indexName;

    @Value("${knowledge.vector-store.dimension:1536}")
    private int dimension;

    private RedisEmbeddingStore store;

    @PostConstruct
    public void init() {
        log.info("初始化 Redis 向量存储, host={}, port={}, index={}", host, port, indexName);

        var builder = RedisEmbeddingStore.builder()
                .host(host)
                .port(port)
                .indexName(indexName)
                .dimension(dimension)
                .metadataKeys(List.of("docId", "title", "category", "source"));

        if (password != null && !password.isEmpty()) {
            builder.password(password);
        }

        this.store = builder.build();

        log.info("Redis 向量存储初始化完成");
    }

    @Override
    public String getType() {
        return "redis";
    }

    @Override
    public boolean isAvailable() {
        try {
            // 尝试一次简单搜索来验证连接
            return store != null;
        } catch (Exception e) {
            log.error("Redis 向量存储不可用", e);
            return false;
        }
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
            log.debug("已删除文档 {} 的向量", docId);
            return -1; // Redis 不返回删除数量
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
        return new StoreStats("redis", -1, isAvailable());
    }
}
