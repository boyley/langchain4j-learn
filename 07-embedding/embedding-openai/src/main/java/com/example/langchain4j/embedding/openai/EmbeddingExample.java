package com.example.langchain4j.embedding.openai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.stream.Collectors;

public class EmbeddingExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置 OPENAI_API_KEY 环境变量");
            System.exit(1);
        }

        // 创建 Embedding 模型
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")  // 或 text-embedding-ada-002
                .build();

        System.out.println("=== 文本向量化示例 (OpenAI) ===\n");

        // 示例 1: 单个文本向量化
        System.out.println("--- 示例 1: 单个文本向量化 ---\n");

        String text1 = "LangChain4j 是一个 Java 版本的 LangChain 框架";
        Response<Embedding> response1 = model.embed(text1);
        Embedding embedding1 = response1.content();

        System.out.println("文本: " + text1);
        System.out.println("向量维度: " + embedding1.dimension());
        System.out.println("向量前5个值: " + formatVector(embedding1.vector(), 5));

        // 示例 2: 多个文本向量化
        System.out.println("\n--- 示例 2: 批量向量化 ---\n");

        List<String> texts = List.of(
            "Java 是一种面向对象的编程语言",
            "Python 是一种动态类型的脚本语言",
            "机器学习是人工智能的一个子领域"
        );

        List<TextSegment> segments = texts.stream()
            .map(TextSegment::from)
            .collect(Collectors.toList());

        Response<List<Embedding>> response2 = model.embedAll(segments);
        List<Embedding> embeddings = response2.content();

        for (int i = 0; i < texts.size(); i++) {
            System.out.println("文本 " + (i + 1) + ": " + texts.get(i));
            System.out.println("向量维度: " + embeddings.get(i).dimension());
        }

        // 示例 3: 计算文本相似度
        System.out.println("\n--- 示例 3: 文本相似度计算 ---\n");

        String query = "Java 编程";
        String doc1 = "Java 是一种广泛使用的编程语言";
        String doc2 = "Python 用于数据科学";
        String doc3 = "今天天气很好";

        Embedding queryEmb = model.embed(query).content();
        Embedding doc1Emb = model.embed(doc1).content();
        Embedding doc2Emb = model.embed(doc2).content();
        Embedding doc3Emb = model.embed(doc3).content();

        System.out.println("查询: " + query);
        System.out.println();
        System.out.println("与 \"" + doc1 + "\" 的相似度: " +
            String.format("%.4f", cosineSimilarity(queryEmb.vector(), doc1Emb.vector())));
        System.out.println("与 \"" + doc2 + "\" 的相似度: " +
            String.format("%.4f", cosineSimilarity(queryEmb.vector(), doc2Emb.vector())));
        System.out.println("与 \"" + doc3 + "\" 的相似度: " +
            String.format("%.4f", cosineSimilarity(queryEmb.vector(), doc3Emb.vector())));
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
