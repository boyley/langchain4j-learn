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
 * ═══════════════════════════════════════════════════════════════════════════
 * 【职责】管理文档内容的存储和读取
 * ═══════════════════════════════════════════════════════════════════════════
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeContentRepository contentRepository;

    /**
     * 获取文档完整内容
     *
     * @param docId 文档ID
     * @return 内容文本，如果不存在返回空
     */
    public Optional<String> getContent(String docId) {
        return contentRepository.findContentByDocId(docId);
    }

    /**
     * 获取内容实体 (包含元信息)
     */
    public Optional<KnowledgeContent> getContentEntity(String docId) {
        return contentRepository.findByDocId(docId);
    }

    /**
     * 保存文档 (元数据 + 内容)
     *
     * @param document 文档元数据
     * @param content  文档内容
     */
    @Transactional
    public void saveDocumentWithContent(KnowledgeDocument document, String content) {
        // 计算内容元信息
        document.setContentSize((long) content.getBytes().length);
        document.setContentHash(computeHash(content));
        document.setSummary(generateSummary(content));

        // 保存元数据
        documentRepository.save(document);

        // 保存内容 (分离存储)
        KnowledgeContent contentEntity = contentRepository.findByDocId(document.getDocId())
                .orElse(new KnowledgeContent());

        contentEntity.setDocId(document.getDocId());
        contentEntity.setContent(content);
        contentEntity.setContentSize(document.getContentSize());
        contentEntity.setContentHash(document.getContentHash());
        contentRepository.save(contentEntity);

        log.debug("保存文档: docId={}, contentSize={}", document.getDocId(), document.getContentSizeReadable());
    }

    /**
     * 检查内容是否变化
     *
     * @param docId   文档ID
     * @param newHash 新内容的哈希
     * @return true 表示内容有变化
     */
    public boolean isContentChanged(String docId, String newHash) {
        Optional<String> existingHash = contentRepository.findContentHashByDocId(docId);
        return existingHash.isEmpty() || !existingHash.get().equals(newHash);
    }

    /**
     * 删除文档内容
     */
    @Transactional
    public void deleteContent(String docId) {
        contentRepository.deleteByDocId(docId);
    }

    /**
     * 生成内容摘要 (前500字符)
     */
    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 去除换行符，取前500字符
        String cleaned = content.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 500) {
            return cleaned;
        }
        return cleaned.substring(0, 497) + "...";
    }

    /**
     * 计算内容哈希
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
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * 计算字符串的哈希 (用于外部比较)
     */
    public String hash(String content) {
        return computeHash(content);
    }
}
