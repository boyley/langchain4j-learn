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
 * 定时执行知识同步:
 * - 增量同步: 每小时执行，只同步变化的文档
 * - 全量同步: 每天凌晨执行 (可选)
 *
 * 配置:
 * knowledge.sync.incremental.enabled=true
 * knowledge.sync.incremental.cron=0 0 * * * *
 * knowledge.sync.full.enabled=false
 * knowledge.sync.full.cron=0 0 3 * * *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final KnowledgePipeline pipeline;
    private final List<KnowledgeSource> sources;

    @Value("${knowledge.sync.incremental.enabled:true}")
    private boolean incrementalEnabled;

    @Value("${knowledge.sync.full.enabled:false}")
    private boolean fullEnabled;

    /**
     * 增量同步定时任务
     *
     * 默认每小时执行一次
     */
    @Scheduled(cron = "${knowledge.sync.incremental.cron:0 0 * * * *}")
    public void incrementalSync() {
        if (!incrementalEnabled) {
            log.debug("增量同步已禁用");
            return;
        }

        log.info("====== 开始增量同步任务 ======");

        for (KnowledgeSource source : sources) {
            try {
                if (!source.isAvailable()) {
                    log.warn("知识源 {} 不可用，跳过", source.getName());
                    continue;
                }

                KnowledgePipeline.SyncResult result = pipeline.sync(source, false);
                log.info("[{}] 增量同步完成: {} 篇文档, {} 个片段",
                        source.getName(), result.docCount(), result.segmentCount());

            } catch (Exception e) {
                log.error("[{}] 增量同步失败", source.getName(), e);
                // 发送告警通知 (可以集成钉钉/企业微信/邮件等)
                // alertService.sendAlert("知识同步失败", source.getName(), e.getMessage());
            }
        }

        log.info("====== 增量同步任务完成 ======");
    }

    /**
     * 全量同步定时任务
     *
     * 默认每天凌晨3点执行 (用于修复数据)
     */
    @Scheduled(cron = "${knowledge.sync.full.cron:0 0 3 * * *}")
    public void fullSync() {
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
