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

/**
 * 文件系统知识源实现 (企业级)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【功能概述】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 从指定目录递归读取文档文件 (PDF/Word/TXT/Markdown等)。
 *
 * 数据流:
 *   文件系统目录 → 扫描索引 → 解析文件 → KnowledgeDocument → 向量化
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【企业级设计要点】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 索引与解析分离
 *    ┌─────────────────────────────────────────────────────────────────────────┐
 *    │  传统方式 (有问题):                                                      │
 *    │  fetchBatch() → 扫描目录 → 解析所有文件 → 返回                           │
 *    │  每次调用都要重新扫描，效率低                                             │
 *    │                                                                         │
 *    │  企业级方式:                                                              │
 *    │  buildFileIndex() → 扫描目录 → 缓存索引 (只有文件路径和元数据)            │
 *    │  fetchBatch() → 使用缓存索引 → 只解析当前批次的文件                       │
 *    │  高效，索引可复用                                                        │
 *    └─────────────────────────────────────────────────────────────────────────┘
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
 *    - 超过阈值的文件会跳过并记录警告
 *    - 实际生产中可接入分片处理或流式解析
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   source:
 *     file:
 *       enabled: true                    # 启用文件知识源
 *       root-path: /data/knowledge/docs  # 文档根目录
 *       max-file-size-mb: 50            # 单文件最大 MB
 *       index-cache-minutes: 30          # 索引缓存时间
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【支持的文件格式】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 格式      | 扩展名                    | 解析器                    |
 * |----------|--------------------------|--------------------------|
 * | 文本      | .txt, .md, .json, .xml   | TextDocumentParser       |
 * | PDF      | .pdf                      | ApachePdfBoxDocumentParser|
 * | Word     | .docx, .doc               | ApachePoiDocumentParser  |
 * | Excel    | .xlsx, .xls               | ApachePoiDocumentParser  |
 * | PPT      | .pptx, .ppt               | ApachePoiDocumentParser  |
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【LangChain4j 文档解析】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * LangChain4j 提供了统一的文档加载和解析接口:
 *
 * DocumentParser 接口:
 * - TextDocumentParser: 纯文本解析
 * - ApachePdfBoxDocumentParser: PDF 解析 (需要 apache-pdfbox 依赖)
 * - ApachePoiDocumentParser: Office 文档解析 (需要 apache-poi 依赖)
 *
 * 使用示例:
 * ```java
 * DocumentParser parser = new ApachePdfBoxDocumentParser();
 * Document doc = FileSystemDocumentLoader.loadDocument(path, parser);
 * String text = doc.text();
 * ```
 */
@Slf4j
/*
 * @Component - Spring 组件注解
 *
 * 将此类注册为 Spring Bean
 * 会被 KnowledgeSource 接口的注入点自动发现
 */
@Component
/*
 * @ConditionalOnProperty - 条件装配
 *
 * 参数:
 * - name: 配置属性名
 * - havingValue: 期望的值
 *
 * 效果:
 * - knowledge.source.file.enabled=true → 创建 Bean
 * - knowledge.source.file.enabled=false → 不创建
 * - 未配置 → 不创建 (与 DatabaseKnowledgeSource 不同)
 *
 * 为什么文件源默认不启用:
 * - 需要配置 root-path
 * - 避免启动时扫描不存在的目录
 */
@ConditionalOnProperty(name = "knowledge.source.file.enabled", havingValue = "true")
public class FileSystemKnowledgeSource implements KnowledgeSource {

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置属性
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档根目录
     *
     * 配置: knowledge.source.file.root-path
     * 示例: /data/knowledge/docs
     *
     * 目录结构建议:
     * /data/knowledge/docs/
     * ├── HR政策/
     * │   ├── 员工手册.pdf
     * │   └── 休假管理.docx
     * ├── 技术文档/
     * │   ├── API文档.md
     * │   └── 架构设计.pdf
     * └── 产品手册/
     *     └── 用户指南.pdf
     *
     * 子目录名会作为文档分类 (category)
     */
    @Value("${knowledge.source.file.root-path:}")
    private String rootPath;

    /**
     * 单文件最大大小 (MB)
     *
     * 超过此大小的文件会被跳过
     * 避免大文件导致 OOM
     *
     * 默认: 50MB
     */
    @Value("${knowledge.source.file.max-file-size-mb:50}")
    private int maxFileSizeMB;

    /**
     * 索引缓存时间 (分钟)
     *
     * 文件索引会缓存指定时间
     * 在此期间不会重新扫描目录
     *
     * 默认: 30 分钟
     */
    @Value("${knowledge.source.file.index-cache-minutes:30}")
    private int indexCacheMinutes;

