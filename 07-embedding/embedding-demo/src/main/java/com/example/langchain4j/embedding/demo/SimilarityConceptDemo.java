package com.example.langchain4j.embedding.demo;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/**
 * 相似度概念详解 - 从零开始理解
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                        什么是相似度？
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 【生活中的相似度】
 *
 *   想象你在超市找苹果：
 *   - "红富士苹果" 和 "红苹果" → 很相似（都是苹果）
 *   - "红富士苹果" 和 "香蕉" → 不太相似（都是水果，但不同）
 *   - "红富士苹果" 和 "电脑" → 完全不相似
 *
 *   相似度就是用数字来表示"两个东西有多像"：
 *   - 1.0 = 完全相同
 *   - 0.5 = 有点像
 *   - 0.0 = 完全不像
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                        为什么需要相似度？
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 【问题场景】
 *
 *   用户搜索："苹果手机怎么用"
 *
 *   传统关键词搜索：
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │ 知识库文档              │ 能找到吗？ │ 原因                     │
 *   ├─────────────────────────────────────────────────────────────────┤
 *   │ "苹果手机使用教程"      │ ✓ 能      │ 包含"苹果手机"           │
 *   │ "iPhone 使用指南"       │ ✗ 不能    │ 没有"苹果手机"这几个字   │
 *   │ "iOS 设备入门"          │ ✗ 不能    │ 没有"苹果手机"这几个字   │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 *   相似度搜索：
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │ 知识库文档              │ 相似度    │ 能找到吗？               │
 *   ├─────────────────────────────────────────────────────────────────┤
 *   │ "苹果手机使用教程"      │ 0.95     │ ✓ 非常相似               │
 *   │ "iPhone 使用指南"       │ 0.88     │ ✓ 很相似（意思一样）     │
 *   │ "iOS 设备入门"          │ 0.82     │ ✓ 相似（都是苹果设备）   │
 *   │ "如何种苹果树"          │ 0.35     │ ✗ 不相似（此苹果非彼苹果）│
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                        相似度是怎么计算的？
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 【第一步：把文字变成数字（向量）】
 *
 *   "我爱吃苹果" → [0.12, -0.34, 0.56, 0.78, ...] （1536个数字）
 *
 *   这组数字代表了这句话的"语义特征"，就像人的指纹一样。
 *   意思相近的话，数字也会相近。
 *
 * 【第二步：比较两组数字有多接近】
 *
 *   简单理解：
 *
 *   文本 A: "我爱吃苹果" → 向量 A: [0.1, 0.2, 0.3]
 *   文本 B: "我喜欢苹果" → 向量 B: [0.1, 0.2, 0.3]  ← 几乎一样！相似度高
 *   文本 C: "今天下雨了" → 向量 C: [0.9, -0.5, 0.1] ← 差很多！相似度低
 *
 * 【第三步：用数学公式计算】
 *
 *   最常用的是"余弦相似度"：
 *
 *   想象两个箭头（向量）从原点出发：
 *   - 两个箭头方向相同 → 相似度 = 1（完全相同）
 *   - 两个箭头垂直 → 相似度 = 0（无关）
 *   - 两个箭头方向相反 → 相似度 = -1（完全相反）
 *
 *          ↗ A
 *         /
 *        / θ（夹角越小，越相似）
 *       /____→ B
 *
 *   相似度 = cos(θ) = 夹角的余弦值
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class SimilarityConceptDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(70));
        System.out.println("              相似度概念详解 - 从零开始理解");
        System.out.println("═".repeat(70));

        // 初始化模型
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")
                .build();

        // ═══════════════════════════════════════════════════════════════
        // 演示 1：理解什么是"把文字变成数字"
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【演示 1】把文字变成数字（向量化）");
        System.out.println("─".repeat(70));

        String text = "我喜欢吃苹果";
        Embedding embedding = model.embed(text).content();
        float[] vector = embedding.vector();

        System.out.println("\n原始文字: \"" + text + "\"");
        System.out.println("\n转换后的数字（只显示前10个，实际有 " + vector.length + " 个）:\n");
        System.out.print("  [");
        for (int i = 0; i < 10; i++) {
            System.out.printf("%.4f", vector[i]);
            if (i < 9) System.out.print(", ");
        }
        System.out.println(", ...]");

        System.out.println("\n说明：");
        System.out.println("  - 这 " + vector.length + " 个数字就是这句话的\"数字指纹\"");
        System.out.println("  - 意思相近的话，数字指纹也会相近");
        System.out.println("  - AI 通过比较这些数字来判断两句话是否相似");

        // ═══════════════════════════════════════════════════════════════
        // 演示 2：相似度的直观感受
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n\n【演示 2】相似度的直观感受");
        System.out.println("─".repeat(70));

        System.out.println("\n基准句子: \"我喜欢吃苹果\"\n");
        System.out.println("与其他句子的相似度：\n");

        String base = "我喜欢吃苹果";
        String[] comparisons = {
            "我爱吃苹果",           // 意思几乎相同
            "我喜欢吃水果",         // 意思相近
            "苹果很好吃",           // 相关但不同
            "我喜欢用苹果手机",     // 苹果的不同含义
            "今天天气真好",         // 完全不相关
            "我讨厌吃苹果"          // 相反的意思
        };

        Embedding baseEmb = model.embed(base).content();

        for (String comp : comparisons) {
            Embedding compEmb = model.embed(comp).content();
            double similarity = cosineSimilarity(baseEmb.vector(), compEmb.vector());

            // 生成相似度条形图
            int barLength = (int) (similarity * 20);
            String bar = "█".repeat(Math.max(0, barLength)) + "░".repeat(Math.max(0, 20 - barLength));

            // 相似度等级
            String level = getSimilarityLevel(similarity);

            System.out.printf("  \"%s\"%n", comp);
            System.out.printf("    相似度: %.2f [%s] %s%n%n", similarity, bar, level);
        }

        // ═══════════════════════════════════════════════════════════════
        // 演示 3：相似度分数的含义
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【演示 3】相似度分数代表什么？");
        System.out.println("─".repeat(70));

        System.out.println("""

            相似度分数范围是 0 到 1（有时是 -1 到 1）：

            ┌────────────┬─────────────────────────────────────────────────┐
            │ 分数       │ 含义                                            │
            ├────────────┼─────────────────────────────────────────────────┤
            │ 0.95-1.00  │ 几乎相同（可能是重复内容或换了个说法）          │
            │            │ 例: "我喜欢苹果" vs "我喜爱苹果"                │
            ├────────────┼─────────────────────────────────────────────────┤
            │ 0.80-0.95  │ 非常相似（说的是同一件事）                      │
            │            │ 例: "如何重置密码" vs "忘记密码怎么办"          │
            ├────────────┼─────────────────────────────────────────────────┤
            │ 0.60-0.80  │ 比较相关（同一个话题）                          │
            │            │ 例: "苹果手机" vs "iPhone 使用教程"             │
            ├────────────┼─────────────────────────────────────────────────┤
            │ 0.40-0.60  │ 有点关系（可能相关也可能不相关）                │
            │            │ 例: "苹果" vs "水果" (水果包含苹果)             │
            ├────────────┼─────────────────────────────────────────────────┤
            │ 0.00-0.40  │ 基本不相关                                      │
            │            │ 例: "苹果手机" vs "今天天气"                    │
            └────────────┴─────────────────────────────────────────────────┘
            """);

        // ═══════════════════════════════════════════════════════════════
        // 演示 4：实际应用 - 智能问答匹配
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【演示 4】实际应用 - 智能问答匹配");
        System.out.println("─".repeat(70));

        System.out.println("\n场景：用户用各种方式问同一个问题，系统能识别出来吗？\n");

        // 标准问题库
        String[] standardQuestions = {
            "如何修改登录密码",
            "商品怎么退货退款",
            "配送费用是多少",
            "会员有什么优惠"
        };

        // 用户的各种问法
        String[] userQuestions = {
            "密码忘了咋整",
            "我想把货退了",
            "运费多少钱",
            "办会员划算吗"
        };

        System.out.println("标准问题库：");
        for (int i = 0; i < standardQuestions.length; i++) {
            System.out.printf("  Q%d: %s%n", i + 1, standardQuestions[i]);
        }
        System.out.println();

        // 预计算标准问题的向量
        Embedding[] standardEmbeddings = new Embedding[standardQuestions.length];
        for (int i = 0; i < standardQuestions.length; i++) {
            standardEmbeddings[i] = model.embed(standardQuestions[i]).content();
        }

        System.out.println("用户问题 → 自动匹配结果：\n");

        for (String userQ : userQuestions) {
            Embedding userEmb = model.embed(userQ).content();

            // 找最相似的标准问题
            int bestMatch = 0;
            double bestScore = 0;
            for (int i = 0; i < standardEmbeddings.length; i++) {
                double score = cosineSimilarity(userEmb.vector(), standardEmbeddings[i].vector());
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = i;
                }
            }

            System.out.printf("  用户问: \"%s\"%n", userQ);
            System.out.printf("    ↓ 匹配到: \"%s\" (相似度: %.2f)%n%n",
                    standardQuestions[bestMatch], bestScore);
        }

        // ═══════════════════════════════════════════════════════════════
        // 演示 5：手动计算相似度
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【演示 5】手动计算相似度（理解原理）");
        System.out.println("─".repeat(70));

        System.out.println("""

            假设有两个超级简化的向量（实际有1536维，这里用3维演示）：

            文本 A: "苹果" → 向量 A = [1, 2, 3]
            文本 B: "水果" → 向量 B = [1, 2, 4]
            文本 C: "电脑" → 向量 C = [5, 0, 1]

            余弦相似度计算步骤：

            ┌─────────────────────────────────────────────────────────────────┐
            │ 步骤 1: 计算点积（对应位置相乘再相加）                          │
            │                                                                 │
            │   A·B = 1×1 + 2×2 + 3×4 = 1 + 4 + 12 = 17                      │
            │   A·C = 1×5 + 2×0 + 3×1 = 5 + 0 + 3  = 8                       │
            │                                                                 │
            ├─────────────────────────────────────────────────────────────────┤
            │ 步骤 2: 计算向量长度                                            │
            │                                                                 │
            │   |A| = √(1² + 2² + 3²) = √14 ≈ 3.74                           │
            │   |B| = √(1² + 2² + 4²) = √21 ≈ 4.58                           │
            │   |C| = √(5² + 0² + 1²) = √26 ≈ 5.10                           │
            │                                                                 │
            ├─────────────────────────────────────────────────────────────────┤
            │ 步骤 3: 相似度 = 点积 / (长度A × 长度B)                         │
            │                                                                 │
            │   相似度(A,B) = 17 / (3.74 × 4.58) ≈ 0.99  ← 很相似！          │
            │   相似度(A,C) = 8  / (3.74 × 5.10) ≈ 0.42  ← 不太相似          │
            │                                                                 │
            └─────────────────────────────────────────────────────────────────┘
            """);

        // 用真实数据验证
        System.out.println("用真实数据验证：\n");

        String textA = "苹果";
        String textB = "水果";
        String textC = "电脑";

        Embedding embA = model.embed(textA).content();
        Embedding embB = model.embed(textB).content();
        Embedding embC = model.embed(textC).content();

        double simAB = cosineSimilarity(embA.vector(), embB.vector());
        double simAC = cosineSimilarity(embA.vector(), embC.vector());

        System.out.printf("  \"苹果\" 和 \"水果\" 的相似度: %.4f (%s)%n", simAB, getSimilarityLevel(simAB));
        System.out.printf("  \"苹果\" 和 \"电脑\" 的相似度: %.4f (%s)%n", simAC, getSimilarityLevel(simAC));

        // 总结
        printSummary();
    }

    /**
     * 计算余弦相似度
     *
     * 公式：
     *              A · B              所有对应元素相乘的和
     * cos(θ) = ─────────── = ────────────────────────────────
     *           |A| × |B|       A的长度 × B的长度
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似度，范围 -1 到 1
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        // 第一步：计算点积 (A · B)
        double dotProduct = 0.0;

        // 第二步：计算两个向量的长度（模）
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];  // 对应位置相乘，累加
            normA += a[i] * a[i];       // 平方和
            normB += b[i] * b[i];       // 平方和
        }

        // 第三步：相似度 = 点积 / (长度A × 长度B)
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 根据相似度分数返回等级描述
     */
    private static String getSimilarityLevel(double score) {
        if (score >= 0.90) return "几乎相同 ★★★★★";
        if (score >= 0.80) return "非常相似 ★★★★☆";
        if (score >= 0.70) return "比较相似 ★★★☆☆";
        if (score >= 0.60) return "有些相关 ★★☆☆☆";
        if (score >= 0.50) return "略有关系 ★☆☆☆☆";
        return "基本无关 ☆☆☆☆☆";
    }

    private static void printSummary() {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("                           总  结");
        System.out.println("═".repeat(70));

        System.out.println("""

            【相似度是什么？】

            相似度是一个 0 到 1 的数字，表示两段文字"意思有多像"：
            - 1.0 = 意思完全相同
            - 0.5 = 有点关系
            - 0.0 = 毫无关系


            【相似度怎么算的？】

            1. 先把文字变成一串数字（向量）
               "我喜欢苹果" → [0.1, 0.2, 0.3, ...]

            2. 用数学公式比较两串数字有多接近
               越接近 → 相似度越高


            【相似度有什么用？】

            ┌─────────────────────────────────────────────────────────────────┐
            │ 场景                │ 作用                                      │
            ├─────────────────────────────────────────────────────────────────┤
            │ 智能搜索            │ 搜"苹果手机"能找到"iPhone"               │
            │ 智能客服            │ 用户说"密码忘了"匹配到"重置密码"         │
            │ 推荐系统            │ 看了 A 文章，推荐相似的 B、C 文章        │
            │ 去重检测            │ 找出意思相同但表述不同的重复内容          │
            │ RAG 知识库          │ 从文档中找到和问题最相关的内容            │
            └─────────────────────────────────────────────────────────────────┘


            【关键理解】

            传统搜索：只能匹配"关键词"，字不一样就找不到
            相似度搜索：能匹配"意思"，意思一样就能找到

            这就是为什么 AI 搜索比传统搜索更"智能"的原因！
            """);
    }
}
