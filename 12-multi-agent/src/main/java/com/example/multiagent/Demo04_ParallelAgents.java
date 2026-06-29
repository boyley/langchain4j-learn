package com.example.multiagent;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo04: 并行 Agent
 * 多个 Agent 同时工作，然后汇总结果
 *
 * 场景：代码审查 - 同时从多个角度分析
 */
public class Demo04_ParallelAgents {

    // ========== 定义多个审查视角的 Agent ==========

    interface SecurityReviewer {
        @SystemMessage("""
                你是安全专家。分析代码的安全问题：
                - SQL注入、XSS、命令注入等
                - 敏感数据处理
                - 认证授权问题
                输出：列出发现的问题，无问题则说明"未发现安全问题"
                """)
        @UserMessage("审查以下代码的安全性：\n```\n{{code}}\n```")
        String review(@V("code") String code);
    }

    interface PerformanceReviewer {
        @SystemMessage("""
                你是性能专家。分析代码的性能问题：
                - 时间复杂度
                - 内存使用
                - 可能的性能瓶颈
                输出：列出发现的问题和优化建议
                """)
        @UserMessage("审查以下代码的性能：\n```\n{{code}}\n```")
        String review(@V("code") String code);
    }

    interface StyleReviewer {
        @SystemMessage("""
                你是代码规范专家。检查代码风格：
                - 命名规范
                - 代码结构
                - 可读性
                - 最佳实践
                输出：列出改进建议
                """)
        @UserMessage("审查以下代码的风格：\n```\n{{code}}\n```")
        String review(@V("code") String code);
    }

    interface Summarizer {
        @SystemMessage("""
                你是技术负责人。
                汇总多位专家的审查意见，生成一份简洁的审查报告。
                格式：
                ## 总体评价
                ## 关键问题
                ## 优化建议
                """)
        @UserMessage("汇总以下审查意见：\n{{reviews}}")
        String summarize(@V("reviews") String reviews);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Demo04: 并行 Agent（多角度代码审查）");
        System.out.println("=".repeat(60));

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 创建 Agent
        SecurityReviewer security = AiServices.create(SecurityReviewer.class, model);
        PerformanceReviewer performance = AiServices.create(PerformanceReviewer.class, model);
        StyleReviewer style = AiServices.create(StyleReviewer.class, model);
        Summarizer summarizer = AiServices.create(Summarizer.class, model);

        // 待审查的代码
        String code = """
                public class UserService {
                    public User findUser(String username) {
                        String sql = "SELECT * FROM users WHERE name = '" + username + "'";
                        Connection conn = DriverManager.getConnection(DB_URL);
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery(sql);
                        if (rs.next()) {
                            User user = new User();
                            user.setId(rs.getInt("id"));
                            user.setName(rs.getString("name"));
                            user.setPassword(rs.getString("password"));
                            return user;
                        }
                        return null;
                    }
                }
                """;

        System.out.println("\n待审查代码：");
        System.out.println(code);

        // ========== 并行审查 ==========
        System.out.println("-".repeat(60));
        System.out.println("开始并行审查（3个专家同时工作）...");
        System.out.println("-".repeat(60));

        ExecutorService executor = Executors.newFixedThreadPool(3);

        CompletableFuture<String> securityFuture = CompletableFuture.supplyAsync(
                () -> security.review(code), executor);

        CompletableFuture<String> performanceFuture = CompletableFuture.supplyAsync(
                () -> performance.review(code), executor);

        CompletableFuture<String> styleFuture = CompletableFuture.supplyAsync(
                () -> style.review(code), executor);

        // 等待所有审查完成
        String securityResult = securityFuture.get();
        String performanceResult = performanceFuture.get();
        String styleResult = styleFuture.get();

        executor.shutdown();

        // 输出各专家意见
        System.out.println("\n🔒【安全专家】");
        System.out.println(securityResult);

        System.out.println("\n⚡【性能专家】");
        System.out.println(performanceResult);

        System.out.println("\n📐【规范专家】");
        System.out.println(styleResult);

        // ========== 汇总 ==========
        System.out.println("\n" + "-".repeat(60));
        System.out.println("📋【汇总报告】");
        System.out.println("-".repeat(60));

        String allReviews = String.format("""
                ## 安全审查
                %s

                ## 性能审查
                %s

                ## 风格审查
                %s
                """, securityResult, performanceResult, styleResult);

        String summary = summarizer.summarize(allReviews);
        System.out.println(summary);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("并行审查完成！");
        System.out.println("=".repeat(60));
    }
}
