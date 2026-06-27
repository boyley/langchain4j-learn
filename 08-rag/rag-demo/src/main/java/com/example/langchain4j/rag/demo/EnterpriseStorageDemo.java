package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 企业级知识库存储方案 - 完整教程
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                    企业知识库系统架构
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   ┌─────────────────────────────────────────────────────────────────────────────┐
 *   │                           数据来源层                                         │
 *   │                                                                              │
 *   │    ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐         │
 *   │    │  TXT    │  │  PDF    │  │  Word   │  │ 数据库  │  │  网页    │         │
 *   │    │  文件   │  │  文件   │  │  文件   │  │  MySQL  │  │  URL    │         │
 *   │    └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘         │
 *   │         │            │            │            │            │               │
 *   │         └────────────┴────────────┴────────────┴────────────┘               │
 *   │                                   │                                          │
 *   │                                   ▼                                          │
 *   │                         ┌─────────────────┐                                  │
 *   │                         │   文档加载器     │                                  │
 *   │                         │ DocumentLoader  │                                  │
 *   │                         └────────┬────────┘                                  │
 *   └──────────────────────────────────┼──────────────────────────────────────────┘
 *                                      │
 *                                      ▼
 *   ┌─────────────────────────────────────────────────────────────────────────────┐
 *   │                           处理层                                             │
 *   │                                                                              │
 *   │    ┌─────────────────┐         ┌─────────────────┐                          │
 *   │    │   文档分割器     │   ───▶  │   向量化模型     │                          │
 *   │    │ DocumentSplitter│         │ EmbeddingModel  │                          │
 *   │    │                 │         │                 │                          │
 *   │    │ 长文档→小片段   │         │ 文本→1536维向量  │                          │
 *   │    └─────────────────┘         └────────┬────────┘                          │
 *   └─────────────────────────────────────────┼───────────────────────────────────┘
 *                                             │
 *                                             ▼
 *   ┌─────────────────────────────────────────────────────────────────────────────┐
 *   │                           存储层 (选择其一)                                   │
 *   │                                                                              │
 *   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐   │
 *   │  │   内存存储     │ │    Redis      │ │  PostgreSQL   │ │    Milvus     │   │
 *   │  │  (测试用)     │ │  (高速缓存)   │ │  + pgvector   │ │  (专业向量库) │   │
 *   │  │              │ │              │ │  (关系数据库) │ │              │   │
 *   │  │ 重启丢失     │ │ 支持持久化   │ │  全功能      │ │  大规模场景  │   │
 *   │  └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘   │
 *   │                                                                              │
 *   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐   │
 *   │  │ Elasticsearch │ │   Pinecone    │ │    Qdrant     │ │   Chroma      │   │
 *   │  │  (全文+向量)  │ │  (云托管)    │ │  (高性能)    │ │  (轻量级)    │   │
 *   │  └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘   │
 *   └─────────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                    各存储方案对比
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────┬────────────┬────────────┬────────────┬────────────────────┐
 * │ 存储方案         │ 适用场景    │ 数据量     │ 部署难度   │ 特点               │
 * ├──────────────────┼────────────┼────────────┼────────────┼────────────────────┤
 * │ InMemory         │ 开发测试    │ <1万条     │ 无需部署   │ 重启丢失           │
 * │ Redis            │ 中小规模    │ <100万条   │ 简单       │ 速度快，可持久化   │
 * │ PostgreSQL       │ 中等规模    │ <1000万条  │ 简单       │ 可靠，全功能       │
 * │ Elasticsearch    │ 全文+向量   │ 大规模     │ 中等       │ 混合搜索           │
 * │ Milvus           │ 大规模      │ 亿级       │ 中等       │ 专业向量数据库     │
 * │ Pinecone         │ 云托管      │ 大规模     │ 最简单     │ 无需运维，按量付费 │
 * │ Qdrant           │ 高性能      │ 大规模     │ 中等       │ Rust实现，速度快   │
 * └──────────────────┴────────────┴────────────┴────────────┴────────────────────┘
 *
 * 推荐选择：
 * - 刚开始学习/Demo：InMemory
 * - 小公司/初创团队：PostgreSQL + pgvector 或 Redis
 * - 中大型公司：Milvus 或 Elasticsearch
 * - 不想运维：Pinecone (云服务)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class EnterpriseStorageDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═".repeat(75));
        System.out.println("          企业级知识库存储方案 - 完整教程");
        System.out.println("═".repeat(75));

        // 初始化向量化模型
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")
                .build();

        // ═══════════════════════════════════════════════════════════════
        // 第一部分：文档加载 - 从各种来源读取文档
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("        第一部分：文档加载 - 从各种来源读取文档");
        System.out.println("▓".repeat(75));

        demoDocumentLoading();

        // ═══════════════════════════════════════════════════════════════
        // 第二部分：向量存储方案
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("        第二部分：向量存储方案");
        System.out.println("▓".repeat(75));

        demoVectorStorage(embeddingModel);

        // ═══════════════════════════════════════════════════════════════
        // 第三部分：生产环境最佳实践
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("        第三部分：生产环境最佳实践");
        System.out.println("▓".repeat(75));

        demoBestPractices();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //
    //                       第一部分：文档加载
    //
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoDocumentLoading() throws Exception {
        System.out.println("\n\n【1.1】从 TXT 文件加载");
        System.out.println("─".repeat(75));
        demoLoadFromTxt();

        System.out.println("\n\n【1.2】从文件夹批量加载");
        System.out.println("─".repeat(75));
        demoLoadFromDirectory();

        System.out.println("\n\n【1.3】从数据库加载");
        System.out.println("─".repeat(75));
        demoLoadFromDatabase();

        System.out.println("\n\n【1.4】从 PDF 文件加载");
        System.out.println("─".repeat(75));
        demoLoadFromPdf();

        System.out.println("\n\n【1.5】从 URL/网页加载");
        System.out.println("─".repeat(75));
        demoLoadFromUrl();

        System.out.println("\n\n【1.6】文档加载器对比表");
        System.out.println("─".repeat(75));
        printDocumentLoaderComparison();
    }

    /**
     * 从 TXT 文件加载
     */
    private static void demoLoadFromTxt() throws Exception {
        System.out.println("""

            方法 1：使用 Java 原生 API 读取
            ─────────────────────────────────────────────────────
            """);

        System.out.println("代码示例：\n");
        System.out.println("""
            // 方式 1：读取整个文件为字符串
            String content = Files.readString(Path.of("knowledge/员工手册.txt"));
            Document document = Document.from(content);

            // 方式 2：按行读取
            List<String> lines = Files.readAllLines(Path.of("knowledge/FAQ.txt"));
            String content = String.join("\\n", lines);
            Document document = Document.from(content);

            // 方式 3：带元数据
            String content = Files.readString(Path.of("knowledge/产品文档.txt"));
            Metadata metadata = Metadata.from(Map.of(
                "source", "产品文档.txt",
                "department", "产品部",
                "updateDate", "2024-01-15"
            ));
            Document document = Document.from(content, metadata);
            """);

        System.out.println("\n" + """
            方法 2：使用 LangChain4j 文档加载器
            ─────────────────────────────────────────────────────
            """);

        System.out.println("代码示例：\n");
        System.out.println("""
            // 需要添加依赖：langchain4j-document-loader-filesystem
            // <dependency>
            //     <groupId>dev.langchain4j</groupId>
            //     <artifactId>langchain4j-document-loader-filesystem</artifactId>
            //     <version>0.27.1</version>
            // </dependency>

            import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;

            // 加载单个文件
            Document doc = FileSystemDocumentLoader.loadDocument("knowledge/员工手册.txt");

            // 加载文件夹下所有文件
            List<Document> docs = FileSystemDocumentLoader.loadDocuments("knowledge/");
            """);
    }

    /**
     * 从文件夹批量加载
     */
    private static void demoLoadFromDirectory() throws Exception {
        System.out.println("""

            场景：批量导入整个知识库文件夹
            ─────────────────────────────────────────────────────

            假设文件夹结构：
            knowledge/
            ├── hr/
            │   ├── 员工手册.txt
            │   ├── 请假制度.txt
            │   └── 福利政策.txt
            ├── tech/
            │   ├── 技术架构.txt
            │   └── 编码规范.txt
            └── product/
                ├── 产品介绍.txt
                └── 使用指南.txt
            """);

        System.out.println("代码示例：\n");
        System.out.println("""
            // 方法 1：Java 原生递归遍历
            public List<Document> loadFromDirectory(String dirPath) throws IOException {
                List<Document> documents = new ArrayList<>();

                // 遍历目录下所有 .txt 文件
                Files.walk(Path.of(dirPath))
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            // 自动添加文件路径作为元数据
                            Metadata metadata = Metadata.from(Map.of(
                                "source", path.toString(),
                                "fileName", path.getFileName().toString(),
                                "department", path.getParent().getFileName().toString()
                            ));
                            documents.add(Document.from(content, metadata));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                return documents;
            }

            // 使用
            List<Document> allDocs = loadFromDirectory("knowledge/");
            System.out.println("共加载 " + allDocs.size() + " 个文档");


            // 方法 2：使用 LangChain4j 加载器
            import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
            import dev.langchain4j.data.document.parser.TextDocumentParser;

            // 加载指定类型的文件
            List<Document> docs = FileSystemDocumentLoader.loadDocuments(
                "knowledge/",
                new TextDocumentParser()  // 使用文本解析器
            );
            """);
    }

    /**
     * 从数据库加载
     */
    private static void demoLoadFromDatabase() {
        System.out.println("""

            场景：从 MySQL/PostgreSQL 等数据库加载文档
            ─────────────────────────────────────────────────────

            数据库表结构示例：

            CREATE TABLE knowledge_base (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                title VARCHAR(255) NOT NULL,           -- 文档标题
                content TEXT NOT NULL,                 -- 文档内容
                department VARCHAR(100),               -- 所属部门
                access_level VARCHAR(50),              -- 访问级别
                created_at TIMESTAMP DEFAULT NOW(),    -- 创建时间
                updated_at TIMESTAMP DEFAULT NOW()     -- 更新时间
            );
            """);

        System.out.println("代码示例：\n");
        System.out.println("""
            /**
             * 从数据库加载文档
             *
             * 连接参数说明：
             * - url: 数据库连接地址
             *   格式: jdbc:mysql://主机:端口/数据库名
             *   示例: jdbc:mysql://localhost:3306/knowledge_db
             *
             * - user: 数据库用户名
             * - password: 数据库密码
             */
            public List<Document> loadFromDatabase() throws SQLException {
                List<Document> documents = new ArrayList<>();

                // 数据库连接配置
                String url = "jdbc:mysql://localhost:3306/knowledge_db";
                String user = "root";
                String password = "your_password";

                // 建立连接并查询
                try (Connection conn = DriverManager.getConnection(url, user, password);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT id, title, content, department, access_level FROM knowledge_base"
                     )) {

                    while (rs.next()) {
                        // 获取内容
                        String content = rs.getString("content");

                        // 构建元数据（用于后续过滤和溯源）
                        Metadata metadata = Metadata.from(Map.of(
                            "id", rs.getString("id"),
                            "title", rs.getString("title"),
                            "department", rs.getString("department"),
                            "accessLevel", rs.getString("access_level")
                        ));

                        documents.add(Document.from(content, metadata));
                    }
                }

                return documents;
            }


            // ═══════════════════════════════════════════════════════════
            // 使用 Spring JdbcTemplate（推荐）
            // ═══════════════════════════════════════════════════════════

            @Autowired
            private JdbcTemplate jdbcTemplate;

            public List<Document> loadFromDatabaseWithSpring() {
                String sql = "SELECT * FROM knowledge_base WHERE access_level = ?";

                return jdbcTemplate.query(sql, new Object[]{"public"}, (rs, rowNum) -> {
                    String content = rs.getString("content");
                    Metadata metadata = Metadata.from(Map.of(
                        "id", rs.getString("id"),
                        "title", rs.getString("title"),
                        "department", rs.getString("department")
                    ));
                    return Document.from(content, metadata);
                });
            }


            // ═══════════════════════════════════════════════════════════
            // 使用 MyBatis
            // ═══════════════════════════════════════════════════════════

            // Mapper 接口
            @Mapper
            public interface KnowledgeMapper {
                @Select("SELECT * FROM knowledge_base")
                List<KnowledgeEntity> selectAll();
            }

            // 转换为 Document
            public List<Document> loadWithMyBatis() {
                return knowledgeMapper.selectAll().stream()
                    .map(entity -> {
                        Metadata metadata = Metadata.from(Map.of(
                            "id", entity.getId().toString(),
                            "title", entity.getTitle(),
                            "department", entity.getDepartment()
                        ));
                        return Document.from(entity.getContent(), metadata);
                    })
                    .collect(Collectors.toList());
            }
            """);
    }

    /**
     * 从 PDF 文件加载
     */
    private static void demoLoadFromPdf() {
        System.out.println("""

            场景：加载 PDF 格式的文档（合同、报告、手册等）
            ─────────────────────────────────────────────────────
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <!-- 方式 1：使用 LangChain4j PDF 解析器 -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
                <version>0.27.1</version>
            </dependency>

            <!-- 方式 2：使用 Apache PDFBox（更底层控制） -->
            <dependency>
                <groupId>org.apache.pdfbox</groupId>
                <artifactId>pdfbox</artifactId>
                <version>2.0.29</version>
            </dependency>

            <!-- 方式 3：使用 Apache Tika（支持多种格式） -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-document-parser-apache-tika</artifactId>
                <version>0.27.1</version>
            </dependency>
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            // ═══════════════════════════════════════════════════════════
            // 方式 1：使用 LangChain4j PDF 解析器（推荐）
            // ═══════════════════════════════════════════════════════════

            import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
            import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;

            // 加载单个 PDF
            Document doc = FileSystemDocumentLoader.loadDocument(
                "documents/员工手册.pdf",
                new ApachePdfBoxDocumentParser()
            );

            // 加载文件夹下所有 PDF
            List<Document> docs = FileSystemDocumentLoader.loadDocuments(
                "documents/",
                new ApachePdfBoxDocumentParser()
            );


            // ═══════════════════════════════════════════════════════════
            // 方式 2：使用 Apache PDFBox 直接解析
            // ═══════════════════════════════════════════════════════════

            import org.apache.pdfbox.pdmodel.PDDocument;
            import org.apache.pdfbox.text.PDFTextStripper;

            public Document loadPdfWithPdfBox(String filePath) throws IOException {
                try (PDDocument pdfDoc = PDDocument.load(new File(filePath))) {
                    PDFTextStripper stripper = new PDFTextStripper();

                    // 提取全部文本
                    String content = stripper.getText(pdfDoc);

                    // 获取 PDF 元数据
                    Metadata metadata = Metadata.from(Map.of(
                        "source", filePath,
                        "pageCount", String.valueOf(pdfDoc.getNumberOfPages()),
                        "title", pdfDoc.getDocumentInformation().getTitle()
                    ));

                    return Document.from(content, metadata);
                }
            }

            // 按页提取（适合大文档）
            public List<Document> loadPdfByPage(String filePath) throws IOException {
                List<Document> pages = new ArrayList<>();

                try (PDDocument pdfDoc = PDDocument.load(new File(filePath))) {
                    PDFTextStripper stripper = new PDFTextStripper();

                    for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                        stripper.setStartPage(i);
                        stripper.setEndPage(i);

                        String content = stripper.getText(pdfDoc);
                        Metadata metadata = Metadata.from(Map.of(
                            "source", filePath,
                            "page", String.valueOf(i)
                        ));

                        pages.add(Document.from(content, metadata));
                    }
                }

                return pages;
            }


            // ═══════════════════════════════════════════════════════════
            // 方式 3：使用 Apache Tika（支持 PDF、Word、Excel 等多种格式）
            // ═══════════════════════════════════════════════════════════

            import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

            // Tika 可以自动识别文件类型
            Document doc = FileSystemDocumentLoader.loadDocument(
                "documents/report.pdf",      // 也可以是 .docx, .xlsx, .pptx 等
                new ApacheTikaDocumentParser()
            );
            """);
    }

    /**
     * 从 URL/网页加载
     */
    private static void demoLoadFromUrl() {
        System.out.println("""

            场景：爬取网页内容、API 数据等
            ─────────────────────────────────────────────────────
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <!-- Jsoup - HTML 解析 -->
            <dependency>
                <groupId>org.jsoup</groupId>
                <artifactId>jsoup</artifactId>
                <version>1.16.1</version>
            </dependency>
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            // ═══════════════════════════════════════════════════════════
            // 方式 1：使用 Java 原生 HTTP（简单场景）
            // ═══════════════════════════════════════════════════════════

            import java.net.http.*;

            public Document loadFromUrl(String url) throws Exception {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

                String content = response.body();
                Metadata metadata = Metadata.from(Map.of(
                    "source", url,
                    "fetchTime", LocalDateTime.now().toString()
                ));

                return Document.from(content, metadata);
            }


            // ═══════════════════════════════════════════════════════════
            // 方式 2：使用 Jsoup 解析 HTML（推荐）
            // ═══════════════════════════════════════════════════════════

            import org.jsoup.Jsoup;
            import org.jsoup.nodes.Document as JsoupDoc;

            public Document loadFromHtml(String url) throws IOException {
                // 获取并解析 HTML
                JsoupDoc html = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")  // 设置 User-Agent
                    .timeout(10000)            // 超时 10 秒
                    .get();

                // 提取正文内容（去除 HTML 标签）
                String title = html.title();
                String content = html.body().text();  // 只获取文本内容

                // 或者提取特定元素
                // String content = html.select("article").text();
                // String content = html.select(".content").text();

                Metadata metadata = Metadata.from(Map.of(
                    "source", url,
                    "title", title
                ));

                return Document.from(content, metadata);
            }


            // ═══════════════════════════════════════════════════════════
            // 方式 3：批量爬取网站
            // ═══════════════════════════════════════════════════════════

            public List<Document> crawlWebsite(String baseUrl, int maxPages) {
                List<Document> documents = new ArrayList<>();
                Set<String> visited = new HashSet<>();
                Queue<String> toVisit = new LinkedList<>();

                toVisit.add(baseUrl);

                while (!toVisit.isEmpty() && documents.size() < maxPages) {
                    String url = toVisit.poll();
                    if (visited.contains(url)) continue;
                    visited.add(url);

                    try {
                        JsoupDoc html = Jsoup.connect(url).get();
                        documents.add(Document.from(html.body().text(),
                            Metadata.from(Map.of("source", url))));

                        // 提取页面中的链接
                        html.select("a[href]").forEach(link -> {
                            String href = link.absUrl("href");
                            if (href.startsWith(baseUrl) && !visited.contains(href)) {
                                toVisit.add(href);
                            }
                        });
                    } catch (Exception e) {
                        // 忽略失败的页面
                    }
                }

                return documents;
            }
            """);
    }

    /**
     * 文档加载器对比表
     */
    private static void printDocumentLoaderComparison() {
        System.out.println("""

            ┌─────────────────┬───────────────────┬────────────────────────────────────────┐
            │ 数据来源        │ 推荐工具/库       │ Maven 依赖                             │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ TXT/MD 文件     │ Java Files API    │ 无需额外依赖                           │
            │                 │ 或 FileSystem     │ langchain4j-document-loader-filesystem │
            │                 │ DocumentLoader    │                                        │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ PDF 文件        │ ApachePdfBox      │ langchain4j-document-parser-apache-    │
            │                 │ DocumentParser    │ pdfbox                                 │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ Word/Excel/PPT  │ ApacheTika        │ langchain4j-document-parser-apache-    │
            │                 │ DocumentParser    │ tika                                   │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ 数据库          │ JDBC / JPA /      │ mysql-connector-java 或                │
            │                 │ MyBatis           │ postgresql                             │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ 网页 HTML       │ Jsoup             │ org.jsoup:jsoup                        │
            ├─────────────────┼───────────────────┼────────────────────────────────────────┤
            │ REST API        │ Java HttpClient   │ 无需额外依赖 (Java 11+)                │
            │                 │ 或 OkHttp/RestTpl │                                        │
            └─────────────────┴───────────────────┴────────────────────────────────────────┘
            """);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //
    //                       第二部分：向量存储方案
    //
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoVectorStorage(EmbeddingModel embeddingModel) {
        System.out.println("\n\n【2.1】InMemory 内存存储（开发测试用）");
        System.out.println("─".repeat(75));
        demoInMemoryStorage(embeddingModel);

        System.out.println("\n\n【2.2】Redis 向量存储");
        System.out.println("─".repeat(75));
        demoRedisStorage();

        System.out.println("\n\n【2.3】PostgreSQL + pgvector");
        System.out.println("─".repeat(75));
        demoPostgresStorage();

        System.out.println("\n\n【2.4】Milvus 专业向量数据库");
        System.out.println("─".repeat(75));
        demoMilvusStorage();

        System.out.println("\n\n【2.5】Elasticsearch 向量存储");
        System.out.println("─".repeat(75));
        demoElasticsearchStorage();

        System.out.println("\n\n【2.6】存储方案选择指南");
        System.out.println("─".repeat(75));
        printStorageGuide();
    }

    /**
     * InMemory 存储演示
     */
    private static void demoInMemoryStorage(EmbeddingModel embeddingModel) {
        System.out.println("""

            InMemoryEmbeddingStore - 内存存储
            ─────────────────────────────────────────────────────

            特点：
            - ✓ 无需任何配置，开箱即用
            - ✓ 速度最快
            - ✗ 重启后数据丢失
            - ✗ 不能跨进程共享

            适用场景：
            - 开发测试
            - Demo 演示
            - 数据量很小（<1万条）且可以每次重建
            """);

        System.out.println("代码示例：\n");
        System.out.println("""
            // 创建内存存储
            EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

            // 添加数据
            Embedding embedding = embeddingModel.embed("文档内容").content();
            TextSegment segment = TextSegment.from("文档内容");
            String id = store.add(embedding, segment);

            // 搜索
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build();
            EmbeddingSearchResult<TextSegment> result = store.search(request);

            // 可选：导出到文件（持久化）
            String json = ((InMemoryEmbeddingStore<TextSegment>) store).serializeToJson();
            Files.writeString(Path.of("embeddings.json"), json);

            // 从文件恢复
            String json = Files.readString(Path.of("embeddings.json"));
            InMemoryEmbeddingStore<TextSegment> store =
                InMemoryEmbeddingStore.fromJson(json);
            """);

        // 实际演示
        System.out.println("\n实际演示：\n");

        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // 添加测试数据
        String[] texts = {"Java 编程入门", "Python 数据分析", "Spring Boot 教程"};
        for (String text : texts) {
            Embedding emb = embeddingModel.embed(text).content();
            store.add(emb, TextSegment.from(text));
            System.out.println("  已添加: " + text);
        }

        // 搜索
        Embedding queryEmb = embeddingModel.embed("Java 开发").content();
        EmbeddingSearchResult<TextSegment> result = store.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(2)
                .build()
        );

        System.out.println("\n搜索 'Java 开发' 的结果：");
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            System.out.printf("  - %s (相似度: %.4f)%n",
                match.embedded().text(), match.score());
        }
    }

    /**
     * Redis 存储演示
     */
    private static void demoRedisStorage() {
        System.out.println("""

            Redis 向量存储
            ─────────────────────────────────────────────────────

            特点：
            - ✓ 速度快，亚毫秒级响应
            - ✓ 支持持久化
            - ✓ 部署简单，很多公司已有 Redis
            - ✓ 支持元数据过滤
            - ✗ 向量搜索是 Redis 7.2+ 的新功能

            适用场景：
            - 中小规模（<100万条）
            - 对响应速度要求高
            - 公司已有 Redis 基础设施
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-redis</artifactId>
                <version>0.27.1</version>
            </dependency>
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;

            /**
             * RedisEmbeddingStore.builder() 参数说明：
             *
             * ┌────────────────┬─────────────────────────────────────────────────┐
             * │ 参数           │ 说明                                            │
             * ├────────────────┼─────────────────────────────────────────────────┤
             * │ host           │ Redis 服务器地址，默认 localhost                │
             * │ port           │ Redis 端口，默认 6379                           │
             * │ user           │ 用户名（Redis 6.0+ ACL）                       │
             * │ password       │ 密码                                            │
             * │ indexName      │ 索引名称，用于区分不同的知识库                  │
             * │ dimension      │ 向量维度，必须与 EmbeddingModel 输出一致        │
             * │ metadataKeys   │ 需要支持过滤的元数据字段                        │
             * └────────────────┴─────────────────────────────────────────────────┘
             */
            EmbeddingStore<TextSegment> store = RedisEmbeddingStore.builder()
                .host("localhost")           // Redis 地址
                .port(6379)                  // Redis 端口
                .password("your_password")   // 密码（如果有）
                .indexName("knowledge_base") // 索引名（区分不同知识库）
                .dimension(1536)             // 向量维度（text-embedding-3-small）
                .metadataKeys(List.of(       // 支持过滤的元数据字段
                    "department",
                    "accessLevel"
                ))
                .build();

            // 使用方式与 InMemory 完全相同
            store.add(embedding, segment);
            store.search(request);


            // ═══════════════════════════════════════════════════════════
            // Docker 快速启动 Redis Stack（支持向量搜索）
            // ═══════════════════════════════════════════════════════════

            docker run -d --name redis-stack \\
                -p 6379:6379 \\
                -p 8001:8001 \\
                redis/redis-stack:latest
            """);
    }

    /**
     * PostgreSQL + pgvector 存储演示
     */
    private static void demoPostgresStorage() {
        System.out.println("""

            PostgreSQL + pgvector 向量存储
            ─────────────────────────────────────────────────────

            特点：
            - ✓ 关系数据库，可靠性高
            - ✓ 支持 SQL 查询，灵活度高
            - ✓ 可以结合业务数据一起管理
            - ✓ 很多公司已有 PostgreSQL
            - ✓ 支持事务

            适用场景：
            - 中等规模（<1000万条）
            - 需要和业务数据关联
            - 需要事务支持
            - 已有 PostgreSQL 基础设施
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-pgvector</artifactId>
                <version>0.27.1</version>
            </dependency>
            """);

        System.out.println("\n数据库准备：\n");
        System.out.println("""
            -- 1. 安装 pgvector 扩展
            CREATE EXTENSION IF NOT EXISTS vector;

            -- 2. 创建表（LangChain4j 会自动创建，也可手动创建）
            CREATE TABLE IF NOT EXISTS embeddings (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                embedding vector(1536),      -- 向量字段，1536 维
                text TEXT,                   -- 原文
                metadata JSONB               -- 元数据（JSON 格式）
            );

            -- 3. 创建向量索引（加速搜索）
            CREATE INDEX ON embeddings
                USING ivfflat (embedding vector_cosine_ops)
                WITH (lists = 100);
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

            /**
             * PgVectorEmbeddingStore.builder() 参数说明：
             *
             * ┌────────────────┬─────────────────────────────────────────────────┐
             * │ 参数           │ 说明                                            │
             * ├────────────────┼─────────────────────────────────────────────────┤
             * │ host           │ PostgreSQL 地址                                 │
             * │ port           │ PostgreSQL 端口，默认 5432                      │
             * │ database       │ 数据库名                                        │
             * │ user           │ 用户名                                          │
             * │ password       │ 密码                                            │
             * │ table          │ 表名，默认 "embeddings"                         │
             * │ dimension      │ 向量维度                                        │
             * │ createTable    │ 是否自动创建表，默认 true                       │
             * │ dropTableFirst │ 是否先删除旧表，默认 false                      │
             * │ useIndex       │ 是否使用索引，默认 true                         │
             * │ indexListSize  │ IVFFlat 索引的 lists 参数                       │
             * └────────────────┴─────────────────────────────────────────────────┘
             */
            EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("knowledge_db")
                .user("postgres")
                .password("your_password")
                .table("embeddings")
                .dimension(1536)
                .createTable(true)       // 自动创建表
                .useIndex(true)          // 使用索引加速
                .indexListSize(100)      // 索引参数
                .build();


            // ═══════════════════════════════════════════════════════════
            // 使用 DataSource（Spring Boot 集成）
            // ═══════════════════════════════════════════════════════════

            @Bean
            public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
                return PgVectorEmbeddingStore.builder()
                    .dataSource(dataSource)    // 使用 Spring 管理的数据源
                    .table("embeddings")
                    .dimension(1536)
                    .build();
            }


            // ═══════════════════════════════════════════════════════════
            // Docker 快速启动 PostgreSQL + pgvector
            // ═══════════════════════════════════════════════════════════

            docker run -d --name pgvector \\
                -p 5432:5432 \\
                -e POSTGRES_PASSWORD=password \\
                pgvector/pgvector:pg16
            """);
    }

    /**
     * Milvus 存储演示
     */
    private static void demoMilvusStorage() {
        System.out.println("""

            Milvus 专业向量数据库
            ─────────────────────────────────────────────────────

            特点：
            - ✓ 专为向量搜索设计，性能最优
            - ✓ 支持亿级数据
            - ✓ 支持多种索引类型
            - ✓ 支持分布式部署
            - ✗ 需要单独部署和运维

            适用场景：
            - 大规模数据（百万到亿级）
            - 对搜索性能要求极高
            - 有专业运维团队
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-milvus</artifactId>
                <version>0.27.1</version>
            </dependency>
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;

            /**
             * MilvusEmbeddingStore.builder() 参数说明：
             *
             * ┌────────────────┬─────────────────────────────────────────────────┐
             * │ 参数           │ 说明                                            │
             * ├────────────────┼─────────────────────────────────────────────────┤
             * │ host           │ Milvus 地址                                     │
             * │ port           │ Milvus 端口，默认 19530                         │
             * │ collectionName │ 集合名（类似数据库表）                          │
             * │ dimension      │ 向量维度                                        │
             * │ indexType      │ 索引类型：IVF_FLAT, IVF_SQ8, HNSW 等           │
             * │ metricType     │ 距离类型：L2, IP, COSINE                        │
             * │ username       │ 用户名（如果启用了认证）                        │
             * │ password       │ 密码                                            │
             * └────────────────┴─────────────────────────────────────────────────┘
             *
             * 索引类型选择：
             * - IVF_FLAT：精度最高，速度较慢，适合小规模
             * - IVF_SQ8：精度略低，速度快，适合中等规模
             * - HNSW：速度和精度平衡好，推荐使用
             */
            EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
                .host("localhost")
                .port(19530)
                .collectionName("knowledge_base")
                .dimension(1536)
                .indexType(IndexType.HNSW)        // 推荐 HNSW 索引
                .metricType(MetricType.COSINE)   // 余弦相似度
                .build();


            // ═══════════════════════════════════════════════════════════
            // Docker 快速启动 Milvus（单机版）
            // ═══════════════════════════════════════════════════════════

            # 下载 docker-compose 文件
            wget https://github.com/milvus-io/milvus/releases/download/v2.3.3/milvus-standalone-docker-compose.yml -O docker-compose.yml

            # 启动
            docker-compose up -d

            # Milvus 管理界面：http://localhost:8000
            """);
    }

    /**
     * Elasticsearch 存储演示
     */
    private static void demoElasticsearchStorage() {
        System.out.println("""

            Elasticsearch 向量存储
            ─────────────────────────────────────────────────────

            特点：
            - ✓ 支持混合搜索（向量 + 关键词）
            - ✓ 强大的全文搜索能力
            - ✓ 生态成熟，监控完善
            - ✓ 很多公司已有 ES 集群
            - ✗ 向量搜索是 ES 8.0+ 的功能

            适用场景：
            - 需要混合搜索（向量 + 关键词）
            - 已有 ES 基础设施
            - 需要强大的查询功能
            """);

        System.out.println("Maven 依赖：\n");
        System.out.println("""
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-elasticsearch</artifactId>
                <version>0.27.1</version>
            </dependency>
            """);

        System.out.println("\n代码示例：\n");
        System.out.println("""
            import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;

            /**
             * ElasticsearchEmbeddingStore.builder() 参数说明：
             *
             * ┌────────────────┬─────────────────────────────────────────────────┐
             * │ 参数           │ 说明                                            │
             * ├────────────────┼─────────────────────────────────────────────────┤
             * │ serverUrl      │ ES 地址，如 http://localhost:9200               │
             * │ apiKey         │ API Key（如果启用了安全）                       │
             * │ userName       │ 用户名                                          │
             * │ password       │ 密码                                            │
             * │ indexName      │ 索引名                                          │
             * │ dimension      │ 向量维度                                        │
             * └────────────────┴─────────────────────────────────────────────────┘
             */
            EmbeddingStore<TextSegment> store = ElasticsearchEmbeddingStore.builder()
                .serverUrl("http://localhost:9200")
                .indexName("knowledge_base")
                .dimension(1536)
                // .userName("elastic")
                // .password("password")
                .build();


            // ═══════════════════════════════════════════════════════════
            // Docker 快速启动 Elasticsearch 8.x
            // ═══════════════════════════════════════════════════════════

            docker run -d --name elasticsearch \\
                -p 9200:9200 \\
                -e "discovery.type=single-node" \\
                -e "xpack.security.enabled=false" \\
                docker.elastic.co/elasticsearch/elasticsearch:8.11.0
            """);
    }

    /**
     * 存储方案选择指南
     */
    private static void printStorageGuide() {
        System.out.println("""

            ┌─────────────────────────────────────────────────────────────────────────┐
            │                       存储方案选择决策树                                 │
            └─────────────────────────────────────────────────────────────────────────┘

                                      开始
                                        │
                                        ▼
                              ┌─────────────────┐
                              │  是开发测试吗？  │
                              └────────┬────────┘
                                       │
                          ┌────────────┴────────────┐
                          │是                       │否
                          ▼                         ▼
                   ┌──────────────┐        ┌─────────────────┐
                   │  InMemory    │        │  数据量多大？    │
                   │  内存存储    │        └────────┬────────┘
                   └──────────────┘                 │
                                       ┌───────────┼───────────┐
                                       │           │           │
                                   <100万      100万-1000万   >1000万
                                       │           │           │
                                       ▼           ▼           ▼
                              ┌──────────────┐ ┌────────────┐ ┌──────────┐
                              │ 公司有什么？  │ │PostgreSQL │ │  Milvus  │
                              └──────┬───────┘ │ +pgvector  │ │   或     │
                                     │         └────────────┘ │ Pinecone │
                          ┌──────────┼──────────┐             └──────────┘
                          │          │          │
                        Redis   PostgreSQL   ES集群
                          │          │          │
                          ▼          ▼          ▼
                    ┌──────────┐┌──────────┐┌────────────────┐
                    │  Redis   ││PostgreSQL││ Elasticsearch  │
                    │ 向量存储 ││ +pgvector ││   向量存储     │
                    └──────────┘└──────────┘└────────────────┘


            ┌─────────────────────────────────────────────────────────────────────────┐
            │                       各方案适用场景总结                                 │
            ├─────────────────┬───────────────────────────────────────────────────────┤
            │ InMemory        │ 开发测试、Demo、临时脚本                              │
            ├─────────────────┼───────────────────────────────────────────────────────┤
            │ Redis           │ 小公司、对速度要求高、已有 Redis                      │
            ├─────────────────┼───────────────────────────────────────────────────────┤
            │ PostgreSQL      │ 中小公司、需要和业务数据关联、已有 PG                 │
            │ + pgvector      │                                                       │
            ├─────────────────┼───────────────────────────────────────────────────────┤
            │ Elasticsearch   │ 需要混合搜索、已有 ES 集群、需要强大查询              │
            ├─────────────────┼───────────────────────────────────────────────────────┤
            │ Milvus          │ 大公司、大数据量、追求极致性能、有运维团队            │
            ├─────────────────┼───────────────────────────────────────────────────────┤
            │ Pinecone        │ 不想运维、云原生、按量付费                            │
            └─────────────────┴───────────────────────────────────────────────────────┘
            """);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //
    //                       第三部分：生产环境最佳实践
    //
    // ═══════════════════════════════════════════════════════════════════════════

    private static void demoBestPractices() {
        System.out.println("""

            【3.1】生产环境配置建议
            ─────────────────────────────────────────────────────
            """);

        System.out.println("""
            1. 使用环境变量管理敏感信息
            ─────────────────────────────────────────────────────

            // application.yml
            embedding:
              store:
                type: postgres
                host: ${DB_HOST:localhost}
                port: ${DB_PORT:5432}
                database: ${DB_NAME:knowledge}
                username: ${DB_USER:postgres}
                password: ${DB_PASSWORD}

            // 代码中读取
            @Value("${embedding.store.host}")
            private String dbHost;


            2. 使用连接池
            ─────────────────────────────────────────────────────

            // HikariCP 连接池配置
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/knowledge");
            config.setUsername("postgres");
            config.setPassword("password");
            config.setMaximumPoolSize(10);       // 最大连接数
            config.setMinimumIdle(5);            // 最小空闲连接
            config.setConnectionTimeout(30000);  // 连接超时 30秒

            DataSource dataSource = new HikariDataSource(config);

            // 使用 DataSource 创建 EmbeddingStore
            EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                .dataSource(dataSource)
                .build();


            3. 批量操作提升性能
            ─────────────────────────────────────────────────────

            // 不好的做法：逐条添加
            for (TextSegment segment : segments) {
                Embedding emb = embeddingModel.embed(segment.text()).content();
                store.add(emb, segment);  // 每次都是一次网络请求
            }

            // 好的做法：批量添加
            List<Embedding> embeddings = embeddingModel.embedAll(
                segments.stream().map(TextSegment::text).collect(Collectors.toList())
            ).content();
            store.addAll(embeddings, segments);  // 一次请求


            4. 添加重试机制
            ─────────────────────────────────────────────────────

            // 使用 Resilience4j 添加重试
            RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, TimeoutException.class)
                .build();

            Retry retry = Retry.of("embeddingService", config);

            Embedding result = Retry.decorateSupplier(retry, () ->
                embeddingModel.embed(text).content()
            ).get();


            5. 监控和日志
            ─────────────────────────────────────────────────────

            // 记录关键指标
            - 向量化平均耗时
            - 搜索平均耗时
            - 搜索结果平均相似度
            - 每日索引文档数
            - 存储空间使用率


            6. 定期维护
            ─────────────────────────────────────────────────────

            // 定期重建索引（提升搜索性能）
            @Scheduled(cron = "0 0 3 * * SUN")  // 每周日凌晨3点
            public void rebuildIndex() {
                // Milvus
                milvusClient.releaseCollection(collectionName);
                milvusClient.loadCollection(collectionName);

                // PostgreSQL
                jdbcTemplate.execute("REINDEX INDEX embeddings_idx");
            }
            """);

        System.out.println("""

            【3.2】Spring Boot 集成示例
            ─────────────────────────────────────────────────────
            """);

        System.out.println("""
            // application.yml
            langchain4j:
              embedding-model:
                provider: openai
                api-key: ${OPENAI_API_KEY}
                model-name: text-embedding-3-small

              embedding-store:
                provider: pgvector
                host: ${DB_HOST:localhost}
                port: 5432
                database: knowledge_db
                username: ${DB_USER}
                password: ${DB_PASSWORD}
                table: embeddings
                dimension: 1536


            // 配置类
            @Configuration
            public class LangChain4jConfig {

                @Bean
                public EmbeddingModel embeddingModel(
                        @Value("${langchain4j.embedding-model.api-key}") String apiKey) {
                    return OpenAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .modelName("text-embedding-3-small")
                        .build();
                }

                @Bean
                public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
                    return PgVectorEmbeddingStore.builder()
                        .dataSource(dataSource)
                        .table("embeddings")
                        .dimension(1536)
                        .build();
                }

                @Bean
                public ContentRetriever contentRetriever(
                        EmbeddingStore<TextSegment> store,
                        EmbeddingModel model) {
                    return EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(store)
                        .embeddingModel(model)
                        .maxResults(5)
                        .minScore(0.6)
                        .build();
                }
            }


            // 服务类
            @Service
            public class KnowledgeBaseService {

                @Autowired
                private EmbeddingStore<TextSegment> embeddingStore;

                @Autowired
                private EmbeddingModel embeddingModel;

                /**
                 * 添加文档到知识库
                 */
                public void addDocument(String content, Map<String, String> metadata) {
                    Embedding embedding = embeddingModel.embed(content).content();
                    TextSegment segment = TextSegment.from(content, Metadata.from(metadata));
                    embeddingStore.add(embedding, segment);
                }

                /**
                 * 搜索相关文档
                 */
                public List<String> search(String query, int maxResults) {
                    Embedding queryEmbedding = embeddingModel.embed(query).content();

                    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(0.5)
                        .build();

                    return embeddingStore.search(request).matches().stream()
                        .map(match -> match.embedded().text())
                        .collect(Collectors.toList());
                }
            }
            """);

        System.out.println("""

            【3.3】完整项目结构参考
            ─────────────────────────────────────────────────────

            knowledge-base-service/
            ├── src/main/java/
            │   └── com/example/kb/
            │       ├── KnowledgeBaseApplication.java    # 启动类
            │       ├── config/
            │       │   └── LangChain4jConfig.java       # LangChain4j 配置
            │       ├── controller/
            │       │   ├── DocumentController.java      # 文档管理 API
            │       │   └── SearchController.java        # 搜索 API
            │       ├── service/
            │       │   ├── DocumentService.java         # 文档处理服务
            │       │   ├── EmbeddingService.java        # 向量化服务
            │       │   └── SearchService.java           # 搜索服务
            │       ├── loader/
            │       │   ├── FileDocumentLoader.java      # 文件加载器
            │       │   ├── DatabaseDocumentLoader.java  # 数据库加载器
            │       │   └── WebDocumentLoader.java       # 网页加载器
            │       └── model/
            │           └── DocumentDTO.java             # 数据传输对象
            ├── src/main/resources/
            │   ├── application.yml                      # 配置文件
            │   └── application-prod.yml                 # 生产环境配置
            └── pom.xml
            """);
    }
}