    // ═══════════════════════════════════════════════════════════════════════════
    // 解析器
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文件扩展名 → 解析器 映射表
     *
     * 在 @PostConstruct 初始化时填充
     */
    private final Map<String, DocumentParser> parsers = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // 文件索引缓存 (核心优化点)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文件索引项
     *
     * record 语法: Java 14+ 的不可变数据类
     * 自动生成构造函数、getter、equals、hashCode、toString
     *
     * 只存储元数据，不存储文件内容
     * 这样即使有 10000 个文件，索引也只占用很少内存
     *
     * @param path         文件路径
     * @param size         文件大小 (字节)
     * @param lastModified 最后修改时间 (毫秒时间戳)
     * @param extension    文件扩展名
     */
    private record FileIndexEntry(
            Path path,
            long size,
            long lastModified,
            String extension
    ) {}

    /**
     * 文件索引缓存
     *
     * volatile: 保证多线程可见性
     * 索引更新时直接替换整个列表，保证线程安全
     */
    private volatile List<FileIndexEntry> fileIndex = new ArrayList<>();

    /**
     * 索引构建时间戳 (毫秒)
     *
     * 用于判断缓存是否过期
     */
    private volatile long indexBuildTime = 0;

    /**
     * 索引构建锁
     *
     * 避免多线程同时构建索引
     */
    private final Object indexLock = new Object();

    /**
     * 文件修改时间缓存
     *
     * 用于增量同步时判断文件是否变化
     * key: 文件路径字符串
     * value: 上次处理时的修改时间
     *
     * ConcurrentHashMap: 线程安全的 Map
     */
    private final Map<String, Long> fileModTimeCache = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // 统计信息
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * AtomicLong: 线程安全的计数器
     *
     * 用于统计:
     * - 扫描过的文件总数
     * - 解析过的文件总数
     * - 跳过的大文件数
     */
    private final AtomicLong totalFilesScanned = new AtomicLong(0);
    private final AtomicLong totalFilesParsed = new AtomicLong(0);
    private final AtomicLong skippedLargeFiles = new AtomicLong(0);

