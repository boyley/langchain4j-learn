package com.example.knowledge.source.impl;

import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.source.KnowledgeSource;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统知识源实现
 *
 * 从指定目录递归读取文档文件 (PDF/Word/TXT/Markdown等)。
 *
 * 适用场景:
 * - 知识存储在文件服务器
 * - 需要批量导入文档
 * - 文档由人工维护
 *
 * 配置:
 * knowledge.source.file.enabled=true
 * knowledge.source.file.root-path=/data/knowledge/docs
 *
 * 支持的格式:
 * - 文本: .txt, .md, .json, .xml, .csv
 * - PDF: .pdf
 * - Office: .docx, .doc, .xlsx, .xls, .pptx, .ppt
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.source.file.enabled", havingValue = "true")
public class FileSystemKnowledgeSource implements KnowledgeSource {

    @Value("${knowledge.source.file.root-path:}")
    private String rootPath;

    // 解析器映射表
    private final Map<String, DocumentParser> parsers = new HashMap<>();

    // 文件缓存 (用于增量同步判断)
    private final Map<String, Long> fileModTimeCache = new HashMap<>();

    @PostConstruct
    public void init() {
        // 注册各种格式的解析器
        DocumentParser textParser = new TextDocumentParser();
        parsers.put("txt", textParser);
        parsers.put("md", textParser);
        parsers.put("json", textParser);
        parsers.put("xml", textParser);
        parsers.put("csv", textParser);
        parsers.put("yaml", textParser);
        parsers.put("yml", textParser);
        parsers.put("log", textParser);

        // PDF 解析器
        parsers.put("pdf", new ApachePdfBoxDocumentParser());

        // Office 文档解析器
        DocumentParser officeParser = new ApachePoiDocumentParser();
        parsers.put("docx", officeParser);
        parsers.put("doc", officeParser);
        parsers.put("xlsx", officeParser);
        parsers.put("xls", officeParser);
        parsers.put("pptx", officeParser);
        parsers.put("ppt", officeParser);

        log.info("文件系统知识源初始化完成, rootPath={}, 支持格式={}", rootPath, parsers.keySet());
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getDisplayName() {
        return "文件系统";
    }

    @Override
    public boolean isAvailable() {
        if (rootPath == null || rootPath.isEmpty()) {
            return false;
        }
        Path path = Paths.get(rootPath);
        return Files.exists(path) && Files.isDirectory(path);
    }

    @Override
    public List<KnowledgeDocument> fetchAll() {
        log.info("开始全量扫描文件目录: {}", rootPath);
        return fetchIncremental(null);
    }

    @Override
    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
        if (!isAvailable()) {
            log.warn("文件目录不可用: {}", rootPath);
            return Collections.emptyList();
        }

        List<KnowledgeDocument> documents = new ArrayList<>();
        Path root = Paths.get(rootPath);

        try (Stream<Path> pathStream = Files.walk(root)) {
            List<Path> files = pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(path -> shouldProcess(path, lastSyncTime))
                    .collect(Collectors.toList());

            log.info("找到 {} 个需要处理的文件", files.size());

            for (Path filePath : files) {
                try {
                    KnowledgeDocument doc = parseFile(filePath);
                    if (doc != null) {
                        documents.add(doc);
                        // 更新缓存
                        fileModTimeCache.put(filePath.toString(), getFileModTime(filePath));
                    }
                } catch (Exception e) {
                    log.error("解析文件失败: {}", filePath, e);
                }
            }
        } catch (IOException e) {
            log.error("扫描目录失败: {}", rootPath, e);
        }

        log.info("文件系统知识源获取到 {} 篇文档", documents.size());
        return documents;
    }

    @Override
    public List<KnowledgeDocument> fetchBatch(int offset, int limit) {
        // 文件系统不支持分批，返回全部后由上层处理
        List<KnowledgeDocument> all = fetchAll();
        int end = Math.min(offset + limit, all.size());
        if (offset >= all.size()) {
            return Collections.emptyList();
        }
        return all.subList(offset, end);
    }

    @Override
    public long count() {
        if (!isAvailable()) {
            return 0;
        }

        try (Stream<Path> pathStream = Files.walk(Paths.get(rootPath))) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .count();
        } catch (IOException e) {
            log.error("统计文件数量失败", e);
            return 0;
        }
    }

    @Override
    public KnowledgeDocument fetchById(String docId) {
        // docId 格式: file:/path/to/file.pdf
        if (!docId.startsWith("file:")) {
            return null;
        }
        String filePath = docId.substring(5);
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return parseFile(path);
        }
        return null;
    }

    /**
     * 判断是否需要处理此文件
     */
    private boolean shouldProcess(Path path, LocalDateTime lastSyncTime) {
        if (lastSyncTime == null) {
            return true; // 全量同步
        }

        try {
            long fileModTime = getFileModTime(path);
            long syncTime = lastSyncTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // 文件修改时间 > 上次同步时间
            return fileModTime > syncTime;
        } catch (Exception e) {
            log.warn("获取文件修改时间失败: {}", path, e);
            return true; // 无法判断则处理
        }
    }

    /**
     * 获取文件修改时间
     */
    private long getFileModTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 判断是否支持的文件格式
     */
    private boolean isSupportedFile(Path path) {
        String ext = getExtension(path).toLowerCase();
        return parsers.containsKey(ext);
    }

    /**
     * 解析单个文件
     */
    private KnowledgeDocument parseFile(Path filePath) {
        String ext = getExtension(filePath).toLowerCase();
        DocumentParser parser = parsers.get(ext);

        if (parser == null) {
            log.warn("不支持的文件格式: {}", filePath);
            return null;
        }

        try {
            // 使用 LangChain4j 解析
            Document doc = FileSystemDocumentLoader.loadDocument(filePath, parser);

            // 转换为我们的实体
            KnowledgeDocument knowledgeDoc = new KnowledgeDocument();
            knowledgeDoc.setDocId("file:" + filePath.toString());
            knowledgeDoc.setTitle(filePath.getFileName().toString());
            knowledgeDoc.setContent(doc.text());
            knowledgeDoc.setCategory(extractCategory(filePath));
            knowledgeDoc.setSource("file");
            knowledgeDoc.setVersion("1.0");
            knowledgeDoc.setUpdateTime(LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(filePath).toInstant(),
                    ZoneId.systemDefault()
            ));

            return knowledgeDoc;

        } catch (Exception e) {
            log.error("解析文件失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 从路径提取分类 (使用父目录名)
     */
    private String extractCategory(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null && !parent.equals(Paths.get(rootPath))) {
            return parent.getFileName().toString();
        }
        return "未分类";
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }
}
