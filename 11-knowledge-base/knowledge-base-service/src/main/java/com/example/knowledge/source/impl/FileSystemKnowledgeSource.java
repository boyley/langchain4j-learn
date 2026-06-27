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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统知识源实现 (企业级)
 *
 * 从指定目录递归读取文档文件 (PDF/Word/TXT/Markdown等)。
 *
 * ══════════════════════════════════════════════════════════════
 * 企业级设计要点:
 * ══════════════════════════════════════════════════════════════
 *
 * 1. 索引与解析分离
 *    - 先扫描建立文件索引 (只读元数据，不解析内容)
 *    - fetchBatch 时才真正解析文件内容
 *    - 避免每次都全量扫描解析
 *
 * 2. 懒加载
 *    - 只在需要时才解析文件
 *    - 分批处理，每批只解析当前批次的文件
 *    - 大文件不会一次性全部加载到内存
 *
 * 3. 增量索引
 *    - 缓存文件索引，支持增量更新
 *    - 通过文件修改时间判断是否需要重新索引
 *
 * 4. 大文件处理
 *    - 超过阈值的文件会记录警告
 *    - 实际生产中可接入分片处理或流式解析
 *
 * ══════════════════════════════════════════════════════════════
 *
 * 适用场景:
 * - 知识存储在文件服务器
 * - 需要批量导入文档
 * - 文档由人工维护
 *
 * 配置:
 * knowledge.source.file.enabled=true
 * knowledge.source.file.root-path=/data/knowledge/docs
 * knowledge.source.file.max-file-size-mb=50
 * knowledge.source.file.index-cache-minutes=30
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

    @Value("${knowledge.source.file.max-file-size-mb:50}")
    private int maxFileSizeMB;

    @Value("${knowledge.source.file.index-cache-minutes:30}")
    private int indexCacheMinutes;

    // 解析器映射表
    private final Map<String, DocumentParser> parsers = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // 文件索引缓存 (核心优化点)
    // ═══════════════════════════════════════════════════════════════════
    // 只存储文件路径和元数据，不存储文件内容
    // 这样 fetchBatch 时不需要重新扫描目录
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 文件索引项 - 只存储元数据，不存储内容
     */
    private record FileIndexEntry(
            Path path,
            long size,
            long lastModified,
            String extension
    ) {}

    // 文件索引缓存
    private volatile List<FileIndexEntry> fileIndex = new ArrayList<>();
    private volatile long indexBuildTime = 0;
    private final Object indexLock = new Object();

    // 文件修改时间缓存 (用于增量同步判断)
    private final Map<String, Long> fileModTimeCache = new ConcurrentHashMap<>();

    // 统计信息
    private final AtomicLong totalFilesScanned = new AtomicLong(0);
    private final AtomicLong totalFilesParsed = new AtomicLong(0);
    private final AtomicLong skippedLargeFiles = new AtomicLong(0);

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

        log.info("文件系统知识源初始化完成, rootPath={}, 支持格式={}, 最大文件={}MB",
                rootPath, parsers.keySet(), maxFileSizeMB);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 索引管理 (企业级核心优化)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 构建或刷新文件索引
     *
     * 只扫描目录结构和文件元数据，不读取文件内容。
     * 这样即使有 10000 个文件，索引构建也很快 (通常 < 1秒)
     */
    private void buildFileIndex(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        long cacheExpireTime = indexCacheMinutes * 60 * 1000L;

        // 检查缓存是否有效
        if (!forceRefresh && !fileIndex.isEmpty() && (now - indexBuildTime) < cacheExpireTime) {
            log.debug("使用缓存的文件索引, 剩余有效时间={}秒",
                    (cacheExpireTime - (now - indexBuildTime)) / 1000);
            return;
        }

        synchronized (indexLock) {
            // 双重检查
            if (!forceRefresh && !fileIndex.isEmpty() && (now - indexBuildTime) < cacheExpireTime) {
                return;
            }

            log.info("开始构建文件索引, rootPath={}", rootPath);
            long startTime = System.currentTimeMillis();

            List<FileIndexEntry> newIndex = new ArrayList<>();
            Path root = Paths.get(rootPath);

            try {
                // 使用 walkFileTree 更高效，可以在遍历时获取属性
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String ext = getExtension(file).toLowerCase();
                        if (parsers.containsKey(ext)) {
                            newIndex.add(new FileIndexEntry(
                                    file,
                                    attrs.size(),
                                    attrs.lastModifiedTime().toMillis(),
                                    ext
                            ));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        log.warn("无法访问文件: {}", file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("构建文件索引失败", e);
            }

            // 按修改时间倒序排列 (最新的优先处理)
            newIndex.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            this.fileIndex = newIndex;
            this.indexBuildTime = System.currentTimeMillis();
            this.totalFilesScanned.addAndGet(newIndex.size());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("文件索引构建完成, 文件数={}, 耗时={}ms", newIndex.size(), elapsed);
        }
    }

    /**
     * 强制刷新索引
     */
    public void refreshIndex() {
        buildFileIndex(true);
    }

    /**
     * 获取索引统计信息
     */
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("indexedFiles", fileIndex.size());
        stats.put("indexBuildTime", indexBuildTime > 0 ?
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(indexBuildTime), ZoneId.systemDefault()) : null);
        stats.put("totalFilesScanned", totalFilesScanned.get());
        stats.put("totalFilesParsed", totalFilesParsed.get());
        stats.put("skippedLargeFiles", skippedLargeFiles.get());
        stats.put("cacheValidMinutes", indexCacheMinutes);

        // 按扩展名统计
        Map<String, Long> byExtension = fileIndex.stream()
                .collect(Collectors.groupingBy(FileIndexEntry::extension, Collectors.counting()));
        stats.put("byExtension", byExtension);

        // 统计大文件数量
        long maxBytes = maxFileSizeMB * 1024L * 1024L;
        long largeFileCount = fileIndex.stream().filter(f -> f.size() > maxBytes).count();
        stats.put("largeFileCount", largeFileCount);

        return stats;
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

    /**
     * 全量获取 (企业级: 内部使用分批处理)
     *
     * ⚠️ 注意: 如果文件数量很大，建议使用 batchIterator() 或 fetchBatch()
     * 这个方法会将所有文档加载到内存，可能导致 OOM
     */
    @Override
    public List<KnowledgeDocument> fetchAll() {
        log.info("开始全量获取文件, rootPath={}", rootPath);

        if (!isAvailable()) {
            log.warn("文件目录不可用: {}", rootPath);
            return Collections.emptyList();
        }

        // 使用分批获取，避免一次性扫描所有文件
        buildFileIndex(true); // 强制刷新索引

        int totalFiles = fileIndex.size();
        if (totalFiles > 1000) {
            log.warn("文件数量较大 ({}), 建议使用 fetchBatch() 或 batchIterator() 分批处理", totalFiles);
        }

        // 分批加载
        List<KnowledgeDocument> allDocs = new ArrayList<>();
        int batchSize = 100;

        for (int offset = 0; offset < totalFiles; offset += batchSize) {
            List<KnowledgeDocument> batch = fetchBatch(offset, batchSize);
            allDocs.addAll(batch);

            // 打印进度
            if (totalFiles > 100) {
                int progress = Math.min(100, (offset + batchSize) * 100 / totalFiles);
                log.info("全量获取进度: {}% ({}/{})", progress, Math.min(offset + batchSize, totalFiles), totalFiles);
            }
        }

        log.info("全量获取完成, 共 {} 篇文档", allDocs.size());
        return allDocs;
    }

    /**
     * 增量获取 (只获取自上次同步后修改的文件)
     *
     * 基于索引过滤，只解析需要处理的文件
     */
    @Override
    public List<KnowledgeDocument> fetchIncremental(LocalDateTime lastSyncTime) {
        if (!isAvailable()) {
            log.warn("文件目录不可用: {}", rootPath);
            return Collections.emptyList();
        }

        if (lastSyncTime == null) {
            log.info("lastSyncTime 为空，执行全量获取");
            return fetchAll();
        }

        // 刷新索引
        buildFileIndex(true);

        // 过滤出需要处理的文件
        long syncTimeMillis = lastSyncTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        List<FileIndexEntry> changedFiles = fileIndex.stream()
                .filter(entry -> entry.lastModified() > syncTimeMillis)
                .collect(Collectors.toList());

        log.info("增量同步: 发现 {} 个文件自 {} 以来有变更", changedFiles.size(), lastSyncTime);

        if (changedFiles.isEmpty()) {
            return Collections.emptyList();
        }

        // 解析变更的文件
        List<KnowledgeDocument> documents = new ArrayList<>();
        long maxBytes = maxFileSizeMB * 1024L * 1024L;

        for (FileIndexEntry entry : changedFiles) {
            try {
                if (entry.size() > maxBytes) {
                    log.warn("跳过超大文件: {} ({}MB)", entry.path().getFileName(), entry.size() / 1024 / 1024);
                    skippedLargeFiles.incrementAndGet();
                    continue;
                }

                KnowledgeDocument doc = parseFile(entry.path());
                if (doc != null) {
                    documents.add(doc);
                    totalFilesParsed.incrementAndGet();
                    fileModTimeCache.put(entry.path().toString(), entry.lastModified());
                }
            } catch (Exception e) {
                log.error("解析文件失败: {}", entry.path(), e);
            }
        }

        log.info("增量同步完成, 解析 {} 篇文档", documents.size());
        return documents;
    }

    /**
     * 分批获取文档 (企业级实现)
     *
     * ══════════════════════════════════════════════════════════════
     * 核心优化:
     * 1. 使用文件索引，不重复扫描目录
     * 2. 只解析当前批次的文件，不全量加载
     * 3. 跳过超大文件，避免 OOM
     * ══════════════════════════════════════════════════════════════
     *
     * 调用示例:
     *   第1批: fetchBatch(0, 100)   → 解析文件 0-99
     *   第2批: fetchBatch(100, 100) → 解析文件 100-199
     *   ...
     *
     * @param offset 起始位置
     * @param limit  每批数量
     * @return 当前批次的文档列表
     */
    @Override
    public List<KnowledgeDocument> fetchBatch(int offset, int limit) {
        if (!isAvailable()) {
            return Collections.emptyList();
        }

        // 1. 确保索引已构建 (使用缓存，不会每次都扫描)
        buildFileIndex(false);

        // 2. 计算本批次范围
        int indexSize = fileIndex.size();
        if (offset >= indexSize) {
            log.debug("fetchBatch: offset={} 超出索引范围 {}", offset, indexSize);
            return Collections.emptyList();
        }

        int end = Math.min(offset + limit, indexSize);
        List<FileIndexEntry> batch = fileIndex.subList(offset, end);

        log.info("fetchBatch: 处理文件 [{}-{}], 共 {} 个", offset, end - 1, batch.size());

        // 3. 只解析当前批次的文件
        List<KnowledgeDocument> documents = new ArrayList<>();
        long maxBytes = maxFileSizeMB * 1024L * 1024L;

        for (FileIndexEntry entry : batch) {
            try {
                // 跳过超大文件
                if (entry.size() > maxBytes) {
                    log.warn("跳过超大文件: {} ({}MB > {}MB)",
                            entry.path().getFileName(),
                            entry.size() / 1024 / 1024,
                            maxFileSizeMB);
                    skippedLargeFiles.incrementAndGet();
                    continue;
                }

                // 解析文件
                KnowledgeDocument doc = parseFile(entry.path());
                if (doc != null) {
                    documents.add(doc);
                    totalFilesParsed.incrementAndGet();
                }

            } catch (Exception e) {
                log.error("解析文件失败: {}", entry.path(), e);
            }
        }

        log.info("fetchBatch: 成功解析 {} 个文档", documents.size());
        return documents;
    }

    /**
     * 使用迭代器模式分批处理 (更节省内存)
     *
     * 使用方式:
     * ```java
     * Iterator<List<KnowledgeDocument>> iter = source.batchIterator(100);
     * while (iter.hasNext()) {
     *     List<KnowledgeDocument> batch = iter.next();
     *     processBatch(batch);
     * }
     * ```
     */
    public Iterator<List<KnowledgeDocument>> batchIterator(int batchSize) {
        buildFileIndex(false);

        return new Iterator<>() {
            private int currentOffset = 0;

            @Override
            public boolean hasNext() {
                return currentOffset < fileIndex.size();
            }

            @Override
            public List<KnowledgeDocument> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<KnowledgeDocument> batch = fetchBatch(currentOffset, batchSize);
                currentOffset += batchSize;
                return batch;
            }
        };
    }

    /**
     * 统计文件数量 (使用索引，无需重新扫描)
     */
    @Override
    public long count() {
        if (!isAvailable()) {
            return 0;
        }
        buildFileIndex(false);
        return fileIndex.size();
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
