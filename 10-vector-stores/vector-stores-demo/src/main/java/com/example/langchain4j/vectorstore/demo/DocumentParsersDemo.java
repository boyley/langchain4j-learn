package com.example.langchain4j.vectorstore.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 文档解析器 Demo - 市场主流格式全覆盖
 *
 * 企业知识库通常需要处理各种格式的文档:
 * - 纯文本: TXT, Markdown, JSON, XML
 * - Office 文档: Word (.docx), Excel (.xlsx), PowerPoint (.pptx)
 * - PDF 文档: 扫描件, 可编辑 PDF
 * - 网页: HTML
 * - 代码: 各种编程语言源文件
 *
 * 本 Demo 展示如何使用 LangChain4j 解析这些格式。
 *
 * @author LangChain4j 学习项目
 */
public class DocumentParsersDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           文档解析器 Demo - 市场主流格式全覆盖                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 第一部分: 解析器概览 ====================
        printOverview();

        // ==================== 第二部分: 文本文件解析 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第二部分】文本文件解析 - TextDocumentParser");
        System.out.println("═".repeat(65));
        demoTextParser();

        // ==================== 第三部分: PDF 解析 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第三部分】PDF 文件解析 - ApachePdfBoxDocumentParser");
        System.out.println("═".repeat(65));
        demoPdfParser();

        // ==================== 第四部分: Office 文档解析 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第四部分】Office 文档解析 - ApachePoiDocumentParser");
        System.out.println("═".repeat(65));
        demoOfficeParser();

        // ==================== 第五部分: 自定义解析器 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第五部分】自定义解析器实现");
        System.out.println("═".repeat(65));
        printCustomParser();

        // ==================== 第六部分: 企业级文档加载方案 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第六部分】企业级文档加载方案");
        System.out.println("═".repeat(65));
        printEnterpriseLoader();

        // ==================== 第七部分: Maven 依赖清单 ====================
        System.out.println("\n");
        System.out.println("═".repeat(65));
        System.out.println("【第七部分】Maven 依赖清单");
        System.out.println("═".repeat(65));
        printMavenDependencies();
    }

    private static void printOverview() {
        System.out.println("""
                ═══════════════════════════════════════════════════════════════════
                【第一部分】文档解析器概览
                ═══════════════════════════════════════════════════════════════════

                ┌─────────────────────────────────────────────────────────────────┐
                │                    LangChain4j 文档解析器                        │
                ├────────────────────┬────────────────────────────────────────────┤
                │      解析器         │              支持格式                       │
                ├────────────────────┼────────────────────────────────────────────┤
                │ TextDocumentParser │ .txt, .md, .json, .xml, .csv, .log        │
                │                    │ 以及所有纯文本格式                          │
                ├────────────────────┼────────────────────────────────────────────┤
                │ ApachePdfBoxParser │ .pdf (可编辑PDF, 扫描件需OCR)              │
                ├────────────────────┼────────────────────────────────────────────┤
                │ ApachePoiParser    │ .docx, .doc, .xlsx, .xls, .pptx, .ppt     │
                │                    │ (Microsoft Office 全系列)                  │
                ├────────────────────┼────────────────────────────────────────────┤
                │ ApacheTikaParser   │ 自动检测格式，支持 1000+ 种文件类型         │
                │  (可选)            │ 包括音频、视频元数据等                      │
                └────────────────────┴────────────────────────────────────────────┘

                【解析流程】

                ┌─────────┐     ┌─────────────┐     ┌─────────┐     ┌─────────┐
                │ 文件/流  │ ──→ │ DocumentParser │ ──→ │ Document │ ──→ │ 分割/向量化│
                │ .pdf等  │     │  解析器       │     │ 文本+元数据│    │          │
                └─────────┘     └─────────────┘     └─────────┘     └─────────┘

                """);
    }

    private static void demoTextParser() {
        System.out.println("""

                【TextDocumentParser 使用方法】
                ─────────────────────────────────────────────────────────────
                """);

        // 模拟文本内容
        String textContent = """
                # 员工手册

                ## 第一章 公司简介

                本公司成立于2020年，是一家专注于人工智能的科技公司。

                ## 第二章 员工福利

                1. 五险一金
                2. 带薪年假
                3. 节日福利
                """;

        System.out.println("📄 示例: 解析 Markdown 文本\n");

        /**
         * TextDocumentParser 说明:
         *
         * 最简单的解析器，适用于所有纯文本格式。
         * 直接读取文件内容，不做任何转换。
         *
         * 支持格式: .txt, .md, .json, .xml, .csv, .log, .yaml 等
         */
        DocumentParser textParser = new TextDocumentParser();

        // 从字符串创建输入流 (演示用)
        InputStream inputStream = new ByteArrayInputStream(
                textContent.getBytes(StandardCharsets.UTF_8));

        // 解析
        Document document = textParser.parse(inputStream);

        System.out.println("解析结果:");
        System.out.println("-".repeat(50));
        System.out.println(document.text());
        System.out.println("-".repeat(50));

        System.out.println("""

                【代码示例】从文件加载
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // 方式1: 直接加载单个文件
                Document doc = FileSystemDocumentLoader.loadDocument(
                    Path.of("/path/to/document.md"),
                    new TextDocumentParser()
                );

                // 方式2: 加载整个目录
                List<Document> docs = FileSystemDocumentLoader.loadDocuments(
                    Path.of("/path/to/docs/"),
                    new TextDocumentParser()
                );

                // 方式3: 使用 glob 模式匹配
                List<Document> docs = FileSystemDocumentLoader.loadDocuments(
                    PathMatcher.glob("**/*.md"),  // 匹配所有 .md 文件
                    Path.of("/path/to/docs/"),
                    new TextDocumentParser()
                );
                """);
    }

    private static void demoPdfParser() {
        System.out.println("""

                【ApachePdfBoxDocumentParser 使用方法】
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * ApachePdfBoxDocumentParser 说明:
                 *
                 * 使用 Apache PDFBox 解析 PDF 文件。
                 * - 支持可编辑 PDF (文本直接提取)
                 * - 支持加密 PDF (需要提供密码)
                 * - 不支持扫描件 (需要 OCR)
                 *
                 * Maven 依赖:
                 * <dependency>
                 *     <groupId>dev.langchain4j</groupId>
                 *     <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
                 *     <version>0.36.2</version>
                 * </dependency>
                 */

                // 创建 PDF 解析器
                DocumentParser pdfParser = new ApachePdfBoxDocumentParser();

                // 从文件加载
                Document doc = FileSystemDocumentLoader.loadDocument(
                    Path.of("/path/to/document.pdf"),
                    pdfParser
                );

                // 获取解析后的文本
                String text = doc.text();

                // 获取元数据 (PDF 属性)
                Metadata metadata = doc.metadata();
                // metadata 可能包含: title, author, subject, keywords, creator 等
                """);

        System.out.println("""

                【处理扫描件 PDF - OCR 方案】
                ─────────────────────────────────────────────────────────────

                扫描件 PDF 需要先进行 OCR (光学字符识别)，方案如下:

                方案1: 使用 Tesseract OCR
                ─────────────────────────────────────────────────────────────
                // 需要安装 Tesseract: brew install tesseract
                // 需要下载中文语言包: tessdata/chi_sim.traineddata

                // 先将 PDF 转为图片
                PDDocument pdf = PDDocument.load(new File("scan.pdf"));
                PDFRenderer renderer = new PDFRenderer(pdf);

                StringBuilder text = new StringBuilder();
                Tesseract tesseract = new Tesseract();
                tesseract.setLanguage("chi_sim");  // 简体中文

                for (int page = 0; page < pdf.getNumberOfPages(); page++) {
                    BufferedImage image = renderer.renderImageWithDPI(page, 300);
                    String pageText = tesseract.doOCR(image);
                    text.append(pageText);
                }

                方案2: 使用云服务 OCR API
                ─────────────────────────────────────────────────────────────
                // 阿里云 OCR
                // 腾讯云 OCR
                // 百度 OCR
                // Azure Document Intelligence
                // Google Cloud Vision

                // 示例: 调用阿里云 OCR
                String text = aliOcrClient.recognizeGeneral(pdfBytes);
                Document doc = Document.from(text);
                """);
    }

    private static void demoOfficeParser() {
        System.out.println("""

                【ApachePoiDocumentParser 使用方法】
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * ApachePoiDocumentParser 说明:
                 *
                 * 使用 Apache POI 解析 Microsoft Office 文档。
                 * - Word: .docx, .doc
                 * - Excel: .xlsx, .xls
                 * - PowerPoint: .pptx, .ppt
                 *
                 * Maven 依赖:
                 * <dependency>
                 *     <groupId>dev.langchain4j</groupId>
                 *     <artifactId>langchain4j-document-parser-apache-poi</artifactId>
                 *     <version>0.36.2</version>
                 * </dependency>
                 */

                // 创建 Office 解析器
                DocumentParser officeParser = new ApachePoiDocumentParser();

                // 解析 Word 文档
                Document wordDoc = FileSystemDocumentLoader.loadDocument(
                    Path.of("/path/to/document.docx"),
                    officeParser
                );

                // 解析 Excel 文件 (提取所有单元格文本)
                Document excelDoc = FileSystemDocumentLoader.loadDocument(
                    Path.of("/path/to/data.xlsx"),
                    officeParser
                );

                // 解析 PowerPoint (提取所有幻灯片文本)
                Document pptDoc = FileSystemDocumentLoader.loadDocument(
                    Path.of("/path/to/presentation.pptx"),
                    officeParser
                );
                """);

        System.out.println("""

                【Excel 特殊处理】
                ─────────────────────────────────────────────────────────────

                Excel 文件通常是结构化数据，需要特殊处理:

                // 如果需要保留表格结构，可以自定义解析
                public class ExcelTableParser implements DocumentParser {

                    @Override
                    public Document parse(InputStream inputStream) {
                        Workbook workbook = WorkbookFactory.create(inputStream);
                        StringBuilder text = new StringBuilder();

                        for (Sheet sheet : workbook) {
                            text.append("## Sheet: ").append(sheet.getSheetName()).append("\\n\\n");

                            for (Row row : sheet) {
                                List<String> cells = new ArrayList<>();
                                for (Cell cell : row) {
                                    cells.add(getCellValue(cell));
                                }
                                text.append(String.join(" | ", cells)).append("\\n");
                            }
                            text.append("\\n");
                        }

                        return Document.from(text.toString());
                    }

                    private String getCellValue(Cell cell) {
                        switch (cell.getCellType()) {
                            case STRING: return cell.getStringCellValue();
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    return cell.getLocalDateTimeCellValue().toString();
                                }
                                return String.valueOf(cell.getNumericCellValue());
                            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                            default: return "";
                        }
                    }
                }
                """);
    }

    private static void printCustomParser() {
        System.out.println("""

                【自定义解析器接口】
                ─────────────────────────────────────────────────────────────

                LangChain4j 的 DocumentParser 接口非常简单:

                public interface DocumentParser {
                    Document parse(InputStream inputStream);
                }

                只需实现这一个方法，就可以支持任何格式!

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                // ============================================================
                // 示例1: HTML 解析器 (使用 Jsoup)
                // ============================================================

                /**
                 * HTML 文档解析器
                 * 提取 HTML 中的纯文本内容
                 */
                public class HtmlDocumentParser implements DocumentParser {

                    @Override
                    public Document parse(InputStream inputStream) {
                        try {
                            // 使用 Jsoup 解析 HTML
                            org.jsoup.nodes.Document html = Jsoup.parse(inputStream, "UTF-8", "");

                            // 提取标题
                            String title = html.title();

                            // 提取正文 (去除脚本、样式等)
                            html.select("script, style, nav, footer, header").remove();
                            String text = html.body().text();

                            // 构建文档
                            Metadata metadata = new Metadata();
                            metadata.put("title", title);

                            return Document.from(text, metadata);
                        } catch (IOException e) {
                            throw new RuntimeException("解析 HTML 失败", e);
                        }
                    }
                }

                // 使用
                DocumentParser htmlParser = new HtmlDocumentParser();
                Document doc = htmlParser.parse(new FileInputStream("page.html"));

                // ============================================================
                // 示例2: JSON 解析器 (结构化提取)
                // ============================================================

                /**
                 * JSON 文档解析器
                 * 将 JSON 转换为可读文本
                 */
                public class JsonDocumentParser implements DocumentParser {

                    private final ObjectMapper mapper = new ObjectMapper();

                    @Override
                    public Document parse(InputStream inputStream) {
                        try {
                            JsonNode root = mapper.readTree(inputStream);
                            StringBuilder text = new StringBuilder();

                            // 递归提取所有文本值
                            extractText(root, text, "");

                            return Document.from(text.toString());
                        } catch (IOException e) {
                            throw new RuntimeException("解析 JSON 失败", e);
                        }
                    }

                    private void extractText(JsonNode node, StringBuilder sb, String prefix) {
                        if (node.isTextual()) {
                            sb.append(prefix).append(": ").append(node.asText()).append("\\n");
                        } else if (node.isObject()) {
                            node.fields().forEachRemaining(entry -> {
                                extractText(entry.getValue(), sb, entry.getKey());
                            });
                        } else if (node.isArray()) {
                            int i = 0;
                            for (JsonNode item : node) {
                                extractText(item, sb, prefix + "[" + i++ + "]");
                            }
                        }
                    }
                }

                // ============================================================
                // 示例3: 代码文件解析器 (保留注释)
                // ============================================================

                /**
                 * 源代码解析器
                 * 提取代码中的注释和文档字符串
                 */
                public class CodeDocumentParser implements DocumentParser {

                    @Override
                    public Document parse(InputStream inputStream) {
                        try {
                            String code = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                            StringBuilder text = new StringBuilder();

                            // 提取 JavaDoc 注释
                            Pattern javadocPattern = Pattern.compile("/\\\\*\\\\*([\\\\s\\\\S]*?)\\\\*/");
                            Matcher matcher = javadocPattern.matcher(code);
                            while (matcher.find()) {
                                text.append(cleanComment(matcher.group(1))).append("\\n\\n");
                            }

                            // 提取单行注释
                            Pattern lineCommentPattern = Pattern.compile("//\\\\s*(.*)");
                            matcher = lineCommentPattern.matcher(code);
                            while (matcher.find()) {
                                text.append(matcher.group(1)).append("\\n");
                            }

                            return Document.from(text.toString());
                        } catch (IOException e) {
                            throw new RuntimeException("解析代码失败", e);
                        }
                    }

                    private String cleanComment(String comment) {
                        return comment.replaceAll("\\\\*", "").trim();
                    }
                }
                """);
    }

    private static void printEnterpriseLoader() {
        System.out.println("""

                【企业级文档加载器设计】
                ─────────────────────────────────────────────────────────────

                企业环境中，需要处理多种格式的文档。
                设计一个统一的加载器，根据文件扩展名自动选择解析器。

                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                /**
                 * 企业级文档加载器 - 自动识别格式
                 */
                @Service
                public class EnterpriseDocumentLoader {

                    // 解析器映射表
                    private final Map<String, DocumentParser> parsers = new HashMap<>();

                    @PostConstruct
                    public void init() {
                        // 文本类
                        DocumentParser textParser = new TextDocumentParser();
                        parsers.put("txt", textParser);
                        parsers.put("md", textParser);
                        parsers.put("json", textParser);
                        parsers.put("xml", textParser);
                        parsers.put("csv", textParser);
                        parsers.put("yaml", textParser);
                        parsers.put("yml", textParser);
                        parsers.put("log", textParser);

                        // PDF
                        parsers.put("pdf", new ApachePdfBoxDocumentParser());

                        // Office 文档
                        DocumentParser officeParser = new ApachePoiDocumentParser();
                        parsers.put("docx", officeParser);
                        parsers.put("doc", officeParser);
                        parsers.put("xlsx", officeParser);
                        parsers.put("xls", officeParser);
                        parsers.put("pptx", officeParser);
                        parsers.put("ppt", officeParser);

                        // HTML (自定义)
                        parsers.put("html", new HtmlDocumentParser());
                        parsers.put("htm", new HtmlDocumentParser());

                        // 代码文件 (自定义)
                        DocumentParser codeParser = new CodeDocumentParser();
                        parsers.put("java", codeParser);
                        parsers.put("py", codeParser);
                        parsers.put("js", codeParser);
                        parsers.put("ts", codeParser);
                    }

                    /**
                     * 加载单个文档
                     */
                    public Document load(Path filePath) {
                        String extension = getExtension(filePath);
                        DocumentParser parser = parsers.get(extension.toLowerCase());

                        if (parser == null) {
                            log.warn("不支持的文件格式: {}, 使用文本解析器", extension);
                            parser = new TextDocumentParser();
                        }

                        Document doc = FileSystemDocumentLoader.loadDocument(filePath, parser);

                        // 添加文件元数据
                        doc.metadata().put("fileName", filePath.getFileName().toString());
                        doc.metadata().put("filePath", filePath.toString());
                        doc.metadata().put("fileType", extension);
                        doc.metadata().put("loadTime", LocalDateTime.now().toString());

                        return doc;
                    }

                    /**
                     * 递归加载目录
                     */
                    public List<Document> loadDirectory(Path directory) {
                        List<Document> documents = new ArrayList<>();

                        try {
                            Files.walk(directory)
                                .filter(Files::isRegularFile)
                                .filter(this::isSupportedFile)
                                .forEach(path -> {
                                    try {
                                        Document doc = load(path);
                                        documents.add(doc);
                                        log.info("加载成功: {}", path.getFileName());
                                    } catch (Exception e) {
                                        log.error("加载失败: {}", path, e);
                                    }
                                });
                        } catch (IOException e) {
                            throw new RuntimeException("扫描目录失败", e);
                        }

                        return documents;
                    }

                    /**
                     * 检查是否支持的文件格式
                     */
                    private boolean isSupportedFile(Path path) {
                        String ext = getExtension(path).toLowerCase();
                        return parsers.containsKey(ext);
                    }

                    private String getExtension(Path path) {
                        String fileName = path.getFileName().toString();
                        int dotIndex = fileName.lastIndexOf('.');
                        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
                    }
                }

                // ============================================================
                // 使用示例
                // ============================================================

                @Autowired
                private EnterpriseDocumentLoader documentLoader;

                // 加载单个文件 (自动识别格式)
                Document doc = documentLoader.load(Path.of("/data/docs/员工手册.pdf"));

                // 加载整个目录 (递归，自动识别格式)
                List<Document> docs = documentLoader.loadDirectory(Path.of("/data/docs/"));

                // 打印统计
                Map<String, Long> stats = docs.stream()
                    .collect(Collectors.groupingBy(
                        d -> d.metadata().getString("fileType"),
                        Collectors.counting()
                    ));
                System.out.println("加载统计: " + stats);
                // 输出: {pdf=10, docx=5, txt=20, md=15}
                """);
    }

    private static void printMavenDependencies() {
        System.out.println("""

                【完整 Maven 依赖】
                ─────────────────────────────────────────────────────────────
                """);

        System.out.println("""
                <dependencies>
                    <!-- LangChain4j 核心 -->
                    <dependency>
                        <groupId>dev.langchain4j</groupId>
                        <artifactId>langchain4j</artifactId>
                        <version>0.36.2</version>
                    </dependency>

                    <!-- ==================== 文档解析器 ==================== -->

                    <!-- PDF 解析 (Apache PDFBox) -->
                    <dependency>
                        <groupId>dev.langchain4j</groupId>
                        <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
                        <version>0.36.2</version>
                    </dependency>

                    <!-- Office 文档解析 (Apache POI) -->
                    <!-- Word (.docx, .doc), Excel (.xlsx, .xls), PPT (.pptx, .ppt) -->
                    <dependency>
                        <groupId>dev.langchain4j</groupId>
                        <artifactId>langchain4j-document-parser-apache-poi</artifactId>
                        <version>0.36.2</version>
                    </dependency>

                    <!-- Tika 解析器 (可选，支持 1000+ 种格式) -->
                    <dependency>
                        <groupId>dev.langchain4j</groupId>
                        <artifactId>langchain4j-document-parser-apache-tika</artifactId>
                        <version>0.36.2</version>
                    </dependency>

                    <!-- ==================== 自定义解析器依赖 ==================== -->

                    <!-- HTML 解析 (Jsoup) -->
                    <dependency>
                        <groupId>org.jsoup</groupId>
                        <artifactId>jsoup</artifactId>
                        <version>1.17.2</version>
                    </dependency>

                    <!-- JSON 解析 (Jackson) -->
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.17.0</version>
                    </dependency>

                    <!-- OCR 支持 (Tesseract) -->
                    <dependency>
                        <groupId>net.sourceforge.tess4j</groupId>
                        <artifactId>tess4j</artifactId>
                        <version>5.10.0</version>
                    </dependency>

                </dependencies>
                """);

        System.out.println("""

                ═══════════════════════════════════════════════════════════════════
                【总结】文档解析器选择指南
                ═══════════════════════════════════════════════════════════════════

                ┌──────────────────┬────────────────────────────────────────────┐
                │    文件格式       │              推荐解析器                     │
                ├──────────────────┼────────────────────────────────────────────┤
                │ .txt, .md, .json │ TextDocumentParser (内置)                   │
                │ .pdf             │ ApachePdfBoxDocumentParser                  │
                │ .docx, .xlsx     │ ApachePoiDocumentParser                     │
                │ .html            │ 自定义 HtmlDocumentParser (Jsoup)           │
                │ 扫描件 PDF       │ Tess4j + ApachePdfBoxDocumentParser        │
                │ 任意格式         │ ApacheTikaDocumentParser (自动识别)         │
                └──────────────────┴────────────────────────────────────────────┘

                【最佳实践】

                1. 使用 EnterpriseDocumentLoader 统一加载接口
                2. 根据文件扩展名自动选择解析器
                3. 添加文件元数据 (路径、类型、加载时间)
                4. 处理解析失败 (记录日志，继续处理其他文件)
                5. 大文件分批加载，避免内存溢出
                """);
    }
}
