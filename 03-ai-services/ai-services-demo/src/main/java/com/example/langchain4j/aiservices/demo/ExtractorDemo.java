package com.example.langchain4j.aiservices.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 信息提取示例
 *
 * 展示如何使用 AI Services 从非结构化文本中提取结构化信息
 * AI 会自动解析文本并填充 Java 对象的字段
 *
 * 应用场景：
 * - 简历解析：提取姓名、学历、工作经历
 * - 订单处理：提取商品、数量、地址
 * - 客服对话：提取用户意图、问题类型
 * - 新闻分析：提取人物、事件、时间、地点
 */
public class ExtractorDemo {

    // ==================== 定义提取的数据结构 ====================

    /**
     * 人员信息
     */
    record Person(
        String name,        // 姓名
        Integer age,        // 年龄
        String occupation,  // 职业
        String company      // 公司
    ) {}

    /**
     * 订单信息
     */
    record Order(
        String customerName,    // 客户姓名
        String product,         // 商品名称
        Integer quantity,       // 数量
        String shippingAddress, // 收货地址
        String phoneNumber      // 联系电话
    ) {}

    /**
     * 会议信息
     */
    record Meeting(
        String topic,           // 会议主题
        String date,            // 日期
        String time,            // 时间
        String location,        // 地点
        List<String> attendees  // 参会人员
    ) {}

    /**
     * 情感分析结果
     */
    enum Sentiment {
        POSITIVE,   // 正面
        NEGATIVE,   // 负面
        NEUTRAL     // 中性
    }

    /**
     * 客户反馈分析
     */
    record FeedbackAnalysis(
        Sentiment sentiment,        // 情感倾向
        String mainIssue,           // 主要问题
        List<String> keywords,      // 关键词
        Integer urgencyLevel,       // 紧急程度 1-5
        String suggestedAction      // 建议操作
    ) {}

    // ==================== 定义提取接口 ====================

    /**
     * 数据提取接口
     *
     * 核心注解说明：
     * - @UserMessage: 定义发送给 AI 的提示词模板，{{变量名}} 会被实际参数值替换
     * - @V("变量名"): 将方法参数绑定到 @UserMessage 中的 {{变量名}}
     * - 返回值类型: AI 会自动将回复解析为指定的 Java 类型（Record、List、Enum 等）
     */
    interface DataExtractor {

        /**
         * @UserMessage - 定义提示词模板
         *   作用：告诉 AI 要执行什么任务
         *   {{text}} 是占位符，运行时会被实际参数替换
         *
         * @V("text") - 变量绑定注解
         *   作用：将方法参数 text 绑定到模板中的 {{text}}
         *   必须与模板中的变量名一致
         *
         * 返回值 Person - 结构化输出
         *   作用：AI 会将回复自动解析为 Person 对象
         *   字段名（name, age 等）指导 AI 提取哪些信息
         */
        @UserMessage("从以下文本中提取人员信息：{{text}}")
        Person extractPerson(@V("text") String text);

        @UserMessage("从以下订单描述中提取订单信息：{{text}}")
        Order extractOrder(@V("text") String text);

        @UserMessage("从以下文本中提取会议信息：{{text}}")
        Meeting extractMeeting(@V("text") String text);

        @UserMessage("分析以下客户反馈，提取情感、问题和建议：{{text}}")
        FeedbackAnalysis analyzeFeedback(@V("text") String text);

        /**
         * 返回 List<String> - AI 会返回字符串列表
         */
        @UserMessage("从以下文本中提取所有提到的人名：{{text}}")
        List<String> extractNames(@V("text") String text);

        /**
         * 返回 Enum - AI 会返回枚举值之一
         * AI 只能返回 POSITIVE、NEGATIVE、NEUTRAL 三个值
         */
        @UserMessage("判断以下评论的情感倾向：{{text}}")
        Sentiment analyzeSentiment(@V("text") String text);
    }

    public static void main(String[] args) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        DataExtractor extractor = AiServices.create(DataExtractor.class, model);

        System.out.println("=== AI 信息提取示例 ===\n");

        // ========== 示例 1: 提取人员信息 ==========
        System.out.println("【示例 1: 提取人员信息】");
        System.out.println("-".repeat(50));

        String personText = "张三是一名资深Java工程师，今年32岁，目前在阿里巴巴工作，" +
                           "有8年的开发经验，擅长分布式系统设计。";

