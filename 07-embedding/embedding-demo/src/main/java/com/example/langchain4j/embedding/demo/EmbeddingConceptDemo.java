package com.example.langchain4j.embedding.demo;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.util.Arrays;
import java.util.List;

/**
 * Embedding（嵌入）概念详解 Demo
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【什么是 Embedding（嵌入）？】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Embedding = 把文字"嵌入"到数学空间中
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Embedding 的本质                         │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │   文字世界                        数学世界                   │
 * │   (人类理解)                      (计算机理解)               │
 * │                                                             │
 * │   "苹果"        ───────────→      [0.12, 0.85, 0.33, ...]  │
 * │   "香蕉"        ───────────→      [0.15, 0.82, 0.29, ...]  │
 * │   "汽车"        ───────────→      [0.91, 0.02, 0.76, ...]  │
 * │                                                             │
 * │                  EmbeddingModel                             │
 * │                  (翻译官)                                   │
 * │                                                             │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【为什么叫"嵌入"？】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 想象一个高维空间（比如 1536 维），每个词/句子都被"嵌入"到这个空间的某个位置：
 *
 *                     数学空间 (简化为2D展示)
 *
 *                     ^
 *                     │      苹果
 *                     │   香蕉    橙子
 *                     │
 *             水果区域 │  葡萄
 *                     │
 *         ────────────┼────────────────────→
 *                     │
 *                     │              汽车
 *             交通区域 │        公交车
 *                     │    火箭
 *                     │
 *
 * 关键特点：
 * - 意思相近的词，在空间中距离近！
 * - 意思不同的词，在空间中距离远！
 *
 * "嵌入"的含义：把文字"放进"一个数学空间，让它有了位置和坐标。
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【EmbeddingModel 做什么？】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 输入 (文本)                      输出 (向量)
 *
 * "员工每年有5天年假"    →    [0.12, 0.34, 0.56, ..., 0.78]
 *                              (1536个数字)
 *
 * 这串数字就是这句话的"数学指纹"：
 * - 相似的话 → 相似的数字
 * - 不同的话 → 不同的数字
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【达到什么目的？】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 核心目的：让计算机能够理解"语义相似性"
 *
 * 传统方式 (关键词匹配)：
 * ─────────────────────────────
 * "如何请假" vs "年假规定"    → 没有共同关键词 → 匹配失败 ❌
 *
 * Embedding 方式 (语义理解)：
 * ─────────────────────────────
 * "如何请假"  → [0.23, 0.45, 0.67, ...]
 * "年假规定"  → [0.25, 0.43, 0.69, ...]
 *                ↓
 *            计算距离 = 0.95 (很近!)
 *                ↓
 *            语义相似！匹配成功 ✅
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【在知识库中的应用】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【入库阶段】
 *
 *   知识文档              EmbeddingModel              向量数据库
 *  ┌─────────┐           ┌─────────────┐            ┌─────────┐
 *  │"年假5天" │ ────────→ │   文字→向量  │ ─────────→ │ [0.1,...]│
 *  │"报销流程"│           │   翻译官     │            │ [0.3,...]│
 *  │"入职指南"│           └─────────────┘            │ [0.5,...]│
 *  └─────────┘                                      └─────────┘
 *
 * 【检索阶段】
 *
 *   用户问题              EmbeddingModel              向量数据库
 *  ┌─────────┐           ┌─────────────┐            ┌─────────┐
 *  │"怎么请假"│ ────────→ │   文字→向量  │ ─────────→ │ 找最近的 │
 *  └─────────┘           └─────────────┘            │ 向量    │
 *                              ↓                   └────┬────┘
 *                         [0.12, ...]                   │
 *                                                       ↓
 *                                                  返回"年假5天"
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 【一句话总结】
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * | 概念           | 解释                                    |
 * |----------------|----------------------------------------|
 * | Embedding      | 把文字转成数字向量（数学坐标）            |
 * | 为什么叫嵌入   | 把文字"放入"数学空间，有了位置            |
 * | 做什么         | 文字 → 1536个数字                        |
 * | 目的           | 让计算机能比较"语义相似性"               |
 *
 * 本质：Embedding 是人类语言和计算机数学之间的桥梁！
 *
 * @author LangChain4j 学习项目
 */
