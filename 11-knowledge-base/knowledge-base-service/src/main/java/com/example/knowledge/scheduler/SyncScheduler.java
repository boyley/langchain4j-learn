package com.example.knowledge.scheduler;

import com.example.knowledge.pipeline.KnowledgePipeline;
import com.example.knowledge.source.KnowledgeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识同步定时任务
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【功能概述】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 自动定时执行知识同步:
 *
 *   增量同步 (默认每小时)              全量同步 (默认每天凌晨3点)
 *         │                                    │
 *         ▼                                    ▼
 *   只同步变化的文档                      重新同步所有文档
 *         │                                    │
 *         ▼                                    ▼
 *   快速、节省资源                       完整、可修复数据
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   sync:
 *     incremental:
 *       enabled: true                  # 是否启用增量同步
 *       cron: "0 0 * * * *"           # Cron 表达式 (每小时)
 *     full:
 *       enabled: false                 # 是否启用全量同步
 *       cron: "0 0 3 * * *"           # Cron 表达式 (每天3点)
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【Cron 表达式说明】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Spring Cron 格式: 秒 分 时 日 月 周
 *
 * 常用示例:
 * - "0 0 * * * *"     每小时整点执行
 * - "0 0/30 * * * *"  每30分钟执行
 * - "0 0 9 * * *"     每天早上9点执行
 * - "0 0 3 * * *"     每天凌晨3点执行
 * - "0 0 9 * * MON"   每周一早上9点执行
 * - "0 0 0 1 * *"     每月1号零点执行
 *
 * 特殊字符:
 * - *: 任意值
 * - ?: 不指定 (只用于日和周)
 * - -: 范围 (如 9-17 表示9点到17点)
 * - ,: 列表 (如 MON,WED,FRI)
 * - /: 间隔 (如 0/15 表示每15分钟)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【启用定时任务】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 要使 @Scheduled 生效，需要在主类或配置类添加:
 *
 * ```java
 * @SpringBootApplication
 * @EnableScheduling  // 启用定时任务
 * public class KnowledgeBaseApplication { ... }
 * ```
 *
 * 或在配置类中:
 *
 * ```java
 * @Configuration
 * @EnableScheduling
 * public class SchedulingConfig { ... }
 * ```
 */
