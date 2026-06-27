package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

/**
 * RAG 完整流程演示 - 一步一步展示
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                        RAG 是什么？
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * RAG = Retrieval-Augmented Generation = 检索增强生成
 *
 * 简单理解：让 AI 先"查资料"，再回答问题
 *
 * 【没有 RAG 的 AI】
 *
 *   用户：公司的请假制度是什么？
 *   AI：抱歉，我不知道你们公司的制度...（AI 只知道训练时学到的知识）
 *
 * 【有 RAG 的 AI】
 *
 *   用户：公司的请假制度是什么？
 *   AI：(先从公司文档中找到相关内容)
 *       根据员工手册，请假需要提前3天申请，年假15天...
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                        RAG 完整流程图
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *                          【准备阶段 - 构建知识库】
 *
 *   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 *   │   原始文档    │     │   文档分割    │     │   向量化      │     │  向量数据库   │
 *   │              │     │              │     │              │     │              │
 *   │  员工手册.txt │ ──▶ │ 切成小块      │ ──▶ │ 文字→数字    │ ──▶ │ 存储向量     │
 *   │  产品文档.pdf │     │ (每块200字)  │     │ (Embedding)  │     │ +原文        │
 *   │  FAQ.md      │     │              │     │              │     │              │
 *   └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
 *         │                    │                    │                    │
 *         │              步骤 1: Split        步骤 2: Embed         步骤 3: Store
 *         │                    │                    │                    │
 *   ──────┴────────────────────┴────────────────────┴────────────────────┘
 *
 *
 *                          【查询阶段 - 用户提问】
 *
 *   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 *   │   用户提问    │     │  问题向量化   │     │  相似度检索   │     │  找到相关    │
 *   │              │     │              │     │              │     │  文档片段    │
 *   │ "请假怎么请?" │ ──▶ │ 问题→向量    │ ──▶ │ 在向量库搜索  │ ──▶ │              │
 *   │              │     │              │     │              │     │ "请假需提前.."│
 *   └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
 *         │                    │                    │                    │
 *         │              步骤 4: Embed        步骤 5: Search        步骤 6: Retrieve
 *         │                    │                    │                    │
 *   ──────┴────────────────────┴────────────────────┴────────────────────┘
 *
 *
 *                          【生成阶段 - AI 回答】
 *
 *   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 *   │  组装 Prompt │     │   LLM 生成    │     │   返回答案    │
 *   │              │     │              │     │              │
 *   │ 上下文+问题   │ ──▶ │ AI 根据上下文 │ ──▶ │  用户看到    │
 *   │              │     │ 生成回答      │     │  最终答案    │
 *   └──────────────┘     └──────────────┘     └──────────────┘
 *         │                    │                    │
 *         │              步骤 7: Generate      步骤 8: Return
 *         │                    │                    │
 *   ──────┴────────────────────┴────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public class RagFullProcessDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(75));
        System.out.println("              RAG 完整流程演示 - 一步一步展示");
        System.out.println("═".repeat(75));

        // ═══════════════════════════════════════════════════════════════
        // 初始化组件
        // ═══════════════════════════════════════════════════════════════

        /**
         * EmbeddingModel - 向量化模型
         * 作用：把文字转换成数字向量
         */
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("text-embedding-3-small")  // 输出 1536 维向量
                .build();

        /**
         * ChatLanguageModel - 聊天模型
         * 作用：根据上下文生成回答
         */
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        /**
         * EmbeddingStore - 向量存储
         * 作用：存储向量和原文，支持相似度搜索
         */
        EmbeddingStore<TextSegment> vectorStore = new InMemoryEmbeddingStore<>();

        System.out.println("\n组件初始化完成：");
        System.out.println("  ✓ EmbeddingModel (向量化模型): text-embedding-3-small");
        System.out.println("  ✓ ChatLanguageModel (聊天模型): gpt-4o-mini");
        System.out.println("  ✓ EmbeddingStore (向量存储): InMemoryEmbeddingStore");

        // ═══════════════════════════════════════════════════════════════════════
        //
        //                       准 备 阶 段
        //
        // ═══════════════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("                         准 备 阶 段");
        System.out.println("▓".repeat(75));

        // ═══════════════════════════════════════════════════════════════
        // 步骤 0: 原始文档
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n【步骤 0】原始文档");
        System.out.println("─".repeat(75));

        String rawDocument = """
            《员工手册 - 请假制度》

            第一章：请假类型

            1. 年假：员工入职满一年后，每年享有15天带薪年假。年假可分次使用，但单次不少于半天。未使用的年假可顺延至次年3月底，逾期作废。

            2. 病假：员工因病需要休息，应提供医院证明。病假期间工资按80%发放。连续病假超过3天需提供三甲医院诊断证明。

            3. 事假：员工因个人事务需要请假，应提前申请。事假期间不发放工资。每月事假累计不得超过3天。

            第二章：请假流程

            1. 申请方式：通过公司OA系统提交请假申请，填写请假类型、起止时间、请假原因。

            2. 审批流程：
               - 1-3天假期：直属主管审批
               - 3-7天假期：部门经理审批
               - 7天以上：需HR总监审批

            3. 紧急请假：如遇紧急情况无法提前申请，应在2小时内电话通知主管，并在返岗后1天内补交申请。

            第三章：加班与调休

            1. 加班申请：加班需提前通过OA系统申请，注明加班原因和预计时长。

            2. 调休规则：工作日加班可申请1:1调休，周末加班可申请1:1.5调休，法定节假日加班可申请1:3调休或按规定发放加班工资。

            3. 调休有效期：调休需在加班后3个月内使用，逾期作废。
            """;

        System.out.println("\n原始文档内容（模拟从文件读取）：\n");
        System.out.println("─".repeat(40) + " 文档开始 " + "─".repeat(40));
        System.out.println(rawDocument.substring(0, Math.min(500, rawDocument.length())) + "...");
        System.out.println("─".repeat(40) + " 文档结束 " + "─".repeat(40));
        System.out.println("\n文档总长度: " + rawDocument.length() + " 字符");

        // 创建 Document 对象
        Document document = Document.from(rawDocument);

        // ═══════════════════════════════════════════════════════════════
        // 步骤 1: 文档分割 (Split)
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n【步骤 1】文档分割 (Split)");
        System.out.println("─".repeat(75));

        System.out.println("""

            为什么要分割？
            ┌─────────────────────────────────────────────────────────────────────┐
            │ 1. LLM 有上下文长度限制，不能一次处理太长的文本                      │
            │ 2. 太长的文本向量化后，语义会"稀释"，搜索效果差                      │
            │ 3. 用户问题通常只涉及文档的一小部分，需要精确定位                    │
            └─────────────────────────────────────────────────────────────────────┘
            """);

        /**
         * DocumentSplitters.recursive(maxSegmentSize, maxOverlap) 参数：
         *
         * maxSegmentSize (300): 每个片段最多 300 个字符
         * maxOverlap (30): 相邻片段重叠 30 个字符，避免句子被截断
         *
         * 分割策略：
         * 1. 优先按段落 (\n\n) 分割
         * 2. 段落太长则按句子 (。！？) 分割
         * 3. 句子太长则按逗号/空格分割
         */
        DocumentSplitter splitter = DocumentSplitters.recursive(
                300,  // maxSegmentSize: 每个片段最多 300 字符
                30    // maxOverlap: 重叠 30 字符
        );

        List<TextSegment> segments = splitter.split(document);

        System.out.println("分割参数：");
        System.out.println("  - maxSegmentSize = 300 (每个片段最多300字符)");
        System.out.println("  - maxOverlap = 30 (相邻片段重叠30字符)");
        System.out.println("\n分割结果：共 " + segments.size() + " 个片段\n");

        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i).text();
            String preview = text.length() > 80
                    ? text.substring(0, 80).replace("\n", " ") + "..."
                    : text.replace("\n", " ");
            System.out.printf("  片段 %d (%3d 字符): %s%n", i + 1, text.length(), preview);
        }

        // ═══════════════════════════════════════════════════════════════
        // 步骤 2: 向量化 (Embedding)
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n【步骤 2】向量化 (Embedding)");
        System.out.println("─".repeat(75));

        System.out.println("""

            向量化过程：
            ┌─────────────────────────────────────────────────────────────────────┐
            │                                                                     │
            │   "年假：员工入职满一年后..."  ──▶  [0.12, -0.34, 0.56, ...]        │
            │                                         │                           │
            │                                    1536 个数字                      │
            │                                    (语义特征)                       │
            │                                                                     │
            └─────────────────────────────────────────────────────────────────────┘
            """);

        System.out.println("开始向量化每个片段...\n");

        // 存储所有向量
        Embedding[] embeddings = new Embedding[segments.size()];

        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i).text();

            // 调用 EmbeddingModel 将文本转为向量
            Embedding embedding = embeddingModel.embed(text).content();
            embeddings[i] = embedding;

            // 显示向量的前几个数字
            float[] vector = embedding.vector();
            System.out.printf("  片段 %d → 向量 [%.4f, %.4f, %.4f, %.4f, ...] (共 %d 维)%n",
                    i + 1, vector[0], vector[1], vector[2], vector[3], vector.length);
        }

        // ═══════════════════════════════════════════════════════════════
        // 步骤 3: 存储到向量库 (Store)
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n【步骤 3】存储到向量库 (Store)");
        System.out.println("─".repeat(75));

        System.out.println("""

            存储内容：
            ┌─────────────────────────────────────────────────────────────────────┐
            │  ID        │  向量                      │  原文                      │
            ├─────────────────────────────────────────────────────────────────────┤
            │  id_001    │  [0.12, -0.34, ...]       │  "年假：员工入职满..."     │
            │  id_002    │  [0.45, 0.23, ...]        │  "病假：员工因病..."       │
            │  id_003    │  [0.78, -0.12, ...]       │  "申请方式：通过OA..."     │
            │  ...       │  ...                      │  ...                       │
            └─────────────────────────────────────────────────────────────────────┘
            """);

        System.out.println("存储向量和原文到向量库...\n");

        for (int i = 0; i < segments.size(); i++) {
            // add(embedding, segment) - 存储向量和原文
            // 返回自动生成的 ID
            String id = vectorStore.add(embeddings[i], segments.get(i));
            System.out.printf("  ✓ 片段 %d 已存储，ID: %s%n", i + 1, id.substring(0, 8) + "...");
        }

        System.out.println("\n准备阶段完成！知识库已构建。");
        System.out.println("  - 共 " + segments.size() + " 个片段");
        System.out.println("  - 每个片段都有对应的向量和原文");

        // ═══════════════════════════════════════════════════════════════════════
        //
        //                       查 询 阶 段
        //
        // ═══════════════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("                         查 询 阶 段");
        System.out.println("▓".repeat(75));

        // 用户问题
        String userQuestion = "请假需要怎么申请？审批流程是什么？";

        // ═══════════════════════════════════════════════════════════════
        // 步骤 4: 用户提问 & 问题向量化
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n【步骤 4】用户提问 & 问题向量化");
        System.out.println("─".repeat(75));

        System.out.println("\n用户问题: \"" + userQuestion + "\"\n");

        // 将问题转为向量
        Embedding questionEmbedding = embeddingModel.embed(userQuestion).content();
        float[] qVector = questionEmbedding.vector();

        System.out.println("问题向量化：");
        System.out.printf("  \"%s\"%n", userQuestion);
        System.out.println("       ↓");
        System.out.printf("  [%.4f, %.4f, %.4f, %.4f, ...] (共 %d 维)%n",
                qVector[0], qVector[1], qVector[2], qVector[3], qVector.length);

        // ═══════════════════════════════════════════════════════════════
        // 步骤 5: 相似度检索 (Search)
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n\n【步骤 5】相似度检索 (Search)");
        System.out.println("─".repeat(75));

        System.out.println("""

            检索原理：
            ┌─────────────────────────────────────────────────────────────────────┐
            │                                                                     │
            │   问题向量  ─────┬──▶  与片段1向量比较 ──▶ 相似度 0.45              │
            │                  ├──▶  与片段2向量比较 ──▶ 相似度 0.32              │
            │                  ├──▶  与片段3向量比较 ──▶ 相似度 0.89  ← 最相似!   │
            │                  └──▶  ...                                          │
            │                                                                     │
            │   返回相似度最高的 N 个片段                                         │
            │                                                                     │
            └─────────────────────────────────────────────────────────────────────┘
            """);

        /**
         * EmbeddingSearchRequest 参数：
         * - queryEmbedding: 问题的向量
         * - maxResults: 最多返回几个结果
         * - minScore: 最低相似度阈值（可选）
         */
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)  // 问题向量
                .maxResults(3)                      // 返回最相似的 3 个片段
                .build();

        // 执行搜索
        EmbeddingSearchResult<TextSegment> searchResult = vectorStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        System.out.println("检索参数：maxResults = 3 (返回最相似的3个片段)\n");
        System.out.println("检索结果（按相似度排序）：\n");

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            double score = match.score();
            String text = match.embedded().text();
            String preview = text.length() > 100
                    ? text.substring(0, 100).replace("\n", " ") + "..."
                    : text.replace("\n", " ");

            String bar = "█".repeat((int)(score * 20)) + "░".repeat(20 - (int)(score * 20));

            System.out.printf("  Top %d [相似度: %.4f] [%s]%n", i + 1, score, bar);
            System.out.printf("        %s%n%n", preview);
        }

        // ═══════════════════════════════════════════════════════════════
        // 步骤 6: 提取相关文档片段 (Retrieve)
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n【步骤 6】提取相关文档片段 (Retrieve)");
        System.out.println("─".repeat(75));

        // 拼接检索到的内容作为上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            context.append("【参考资料 ").append(i + 1).append("】\n");
            context.append(matches.get(i).embedded().text());
            context.append("\n\n");
        }

        System.out.println("\n从知识库中提取的相关内容：\n");
        System.out.println("─".repeat(40) + " 上下文开始 " + "─".repeat(40));
        System.out.println(context.toString());
        System.out.println("─".repeat(40) + " 上下文结束 " + "─".repeat(40));

        // ═══════════════════════════════════════════════════════════════════════
        //
        //                       生 成 阶 段
        //
        // ═══════════════════════════════════════════════════════════════════════

        System.out.println("\n\n" + "▓".repeat(75));
        System.out.println("                         生 成 阶 段");
        System.out.println("▓".repeat(75));

        // ═══════════════════════════════════════════════════════════════
        // 步骤 7: 组装 Prompt & LLM 生成
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n【步骤 7】组装 Prompt & LLM 生成");
        System.out.println("─".repeat(75));

        // 组装最终的 Prompt
        String prompt = String.format("""
            你是一个企业知识库助手。请根据以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请说"抱歉，我没有找到相关信息"。
            请用简洁、专业的语言回答。

            ===== 参考资料 =====
            %s
            ===== 参考资料结束 =====

            用户问题：%s

            请回答：
            """, context.toString(), userQuestion);

        System.out.println("\n发送给 LLM 的完整 Prompt：\n");
        System.out.println("─".repeat(40) + " Prompt 开始 " + "─".repeat(40));
        System.out.println(prompt);
        System.out.println("─".repeat(40) + " Prompt 结束 " + "─".repeat(40));

        System.out.println("\n调用 LLM 生成回答...\n");

        // 调用 LLM 生成回答
        String answer = chatModel.generate(prompt);

        // ═══════════════════════════════════════════════════════════════
        // 步骤 8: 返回答案
        // ═══════════════════════════════════════════════════════════════

        System.out.println("\n【步骤 8】返回答案");
        System.out.println("─".repeat(75));

        System.out.println("\n┌" + "─".repeat(73) + "┐");
        System.out.println("│ 用户问题：" + userQuestion + " ".repeat(Math.max(0, 62 - userQuestion.length())) + "│");
        System.out.println("├" + "─".repeat(73) + "┤");
        System.out.println("│ AI 回答：" + " ".repeat(64) + "│");

        // 格式化输出答案
        String[] lines = answer.split("\n");
        for (String line : lines) {
            while (line.length() > 70) {
                System.out.println("│   " + line.substring(0, 70) + "│");
                line = line.substring(70);
            }
            System.out.println("│   " + line + " ".repeat(Math.max(0, 70 - line.length())) + "│");
        }
        System.out.println("└" + "─".repeat(73) + "┘");

        // ═══════════════════════════════════════════════════════════════
        // 总结
        // ═══════════════════════════════════════════════════════════════

        printSummary();

        // 测试更多问题
        testMoreQuestions(vectorStore, embeddingModel, chatModel);
    }

    private static void printSummary() {
        System.out.println("\n\n" + "═".repeat(75));
        System.out.println("                         RAG 流程总结");
        System.out.println("═".repeat(75));

        System.out.println("""

            ┌─────────────────────────────────────────────────────────────────────────┐
            │                           RAG 完整流程                                   │
            ├─────────────────────────────────────────────────────────────────────────┤
            │                                                                          │
            │   【准备阶段 - 只需执行一次】                                            │
            │                                                                          │
            │   步骤 0: 加载原始文档                                                   │
            │           └─ 从文件/数据库读取文档内容                                   │
            │                        ↓                                                 │
            │   步骤 1: 文档分割 (Split)                                               │
            │           └─ DocumentSplitters.recursive(300, 30)                        │
            │           └─ 长文档 → 多个小片段                                         │
            │                        ↓                                                 │
            │   步骤 2: 向量化 (Embed)                                                 │
            │           └─ embeddingModel.embed(text)                                  │
            │           └─ 文本 → 1536维向量                                           │
            │                        ↓                                                 │
            │   步骤 3: 存储 (Store)                                                   │
            │           └─ vectorStore.add(embedding, segment)                         │
            │           └─ 向量+原文 → 向量数据库                                      │
            │                                                                          │
            ├─────────────────────────────────────────────────────────────────────────┤
            │                                                                          │
            │   【查询阶段 - 每次提问都执行】                                          │
            │                                                                          │
            │   步骤 4: 问题向量化                                                     │
            │           └─ embeddingModel.embed(question)                              │
            │           └─ 用户问题 → 向量                                             │
            │                        ↓                                                 │
            │   步骤 5: 相似度检索 (Search)                                            │
            │           └─ vectorStore.search(request)                                 │
            │           └─ 找出最相似的 N 个片段                                       │
            │                        ↓                                                 │
            │   步骤 6: 提取上下文 (Retrieve)                                          │
            │           └─ 把检索到的片段拼接成上下文                                  │
            │                                                                          │
            ├─────────────────────────────────────────────────────────────────────────┤
            │                                                                          │
            │   【生成阶段】                                                           │
            │                                                                          │
            │   步骤 7: 组装 Prompt + LLM 生成                                         │
            │           └─ prompt = 系统指令 + 上下文 + 用户问题                       │
            │           └─ chatModel.generate(prompt)                                  │
            │                        ↓                                                 │
            │   步骤 8: 返回答案                                                       │
            │           └─ AI 根据上下文生成的回答                                     │
            │                                                                          │
            └─────────────────────────────────────────────────────────────────────────┘


            关键组件：
            ┌────────────────────┬────────────────────────────────────────────────────┐
            │ 组件               │ 作用                                               │
            ├────────────────────┼────────────────────────────────────────────────────┤
            │ DocumentSplitter   │ 文档分割器，把长文档切成小块                       │
            │ EmbeddingModel     │ 向量化模型，把文本转成数字向量                     │
            │ EmbeddingStore     │ 向量存储，保存向量并支持相似度搜索                 │
            │ ChatLanguageModel  │ 聊天模型，根据上下文生成回答                       │
            └────────────────────┴────────────────────────────────────────────────────┘
            """);
    }

    /**
     * 测试更多问题
     */
    private static void testMoreQuestions(EmbeddingStore<TextSegment> vectorStore,
                                          EmbeddingModel embeddingModel,
                                          ChatLanguageModel chatModel) {
        System.out.println("\n\n" + "═".repeat(75));
        System.out.println("                         测试更多问题");
        System.out.println("═".repeat(75));

        String[] questions = {
            "年假有多少天？",
            "病假工资怎么算？",
            "加班可以调休吗？调休比例是多少？"
        };

        for (String question : questions) {
            System.out.println("\n─".repeat(75));
            System.out.println("问题: " + question);
            System.out.println("─".repeat(75));

            // 步骤 4: 问题向量化
            Embedding qEmb = embeddingModel.embed(question).content();

            // 步骤 5: 相似度检索
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(qEmb)
                    .maxResults(2)
                    .build();
            EmbeddingSearchResult<TextSegment> result = vectorStore.search(req);

            // 步骤 6: 提取上下文
            StringBuilder ctx = new StringBuilder();
            for (EmbeddingMatch<TextSegment> m : result.matches()) {
                ctx.append(m.embedded().text()).append("\n\n");
            }

            // 步骤 7: 组装 Prompt & 生成
            String prompt = String.format("""
                根据以下资料回答问题，简洁回答：

                资料：
                %s

                问题：%s
                """, ctx.toString(), question);

            String answer = chatModel.generate(prompt);

            System.out.println("\n回答: " + answer);
        }
    }
}