public class EmbeddingConceptDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            Embedding（嵌入）概念详解 Demo                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ==================== 创建 EmbeddingModel ====================
        /**
         * EmbeddingModel - 嵌入模型（翻译官）
         *
         * 作用：把人类语言翻译成计算机能理解的数学向量
         *
         * 常用模型：
         * - text-embedding-3-small: 1536维，便宜，推荐日常使用
         * - text-embedding-3-large: 3072维，更精准，成本更高
         * - text-embedding-ada-002: 1536维，旧版模型
         */
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .modelName("text-embedding-3-small")  // 输出 1536 维向量
                .build();

        // ==================== 第一部分：基本概念演示 ====================
        System.out.println("═".repeat(60));
        System.out.println("【第一部分】Embedding 基本概念");
        System.out.println("═".repeat(60));
        System.out.println();

        System.out.println("EmbeddingModel 的作用：把文字转换成数字向量");
        System.out.println();

        String text = "员工每年享有5天带薪年假";
        System.out.println("输入文本: \"" + text + "\"");
        System.out.println();

        // 执行 Embedding
        Embedding embedding = embeddingModel.embed(text).content();

        System.out.println("输出向量:");
        System.out.println("- 维度: " + embedding.dimension() + " 维");
        System.out.println("- 前10个数字: " + formatVector(embedding.vector(), 10));
        System.out.println();

        System.out.println("这 " + embedding.dimension() + " 个数字就是这句话的\"数学指纹\"！");
        System.out.println();

        // ==================== 第二部分：语义相似性演示 ====================
        System.out.println("═".repeat(60));
        System.out.println("【第二部分】语义相似性 - Embedding 的核心价值");
        System.out.println("═".repeat(60));
        System.out.println();

        System.out.println("问题: 传统关键词匹配 vs Embedding 语义匹配\n");

        // 准备测试文本
        String query = "如何请假";
        String doc1 = "年假规定：员工每年有5天带薪年假";  // 语义相关，但没有"请假"关键词
        String doc2 = "报销流程：差旅费需在30天内报销";   // 语义不相关

        System.out.println("查询: \"" + query + "\"");
        System.out.println("文档1: \"" + doc1 + "\"");
        System.out.println("文档2: \"" + doc2 + "\"");
        System.out.println();

        // 传统方式
        System.out.println("【传统关键词匹配】");
        System.out.println("- \"如何请假\" vs \"年假规定\"");
        System.out.println("- 没有共同关键词 → 匹配失败 ❌");
        System.out.println();

        // Embedding 方式
        System.out.println("【Embedding 语义匹配】");

        Embedding queryEmb = embeddingModel.embed(query).content();
        Embedding doc1Emb = embeddingModel.embed(doc1).content();
        Embedding doc2Emb = embeddingModel.embed(doc2).content();

        double sim1 = cosineSimilarity(queryEmb.vector(), doc1Emb.vector());
        double sim2 = cosineSimilarity(queryEmb.vector(), doc2Emb.vector());

        System.out.println("- \"" + query + "\" vs \"年假规定...\"");
        System.out.println("  相似度: " + String.format("%.1f%%", sim1 * 100) + " → 语义相关 ✅");
        System.out.println();
        System.out.println("- \"" + query + "\" vs \"报销流程...\"");
        System.out.println("  相似度: " + String.format("%.1f%%", sim2 * 100) + " → 语义不相关");
        System.out.println();

        System.out.println("结论: Embedding 能理解\"请假\"和\"年假\"是相关的，即使没有共同关键词！");
        System.out.println();

        // ==================== 第三部分：更多示例 ====================
        System.out.println("═".repeat(60));
        System.out.println("【第三部分】更多语义相似性示例");
        System.out.println("═".repeat(60));
        System.out.println();

        // 示例对
        String[][] examples = {
                {"苹果", "香蕉"},       // 都是水果，应该相似
                {"苹果", "汽车"},       // 不相关，应该不相似
                {"国王", "女王"},       // 都是君主，应该相似
                {"国王", "椅子"},       // 不相关，应该不相似
                {"跑步", "运动"},       // 跑步是运动的一种，应该相似
                {"快乐", "高兴"},       // 同义词，应该很相似
                {"快乐", "悲伤"},       // 反义词，可能中等相似（都是情绪）
        };

        System.out.println("词语对比较:");
        System.out.println("-".repeat(50));

        for (String[] pair : examples) {
            Embedding emb1 = embeddingModel.embed(pair[0]).content();
            Embedding emb2 = embeddingModel.embed(pair[1]).content();
            double similarity = cosineSimilarity(emb1.vector(), emb2.vector());

            String bar = generateBar(similarity);
            System.out.printf("%-6s vs %-6s : %s %.1f%%%n",
                    pair[0], pair[1], bar, similarity * 100);
        }

        System.out.println();

        // ==================== 第四部分：知识库应用场景 ====================
        System.out.println("═".repeat(60));
        System.out.println("【第四部分】在知识库中的实际应用");
        System.out.println("═".repeat(60));
        System.out.println();

        System.out.println("""
                ┌─────────────────────────────────────────────────────────────┐
                │                    知识库检索流程                            │
                └─────────────────────────────────────────────────────────────┘

                【入库阶段】一次性完成，存储到向量数据库

                  知识文档              EmbeddingModel              向量数据库
                 ┌─────────┐           ┌─────────────┐            ┌─────────┐
                 │"年假5天" │ ────────→ │   文字→向量  │ ─────────→ │ 持久存储│
                 │"报销流程"│           │             │            │ 不会丢失│
                 │"入职指南"│           └─────────────┘            └─────────┘
                 └─────────┘                                       (Redis/
                                                                   PostgreSQL/
                                                                   Milvus)

                【检索阶段】每次用户提问时执行

                  用户问题              EmbeddingModel              向量数据库
                 ┌─────────┐           ┌─────────────┐            ┌─────────┐
                 │"怎么请假"│ ────────→ │   文字→向量  │ ─────────→ │ 相似搜索│
                 └─────────┘           └─────────────┘            │ 找最近的│
                                             ↓                    └────┬────┘
                                        [0.12, ...]                    │
                                                                       ↓
                                                                 返回最相似的文档
                """);

        // ==================== 总结 ====================
        System.out.println("═".repeat(60));
        System.out.println("【总结】Embedding 核心要点");
        System.out.println("═".repeat(60));
        System.out.println();

        System.out.println("""
                ┌────────────────┬────────────────────────────────────────────┐
                │     概念        │                    解释                     │
                ├────────────────┼────────────────────────────────────────────┤
                │ Embedding      │ 把文字转成数字向量（数学坐标）               │
                │ 为什么叫嵌入   │ 把文字"放入"数学空间，有了位置               │
                │ EmbeddingModel │ 执行转换的模型（翻译官）                    │
                │ 向量维度       │ 1536维 = 1536个数字描述一段文字             │
                │ 核心目的       │ 让计算机能比较"语义相似性"                  │
                └────────────────┴────────────────────────────────────────────┘

                一句话总结：
                Embedding 是人类语言和计算机数学之间的桥梁！

                通过 Embedding：
                - 计算机能"理解"文字的含义
                - 能找到语义相关的内容（即使没有共同关键词）
                - 这是 RAG、知识库、语义搜索的核心技术
                """);
    }

    /**
     * 计算余弦相似度
     *
     * 余弦相似度：衡量两个向量方向的相似程度
     * - 1.0 = 完全相同方向（完全相似）
     * - 0.0 = 垂直（无关）
     * - -1.0 = 完全相反方向（完全相反）
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 格式化向量，只显示前N个数字
     */
    private static String formatVector(float[] vector, int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(n, vector.length); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", vector[i]));
        }
        sb.append(", ...]");
        return sb.toString();
    }

    /**
     * 生成相似度进度条
     */
    private static String generateBar(double similarity) {
        int filled = (int) (similarity * 20);
        int empty = 20 - filled;
        return "▓".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }
}
