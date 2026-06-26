package com.example.langchain4j.rag.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 文档智能分词/分割示例
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 什么是文档分割？为什么需要？
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 问题：
 *   - LLM 有上下文长度限制（如 4K、8K、128K tokens）
 *   - 太长的文档向量化后语义会"稀释"，搜索效果差
 *   - 检索时需要定位到具体段落，而非整篇文档
 *
 * 解决：
 *   将长文档切分成小块（Chunks），每块独立向量化和检索
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 分割策略对比
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌─────────────────────┬──────────────────────────────────────────────────┐
 * │ 分割器              │ 说明                                             │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ recursive           │ 递归分割（推荐），按段落→句子→词语逐级细分        │
 * │ byParagraph         │ 按段落分割（\n\n）                                │
 * │ bySentence          │ 按句子分割（。！？等）                            │
 * │ byLine              │ 按行分割（\n）                                    │
 * │ byWord              │ 按词分割                                          │
 * │ byCharacter         │ 按字符数分割                                      │
 * │ byRegex             │ 按正则表达式分割                                  │
 * └─────────────────────┴──────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 关键参数
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌─────────────────────┬──────────────────────────────────────────────────┐
 * │ 参数                │ 说明                                             │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ maxSegmentSize      │ 每个片段的最大字符数                              │
 * │                     │ - 太小：语义不完整，检索效果差                    │
 * │                     │ - 太大：检索不精确，浪费 token                    │
 * │                     │ - 推荐：200-1000 字符                             │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ maxOverlap          │ 相邻片段的重叠字符数                              │
 * │                     │ - 作用：避免在句子中间截断，保持语义连贯           │
 * │                     │ - 太小：可能截断重要信息                          │
 * │                     │ - 太大：片段间重复内容多                          │
 * │                     │ - 推荐：maxSegmentSize 的 10-20%                  │
 * └─────────────────────┴──────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 场景选择建议
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌─────────────────────┬──────────────────────────────────────────────────┐
 * │ 场景                │ 推荐分割器                                        │
 * ├─────────────────────┼──────────────────────────────────────────────────┤
 * │ 通用文档            │ recursive (最智能，自动选择最佳分割点)            │
 * │ 文章/博客           │ byParagraph (按段落保持完整性)                    │
 * │ 法律/合同           │ bySentence (每句话可能很重要)                     │
 * │ 代码文件            │ byLine 或自定义 (按函数/类分割)                   │
 * │ 日志文件            │ byLine (每行一条日志)                             │
 * │ CSV/表格            │ byLine 或 byRegex                                 │
 * │ 对话记录            │ byRegex (按对话轮次分割)                          │
 * └─────────────────────┴──────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class DocumentSplitterDemo {

    public static void main(String[] args) {
        System.out.println("═".repeat(60));
        System.out.println("       文档智能分词/分割示例");
        System.out.println("═".repeat(60));

        // 准备测试文档
        String documentContent = """
            LangChain4j 是一个 Java 版本的 LangChain 框架，用于构建 AI 应用。
            它支持多种 LLM 提供商，包括 OpenAI、Ollama、阿里云通义千问等。

            LangChain4j 的主要特性包括：
            1. AI Services - 声明式 AI 服务，使用接口定义 AI 能力
            2. Tools - 工具调用，让 AI 可以执行函数
            3. Memory - 会话记忆，支持多轮对话
            4. RAG - 检索增强生成，结合知识库回答问题
            5. Streaming - 流式响应，实时输出

            LangChain4j 要求 Java 17 或更高版本。
            可以通过 Maven 或 Gradle 引入依赖。
            官方文档地址: https://docs.langchain4j.dev/

            使用 AiServices 可以快速创建 AI 助手。
            定义接口，然后使用 AiServices.create() 方法即可。
            支持 @SystemMessage 定义系统提示词。
            支持 @UserMessage 定义用户消息模板。
            """;

        Document document = Document.from(documentContent);

        System.out.println("\n原始文档长度: " + documentContent.length() + " 字符\n");

        // ═══════════════════════════════════════════════════════════════
        // 示例 1: recursive 递归分割（推荐）
        // ═══════════════════════════════════════════════════════════════
        System.out.println("【示例 1】recursive 递归分割（推荐）");
        // 注意: repeat(60) 仅用于打印 60 个分隔符字符，是显示格式，非框架参数
        System.out.println("─".repeat(60));
        System.out.println("""
            递归分割策略：
            1. 优先按段落（\\n\\n）分割
            2. 段落太长则按句子（。！？.!?）分割
            3. 句子太长则按逗号/空格分割
            4. 保证每个片段不超过 maxSegmentSize
            """);

        /**
         * DocumentSplitters.recursive(maxSegmentSize, maxOverlap) 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxSegmentSize  │ 每个片段最大字符数，超过会继续分割               │
         * │                 │ 值越小片段越多，语义可能不完整                   │
         * │                 │ 值越大片段越少，检索可能不精确                   │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxOverlap      │ 相邻片段重叠字符数                               │
         * │                 │ 作用：防止句子被截断，保持上下文连贯             │
         * │                 │ 推荐：maxSegmentSize 的 10-20%                   │
         * └─────────────────┴──────────────────────────────────────────────────┘
         */
        DocumentSplitter recursiveSplitter = DocumentSplitters.recursive(
                200,  // maxSegmentSize: 每个片段最多 200 字符
                20    // maxOverlap: 相邻片段重叠 20 字符
        );

        List<TextSegment> recursiveSegments = recursiveSplitter.split(document);
        printSegments("recursive", recursiveSegments);

        // ═══════════════════════════════════════════════════════════════
        // 示例 2: byParagraph 按段落分割
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 2】byParagraph 按段落分割");
        System.out.println("─".repeat(60));
        System.out.println("按 \\n\\n（空行）分割，适合文章、博客等结构化文档\n");

        /**
         * DocumentByParagraphSplitter 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxSegmentSize  │ 段落最大字符数，超过会拆分                       │
         * │ maxOverlap      │ 相邻段落重叠字符数                               │
         * └─────────────────┴──────────────────────────────────────────────────┘
         */
        DocumentSplitter paragraphSplitter = new DocumentByParagraphSplitter(
                300,  // maxSegmentSize: 每段最多 300 字符
                30    // maxOverlap: 重叠 30 字符
        );

        List<TextSegment> paragraphSegments = paragraphSplitter.split(document);
        printSegments("byParagraph", paragraphSegments);

        // ═══════════════════════════════════════════════════════════════
        // 示例 3: bySentence 按句子分割
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 3】bySentence 按句子分割");
        System.out.println("─".repeat(60));
        System.out.println("按句子结束符（。！？.!?）分割，适合法律、合同等精确文档\n");

        /**
         * DocumentBySentenceSplitter 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxSegmentSize  │ 合并多个句子直到达到此长度                       │
         * │ maxOverlap      │ 相邻片段重叠字符数                               │
         * └─────────────────┴──────────────────────────────────────────────────┘
         *
         * 注意：如果单个句子超过 maxSegmentSize，会按字符截断
         */
        DocumentSplitter sentenceSplitter = new DocumentBySentenceSplitter(
                150,  // maxSegmentSize: 合并句子直到 150 字符
                15    // maxOverlap: 重叠 15 字符
        );

        List<TextSegment> sentenceSegments = sentenceSplitter.split(document);
        printSegments("bySentence", sentenceSegments);

        // ═══════════════════════════════════════════════════════════════
        // 示例 4: byLine 按行分割
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 4】byLine 按行分割");
        System.out.println("─".repeat(60));
        System.out.println("按换行符（\\n）分割，适合日志、代码、CSV 等\n");

        /**
         * DocumentByLineSplitter 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxSegmentSize  │ 合并多行直到达到此长度                           │
         * │ maxOverlap      │ 相邻片段重叠字符数                               │
         * └─────────────────┴──────────────────────────────────────────────────┘
         */
        DocumentSplitter lineSplitter = new DocumentByLineSplitter(
                200,  // maxSegmentSize
                20    // maxOverlap
        );

        List<TextSegment> lineSegments = lineSplitter.split(document);
        printSegments("byLine", lineSegments);

        // ═══════════════════════════════════════════════════════════════
        // 示例 5: byCharacter 按字符分割
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 5】byCharacter 按字符分割");
        System.out.println("─".repeat(60));
        System.out.println("简单按字符数切分，不考虑语义边界（不推荐单独使用）\n");

        /**
         * DocumentByCharacterSplitter 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ maxSegmentSize  │ 每个片段的字符数                                 │
         * │ maxOverlap      │ 相邻片段重叠字符数                               │
         * └─────────────────┴──────────────────────────────────────────────────┘
         *
         * 缺点：可能在词语/句子中间截断，语义不完整
         * 适用：作为其他分割器的后备方案
         */
        DocumentSplitter charSplitter = new DocumentByCharacterSplitter(
                100,  // maxSegmentSize: 每 100 字符一段
                10    // maxOverlap: 重叠 10 字符
        );

        List<TextSegment> charSegments = charSplitter.split(document);
        printSegments("byCharacter", charSegments);

        // ═══════════════════════════════════════════════════════════════
        // 示例 6: byRegex 按正则表达式分割
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n【示例 6】byRegex 按正则表达式分割");
        System.out.println("─".repeat(60));
        System.out.println("自定义分割规则，适合特殊格式文档\n");

        /**
         * DocumentByRegexSplitter 参数说明：
         *
         * ┌─────────────────┬──────────────────────────────────────────────────┐
         * │ 参数            │ 说明                                             │
         * ├─────────────────┼──────────────────────────────────────────────────┤
         * │ regex           │ 分割正则表达式                                   │
         * │ keepDelimiter   │ 保留分隔符的位置: "start"/"end"/""               │
         * │ maxSegmentSize  │ 每个片段最大字符数                               │
         * │ maxOverlap      │ 相邻片段重叠字符数                               │
         * └─────────────────┴──────────────────────────────────────────────────┘
         *
         * keepDelimiter 说明：
         * - "start": 分隔符放在下一段开头
         * - "end": 分隔符放在当前段结尾
         * - "": 不保留分隔符
         *
         * 常用正则示例：
         * - "\\n\\n"     : 按空行分割（同 byParagraph）
         * - "\\n"        : 按换行分割（同 byLine）
         * - "[。！？]"   : 按中文句子结束符分割
         * - "\\d+\\."    : 按编号列表分割（1. 2. 3.）
         * - "#{1,6}\\s"  : 按 Markdown 标题分割
         */
        DocumentSplitter regexSplitter = new DocumentByRegexSplitter(
                "\\d+\\.",  // regex: 按数字编号分割 (1. 2. 3. 等)
                "start",    // keepDelimiter: 编号保留在段落开头
                300,        // maxSegmentSize
                30          // maxOverlap
        );

        List<TextSegment> regexSegments = regexSplitter.split(document);
        printSegments("byRegex (按编号)", regexSegments);

        // ═══════════════════════════════════════════════════════════════
        // 总结
        // ═══════════════════════════════════════════════════════════════
        System.out.println("\n" + "═".repeat(60));
        System.out.println("【总结】如何选择分割策略？");
        System.out.println("═".repeat(60));
        System.out.println("""

            1. 通用场景首选 recursive
               - 自动选择最佳分割点
               - 保持语义完整性
               - 参数简单，效果最好

            2. 参数调优建议：
               ┌───────────────────────────────────────────────────────┐
               │ 文档类型     │ maxSegmentSize │ maxOverlap            │
               ├───────────────────────────────────────────────────────┤
               │ 短文/FAQ     │ 100-200        │ 10-20                 │
               │ 普通文章     │ 300-500        │ 30-50                 │
               │ 技术文档     │ 500-1000       │ 50-100                │
               │ 长篇内容     │ 1000-2000      │ 100-200               │
               └───────────────────────────────────────────────────────┘

            3. 分割效果验证：
               - 检查片段是否语义完整
               - 检查重要信息是否被截断
               - 通过实际检索测试效果

            4. 高级技巧：
               - 为不同类型文档使用不同分割器
               - 代码文件可按函数/类分割
               - Markdown 可按标题层级分割
            """);
    }

    /**
     * 打印分割结果
     *
     * 注意：此方法中的数字都是显示格式参数，非框架参数：
     * - 60: 预览文本最大显示字符数（超过截断显示）
     * - 3: 最多显示前 3 个片段（避免输出过多）
     */
    private static void printSegments(String splitterName, List<TextSegment> segments) {
        System.out.println("分割结果: " + segments.size() + " 个片段\n");

        // Math.min(3, size): 最多显示前 3 个片段（仅用于演示，非框架限制）
        for (int i = 0; i < Math.min(3, segments.size()); i++) {
            String text = segments.get(i).text();

            // 60: 预览文本最大字符数（仅用于控制台显示，非框架参数）
            // 超过 60 字符的内容截断并添加 "..."
            String preview = text.length() > 60
                    ? text.substring(0, 60).replace("\n", "↵") + "..."
                    : text.replace("\n", "↵");

            System.out.printf("  片段 %d (%d 字符): %s%n", i + 1, text.length(), preview);
        }

        if (segments.size() > 3) {
            System.out.println("  ... 还有 " + (segments.size() - 3) + " 个片段");
        }
    }
}
