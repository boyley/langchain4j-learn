package com.example.langchain4j.vectorstore.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.*;
import java.io.IOException;

/**
 * 企业级知识库架构设计 Demo
 *
 * 本 Demo 展示企业级知识库的完整架构设计:
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                         企业知识库架构全景图                              │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │                                                                         │
 * │  【知识源层】           【处理层】            【存储层】       【应用层】   │
 * │  ┌─────────┐          ┌─────────┐          ┌─────────┐     ┌─────────┐ │
 * │  │ MySQL   │          │         │          │  Redis  │     │  RAG    │ │
 * │  │ 知识表   │──┐       │ 知识处理 │          │ 向量库  │     │  检索   │ │
 * │  └─────────┘  │       │  管道   │          └─────────┘     └─────────┘ │
 * │  ┌─────────┐  │       │         │          ┌─────────┐     ┌─────────┐ │
 * │  │ 文件系统 │──┼──────→│ 1.加载   │─────────→│ Milvus │─────→│ 问答   │ │
 * │  │ PDF/DOC │  │       │ 2.分割   │          │ 向量库  │     │ 系统   │ │
 * │  └─────────┘  │       │ 3.向量化 │          └─────────┘     └─────────┘ │
 * │  ┌─────────┐  │       │ 4.存储   │          ┌─────────┐     ┌─────────┐ │
 * │  │ API     │──┘       │         │          │   PG    │     │  搜索   │ │
 * │  │Confluence│         │         │          │ pgvector│     │  推荐   │ │
 * │  └─────────┘          └─────────┘          └─────────┘     └─────────┘ │
 * │                                                                         │
 * │  ↑                    ↑                    ↑               ↑           │
 * │  接口抽象              统一处理              接口抽象         统一入口     │
 * │  KnowledgeSource      KnowledgePipeline    VectorStore     SearchAPI   │
 * │                                                                         │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * @author LangChain4j 学习项目
 */