@Slf4j
/*
 * @Component - Spring 组件注解
 *
 * 作用:
 * 1. 将此类注册为 Spring Bean
 * 2. 可被 @Scheduled 定时任务框架识别
 *
 * 定时任务类通常使用 @Component 而非 @Service:
 * - 它不是业务逻辑层 (Service)
 * - 它是基础设施层 (Infrastructure)
 */
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    // ═══════════════════════════════════════════════════════════════════════════
    // 依赖注入
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 知识处理管道
     *
     * 执行实际的同步逻辑:
     * - 获取文档
     * - 分割内容
     * - 向量化
     * - 存储
     */
    private final KnowledgePipeline pipeline;

    /**
     * 所有知识源列表
     *
     * Spring 自动注入所有 KnowledgeSource 实现
     * 包括: DatabaseKnowledgeSource, FileSystemKnowledgeSource 等
     *
     * 定时任务会遍历所有知识源进行同步
     */
    private final List<KnowledgeSource> sources;

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置参数
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 是否启用增量同步
     *
     * 配置: knowledge.sync.incremental.enabled
     * 默认: true
     *
     * 设为 false 可临时禁用增量同步
     * 例如: 系统维护期间
     */
    @Value("${knowledge.sync.incremental.enabled:true}")
    private boolean incrementalEnabled;

    /**
     * 是否启用全量同步
     *
     * 配置: knowledge.sync.full.enabled
     * 默认: false
     *
     * 全量同步会消耗更多资源，默认关闭
     * 场景:
     * - 数据修复
     * - 向量模型升级后需重建索引
     */
    @Value("${knowledge.sync.full.enabled:false}")
    private boolean fullEnabled;

    // ═══════════════════════════════════════════════════════════════════════════
    // 定时任务
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 增量同步定时任务
     *
     * 默认每小时执行一次
     *
     * @Scheduled 注解说明:
     * - cron: Cron 表达式，定义执行时间
     * - fixedRate: 固定频率 (毫秒)，上一次开始后间隔
     * - fixedDelay: 固定延迟 (毫秒)，上一次结束后间隔
     * - initialDelay: 首次执行延迟 (毫秒)
     *
     * 参数支持 SpEL 表达式:
     * - ${property}: 从配置文件读取
     * - #{expression}: SpEL 表达式
     *
     * 示例:
     * ```java
     * @Scheduled(fixedRate = 60000)  // 每60秒执行
     * @Scheduled(fixedDelay = 60000) // 上一次结束后60秒执行
     * @Scheduled(cron = "0 0 * * * *") // 每小时整点执行
     * @Scheduled(cron = "${my.cron}") // 从配置读取
     * ```
     *
     * 注意事项:
     * - 方法必须是 public void
     * - 方法不能有参数
     * - 默认是单线程执行，上一次未完成不会开始下一次
     */
    @Scheduled(cron = "${knowledge.sync.incremental.cron:0 0 * * * *}")
    public void incrementalSync() {
        // 检查是否启用
        if (!incrementalEnabled) {
            log.debug("增量同步已禁用");
            return;
        }

        log.info("====== 开始增量同步任务 ======");

        // 遍历所有知识源进行同步
        for (KnowledgeSource source : sources) {
            try {
                // 检查知识源可用性
                if (!source.isAvailable()) {
                    log.warn("知识源 {} 不可用，跳过", source.getName());
                    continue;
                }

                // 执行增量同步 (fullSync = false)
                KnowledgePipeline.SyncResult result = pipeline.sync(source, false);
                log.info("[{}] 增量同步完成: {} 篇文档, {} 个片段",
                        source.getName(), result.docCount(), result.segmentCount());

            } catch (Exception e) {
                // 单个知识源失败不影响其他知识源
                log.error("[{}] 增量同步失败", source.getName(), e);

                // ─────────────────────────────────────────────────────────────
                // 告警通知 (可扩展)
                // ─────────────────────────────────────────────────────────────
                // 生产环境建议集成告警系统:
                // - 钉钉机器人
                // - 企业微信机器人
                // - 邮件通知
                // - Prometheus + Alertmanager
                //
                // 示例:
                // alertService.sendAlert("知识同步失败", source.getName(), e.getMessage());
            }
        }

        log.info("====== 增量同步任务完成 ======");
    }

    /**
     * 全量同步定时任务
     *
     * 默认每天凌晨3点执行
     *
     * 全量同步场景:
     * - 修复数据不一致
     * - 向量模型升级后重建索引
     * - 清理孤立向量数据
     *
     * 注意:
     * - 全量同步会删除旧向量，重新生成
     * - 数据量大时耗时较长
     * - 建议在低峰期执行 (凌晨)
     */
    @Scheduled(cron = "${knowledge.sync.full.cron:0 0 3 * * *}")
    public void fullSync() {
        // 检查是否启用
        if (!fullEnabled) {
            log.debug("全量同步已禁用");
            return;
        }

        log.info("====== 开始全量同步任务 ======");

        for (KnowledgeSource source : sources) {
            try {
                if (!source.isAvailable()) {
                    log.warn("知识源 {} 不可用，跳过", source.getName());
                    continue;
                }

                // 执行全量同步 (fullSync = true)
                KnowledgePipeline.SyncResult result = pipeline.sync(source, true);
                log.info("[{}] 全量同步完成: {} 篇文档, {} 个片段",
                        source.getName(), result.docCount(), result.segmentCount());

            } catch (Exception e) {
                log.error("[{}] 全量同步失败", source.getName(), e);
            }
        }

        log.info("====== 全量同步任务完成 ======");
    }
}
