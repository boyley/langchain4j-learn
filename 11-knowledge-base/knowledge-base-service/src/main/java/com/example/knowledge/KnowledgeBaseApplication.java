package com.example.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业级知识库服务
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【核心功能】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 1. 多知识源接入
 *    - MySQL 数据库
 *    - 文件系统 (PDF/Word/TXT)
 *    - Confluence/Notion API (可扩展)
 *
 * 2. 多向量存储支持
 *    - Redis (RediSearch)
 *    - PostgreSQL (pgvector)
 *    - Milvus (大规模场景)
 *    - Elasticsearch (可扩展)
 *
 * 3. 增量同步
 *    - 定时任务自动同步
 *    - API 手动触发
 *    - 基于时间戳的增量机制
 *
 * 4. 向量检索 API
 *    - 语义搜索
 *    - 分类过滤
 *    - RAG 上下文检索
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【架构特点】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │                            REST API                                    │
 *   │              /api/knowledge/search   /api/knowledge/sync              │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *                                    │
 *                                    ▼
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │                          Service Layer                                 │
 *   │    KnowledgeSearchService    KnowledgePipeline    ContentService       │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *                                    │
 *             ┌──────────────────────┼──────────────────────┐
 *             ▼                      ▼                      ▼
 *   ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
 *   │   KnowledgeSource │  │  VectorStoreService│  │    Repository     │
 *   │   (接口)          │  │  (接口)            │  │    (JPA)          │
 *   └───────────────────┘  └───────────────────┘  └───────────────────┘
 *             │                      │                      │
 *    ┌────────┼────────┐    ┌────────┼────────┐            ▼
 *    ▼        ▼        ▼    ▼        ▼        ▼      ┌───────────┐
 *  Database  File  Confluence Redis pgvector Milvus │   MySQL   │
 *                                                   └───────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置驱动】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 通过配置切换不同实现，无需修改代码:
 *
 * ```yaml
 * knowledge:
 *   # 向量存储选择
 *   vector-store:
 *     type: redis    # 或 pgvector, milvus
 *
 *   # 知识源开关
 *   source:
 *     database:
 *       enabled: true
 *     file:
 *       enabled: true
 *       root-path: /data/docs
 *
 *   # 同步配置
 *   sync:
 *     incremental:
 *       enabled: true
 *       cron: "0 0 * * * *"    # 每小时
 * ```
 *
 * @author LangChain4j 学习项目
 */
/*
 * @SpringBootApplication - Spring Boot 核心注解
 *
 * 这是一个组合注解，等价于:
 *
 * @Configuration
 *   - 标记为配置类，可以定义 @Bean 方法
 *
 * @EnableAutoConfiguration
 *   - 启用 Spring Boot 自动配置
 *   - 根据 classpath 中的依赖自动配置 Bean
 *   - 例如: 有 spring-data-jpa 依赖就自动配置 JPA
 *
 * @ComponentScan
 *   - 自动扫描当前包及子包下的组件
 *   - 包括 @Component, @Service, @Repository, @Controller 等
 *
 * 扫描范围:
 * - com.example.knowledge 及其所有子包
 * - 例如: com.example.knowledge.service.*
 *         com.example.knowledge.controller.*
 *         com.example.knowledge.repository.*
 */
@SpringBootApplication
/*
 * @EnableScheduling - 启用定时任务
 *
 * 作用:
 * 1. 激活 Spring 的定时任务调度器
 * 2. 使 @Scheduled 注解生效
 * 3. 创建 TaskScheduler Bean
 *
 * 不加此注解，@Scheduled 方法不会执行!
 *
 * 原理:
 * - 创建一个后台线程池 (默认单线程)
 * - 根据 @Scheduled 的配置定时执行方法
 *
 * 配置线程池大小:
 * ```yaml
 * spring:
 *   task:
 *     scheduling:
 *       pool:
 *         size: 5    # 定时任务线程池大小
 * ```
 */
@EnableScheduling
public class KnowledgeBaseApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     *
     * SpringApplication.run() 做了什么:
     * 1. 创建 ApplicationContext (IoC 容器)
     * 2. 加载所有配置类和 Bean
     * 3. 执行自动配置
     * 4. 启动内嵌 Web 服务器 (Tomcat)
     * 5. 执行 ApplicationRunner 和 CommandLineRunner
     *
     * 启动方式:
     * 1. IDE 直接运行 main 方法
     * 2. mvn spring-boot:run
     * 3. java -jar app.jar
     * 4. java -jar app.jar --server.port=9090
     */
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
    }
}
