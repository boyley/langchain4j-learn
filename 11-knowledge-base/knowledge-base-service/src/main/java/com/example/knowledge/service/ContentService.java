package com.example.knowledge.service;

import com.example.knowledge.entity.KnowledgeContent;
import com.example.knowledge.entity.KnowledgeDocument;
import com.example.knowledge.repository.KnowledgeContentRepository;
import com.example.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 内容管理服务
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【职责】管理文档内容的存储和读取
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 核心设计:
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │                                                                        │
 * │   KnowledgeDocument (元数据)          KnowledgeContent (内容)          │
 * │   ┌──────────────────────┐           ┌──────────────────────┐         │
 * │   │ docId, title, ...   │   1 : 1   │ docId, content      │         │
 * │   │ 查询快，量小         │◄─────────►│ 按需加载，可能很大    │         │
 * │   └──────────────────────┘           └──────────────────────┘         │
 * │                                                                        │
 * │   使用场景:                                                             │
 * │   - 列表展示: 只查 KnowledgeDocument                                    │
 * │   - 向量化处理: 通过 ContentService 获取内容                            │
 * │   - 搜索结果: 元数据 + 向量库中的片段 (不需要完整内容)                    │
 * │                                                                        │
 * └────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心功能】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 内容读取
 *    - getContent(docId): 只获取内容文本
 *    - getContentEntity(docId): 获取完整内容实体
 *
 * 2. 内容保存
 *    - saveDocumentWithContent(): 同时保存元数据和内容
 *    - 自动计算 contentSize、contentHash、summary
 *
 * 3. 变更检测
 *    - isContentChanged(): 通过哈希比较判断内容是否变化
 *    - 避免重复处理未变化的文档
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【使用示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ```java
 * // 保存文档
 * contentService.saveDocumentWithContent(doc, "文档内容...");
 *
 * // 获取内容
 * String content = contentService.getContent("db:001")
 *     .orElseThrow(() -> new NotFoundException("内容不存在"));
 *
 * // 检查内容是否变化
 * String newHash = contentService.hash(newContent);
 * if (contentService.isContentChanged(docId, newHash)) {
 *     // 内容有变化，需要重新处理
 * }
 * ```
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    // ═══════════════════════════════════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 文档元数据仓库
     */
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * 文档内容仓库
     */
    private final KnowledgeContentRepository contentRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // 内容读取
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 获取文档完整内容
     *
     * @param docId 文档ID
     * @return 内容文本，如果不存在返回空 Optional
     *
     * 使用投影查询，只加载 content 字段
     * 对于大文档，比加载整个实体更高效
     */
    public Optional<String> getContent(String docId) {
        return contentRepository.findContentByDocId(docId);
    }

    /**
     * 获取内容实体 (包含元信息)
     *
     * @param docId 文档ID
     * @return 完整的内容实体
     *
     * 当需要 contentSize、contentHash 等字段时使用
     */
    public Optional<KnowledgeContent> getContentEntity(String docId) {
        return contentRepository.findByDocId(docId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 内容保存
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 保存文档 (元数据 + 内容)
     *
     * @param document 文档元数据
     * @param content  文档内容
     *
     * @Transactional 注解说明:
     * - 开启事务，确保元数据和内容同时成功或同时失败
     * - 默认传播行为: REQUIRED (加入现有事务或创建新事务)
     * - 默认隔离级别: DEFAULT (使用数据库默认)
     * - 抛出 RuntimeException 时自动回滚
     *
     * 执行逻辑:
     * 1. 计算内容元信息 (size, hash, summary)
     * 2. 保存元数据到 knowledge_documents 表
     * 3. 保存内容到 knowledge_content 表 (插入或更新)
     */
    @Transactional
    public void saveDocumentWithContent(KnowledgeDocument document, String content) {
        // ─────────────────────────────────────────────────────────────────────
        // 1. 计算内容元信息
        // ─────────────────────────────────────────────────────────────────────
        document.setContentSize((long) content.getBytes().length);
        document.setContentHash(computeHash(content));
        document.setSummary(generateSummary(content));

        // ─────────────────────────────────────────────────────────────────────
        // 2. 保存元数据
        // ─────────────────────────────────────────────────────────────────────
        documentRepository.save(document);

        // ─────────────────────────────────────────────────────────────────────
        // 3. 保存内容 (分离存储)
        // ─────────────────────────────────────────────────────────────────────
        // 查找已有内容实体，不存在则创建新的
        KnowledgeContent contentEntity = contentRepository.findByDocId(document.getDocId())
                .orElse(new KnowledgeContent());

        contentEntity.setDocId(document.getDocId());
        contentEntity.setContent(content);
        contentEntity.setContentSize(document.getContentSize());
        contentEntity.setContentHash(document.getContentHash());
        contentRepository.save(contentEntity);

        log.debug("保存文档: docId={}, contentSize={}", document.getDocId(), document.getContentSizeReadable());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 变更检测
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 检查内容是否变化
     *
     * @param docId   文档ID
     * @param newHash 新内容的哈希值
     * @return true 表示内容有变化 (包括首次创建的情况)
     *
     * 用途:
     * - 增量同步时判断是否需要重新向量化
     * - 避免重复处理未变化的文档
     *
     * 返回 true 的情况:
     * 1. 内容不存在 (首次创建)
     * 2. 哈希值不同 (内容已变化)
     */
    public boolean isContentChanged(String docId, String newHash) {
        Optional<String> existingHash = contentRepository.findContentHashByDocId(docId);
        // 如果不存在或哈希不同，则认为有变化
        return existingHash.isEmpty() || !existingHash.get().equals(newHash);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 删除操作
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 删除文档内容
     *
     * @param docId 文档ID
     *
     * @Transactional: 确保删除操作在事务中执行
     */
    @Transactional
    public void deleteContent(String docId) {
        contentRepository.deleteByDocId(docId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 生成内容摘要 (前500字符)
     *
     * @param content 原始内容
     * @return 摘要文本
     *
     * 用途:
     * - 列表页预览
     * - 搜索结果展示
     * - 减少不必要的内容加载
     */
    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 去除多余空白，规范化
        String cleaned = content.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 500) {
            return cleaned;
        }
        // 截取前 497 字符 + "..."
        return cleaned.substring(0, 497) + "...";
    }

    /**
     * 计算内容哈希 (MD5)
     *
     * @param content 内容文本
     * @return 32 字符的 MD5 哈希值
     *
     * 用于:
     * - 内容变更检测
     * - 去重判断
     *
     * 算法说明:
     * - 使用 MD5，速度快，32 字符
     * - 不用于安全场景，仅用于内容比较
     * - 生产环境如需更高安全性可改用 SHA-256
     */
    private String computeHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 降级方案: 使用 Java hashCode
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * 计算字符串的哈希 (公开方法，用于外部比较)
     *
     * @param content 内容
     * @return MD5 哈希值
     *
     * 使用示例:
     * ```java
     * String newHash = contentService.hash(newContent);
     * if (contentService.isContentChanged(docId, newHash)) {
     *     // 需要更新
     * }
     * ```
     */
    public String hash(String content) {
        return computeHash(content);
    }
}
