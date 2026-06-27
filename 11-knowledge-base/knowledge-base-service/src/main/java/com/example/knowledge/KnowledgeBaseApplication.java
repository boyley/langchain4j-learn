package com.example.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业级知识库服务
 *
 * 核心功能:
 * 1. 多知识源接入 (MySQL/文件/API)
 * 2. 多向量存储支持 (Redis/Milvus/PostgreSQL/Elasticsearch)
 * 3. 增量同步 (定时任务 + 手动触发)
 * 4. 向量检索 API
 *
 * 架构特点:
 * - 接口抽象，可插拔实现
 * - 配置驱动，无需改代码切换存储
 * - 生产就绪 (监控/健康检查)
 *
 * @author LangChain4j 学习项目
 */
@SpringBootApplication
@EnableScheduling
public class KnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
    }
}