public class KnowledgeArchitectureDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            企业级知识库架构设计 Demo                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 第一部分: 架构概述 ====================
        printArchitectureOverview();

        // ==================== 第二部分: 知识源抽象 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第二部分】知识源抽象 - KnowledgeSource 接口设计");
        System.out.println("═".repeat(65));
        printKnowledgeSourceDesign();

        // ==================== 第三部分: 知识处理管道 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第三部分】知识处理管道 - KnowledgePipeline 设计");
        System.out.println("═".repeat(65));
        printKnowledgePipelineDesign();

        // ==================== 第四部分: 向量存储抽象 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第四部分】向量存储抽象 - VectorStoreAdapter 设计");
        System.out.println("═".repeat(65));
        printVectorStoreDesign();

        // ==================== 第五部分: 增量同步设计 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第五部分】增量同步设计 - 大规模知识库更新策略");
        System.out.println("═".repeat(65));
        printIncrementalSyncDesign();

        // ==================== 第六部分: Spring Boot 集成 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第六部分】Spring Boot 完整集成方案");
        System.out.println("═".repeat(65));
        printSpringBootIntegration();

        // ==================== 第七部分: 生产环境清单 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第七部分】生产环境部署清单");
        System.out.println("═".repeat(65));
        printProductionChecklist();
    }

    private static void printArchitectureOverview() {
        System.out.println("""
                ═══════════════════════════════════════════════════════════════════
                【第一部分】企业知识库架构概述
                ═══════════════════════════════════════════════════════════════════

                【核心设计原则】

                1. 接口抽象 (Interface Abstraction)
                   - 知识源: KnowledgeSource 接口，统一不同数据来源
                   - 向量存储: VectorStore 接口，统一不同存储后端
                   - 好处: 可以随时切换实现，不影响上层业务

                2. 管道模式 (Pipeline Pattern)
                   - 加载 → 分割 → 向量化 → 存储
                   - 每个步骤可独立配置和替换

                3. 增量同步 (Incremental Sync)
                   - 不是每次全量同步
                   - 只处理变化的文档
                   - 使用时间戳追踪

                4. 批量处理 (Batch Processing)
                   - 避免一次性加载过多数据
                   - 分批次向量化，防止 API 超时
                   - 进度追踪和断点续传

                【分层架构】

                ┌─────────────────────────────────────────────────────────────┐
                │                     应用层 (Application)                    │
                │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
                │  │ RAG 检索 │  │  智能问答 │  │  文档搜索 │  │  知识推荐 │   │
                │  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
                └─────────────────────────────────────────────────────────────┘
                                              ↓
                ┌─────────────────────────────────────────────────────────────┐
                │                     服务层 (Service)                        │
                │  ┌──────────────────────────────────────────────────────┐  │
                │  │              KnowledgeSearchService                   │  │
                │  │  - search(query, filters)                            │  │
                │  │  - searchByCategory(query, category)                 │  │
                │  │  - searchWithContext(query, conversationId)          │  │
                │  └──────────────────────────────────────────────────────┘  │
                └─────────────────────────────────────────────────────────────┘
                                              ↓
                ┌─────────────────────────────────────────────────────────────┐
                │                    基础设施层 (Infrastructure)              │
                │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
                │  │KnowledgeSource│ │ VectorStore │  │EmbeddingModel│        │
                │  │   (接口)     │  │   (接口)    │  │   (接口)     │        │
                │  ├─────────────┤  ├─────────────┤  ├─────────────┤        │
                │  │MySQLSource  │  │ RedisStore  │  │ OpenAI      │        │
                │  │FileSource   │  │ MilvusStore │  │ Azure       │        │
                │  │APISource    │  │ PgVectorStore│ │ Local       │        │
                │  └─────────────┘  └─────────────┘  └─────────────┘        │
                └─────────────────────────────────────────────────────────────┘
                """);
    }

    private static void printKnowledgeSourceDesign() {
        System.out.println("""

                【设计思路】
                不同企业的知识存储方式各不相同:
                - 有的存在 MySQL 数据库
                - 有的存在文件服务器 (PDF/Word)
                - 有的通过 API 对接 (Confluence/Notion)

                统一抽象成 KnowledgeSource 接口，业务层无需关心底层实现。

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // ============================================================
                // 1. 知识文档实体
                // ============================================================

                /**
                 * 知识文档 - 统一的知识表示
                 */
                public class KnowledgeDocument {
                    private String docId;           // 文档唯一标识 (用于更新/删除)
                    private String title;           // 文档标题
                    private String content;         // 文档内容
                    private String category;        // 分类
                    private String version;         // 版本号
                    private String source;          // 来源 (mysql/file/api)
                    private LocalDateTime updateTime; // 更新时间

                    // getter/setter...
                }

                // ============================================================
                // 2. 知识源接口
                // ============================================================

                /**
                 * 知识源接口 - 抽象不同的知识来源
                 */
                public interface KnowledgeSource {

                    /**
                     * 获取知识源名称
                     */
                    String getName();

                    /**
                     * 全量获取所有知识文档
                     * 注意: 大数据量时应使用 fetchIncremental
                     */
                    List<KnowledgeDocument> fetchAll();

                    /**
                     * 增量获取 - 只获取指定时间后更新的文档
                     *
                     * @param lastSyncTime 上次同步时间，null 表示全量
                     * @return 更新的文档列表
                     */
                    List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime);

                    /**
                     * 分页获取 - 用于批量处理大数据量
                     *
                     * @param offset 偏移量
                     * @param limit  每页数量
                     * @return 文档列表
                     */
                    List<KnowledgeDocument> fetchBatch(int offset, int limit);

                    /**
                     * 获取总文档数
                     */
                    long count();
                }

                // ============================================================
                // 3. MySQL 知识源实现
                // ============================================================

                /**
                 * MySQL 知识源实现
                 *
                 * 适用场景: 知识文档存储在 MySQL 数据库中
                 */
                @Service
                public class MySQLKnowledgeSource implements KnowledgeSource {

                    @Autowired
                    private JdbcTemplate jdbcTemplate;

                    @Override
                    public String getName() {
                        return "MySQL";
                    }

                    @Override
                    public List<KnowledgeDocument> fetchAll() {
                        String sql = "SELECT * FROM knowledge_documents WHERE status = 1";
                        return jdbcTemplate.query(sql, this::mapRow);
                    }

                    @Override
                    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
                        if (lastSyncTime == null) {
                            return fetchAll();
                        }

                        String sql = "SELECT * FROM knowledge_documents " +
                                     "WHERE status = 1 AND update_time > ?";
                        return jdbcTemplate.query(sql, this::mapRow, lastSyncTime);
                    }

                    @Override
                    public List<KnowledgeDocument> fetchBatch(int offset, int limit) {
                        String sql = "SELECT * FROM knowledge_documents " +
                                     "WHERE status = 1 ORDER BY id LIMIT ? OFFSET ?";
                        return jdbcTemplate.query(sql, this::mapRow, limit, offset);
                    }

                    @Override
                    public long count() {
                        String sql = "SELECT COUNT(*) FROM knowledge_documents WHERE status = 1";
                        return jdbcTemplate.queryForObject(sql, Long.class);
                    }

                    private KnowledgeDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
                        KnowledgeDocument doc = new KnowledgeDocument();
                        doc.setDocId(rs.getString("doc_id"));
                        doc.setTitle(rs.getString("title"));
                        doc.setContent(rs.getString("content"));
                        doc.setCategory(rs.getString("category"));
                        doc.setVersion(rs.getString("version"));
                        doc.setSource("mysql");
                        doc.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
                        return doc;
                    }
                }

                // ============================================================
                // 4. 文件系统知识源实现
                // ============================================================

                /**
                 * 文件系统知识源实现
                 *
                 * 适用场景: 知识文档存储在文件服务器上 (PDF/Word/TXT)
                 */
                @Service
                public class FileSystemKnowledgeSource implements KnowledgeSource {

                    @Value("${knowledge.file.root-path}")
                    private String rootPath;

                    private final DocumentParser pdfParser = new ApachePdfBoxDocumentParser();
                    private final DocumentParser wordParser = new ApachePoiDocumentParser();
                    private final DocumentParser textParser = new TextDocumentParser();

                    @Override
                    public String getName() {
                        return "FileSystem";
                    }

                    @Override
                    public List<KnowledgeDocument> fetchAll() {
                        return fetchIncremental(null);
                    }

                    @Override
                    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
                        List<KnowledgeDocument> documents = new ArrayList<>();
                        Path root = Paths.get(rootPath);

                        try {
                            Files.walk(root)
                                .filter(Files::isRegularFile)
                                .filter(path -> shouldProcess(path, lastSyncTime))
                                .forEach(path -> {
                                    KnowledgeDocument doc = parseFile(path);
                                    if (doc != null) {
                                        documents.add(doc);
                                    }
                                });
                        } catch (IOException e) {
                            throw new RuntimeException("扫描文件目录失败", e);
                        }

                        return documents;
                    }

                    private boolean shouldProcess(Path path, LocalDateTime lastSyncTime) {
                        if (lastSyncTime == null) return true;

                        try {
                            long fileModified = Files.getLastModifiedTime(path).toMillis();
                            long syncTime = lastSyncTime.atZone(ZoneId.systemDefault())
                                                        .toInstant().toEpochMilli();
                            return fileModified > syncTime;
                        } catch (IOException e) {
                            return false;
                        }
                    }

                    private KnowledgeDocument parseFile(Path path) {
                        String fileName = path.getFileName().toString().toLowerCase();

                        try {
                            String content;
                            if (fileName.endsWith(".pdf")) {
                                content = pdfParser.parse(Files.newInputStream(path)).text();
                            } else if (fileName.endsWith(".docx")) {
                                content = wordParser.parse(Files.newInputStream(path)).text();
                            } else if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                                content = Files.readString(path);
                            } else {
                                return null; // 不支持的格式
                            }

                            KnowledgeDocument doc = new KnowledgeDocument();
                            doc.setDocId(path.toString());  // 文件路径作为 ID
                            doc.setTitle(fileName);
                            doc.setContent(content);
                            doc.setCategory(extractCategory(path));  // 目录名作为分类
                            doc.setSource("file");
                            doc.setUpdateTime(LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(path).toInstant(),
                                ZoneId.systemDefault()
                            ));
                            return doc;

                        } catch (Exception e) {
                            log.error("解析文件失败: {}", path, e);
                            return null;
                        }
                    }

                    private String extractCategory(Path path) {
                        // 从路径提取分类: /data/docs/HR/xxx.pdf -> HR
                        Path parent = path.getParent();
                        return parent != null ? parent.getFileName().toString() : "未分类";
                    }
                }

                // ============================================================
                // 5. API 知识源实现 (Confluence 示例)
                // ============================================================

                /**
                 * Confluence 知识源实现
                 *
                 * 适用场景: 企业使用 Confluence 管理文档
                 */
                @Service
                public class ConfluenceKnowledgeSource implements KnowledgeSource {

                    @Value("${confluence.base-url}")
                    private String baseUrl;

                    @Value("${confluence.username}")
                    private String username;

                    @Value("${confluence.api-token}")
                    private String apiToken;

                    @Value("${confluence.space-key}")
                    private String spaceKey;

                    private final RestTemplate restTemplate = new RestTemplate();

                    @Override
                    public String getName() {
                        return "Confluence";
                    }

                    @Override
                    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
                        List<KnowledgeDocument> documents = new ArrayList<>();

                        // Confluence CQL 查询
                        String cql = lastSyncTime == null
                            ? "space = " + spaceKey + " AND type = page"
                            : "space = " + spaceKey + " AND type = page " +
                              "AND lastmodified >= '" + lastSyncTime.toString() + "'";

                        String url = baseUrl + "/rest/api/content/search?cql=" +
                                     URLEncoder.encode(cql, StandardCharsets.UTF_8);

                        // 设置认证头
                        HttpHeaders headers = new HttpHeaders();
                        headers.setBasicAuth(username, apiToken);

                        ResponseEntity<ConfluenceSearchResult> response =
                            restTemplate.exchange(url, HttpMethod.GET,
                                new HttpEntity<>(headers), ConfluenceSearchResult.class);

                        // 解析结果
                        for (ConfluencePage page : response.getBody().getResults()) {
                            KnowledgeDocument doc = new KnowledgeDocument();
                            doc.setDocId("confluence:" + page.getId());
                            doc.setTitle(page.getTitle());
                            doc.setContent(fetchPageContent(page.getId()));
                            doc.setCategory(page.getSpace().getName());
                            doc.setSource("confluence");
                            documents.add(doc);
                        }

                        return documents;
                    }

                    private String fetchPageContent(String pageId) {
                        // 获取页面内容 (HTML → 纯文本)
                        String url = baseUrl + "/rest/api/content/" + pageId +
                                     "?expand=body.storage";
                        // ... API 调用并提取内容
                    }
                }
                """);
    }

    private static void printKnowledgePipelineDesign() {
        System.out.println("""

                【设计思路】
                知识处理是一个流水线:
                  加载 → 分割 → 向量化 → 存储

                使用管道模式，每个步骤可独立配置和监控。

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // ============================================================
                // 1. 知识处理管道
                // ============================================================

                /**
                 * 知识处理管道 - 统一处理流程
                 */
                @Service
                @Slf4j
                public class KnowledgePipeline {

                    @Autowired
                    private EmbeddingModel embeddingModel;

                    @Autowired
                    private EmbeddingStore<TextSegment> embeddingStore;

                    @Autowired
                    private DocumentSplitter splitter;

                    @Autowired
                    private SyncStatusRepository syncStatusRepository;

                    // 批量处理配置
                    private static final int BATCH_SIZE = 100;         // 每批文档数
                    private static final int EMBEDDING_BATCH = 20;     // 每批向量化数

                    /**
                     * 执行知识同步
                     *
                     * @param source   知识源
                     * @param fullSync 是否全量同步
                     * @return 同步结果
                     */
                    public SyncResult sync(KnowledgeSource source, boolean fullSync) {
                        log.info("开始同步知识源: {}, 模式: {}",
                                 source.getName(), fullSync ? "全量" : "增量");

                        // 1. 获取上次同步时间
                        LocalDateTime lastSyncTime = fullSync ? null :
                            syncStatusRepository.getLastSyncTime(source.getName());

                        // 2. 获取需要同步的文档
                        List<KnowledgeDocument> documents =
                            source.fetchIncremental(lastSyncTime);

                        log.info("发现 {} 篇文档需要同步", documents.size());

                        if (documents.isEmpty()) {
                            return new SyncResult(0, 0, "无需同步");
                        }

                        // 3. 分批处理
                        int docCount = 0;
                        int segmentCount = 0;

                        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
                            int end = Math.min(i + BATCH_SIZE, documents.size());
                            List<KnowledgeDocument> batch = documents.subList(i, end);

                            SyncResult batchResult = processBatch(batch);
                            docCount += batchResult.getDocCount();
                            segmentCount += batchResult.getSegmentCount();

                            // 打印进度
                            double progress = (double) (i + batch.size()) / documents.size() * 100;
                            log.info("同步进度: {:.1f}% ({}/{})",
                                     progress, i + batch.size(), documents.size());
                        }

                        // 4. 更新同步时间
                        syncStatusRepository.updateLastSyncTime(
                            source.getName(), LocalDateTime.now());

                        log.info("同步完成: {} 篇文档, {} 个片段", docCount, segmentCount);
                        return new SyncResult(docCount, segmentCount, "成功");
                    }

                    /**
                     * 处理单批文档
                     */
                    private SyncResult processBatch(List<KnowledgeDocument> documents) {
                        int segmentCount = 0;

                        for (KnowledgeDocument doc : documents) {
                            // 1. 删除旧向量 (更新时需要)
                            embeddingStore.removeAll(
                                MetadataFilterBuilder.metadataKey("docId")
                                    .isEqualTo(doc.getDocId())
                            );

                            // 2. 构建 Document 对象
                            Metadata metadata = new Metadata();
                            metadata.put("docId", doc.getDocId());
                            metadata.put("title", doc.getTitle());
                            metadata.put("category", doc.getCategory());
                            metadata.put("source", doc.getSource());
                            metadata.put("version", doc.getVersion());
                            metadata.put("syncTime", LocalDateTime.now().toString());

                            Document document = Document.from(doc.getContent(), metadata);

                            // 3. 分割文档
                            List<TextSegment> segments = splitter.split(document);

                            // 4. 批量向量化并存储
                            for (int j = 0; j < segments.size(); j += EMBEDDING_BATCH) {
                                int end = Math.min(j + EMBEDDING_BATCH, segments.size());
                                List<TextSegment> subBatch = segments.subList(j, end);

                                // 向量化
                                List<Embedding> embeddings = embeddingModel.embedAll(
                                    subBatch.stream().map(TextSegment::text).toList()
                                ).content();

                                // 存储
                                embeddingStore.addAll(embeddings, subBatch);
                            }

                            segmentCount += segments.size();
                        }

                        return new SyncResult(documents.size(), segmentCount, "成功");
                    }
                }

                // ============================================================
                // 2. 同步状态存储
                // ============================================================

                /**
                 * 同步状态存储 - 记录各知识源的同步时间
                 */
                @Repository
                public class SyncStatusRepository {

                    @Autowired
                    private JdbcTemplate jdbcTemplate;

                    public LocalDateTime getLastSyncTime(String sourceName) {
                        String sql = "SELECT last_sync_time FROM sync_status WHERE source_name = ?";
                        try {
                            Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class, sourceName);
                            return ts != null ? ts.toLocalDateTime() : null;
                        } catch (EmptyResultDataAccessException e) {
                            return null;
                        }
                    }

                    public void updateLastSyncTime(String sourceName, LocalDateTime syncTime) {
                        String sql = "INSERT INTO sync_status (source_name, last_sync_time) " +
                                     "VALUES (?, ?) " +
                                     "ON DUPLICATE KEY UPDATE last_sync_time = ?";
                        jdbcTemplate.update(sql, sourceName, syncTime, syncTime);
                    }
                }

                -- SQL 表结构
                CREATE TABLE sync_status (
                    source_name VARCHAR(64) PRIMARY KEY,
                    last_sync_time DATETIME,
                    sync_count INT DEFAULT 0
                );
                """);
    }

    private static void printVectorStoreDesign() {
        System.out.println("""

                【设计思路】
                LangChain4j 已经提供了 EmbeddingStore 接口抽象，
                但企业应用可能需要额外的适配层:
                - 统一配置管理
                - 连接池管理
                - 监控指标
                - 故障转移

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // ============================================================
                // 1. 向量存储适配器
                // ============================================================

                /**
                 * 向量存储适配器 - 企业级封装
                 */
                public interface VectorStoreAdapter {

                    /**
                     * 存储向量
                     */
                    void store(List<Embedding> embeddings, List<TextSegment> segments);

                    /**
                     * 搜索相似向量
                     */
                    List<SearchResult> search(String query, SearchOptions options);

                    /**
                     * 按条件删除
                     */
                    int deleteByDocId(String docId);

                    /**
                     * 删除所有数据 (谨慎!)
                     */
                    void deleteAll();

                    /**
                     * 获取统计信息
                     */
                    StoreStats getStats();

                    /**
                     * 健康检查
                     */
                    boolean isHealthy();
                }

                // ============================================================
                // 2. 搜索选项
                // ============================================================

                /**
                 * 搜索选项 - 统一的搜索参数
                 */
                @Data
                @Builder
                public class SearchOptions {
                    private int maxResults = 5;           // 最大返回数
                    private double minScore = 0.5;        // 最小相似度
                    private String category;              // 分类过滤
                    private String source;                // 来源过滤
                    private LocalDateTime dateFrom;       // 时间范围
                    private LocalDateTime dateTo;
                }

                // ============================================================
                // 3. Redis 适配器实现
                // ============================================================

                @Service
                @ConditionalOnProperty(name = "vector-store.type", havingValue = "redis")
                public class RedisVectorStoreAdapter implements VectorStoreAdapter {

                    private final RedisEmbeddingStore store;
                    private final EmbeddingModel embeddingModel;

                    public RedisVectorStoreAdapter(
                            @Value("${redis.host}") String host,
                            @Value("${redis.port}") int port,
                            @Value("${redis.password:}") String password,
                            EmbeddingModel embeddingModel) {

                        this.store = RedisEmbeddingStore.builder()
                            .host(host)
                            .port(port)
                            .password(password.isEmpty() ? null : password)
                            .indexName("knowledge_vectors")
                            .dimension(1536)
                            .metadataKeys(List.of("docId", "title", "category", "source"))
                            .build();

                        this.embeddingModel = embeddingModel;
                    }

                    @Override
                    public List<SearchResult> search(String query, SearchOptions options) {
                        Embedding queryEmbedding = embeddingModel.embed(query).content();

                        EmbeddingSearchRequest.Builder builder = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(options.getMaxResults())
                            .minScore(options.getMinScore());

                        // 添加过滤条件
                        if (options.getCategory() != null) {
                            builder.filter(MetadataFilterBuilder.metadataKey("category")
                                .isEqualTo(options.getCategory()));
                        }

                        return store.search(builder.build()).matches().stream()
                            .map(match -> new SearchResult(
                                match.embedded().text(),
                                match.embedded().metadata(),
                                match.score()
                            ))
                            .toList();
                    }

                    @Override
                    public int deleteByDocId(String docId) {
                        store.removeAll(MetadataFilterBuilder.metadataKey("docId")
                            .isEqualTo(docId));
                        return 1; // Redis 不返回删除数量
                    }

                    @Override
                    public boolean isHealthy() {
                        try {
                            // 尝试一次简单的搜索
                            search("test", SearchOptions.builder().maxResults(1).build());
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    }
                }

                // ============================================================
                // 4. 自动配置 - 根据配置选择实现
                // ============================================================

                @Configuration
                public class VectorStoreAutoConfiguration {

                    @Bean
                    @ConditionalOnProperty(name = "vector-store.type", havingValue = "redis")
                    public VectorStoreAdapter redisAdapter(...) {
                        return new RedisVectorStoreAdapter(...);
                    }

                    @Bean
                    @ConditionalOnProperty(name = "vector-store.type", havingValue = "milvus")
                    public VectorStoreAdapter milvusAdapter(...) {
                        return new MilvusVectorStoreAdapter(...);
                    }

                    @Bean
                    @ConditionalOnProperty(name = "vector-store.type", havingValue = "pgvector")
                    public VectorStoreAdapter pgvectorAdapter(...) {
                        return new PgVectorStoreAdapter(...);
                    }

                    @Bean
                    @ConditionalOnProperty(name = "vector-store.type",
                                           havingValue = "inmemory", matchIfMissing = true)
                    public VectorStoreAdapter inmemoryAdapter(...) {
                        return new InMemoryVectorStoreAdapter(...);
                    }
                }
                """);
    }

    private static void printIncrementalSyncDesign() {
        System.out.println("""

                ┌─────────────────────────────────────────────────────────────┐
                │                    增量同步流程图                            │
                └─────────────────────────────────────────────────────────────┘

                首次运行 (全量同步):
                ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐
                │ 查询所有    │ → │ 分割+向量化 │ → │  存储向量   │ → │ 记录时间戳  │
                │ 知识文档    │   │  (批量处理) │   │            │   │            │
                └────────────┘   └────────────┘   └────────────┘   └────────────┘

                后续运行 (增量同步):
                ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────┐
                │ 查询变化的  │ → │ 删除旧向量  │ → │  存储新向量 │ → │ 更新时间戳  │
                │ 文档       │   │ (by docId) │   │            │   │            │
                └────────────┘   └────────────┘   └────────────┘   └────────────┘
                     ↑
                WHERE update_time > lastSyncTime

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // ============================================================
                // 定时任务配置
                // ============================================================

                @Configuration
                @EnableScheduling
                public class SyncScheduleConfig {

                    @Autowired
                    private KnowledgePipeline pipeline;

                    @Autowired
                    private List<KnowledgeSource> sources;  // 自动注入所有知识源

                    /**
                     * 每小时增量同步
                     */
                    @Scheduled(cron = "0 0 * * * *")
                    public void incrementalSync() {
                        for (KnowledgeSource source : sources) {
                            try {
                                SyncResult result = pipeline.sync(source, false);
                                log.info("[{}] 增量同步完成: {}", source.getName(), result);
                            } catch (Exception e) {
                                log.error("[{}] 增量同步失败", source.getName(), e);
                                // 发送告警...
                            }
                        }
                    }

                    /**
                     * 每天凌晨全量同步 (可选，用于修复数据)
                     */
                    @Scheduled(cron = "0 0 3 * * *")
                    public void fullSync() {
                        for (KnowledgeSource source : sources) {
                            try {
                                SyncResult result = pipeline.sync(source, true);
                                log.info("[{}] 全量同步完成: {}", source.getName(), result);
                            } catch (Exception e) {
                                log.error("[{}] 全量同步失败", source.getName(), e);
                            }
                        }
                    }
                }

                // ============================================================
                // REST API - 手动触发同步
                // ============================================================

                @RestController
                @RequestMapping("/api/knowledge/sync")
                public class SyncController {

                    @Autowired
                    private KnowledgePipeline pipeline;

                    @Autowired
                    private Map<String, KnowledgeSource> sourceMap;

                    /**
                     * 手动触发同步
                     * POST /api/knowledge/sync?source=mysql&mode=full
                     */
                    @PostMapping
                    public SyncResult triggerSync(
                            @RequestParam String source,
                            @RequestParam(defaultValue = "incremental") String mode) {

                        KnowledgeSource knowledgeSource = sourceMap.get(source);
                        if (knowledgeSource == null) {
                            throw new IllegalArgumentException("未知的知识源: " + source);
                        }

                        return pipeline.sync(knowledgeSource, "full".equals(mode));
                    }

                    /**
                     * 查看同步状态
                     * GET /api/knowledge/sync/status
                     */
                    @GetMapping("/status")
                    public List<SyncStatus> getSyncStatus() {
                        return syncStatusRepository.findAll();
                    }
                }
                """);
    }

    private static void printSpringBootIntegration() {
        System.out.println("""

                【application.yml 完整配置】
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                spring:
                  application:
                    name: knowledge-service

                  datasource:
                    url: jdbc:mysql://localhost:3306/knowledge_db
                    username: ${MYSQL_USER:root}
                    password: ${MYSQL_PASSWORD}
                    hikari:
                      maximum-pool-size: 10

                # OpenAI 配置
                openai:
                  api-key: ${OPENAI_API_KEY}
                  base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
                  model: text-embedding-3-small

                # 向量存储配置 (可选: redis/milvus/pgvector/inmemory)
                vector-store:
                  type: ${VECTOR_STORE_TYPE:redis}

                # Redis 配置 (vector-store.type=redis 时生效)
                redis:
                  host: ${REDIS_HOST:localhost}
                  port: ${REDIS_PORT:6379}
                  password: ${REDIS_PASSWORD:}

                # Milvus 配置 (vector-store.type=milvus 时生效)
                milvus:
                  host: ${MILVUS_HOST:localhost}
                  port: ${MILVUS_PORT:19530}

                # 知识源配置
                knowledge:
                  # 文件知识源
                  file:
                    enabled: true
                    root-path: /data/knowledge/docs

                  # MySQL 知识源
                  mysql:
                    enabled: true

                  # Confluence 知识源
                  confluence:
                    enabled: false
                    base-url: https://your-company.atlassian.net/wiki
                    username: ${CONFLUENCE_USER}
                    api-token: ${CONFLUENCE_TOKEN}
                    space-key: KB

                  # 同步配置
                  sync:
                    batch-size: 100
                    embedding-batch: 20
                    incremental-cron: "0 0 * * * *"   # 每小时
                    full-sync-cron: "0 0 3 * * *"    # 每天凌晨3点
                """);

        System.out.println("""

                【项目结构】
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                knowledge-service/
                ├── src/main/java/com/company/knowledge/
                │   ├── KnowledgeServiceApplication.java
                │   │
                │   ├── config/                          # 配置类
                │   │   ├── EmbeddingModelConfig.java
                │   │   ├── VectorStoreConfig.java
                │   │   └── SyncScheduleConfig.java
                │   │
                │   ├── source/                          # 知识源层
                │   │   ├── KnowledgeSource.java         # 接口
                │   │   ├── KnowledgeDocument.java       # 实体
                │   │   ├── MySQLKnowledgeSource.java    # MySQL 实现
                │   │   ├── FileSystemKnowledgeSource.java # 文件实现
                │   │   └── ConfluenceKnowledgeSource.java # Confluence 实现
                │   │
                │   ├── pipeline/                        # 处理管道
                │   │   ├── KnowledgePipeline.java       # 主管道
                │   │   └── SyncResult.java              # 结果
                │   │
                │   ├── store/                           # 向量存储层
                │   │   ├── VectorStoreAdapter.java      # 接口
                │   │   ├── RedisVectorStoreAdapter.java
                │   │   ├── MilvusVectorStoreAdapter.java
                │   │   └── PgVectorStoreAdapter.java
                │   │
                │   ├── service/                         # 业务服务
                │   │   └── KnowledgeSearchService.java  # 搜索服务
                │   │
                │   ├── controller/                      # REST API
                │   │   ├── SearchController.java
                │   │   └── SyncController.java
                │   │
                │   └── repository/                      # 数据访问
                │       └── SyncStatusRepository.java
                │
                ├── src/main/resources/
                │   ├── application.yml
                │   └── application-prod.yml
                │
                └── pom.xml
                """);
    }

    private static void printProductionChecklist() {
        System.out.println("""

                ┌─────────────────────────────────────────────────────────────┐
                │                  生产环境部署清单                            │
                └─────────────────────────────────────────────────────────────┘

                【1. 基础设施】

                □ 向量数据库部署
                  - [ ] Redis Stack (中小规模) 或 Milvus (大规模)
                  - [ ] 配置持久化存储
                  - [ ] 设置备份策略

                □ 应用数据库
                  - [ ] MySQL/PostgreSQL 部署
                  - [ ] 创建知识文档表
                  - [ ] 创建同步状态表

                □ 消息队列 (可选，用于异步处理)
                  - [ ] RabbitMQ 或 Kafka
                  - [ ] 配置死信队列

                【2. 安全配置】

                □ 敏感信息管理
                  - [ ] API Key 使用环境变量或密钥管理服务
                  - [ ] 数据库密码加密存储
                  - [ ] 禁止硬编码密钥

                □ 访问控制
                  - [ ] API 接口鉴权
                  - [ ] 知识库权限控制 (元数据过滤)

                【3. 监控告警】

                □ 应用监控
                  - [ ] 同步任务执行状态
                  - [ ] 向量化 API 调用量
                  - [ ] 搜索响应时间

                □ 基础设施监控
                  - [ ] 向量库内存/CPU 使用率
                  - [ ] 数据库连接池状态

                □ 告警配置
                  - [ ] 同步失败告警
                  - [ ] API 限流告警
                  - [ ] 存储空间告警

                【4. 性能优化】

                □ 批量处理
                  - [ ] 文档分批处理 (每批 100 篇)
                  - [ ] 向量化分批调用 (每批 20 条)

                □ 缓存策略
                  - [ ] 热门查询结果缓存
                  - [ ] Embedding 缓存 (相同文本)

                □ 索引优化
                  - [ ] 向量索引类型选择 (IVF/HNSW)
                  - [ ] 数据库索引优化

                【5. 运维手册】

                □ 日常运维
                  - [ ] 查看同步状态命令
                  - [ ] 手动触发同步接口
                  - [ ] 清理过期数据脚本

                □ 故障处理
                  - [ ] 同步失败恢复流程
                  - [ ] 数据回滚方案
                  - [ ] 向量库重建流程

                【6. API 接口清单】

                ┌────────────────────────────────┬────────────────────────────┐
                │           接口                 │           功能              │
                ├────────────────────────────────┼────────────────────────────┤
                │ GET  /api/knowledge/search     │ 知识检索                    │
                │ POST /api/knowledge/sync       │ 触发同步                    │
                │ GET  /api/knowledge/sync/status│ 同步状态                    │
                │ GET  /api/knowledge/stats      │ 知识库统计                  │
                │ DELETE /api/knowledge/{docId}  │ 删除知识                    │
                └────────────────────────────────┴────────────────────────────┘
                """);
    }
}