    // ═══════════════════════════════════════════════════════════════════════════
    // 初始化
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 初始化解析器
     *
     * @PostConstruct 注解说明:
     * - 在 Bean 创建后、依赖注入完成后自动调用
     * - 只调用一次
     * - 用于初始化操作
     *
     * 执行顺序:
     * 1. 构造函数
     * 2. @Autowired / @Value 注入
     * 3. @PostConstruct 方法
     * 4. Bean 可用
     */
    @PostConstruct
    public void init() {
        // ─────────────────────────────────────────────────────────────────────
        // 文本类解析器
        // ─────────────────────────────────────────────────────────────────────
        // TextDocumentParser: 直接读取文件内容作为文本
        DocumentParser textParser = new TextDocumentParser();
        parsers.put("txt", textParser);
        parsers.put("md", textParser);
        parsers.put("json", textParser);
        parsers.put("xml", textParser);
        parsers.put("csv", textParser);
        parsers.put("yaml", textParser);
        parsers.put("yml", textParser);
        parsers.put("log", textParser);

        // ─────────────────────────────────────────────────────────────────────
        // PDF 解析器
        // ─────────────────────────────────────────────────────────────────────
        // ApachePdfBoxDocumentParser: 使用 Apache PDFBox 提取文本
        // 依赖: langchain4j-document-parser-apache-pdfbox
        parsers.put("pdf", new ApachePdfBoxDocumentParser());

        // ─────────────────────────────────────────────────────────────────────
        // Office 文档解析器
        // ─────────────────────────────────────────────────────────────────────
        // ApachePoiDocumentParser: 使用 Apache POI 提取文本
        // 依赖: langchain4j-document-parser-apache-poi
        // 支持: Word, Excel, PowerPoint
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

    // ═══════════════════════════════════════════════════════════════════════════
    // 索引管理 (企业级核心优化)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 构建或刷新文件索引
     *
     * 只扫描目录结构和文件元数据，不读取文件内容。
     * 这样即使有 10000 个文件，索引构建也很快 (通常 < 1秒)
     *
     * @param forceRefresh true 强制刷新，false 使用缓存
     *
     * 双重检查锁 (Double-Check Locking):
     * - 第一次检查: 避免不必要的加锁
     * - 第二次检查: 进入同步块后再次验证
     * - 保证线程安全且高效
     */
    private void buildFileIndex(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        long cacheExpireTime = indexCacheMinutes * 60 * 1000L;

        // 第一次检查 (无锁)
        if (!forceRefresh && !fileIndex.isEmpty() && (now - indexBuildTime) < cacheExpireTime) {
            log.debug("使用缓存的文件索引, 剩余有效时间={}秒",
                    (cacheExpireTime - (now - indexBuildTime)) / 1000);
            return;
        }

        synchronized (indexLock) {
            // 第二次检查 (有锁)
            if (!forceRefresh && !fileIndex.isEmpty() && (now - indexBuildTime) < cacheExpireTime) {
                return;
            }

            log.info("开始构建文件索引, rootPath={}", rootPath);
            long startTime = System.currentTimeMillis();

            List<FileIndexEntry> newIndex = new ArrayList<>();
            Path root = Paths.get(rootPath);

            try {
                // ─────────────────────────────────────────────────────────────
                // 使用 Files.walkFileTree 遍历目录
                // ─────────────────────────────────────────────────────────────
                // 比 Files.walk 更高效:
                // - 可以在遍历时获取文件属性 (避免额外 IO)
                // - 可以处理访问失败的情况
                // - 支持提前终止遍历
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String ext = getExtension(file).toLowerCase();
                        // 只索引支持的文件类型
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
                        // 记录警告但继续遍历
                        log.warn("无法访问文件: {}", file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                log.error("构建文件索引失败", e);
            }

            // 按修改时间倒序排列 (最新的优先处理)
            newIndex.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            // 原子更新
            this.fileIndex = newIndex;
            this.indexBuildTime = System.currentTimeMillis();
            this.totalFilesScanned.addAndGet(newIndex.size());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("文件索引构建完成, 文件数={}, 耗时={}ms", newIndex.size(), elapsed);
        }
    }

    /**
     * 强制刷新索引
     *
     * 公开方法，供外部调用
     * 场景: 管理员知道有新文件添加
     */
    public void refreshIndex() {
        buildFileIndex(true);
    }

    /**
     * 获取索引统计信息
     *
     * @return 统计信息 Map
     *
     * 返回内容:
     * - indexedFiles: 索引的文件数
     * - indexBuildTime: 索引构建时间
     * - totalFilesScanned: 累计扫描文件数
     * - totalFilesParsed: 累计解析文件数
     * - skippedLargeFiles: 跳过的大文件数
     * - byExtension: 按扩展名统计
     * - largeFileCount: 当前大文件数
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

    // ═══════════════════════════════════════════════════════════════════════════
    // KnowledgeSource 接口实现
    // ═══════════════════════════════════════════════════════════════════════════

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
     * 全量获取
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

        // 强制刷新索引
        buildFileIndex(true);

        int totalFiles = fileIndex.size();
        if (totalFiles > 1000) {
            log.warn("文件数量较大 ({}), 建议使用 fetchBatch() 或 batchIterator() 分批处理", totalFiles);
        }

        // 分批加载，避免一次性全部解析
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
     * 核心优化:
     * 1. 使用文件索引，不重复扫描目录
     * 2. 只解析当前批次的文件，不全量加载
     * 3. 跳过超大文件，避免 OOM
     *
     * @param offset 起始位置 (基于索引的偏移量)
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
     * @param batchSize 每批大小
     * @return 批次迭代器
     *
     * 使用方式:
     * ```java
     * Iterator<List<KnowledgeDocument>> iter = source.batchIterator(100);
     * while (iter.hasNext()) {
     *     List<KnowledgeDocument> batch = iter.next();
     *     processBatch(batch);
     *     // 每批处理完后，上一批的文档可以被 GC 回收
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

    // ═══════════════════════════════════════════════════════════════════════════
    // 文件解析
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 解析单个文件
     *
     * @param filePath 文件路径
     * @return KnowledgeDocument 实体，解析失败返回 null
     *
     * 使用 LangChain4j 的 FileSystemDocumentLoader 和 DocumentParser
     */
    private KnowledgeDocument parseFile(Path filePath) {
        String ext = getExtension(filePath).toLowerCase();
        DocumentParser parser = parsers.get(ext);

        if (parser == null) {
            log.warn("不支持的文件格式: {}", filePath);
            return null;
        }

        try {
            // 使用 LangChain4j 解析文件
            Document doc = FileSystemDocumentLoader.loadDocument(filePath, parser);

            // 转换为我们的实体
            KnowledgeDocument knowledgeDoc = new KnowledgeDocument();
            knowledgeDoc.setDocId("file:" + filePath.toString());
            knowledgeDoc.setTitle(filePath.getFileName().toString());
            knowledgeDoc.setContent(doc.text()); // 设置到 @Transient 字段
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
     *
     * 示例:
     * /data/docs/HR政策/员工手册.pdf → category = "HR政策"
     * /data/docs/readme.txt → category = "未分类"
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
     *
     * @param path 文件路径
     * @return 扩展名 (不含点)，如 "pdf", "docx"
     */
    private String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }
}