        System.out.println("原文: " + personText);
        Person person = extractor.extractPerson(personText);
        System.out.println("提取结果:");
        System.out.println("  - 姓名: " + person.name());
        System.out.println("  - 年龄: " + person.age());
        System.out.println("  - 职业: " + person.occupation());
        System.out.println("  - 公司: " + person.company());

        // ========== 示例 2: 提取订单信息 ==========
        System.out.println("\n【示例 2: 提取订单信息】");
        System.out.println("-".repeat(50));

        String orderText = "您好，我是李明，想订购3台MacBook Pro笔记本电脑，" +
                          "请寄送到北京市海淀区中关村大街1号科技大厦5楼，我的电话是13812345678。";

        System.out.println("原文: " + orderText);
        Order order = extractor.extractOrder(orderText);
        System.out.println("提取结果:");
        System.out.println("  - 客户: " + order.customerName());
        System.out.println("  - 商品: " + order.product());
        System.out.println("  - 数量: " + order.quantity());
        System.out.println("  - 地址: " + order.shippingAddress());
        System.out.println("  - 电话: " + order.phoneNumber());

        // ========== 示例 3: 提取会议信息 ==========
        System.out.println("\n【示例 3: 提取会议信息】");
        System.out.println("-".repeat(50));

        String meetingText = "通知：定于下周三（12月25日）下午2点在3号会议室召开项目评审会议，" +
                            "讨论Q1产品规划，请张总、李经理、王工程师准时参加。";

        System.out.println("原文: " + meetingText);
        Meeting meeting = extractor.extractMeeting(meetingText);
        System.out.println("提取结果:");
        System.out.println("  - 主题: " + meeting.topic());
        System.out.println("  - 日期: " + meeting.date());
        System.out.println("  - 时间: " + meeting.time());
        System.out.println("  - 地点: " + meeting.location());
        System.out.println("  - 参会人: " + meeting.attendees());

        // ========== 示例 4: 客户反馈分析 ==========
        System.out.println("\n【示例 4: 客户反馈分析】");
        System.out.println("-".repeat(50));

        String feedbackText = "非常失望！我买的手机才用了3天就黑屏了，打客服电话一直占线，" +
                             "等了两个小时也没人接，这是什么服务态度？要求立即退款并赔偿！";

        System.out.println("原文: " + feedbackText);
        FeedbackAnalysis analysis = extractor.analyzeFeedback(feedbackText);
        System.out.println("分析结果:");
        System.out.println("  - 情感: " + analysis.sentiment());
        System.out.println("  - 主要问题: " + analysis.mainIssue());
        System.out.println("  - 关键词: " + analysis.keywords());
        System.out.println("  - 紧急程度: " + analysis.urgencyLevel() + "/5");
        System.out.println("  - 建议操作: " + analysis.suggestedAction());

        // ========== 示例 5: 提取人名列表 ==========
        System.out.println("\n【示例 5: 提取人名列表】");
        System.out.println("-".repeat(50));

        String newsText = "今日，马云、马化腾、李彦宏三位企业家共同出席了人工智能发展论坛，" +
                         "与会者还包括百度CTO王海峰、阿里达摩院院长张建锋等技术专家。";

        System.out.println("原文: " + newsText);
        List<String> names = extractor.extractNames(newsText);
        System.out.println("提取的人名: " + names);

        // ========== 示例 6: 情感分析 ==========
        System.out.println("\n【示例 6: 情感分析（返回枚举）】");
        System.out.println("-".repeat(50));

        String[] reviews = {
            "这个产品太棒了，质量超好，下次还会购买！",
            "一般般吧，没什么特别的，凑合能用。",
            "垃圾产品，千万别买，浪费钱！"
        };

        for (String review : reviews) {
            Sentiment sentiment = extractor.analyzeSentiment(review);
            System.out.println("评论: \"" + review + "\"");
            System.out.println("情感: " + sentiment + "\n");
        }

        // ========== 总结 ==========
        System.out.println("=".repeat(50));
        System.out.println("【总结】AI 信息提取的关键点：");
        System.out.println("1. 定义 Record/Class 描述要提取的数据结构");
        System.out.println("2. 字段名要有语义（如 customerName 而不是 n）");
        System.out.println("3. 使用 @UserMessage 描述提取任务");
        System.out.println("4. AI 自动将文本映射到结构化对象");
        System.out.println("5. 支持嵌套对象、List、Enum 等复杂类型");
    }
}
