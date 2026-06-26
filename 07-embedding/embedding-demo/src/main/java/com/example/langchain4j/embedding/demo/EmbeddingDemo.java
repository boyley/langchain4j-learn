package com.example.langchain4j.embedding.demo;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Embedding（嵌入/向量化）详解
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 什么是 Embedding？
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Embedding 就是把文本转换成一组数字（向量），让计算机能够"理解"文本的含义。
 *
 * 举例：
 *   "我喜欢苹果"  →  [0.12, -0.34, 0.56, 0.78, ...]  (1536个数字)
 *   "我爱吃苹果"  →  [0.11, -0.33, 0.55, 0.79, ...]  (非常相似！)
 *   "今天下雨了"  →  [0.89, 0.23, -0.67, 0.12, ...]  (完全不同)
 *
 * 关键特性：
 *   - 语义相近的文本 → 向量也相近（可以计算距离）
 *   - 语义不同的文本 → 向量差异大
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * Embedding 有什么用？
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. 【语义搜索】 - 不是关键词匹配，而是意思匹配
 *    用户搜索 "如何学编程" → 能找到 "入门写代码的方法"（意思相近）
 *
 * 2. 【相似推荐】 - 找到相似的内容
 *    "你看了这篇文章，可能也喜欢这些..."
 *
 * 3. 【RAG（检索增强生成）】 - 这是最重要的应用！
 *    让 AI 回答时，先从你的文档库中找到相关内容，再生成回答
 *    例如：公司内部知识库问答、PDF 文档问答
 *
 * 4. 【文本分类】 - 判断文本属于哪个类别
 *    客服消息：投诉？咨询？建议？
 *
 * 5. 【去重/聚类】 - 找出相似的文本
 *    新闻去重、评论聚类分析
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * Embedding vs ChatModel 的区别？
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   ChatModel (聊天模型):
 *     输入: 文本 → 输出: 文本回复
 *     用途: 对话、生成内容
 *
 *   EmbeddingModel (向量模型):
 *     输入: 文本 → 输出: 数字向量 [0.1, 0.2, ...]
 *     用途: 相似度计算、语义搜索
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class EmbeddingDemo {

    public static void main(String[] args) {
        // 使用 LangChain4j 官方 Demo API - 无需 API Key！
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")
                .build();

        System.out.println("=== Embedding 文本向量化示例 ===\n");

        // ═══════════════════════════════════════════════════════════════
        // 示例 1: 理解什么是 Embedding
        // ═══════════════════════════════════════════════════════════════
        System.out.println("【示例 1】什么是 Embedding？");
        System.out.println("─".repeat(50));
        System.out.println("Embedding 就是把文字变成一组数字，让计算机能\"理解\"含义\n");

        String text1 = "我喜欢吃苹果";
        Response<Embedding> response1 = model.embed(text1);
        Embedding embedding1 = response1.content();

        System.out.println("原文: \"" + text1 + "\"");
        System.out.println("    ↓ Embedding 转换");
        System.out.println("向量: " + formatVector(embedding1.vector(), 5));
        System.out.println("维度: " + embedding1.dimension() + " 个数字");
        System.out.println("\n这组数字就代表了\"我喜欢吃苹果\"的语义含义！");

        // ═══════════════════════════════════════════════════════════════
        // 示例 2: 核心应用 - 语义相似度计算
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 2】核心应用：语义相似度计算");
        System.out.println("─".repeat(50));
        System.out.println("问题：如何判断两句话意思是否相近？\n");
        System.out.println("传统方法（关键词匹配）：");
        System.out.println("  \"如何学编程\" vs \"入门写代码\" → 没有相同关键词，匹配失败！\n");
        System.out.println("Embedding 方法（语义匹配）：");
        System.out.println("  计算两个向量的距离，距离越近意思越相似！\n");

        // 准备测试数据
        String query = "如何学习 Java 编程";
        String[] documents = {
            "Java 入门教程：从零开始学编程",     // 语义非常相关
            "Python 数据分析实战",              // 都是编程，但不太相关
            "今天北京天气晴朗"                   // 完全不相关
        };

        System.out.println("用户搜索: \"" + query + "\"\n");
        System.out.println("候选文档：");

        Embedding queryEmb = model.embed(query).content();
        for (int i = 0; i < documents.length; i++) {
            Embedding docEmb = model.embed(documents[i]).content();
            double similarity = cosineSimilarity(queryEmb.vector(), docEmb.vector());
            String bar = "█".repeat((int)(similarity * 20));
            System.out.printf("  %d. \"%s\"%n", i + 1, documents[i]);
            System.out.printf("     相似度: %.2f %s%n%n", similarity, bar);
        }

        System.out.println("→ 可以看到，语义相近的文档相似度高，不相关的相似度低！");

        // ═══════════════════════════════════════════════════════════════
        // 示例 3: 实际应用场景
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 3】实际应用场景：智能客服问题匹配");
        System.out.println("─".repeat(50));
        System.out.println("场景：用户问的问题可能和知识库中的表述不同，但意思一样\n");

        // 模拟知识库
        String[] faqQuestions = {
            "如何重置密码",
            "怎么申请退款",
            "运费多少钱",
            "发票怎么开"
        };
        String[] faqAnswers = {
            "点击「忘记密码」，输入手机号验证即可重置。",
            "订单页面点击「申请退款」，3个工作日内处理。",
            "满99元包邮，不满99元运费10元。",
            "订单完成后，在「我的订单」中申请电子发票。"
        };

        // 用户的各种问法
        String userQuestion = "密码忘了怎么办";  // 用户不会说"重置密码"
        System.out.println("用户问: \"" + userQuestion + "\"");
        System.out.println("\n在知识库中搜索最匹配的问题：");

        Embedding userEmb = model.embed(userQuestion).content();
        int bestMatch = 0;
        double bestScore = -1;

        for (int i = 0; i < faqQuestions.length; i++) {
            Embedding faqEmb = model.embed(faqQuestions[i]).content();
            double score = cosineSimilarity(userEmb.vector(), faqEmb.vector());
            System.out.printf("  - \"%s\" → 相似度: %.2f%n", faqQuestions[i], score);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = i;
            }
        }

        System.out.println("\n最佳匹配: \"" + faqQuestions[bestMatch] + "\"");
        System.out.println("自动回复: " + faqAnswers[bestMatch]);

        // ═══════════════════════════════════════════════════════════════
        // 总结
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n" + "═".repeat(50));
        System.out.println("【总结】Embedding 解决了什么问题？");
        System.out.println("═".repeat(50));
        System.out.println("""

            问题：计算机不理解文字的"含义"，只能做关键词匹配

            解决：把文字转成向量，让计算机能计算"语义相似度"

            应用：
            ┌─────────────────────────────────────────────────┐
            │ 1. 语义搜索 - 搜"苹果手机" 能找到 "iPhone"       │
            │ 2. 智能客服 - 用户问法不同，也能匹配到答案       │
            │ 3. 推荐系统 - 找到相似的商品/文章/视频           │
            │ 4. RAG - 让 AI 从你的文档中找信息再回答 (重要!) │
            │ 5. 文本分类/聚类/去重                           │
            └─────────────────────────────────────────────────┘

            下一步：学习 08-rag 模块，看 Embedding 如何用于知识库问答！
            """);
    }

    // 格式化向量输出
    private static String formatVector(float[] vector, int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(n, vector.length); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", vector[i]));
        }
        sb.append(", ...]");
        return sb.toString();
    }

    // 计算余弦相似度
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
}
