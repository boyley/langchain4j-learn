package com.example.langchain4j.rag.demo;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业级知识库管道 Demo - 真实存储集成
 *
 * 本 Demo 展示企业级知识库的真实架构:
 *
 * 【数据源】                    【向量存储】
 * ┌─────────────┐              ┌─────────────┐
 * │   MySQL     │              │   Redis     │
 * │ PostgreSQL  │  ──增量同步──→ │  Milvus    │
 * │   文件系统   │              │  pgvector   │
 * └─────────────┘              └─────────────┘
 *
 * 核心问题解答:
 * Q: 知识库很大怎么办？
 * A: 1. 增量同步 - 只处理变化的文档
 *    2. 批量处理 - 分批向量化，避免内存溢出
 *    3. 异步处理 - 后台任务处理，不阻塞主流程
 *    4. 分布式存储 - 使用专业向量数据库
 *
 * @author LangChain4j 学习项目
 */
public class EnterpriseKnowledgePipelineDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        企业级知识库管道 - 真实存储集成方案                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 第一部分: 架构概述 ====================
        printArchitectureOverview();

        // ==================== 第二部分: 从 MySQL 读取知识 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第二部分】从 MySQL 数据库读取知识文档");
        System.out.println("═".repeat(60));
        printMySQLIntegration();

        // ==================== 第三部分: 从文件系统读取 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第三部分】从文件系统批量读取文档");
        System.out.println("═".repeat(60));
        printFileSystemIntegration();

        // ==================== 第四部分: 增量同步方案 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第四部分】增量同步 - 解决大规模知识库问题");
        System.out.println("═".repeat(60));
        printIncrementalSync();

        // ==================== 第五部分: 批量处理方案 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第五部分】批量处理 - 避免内存溢出");
        System.out.println("═".repeat(60));
        printBatchProcessing();

        // ==================== 第六部分: 向量存储选型 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第六部分】向量存储选型 - 生产环境方案");
        System.out.println("═".repeat(60));
        printVectorStoreOptions();

        // ==================== 第七部分: 完整生产代码 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第七部分】完整生产代码示例");
        System.out.println("═".repeat(60));
        printProductionCode();

        // ==================== 第八部分: Spring Boot 集成 ====================
        System.out.println("\n");
        System.out.println("═".repeat(60));
        System.out.println("【第八部分】Spring Boot 生产环境集成");
        System.out.println("═".repeat(60));
        printSpringBootIntegration();
    }

    private static void printArchitectureOverview() {
        System.out.println("""
                ═══════════════════════════════════════════════════════════════
                【第一部分】企业知识库架构概述
                ═══════════════════════════════════════════════════════════════

                ┌─────────────────────────────────────────────────────────────┐
                │                    企业知识库完整架构                         │
                └─────────────────────────────────────────────────────────────┘

                【数据源层】               【处理层】              【存储层】
                ┌──────────┐
                │  MySQL   │──┐
                │ 知识文档表 │  │
                └──────────┘  │         ┌──────────┐         ┌──────────┐
                ┌──────────┐  │         │          │         │  Redis   │
                │PostgreSQL│──┼────────→│ 知识处理  │────────→│ +Vector  │
                │  文档表   │  │         │   管道   │         └──────────┘
                └──────────┘  │         │          │         ┌──────────┐
                ┌──────────┐  │         │ 1.分割    │         │ Milvus   │
                │  文件系统  │──┤         │ 2.向量化  │────────→│(专业向量库)│
                │ /docs/*  │  │         │ 3.存储    │         └──────────┘
                └──────────┘  │         └──────────┘         ┌──────────┐
                ┌──────────┐  │                              │PostgreSQL│
                │  API     │──┘                              │+pgvector │
                │Confluence│                                 └──────────┘
                └──────────┘

                【关键设计点】

                1. 数据源多样化
                   - 结构化数据: MySQL/PostgreSQL 中的知识表
                   - 非结构化数据: 文件系统中的 PDF/Word/TXT
                   - 外部系统: Confluence/Notion/飞书文档 API

                2. 处理管道
                   - 分割: 大文档切分成小片段 (500-1000字符)
                   - 向量化: 调用 Embedding API (支持批量)
                   - 存储: 向量 + 原文 + 元数据

                3. 存储选型
                   - 小规模 (<10万条): Redis + RediSearch
                   - 中规模 (10-100万): PostgreSQL + pgvector
                   - 大规模 (>100万): Milvus / Elasticsearch
                """);
    }

    private static void printMySQLIntegration() {
        System.out.println("""

                📋 场景: 公司知识文档存储在 MySQL 数据库中

                【Step 1】数据库表设计
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                -- 知识文档表 (存储原始文档)
                CREATE TABLE knowledge_documents (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    doc_id VARCHAR(64) UNIQUE NOT NULL COMMENT '文档唯一标识',
                    title VARCHAR(255) NOT NULL COMMENT '文档标题',
                    content TEXT NOT NULL COMMENT '文档内容',
                    category VARCHAR(64) COMMENT '分类: HR/财务/技术',
                    version VARCHAR(32) DEFAULT '1.0' COMMENT '版本号',
                    status TINYINT DEFAULT 1 COMMENT '状态: 1有效 0废弃',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_update_time (update_time),  -- 增量同步用
                    INDEX idx_category (category)          -- 分类查询用
                );

                -- 同步状态表 (记录上次同步时间)
                CREATE TABLE sync_status (
                    id INT PRIMARY KEY,
                    last_sync_time DATETIME COMMENT '上次同步时间',
                    sync_count INT DEFAULT 0 COMMENT '累计同步文档数'
                );
                """);

        System.out.println("""

                【Step 2】Java 代码 - 从 MySQL 读取文档
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 从 MySQL 读取知识文档
                 *
                 * @param lastSyncTime 上次同步时间 (null=全量同步)
                 * @return 文档列表
                 */
                public List<KnowledgeDocument> fetchFromMySQL(LocalDateTime lastSyncTime) {
                    List<KnowledgeDocument> documents = new ArrayList<>();

                    // 构建 SQL: 增量查询 (只获取更新的文档)
                    String sql;
                    if (lastSyncTime == null) {
                        // 首次全量同步
                        sql = "SELECT * FROM knowledge_documents WHERE status = 1";
                    } else {
                        // 增量同步: 只获取上次同步后更新的文档
                        sql = "SELECT * FROM knowledge_documents " +
                              "WHERE status = 1 AND update_time > ?";
                    }

                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {

                        if (lastSyncTime != null) {
                            stmt.setTimestamp(1, Timestamp.valueOf(lastSyncTime));
                        }

                        ResultSet rs = stmt.executeQuery();
                        while (rs.next()) {
                            KnowledgeDocument doc = new KnowledgeDocument();
                            doc.setDocId(rs.getString("doc_id"));
                            doc.setTitle(rs.getString("title"));
                            doc.setContent(rs.getString("content"));
                            doc.setCategory(rs.getString("category"));
                            doc.setVersion(rs.getString("version"));
                            doc.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
                            documents.add(doc);
                        }
                    }

                    System.out.println("从 MySQL 获取到 " + documents.size() + " 篇文档");
                    return documents;
                }
                """);

        System.out.println("""

                【Step 3】数据实体类
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 知识文档实体 - 对应数据库表
                 */
                public class KnowledgeDocument {
                    private String docId;           // 文档唯一标识
                    private String title;           // 标题
                    private String content;         // 内容
                    private String category;        // 分类
                    private String version;         // 版本
                    private LocalDateTime updateTime; // 更新时间

                    // getter/setter 省略...
                }
                """);
    }

    private static void printFileSystemIntegration() {
        System.out.println("""

                📋 场景: 公司文档存储在文件服务器 /data/docs/ 目录下

                【目录结构示例】
                ──────────────────────────────────────────────────────────
                /data/docs/
                ├── HR/
                │   ├── 员工手册.pdf
                │   ├── 请假制度.docx
                │   └── 入职指南.txt
                ├── 财务/
                │   ├── 报销制度.pdf
                │   └── 预算流程.docx
                └── 技术/
                    ├── API文档.md
                    └── 开发规范.txt

                【代码实现】使用 LangChain4j 内置的文档加载器
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                import dev.langchain4j.data.document.Document;
                import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
                import dev.langchain4j.data.document.parser.TextDocumentParser;
                import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;

                import java.nio.file.*;
                import java.io.IOException;

                /**
                 * 从文件系统批量加载文档
                 */
                public class FileSystemDocumentLoader {

                    /**
                     * 递归扫描目录，加载所有文档
                     *
                     * @param rootPath 根目录路径
                     * @return 文档列表
                     */
                    public List<Document> loadFromDirectory(String rootPath) throws IOException {
                        List<Document> documents = new ArrayList<>();
                        Path root = Paths.get(rootPath);

                        // 递归遍历所有文件
                        Files.walk(root)
                            .filter(Files::isRegularFile)
                            .forEach(filePath -> {
                                try {
                                    Document doc = loadSingleFile(filePath);
                                    if (doc != null) {
                                        documents.add(doc);
                                    }
                                } catch (Exception e) {
                                    System.err.println("加载失败: " + filePath + ", " + e.getMessage());
                                }
                            });

                        System.out.println("从目录加载 " + documents.size() + " 个文档");
                        return documents;
                    }

                    /**
                     * 根据文件类型选择解析器
                     */
                    private Document loadSingleFile(Path filePath) {
                        String fileName = filePath.getFileName().toString().toLowerCase();

                        // 根据扩展名选择解析器
                        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                            // 文本文件: 直接读取
                            return FileSystemDocumentLoader.loadDocument(
                                filePath,
                                new TextDocumentParser()
                            );

                        } else if (fileName.endsWith(".pdf")) {
                            // PDF文件: 使用 PDFBox 解析
                            return FileSystemDocumentLoader.loadDocument(
                                filePath,
                                new ApachePdfBoxDocumentParser()
                            );

                        } else if (fileName.endsWith(".docx")) {
                            // Word文件: 使用 Apache POI 解析
                            return FileSystemDocumentLoader.loadDocument(
                                filePath,
                                new ApachePoiDocumentParser()
                            );
                        }

                        return null; // 不支持的格式
                    }

                    /**
                     * 增量加载: 只加载修改过的文件
                     *
                     * @param rootPath 根目录
                     * @param lastSyncTime 上次同步时间
                     */
                    public List<Document> loadModifiedFiles(String rootPath,
                                                            LocalDateTime lastSyncTime) throws IOException {
                        List<Document> documents = new ArrayList<>();
                        Path root = Paths.get(rootPath);
                        long lastSyncMillis = lastSyncTime.atZone(ZoneId.systemDefault())
                                                          .toInstant().toEpochMilli();

                        Files.walk(root)
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                try {
                                    // 只加载修改时间 > 上次同步时间的文件
                                    long fileModified = Files.getLastModifiedTime(path).toMillis();
                                    return fileModified > lastSyncMillis;
                                } catch (IOException e) {
                                    return false;
                                }
                            })
                            .forEach(filePath -> {
                                Document doc = loadSingleFile(filePath);
                                if (doc != null) {
                                    // 添加元数据: 文件路径作为 docId
                                    doc.metadata().put("docId", filePath.toString());
                                    doc.metadata().put("category", extractCategory(filePath));
                                    documents.add(doc);
                                }
                            });

                        return documents;
                    }

                    /**
                     * 从路径提取分类 (目录名作为分类)
                     */
                    private String extractCategory(Path filePath) {
                        // /data/docs/HR/员工手册.pdf -> HR
                        Path parent = filePath.getParent();
                        return parent != null ? parent.getFileName().toString() : "未分类";
                    }
                }
                """);

        System.out.println("""

                【Maven 依赖】文档解析器
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                <!-- PDF 解析 -->
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
                    <version>0.36.2</version>
                </dependency>

                <!-- Word/Excel 解析 -->
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-document-parser-apache-poi</artifactId>
                    <version>0.36.2</version>
                </dependency>
                """);
    }

    private static void printIncrementalSync() {
        System.out.println("""

                📋 核心问题: 知识库有 10万+ 文档，不可能每次全量同步

                【解决方案】增量同步 - 只处理变化的文档
                ──────────────────────────────────────────────────────────

                ┌──────────────────────────────────────────────────────────┐
                │                    增量同步流程                           │
                └──────────────────────────────────────────────────────────┘

                首次运行 (全量同步):
                ┌────────┐     ┌────────┐     ┌────────┐     ┌────────┐
                │ 查询所有 │ ──→ │  分割   │ ──→ │ 向量化  │ ──→ │  存储   │
                │  文档   │     │        │     │        │     │        │
                └────────┘     └────────┘     └────────┘     └────────┘
                                                                  │
                                                                  ↓
                                                          记录 lastSyncTime

                后续运行 (增量同步):
                ┌────────┐     ┌────────┐     ┌────────┐     ┌────────┐
                │ 查询变化 │ ──→ │删除旧向量│ ──→ │  分割   │ ──→ │ 存储新  │
                │  文档   │     │(by docId)│     │ 向量化  │     │  向量   │
                └────────┘     └────────┘     └────────┘     └────────┘
                     ↑                                            │
                     │                                            ↓
                 WHERE update_time > lastSyncTime          更新 lastSyncTime

                """);

        System.out.println("""
                【完整代码实现】增量同步服务
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 增量同步服务 - 核心类
                 */
                @Service
                public class KnowledgeSyncService {

                    @Autowired
                    private DataSource dataSource;           // MySQL 数据源

                    @Autowired
                    private EmbeddingModel embeddingModel;   // 向量模型

                    @Autowired
                    private EmbeddingStore<TextSegment> embeddingStore;  // 向量存储

                    @Autowired
                    private DocumentSplitter splitter;       // 文档分割器

                    /**
                     * 执行增量同步 (建议定时任务调用, 如每小时一次)
                     *
                     * @Scheduled(cron = "0 0 * * * *")  // 每小时执行
                     */
                    public SyncResult incrementalSync() {
                        // 1. 获取上次同步时间
                        LocalDateTime lastSyncTime = getLastSyncTime();
                        LocalDateTime currentTime = LocalDateTime.now();

                        System.out.println("开始增量同步...");
                        System.out.println("上次同步时间: " + lastSyncTime);

                        // 2. 查询变化的文档
                        List<KnowledgeDocument> changedDocs = fetchChangedDocuments(lastSyncTime);
                        System.out.println("发现 " + changedDocs.size() + " 篇文档需要同步");

                        if (changedDocs.isEmpty()) {
                            return new SyncResult(0, 0, "无需同步");
                        }

                        int processedCount = 0;
                        int segmentCount = 0;

                        // 3. 处理每个变化的文档
                        for (KnowledgeDocument doc : changedDocs) {
                            try {
                                // 3.1 删除该文档的旧向量 (如果存在)
                                deleteOldEmbeddings(doc.getDocId());

                                // 3.2 分割文档
                                List<TextSegment> segments = splitDocument(doc);

                                // 3.3 向量化并存储
                                storeEmbeddings(doc, segments);

                                processedCount++;
                                segmentCount += segments.size();

                            } catch (Exception e) {
                                System.err.println("处理文档失败: " + doc.getDocId() + ", " + e.getMessage());
                            }
                        }

                        // 4. 更新同步时间
                        updateLastSyncTime(currentTime);

                        System.out.println("同步完成: 处理 " + processedCount + " 篇文档, "
                                         + segmentCount + " 个片段");

                        return new SyncResult(processedCount, segmentCount, "成功");
                    }

                    /**
                     * 查询变化的文档 (update_time > lastSyncTime)
                     */
                    private List<KnowledgeDocument> fetchChangedDocuments(LocalDateTime lastSyncTime) {
                        String sql = lastSyncTime == null
                            ? "SELECT * FROM knowledge_documents WHERE status = 1"
                            : "SELECT * FROM knowledge_documents WHERE status = 1 AND update_time > ?";

                        // ... JDBC 查询代码 (见上文)
                    }

                    /**
                     * 删除文档的旧向量
                     *
                     * 注意: 不同向量库删除方式不同:
                     * - Redis: DEL embedding:docId:*
                     * - PostgreSQL: DELETE FROM embeddings WHERE metadata->>'docId' = ?
                     * - Milvus: collection.delete("docId == '" + docId + "'")
                     */
                    private void deleteOldEmbeddings(String docId) {
                        // 使用 LangChain4j 的 filter 删除
                        embeddingStore.removeAll(
                            MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId)
                        );
                    }

                    /**
                     * 分割文档成片段
                     */
                    private List<TextSegment> splitDocument(KnowledgeDocument doc) {
                        // 创建 Document 对象
                        Metadata metadata = new Metadata();
                        metadata.put("docId", doc.getDocId());
                        metadata.put("title", doc.getTitle());
                        metadata.put("category", doc.getCategory());
                        metadata.put("version", doc.getVersion());

                        Document document = Document.from(doc.getContent(), metadata);

                        // 分割
                        return splitter.split(document);
                    }

                    /**
                     * 向量化并存储
                     */
                    private void storeEmbeddings(KnowledgeDocument doc, List<TextSegment> segments) {
                        // 批量向量化 (重要: 减少 API 调用次数)
                        List<Embedding> embeddings = embeddingModel.embedAll(
                            segments.stream().map(TextSegment::text).toList()
                        ).content();

                        // 批量存储
                        embeddingStore.addAll(embeddings, segments);
                    }

                    // 获取/更新同步时间的辅助方法...
                }
                """);
    }

    private static void printBatchProcessing() {
        System.out.println("""

                📋 核心问题: 一次性处理大量文档会导致内存溢出 (OOM)

                【解决方案】批量处理 + 流式处理
                ──────────────────────────────────────────────────────────

                ┌──────────────────────────────────────────────────────────┐
                │                    批量处理策略                           │
                └──────────────────────────────────────────────────────────┘

                ❌ 错误方式: 一次性加载所有文档
                List<Document> allDocs = loadAllDocuments();  // 10万文档 → OOM!
                embeddingModel.embedAll(allDocs);             // API 超时!

                ✅ 正确方式: 分批处理
                while (hasMoreDocuments()) {
                    List<Document> batch = loadBatch(100);    // 每批 100 个
                    process(batch);                           // 处理完释放内存
                }

                """);

        System.out.println("""
                【代码实现】批量处理器
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 批量处理器 - 避免内存溢出
                 */
                public class BatchProcessor {

                    private static final int BATCH_SIZE = 100;        // 每批文档数
                    private static final int EMBEDDING_BATCH = 20;    // 每批向量化数

                    private final EmbeddingModel embeddingModel;
                    private final EmbeddingStore<TextSegment> store;
                    private final DocumentSplitter splitter;

                    /**
                     * 批量处理大规模文档
                     *
                     * @param totalDocuments 总文档数
                     */
                    public void processBatch(int totalDocuments) {
                        int processedCount = 0;
                        int offset = 0;

                        System.out.println("开始批量处理 " + totalDocuments + " 篇文档...");
                        System.out.println("批次大小: " + BATCH_SIZE);

                        while (offset < totalDocuments) {
                            // 1. 分页查询一批文档
                            List<KnowledgeDocument> batch = fetchBatch(offset, BATCH_SIZE);

                            if (batch.isEmpty()) {
                                break;
                            }

                            // 2. 处理这一批
                            int batchProcessed = processSingleBatch(batch);
                            processedCount += batchProcessed;

                            // 3. 打印进度
                            double progress = (double) processedCount / totalDocuments * 100;
                            System.out.printf("进度: %.1f%% (%d/%d)%n",
                                              progress, processedCount, totalDocuments);

                            // 4. 移动到下一批
                            offset += BATCH_SIZE;

                            // 5. 可选: 每批之间暂停，避免 API 限流
                            // Thread.sleep(1000);
                        }

                        System.out.println("批量处理完成: " + processedCount + " 篇文档");
                    }

                    /**
                     * 处理单批文档
                     */
                    private int processSingleBatch(List<KnowledgeDocument> docs) {
                        int count = 0;

                        for (KnowledgeDocument doc : docs) {
                            // 分割文档
                            List<TextSegment> segments = splitDocument(doc);

                            // 批量向量化 (每次最多 EMBEDDING_BATCH 个)
                            for (int i = 0; i < segments.size(); i += EMBEDDING_BATCH) {
                                int end = Math.min(i + EMBEDDING_BATCH, segments.size());
                                List<TextSegment> subBatch = segments.subList(i, end);

                                // 向量化
                                List<Embedding> embeddings = embeddingModel.embedAll(
                                    subBatch.stream().map(TextSegment::text).toList()
                                ).content();

                                // 存储
                                store.addAll(embeddings, subBatch);
                            }

                            count++;
                        }

                        // 处理完一批后，建议手动 GC (可选)
                        // System.gc();

                        return count;
                    }

                    /**
                     * 分页查询
                     */
                    private List<KnowledgeDocument> fetchBatch(int offset, int limit) {
                        String sql = "SELECT * FROM knowledge_documents " +
                                     "WHERE status = 1 " +
                                     "ORDER BY id " +
                                     "LIMIT ? OFFSET ?";
                        // ... JDBC 实现
                    }
                }
                """);

        System.out.println("""

                【异步处理】使用 CompletableFuture
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 异步批量处理 - 不阻塞主线程
                 */
                @Service
                public class AsyncBatchProcessor {

                    @Autowired
                    private ThreadPoolExecutor executor;  // 线程池

                    /**
                     * 异步启动批量处理
                     */
                    public CompletableFuture<SyncResult> processAsync(int totalDocuments) {
                        return CompletableFuture.supplyAsync(() -> {
                            System.out.println("后台任务启动: " + Thread.currentThread().getName());

                            // 执行批量处理
                            processBatch(totalDocuments);

                            return new SyncResult(totalDocuments, 0, "完成");
                        }, executor);
                    }

                    /**
                     * 带进度回调的异步处理
                     */
                    public void processWithProgress(int totalDocuments,
                                                    Consumer<Double> progressCallback) {
                        executor.submit(() -> {
                            int processed = 0;
                            int offset = 0;

                            while (offset < totalDocuments) {
                                List<KnowledgeDocument> batch = fetchBatch(offset, 100);
                                processSingleBatch(batch);

                                processed += batch.size();
                                offset += 100;

                                // 回调进度
                                double progress = (double) processed / totalDocuments;
                                progressCallback.accept(progress);
                            }
                        });
                    }
                }

                // 使用示例:
                asyncProcessor.processWithProgress(100000, progress -> {
                    System.out.printf("同步进度: %.1f%%%n", progress * 100);
                    // 可以更新数据库中的进度状态
                });
                """);
    }

    private static void printVectorStoreOptions() {
        System.out.println("""

                📋 向量存储选型指南
                ──────────────────────────────────────────────────────────

                ┌────────────────┬────────────┬────────────┬────────────┐
                │     存储方案    │   数据规模  │   查询性能  │   适用场景  │
                ├────────────────┼────────────┼────────────┼────────────┤
                │ InMemory       │ <1万条     │ 极快       │ 开发测试    │
                │ Redis+Search   │ <10万条    │ 快 <10ms   │ 中小企业    │
                │ PostgreSQL     │ <100万条   │ 中 <50ms   │ 已有PG环境  │
                │ Milvus         │ 千万级+    │ 快 <10ms   │ 大规模生产  │
                │ Elasticsearch  │ 百万级     │ 中 <30ms   │ 需要全文搜索│
                └────────────────┴────────────┴────────────┴────────────┘

                """);

        System.out.println("""
                【方案1】Redis + RediSearch (推荐中小规模)
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // Maven 依赖
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-redis</artifactId>
                    <version>0.36.2</version>
                </dependency>

                // 代码配置
                EmbeddingStore<TextSegment> store = RedisEmbeddingStore.builder()
                    .host("localhost")                    // Redis 地址
                    .port(6379)                           // Redis 端口
                    .user("default")                      // 用户名 (Redis 6.0+)
                    .password("your-password")            // 密码
                    .indexName("knowledge_vectors")       // 索引名称
                    .dimension(1536)                      // 向量维度 (必须匹配模型)
                    .metadataKeys(List.of(                // 需要索引的元数据字段
                        "docId", "title", "category"
                    ))
                    .build();

                // Docker 启动 Redis Stack (包含 RediSearch)
                // docker run -d --name redis-stack -p 6379:6379 redis/redis-stack:latest
                """);

        System.out.println("""

                【方案2】PostgreSQL + pgvector (推荐已有PG环境)
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // Maven 依赖
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-pgvector</artifactId>
                    <version>0.36.2</version>
                </dependency>

                // 代码配置
                EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                    .host("localhost")
                    .port(5432)
                    .database("knowledge_db")
                    .user("postgres")
                    .password("your-password")
                    .table("embeddings")                  // 表名
                    .dimension(1536)                      // 向量维度
                    .createTable(true)                    // 自动创建表
                    .dropTableFirst(false)                // 不删除已有数据
                    .build();

                // 安装 pgvector 扩展
                // CREATE EXTENSION IF NOT EXISTS vector;

                // 自动创建的表结构:
                // CREATE TABLE embeddings (
                //     id UUID PRIMARY KEY,
                //     embedding vector(1536),
                //     text TEXT,
                //     metadata JSONB
                // );
                """);

        System.out.println("""

                【方案3】Milvus (推荐大规模生产)
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // Maven 依赖
                <dependency>
                    <groupId>dev.langchain4j</groupId>
                    <artifactId>langchain4j-milvus</artifactId>
                    <version>0.36.2</version>
                </dependency>

                // 代码配置
                EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
                    .host("localhost")
                    .port(19530)
                    .collectionName("knowledge_vectors")
                    .dimension(1536)
                    .metricType(MetricType.COSINE)        // 相似度计算方式
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .build();

                // Docker Compose 启动 Milvus
                // version: '3.5'
                // services:
                //   milvus:
                //     image: milvusdb/milvus:latest
                //     ports:
                //       - "19530:19530"
                //       - "9091:9091"
                """);
    }

    private static void printProductionCode() {
        System.out.println("""

                📋 完整生产代码 - 知识库同步任务
                ──────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 生产环境: 知识库同步定时任务
                 *
                 * 功能:
                 * 1. 每小时执行增量同步
                 * 2. 从 MySQL 读取变化的文档
                 * 3. 向量化后存储到 Redis
                 * 4. 支持手动触发全量同步
                 */
                @Component
                @Slf4j
                public class KnowledgeSyncJob {

                    @Autowired
                    private DataSource dataSource;

                    @Autowired
                    private RedisEmbeddingStore embeddingStore;

                    @Autowired
                    private EmbeddingModel embeddingModel;

                    private final DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

                    /**
                     * 定时增量同步 (每小时)
                     */
                    @Scheduled(cron = "0 0 * * * *")
                    public void scheduledSync() {
                        log.info("定时任务: 开始增量同步...");
                        try {
                            SyncResult result = doIncrementalSync();
                            log.info("定时任务完成: {}", result);
                        } catch (Exception e) {
                            log.error("定时任务失败", e);
                            // 发送告警...
                        }
                    }

                    /**
                     * 手动触发全量同步 (管理后台调用)
                     */
                    public SyncResult fullSync() {
                        log.info("手动触发: 全量同步...");
                        // 清空现有数据
                        // embeddingStore.removeAll();  // 谨慎使用!
                        return doSync(null);  // null 表示全量
                    }

                    /**
                     * 执行同步
                     */
                    private SyncResult doSync(LocalDateTime lastSyncTime) {
                        int docCount = 0;
                        int segmentCount = 0;

                        String sql = lastSyncTime == null
                            ? "SELECT * FROM knowledge_documents WHERE status = 1"
                            : "SELECT * FROM knowledge_documents WHERE status = 1 AND update_time > ?";

                        try (Connection conn = dataSource.getConnection();
                             PreparedStatement stmt = conn.prepareStatement(sql)) {

                            if (lastSyncTime != null) {
                                stmt.setTimestamp(1, Timestamp.valueOf(lastSyncTime));
                            }

                            ResultSet rs = stmt.executeQuery();

                            List<TextSegment> batchSegments = new ArrayList<>();

                            while (rs.next()) {
                                String docId = rs.getString("doc_id");

                                // 1. 删除旧向量
                                embeddingStore.removeAll(
                                    MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId)
                                );

                                // 2. 构建文档
                                Metadata metadata = new Metadata();
                                metadata.put("docId", docId);
                                metadata.put("title", rs.getString("title"));
                                metadata.put("category", rs.getString("category"));

                                Document doc = Document.from(rs.getString("content"), metadata);

                                // 3. 分割
                                List<TextSegment> segments = splitter.split(doc);
                                batchSegments.addAll(segments);

                                docCount++;
                                segmentCount += segments.size();

                                // 4. 批量存储 (每 100 个片段)
                                if (batchSegments.size() >= 100) {
                                    storeBatch(batchSegments);
                                    batchSegments.clear();
                                }
                            }

                            // 5. 存储剩余片段
                            if (!batchSegments.isEmpty()) {
                                storeBatch(batchSegments);
                            }

                            // 6. 更新同步时间
                            updateSyncTime(LocalDateTime.now());

                        } catch (SQLException e) {
                            log.error("同步失败", e);
                            throw new RuntimeException(e);
                        }

                        return new SyncResult(docCount, segmentCount, "成功");
                    }

                    /**
                     * 批量存储
                     */
                    private void storeBatch(List<TextSegment> segments) {
                        List<Embedding> embeddings = embeddingModel.embedAll(
                            segments.stream().map(TextSegment::text).toList()
                        ).content();

                        embeddingStore.addAll(embeddings, segments);

                        log.debug("批量存储 {} 个片段", segments.size());
                    }
                }
                """);
    }

    private static void printSpringBootIntegration() {
        System.out.println("""

                📋 Spring Boot 完整配置
                ──────────────────────────────────────────────────────────

                【application.yml】
                """);

        System.out.println("""
                spring:
                  datasource:
                    url: jdbc:mysql://localhost:3306/knowledge_db
                    username: root
                    password: ${MYSQL_PASSWORD}  # 从环境变量读取
                    hikari:
                      maximum-pool-size: 10

                # OpenAI 配置
                openai:
                  api-key: ${OPENAI_API_KEY}
                  base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
                  embedding-model: text-embedding-3-small

                # Redis 向量存储配置
                redis:
                  host: ${REDIS_HOST:localhost}
                  port: ${REDIS_PORT:6379}
                  password: ${REDIS_PASSWORD:}

                # 同步任务配置
                knowledge:
                  sync:
                    enabled: true
                    batch-size: 100
                    cron: "0 0 * * * *"  # 每小时
                """);

        System.out.println("""

                【配置类】
                """);

        System.out.println("""
                @Configuration
                public class KnowledgeConfig {

                    @Bean
                    public EmbeddingModel embeddingModel(
                            @Value("${openai.api-key}") String apiKey,
                            @Value("${openai.base-url}") String baseUrl,
                            @Value("${openai.embedding-model}") String model) {

                        return OpenAiEmbeddingModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(model)
                            .build();
                    }

                    @Bean
                    public EmbeddingStore<TextSegment> embeddingStore(
                            @Value("${redis.host}") String host,
                            @Value("${redis.port}") int port,
                            @Value("${redis.password}") String password) {

                        return RedisEmbeddingStore.builder()
                            .host(host)
                            .port(port)
                            .password(password.isEmpty() ? null : password)
                            .indexName("knowledge_vectors")
                            .dimension(1536)
                            .metadataKeys(List.of("docId", "title", "category"))
                            .build();
                    }

                    @Bean
                    public DocumentSplitter documentSplitter() {
                        return DocumentSplitters.recursive(500, 50);
                    }
                }
                """);

        System.out.println("""

                【REST API 接口】
                """);

        System.out.println("""
                @RestController
                @RequestMapping("/api/knowledge")
                public class KnowledgeController {

                    @Autowired
                    private KnowledgeSyncService syncService;

                    @Autowired
                    private EmbeddingStore<TextSegment> store;

                    @Autowired
                    private EmbeddingModel embeddingModel;

                    /**
                     * 搜索知识
                     * GET /api/knowledge/search?q=如何请假&limit=5
                     */
                    @GetMapping("/search")
                    public List<SearchResult> search(
                            @RequestParam("q") String query,
                            @RequestParam(defaultValue = "5") int limit,
                            @RequestParam(required = false) String category) {

                        Embedding queryEmbedding = embeddingModel.embed(query).content();

                        EmbeddingSearchRequest.Builder builder = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(limit)
                            .minScore(0.5);

                        if (category != null) {
                            builder.filter(MetadataFilterBuilder
                                .metadataKey("category").isEqualTo(category));
                        }

                        return store.search(builder.build()).matches().stream()
                            .map(match -> new SearchResult(
                                match.embedded().text(),
                                match.embedded().metadata().getString("title"),
                                match.score()
                            ))
                            .toList();
                    }

                    /**
                     * 手动触发同步
                     * POST /api/knowledge/sync?type=full
                     */
                    @PostMapping("/sync")
                    public SyncResult triggerSync(
                            @RequestParam(defaultValue = "incremental") String type) {

                        if ("full".equals(type)) {
                            return syncService.fullSync();
                        } else {
                            return syncService.incrementalSync();
                        }
                    }

                    /**
                     * 查看同步状态
                     * GET /api/knowledge/sync/status
                     */
                    @GetMapping("/sync/status")
                    public SyncStatus getSyncStatus() {
                        return syncService.getStatus();
                    }
                }
                """);

        System.out.println("""

                ══════════════════════════════════════════════════════════════
                【总结】企业级知识库架构要点
                ══════════════════════════════════════════════════════════════

                1. 数据源
                   ✓ 从 MySQL/PostgreSQL 读取结构化知识
                   ✓ 从文件系统读取 PDF/Word/TXT
                   ✓ 从 API 对接 Confluence/Notion

                2. 处理管道
                   ✓ 增量同步 - 只处理变化的文档
                   ✓ 批量处理 - 分批次避免 OOM
                   ✓ 异步处理 - 后台任务不阻塞

                3. 向量存储
                   ✓ 开发测试用 InMemory
                   ✓ 中小规模用 Redis
                   ✓ 大规模用 Milvus/PostgreSQL

                4. 元数据管理
                   ✓ docId - 用于更新/删除
                   ✓ category - 用于分类过滤
                   ✓ updateTime - 用于增量同步

                5. 生产就绪
                   ✓ 定时任务调度
                   ✓ 进度监控
                   ✓ 错误处理和重试
                   ✓ REST API 接口
                """);
    }
}
