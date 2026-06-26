package com.example.langchain4j.memory.demo;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化会话记忆示例
 *
 * 演示如何实现自定义 ChatMemoryStore 来持久化存储会话记录
 * 实际应用中可替换为 Redis、MySQL、MongoDB 等存储
 *
 * 关键接口：ChatMemoryStore
 * - getMessages(memoryId)  : 查询 (SELECT)
 * - updateMessages(...)    : 更新/插入 (UPDATE/INSERT)
 * - deleteMessages(...)    : 删除 (DELETE)
 */
public class PersistentMemoryDemo {

    interface PersistentAssistant {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    public static void main(String[] args) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== 持久化会话记忆示例 ===\n");

        // ========== 演示 1: 模拟内存存储 (开发测试用) ==========
        System.out.println("【演示 1: 模拟内存存储】");
        System.out.println("-".repeat(50));
        demoWithInMemoryStore(model);

        // ========== 演示 2: 模拟 Redis 存储 ==========
        System.out.println("\n【演示 2: 模拟 Redis 存储】");
        System.out.println("-".repeat(50));
        demoWithRedisStore(model);

        // ========== 演示 3: 模拟数据库存储 ==========
        System.out.println("\n【演示 3: 模拟数据库存储】");
        System.out.println("-".repeat(50));
        demoWithDatabaseStore(model);
    }

    // ==================== 演示 1: 内存存储 ====================
    private static void demoWithInMemoryStore(ChatLanguageModel model) {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();

        PersistentAssistant assistant = AiServices.builder(PersistentAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .chatMemoryStore(store)  // 注入自定义存储
                        .build())
                .build();

        String userId = "test_user";
        System.out.println("用户: 我叫测试用户，在学习 LangChain4j");
        String response = assistant.chat(userId, "我叫测试用户，在学习 LangChain4j");
        System.out.println("AI: " + response);

        System.out.println("\n[存储状态] 当前存储的消息数: " + store.getMessageCount(userId));
    }

    // ==================== 演示 2: Redis 存储 ====================
    private static void demoWithRedisStore(ChatLanguageModel model) {
        RedisChatMemoryStore store = new RedisChatMemoryStore();

        PersistentAssistant assistant = AiServices.builder(PersistentAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .chatMemoryStore(store)
                        .build())
                .build();

        String userId = "redis_user";
        System.out.println("用户: 我的数据会存到 Redis");
        String response = assistant.chat(userId, "我的数据会存到 Redis");
        System.out.println("AI: " + response);

        System.out.println("\n[Redis 操作日志]");
        store.printOperationLog();
    }

    // ==================== 演示 3: 数据库存储 ====================
    private static void demoWithDatabaseStore(ChatLanguageModel model) {
        DatabaseChatMemoryStore store = new DatabaseChatMemoryStore();

        PersistentAssistant assistant = AiServices.builder(PersistentAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .chatMemoryStore(store)
                        .build())
                .build();

        String userId = "db_user";
        System.out.println("用户: 我的对话会存到数据库");
        String response = assistant.chat(userId, "我的对话会存到数据库");
        System.out.println("AI: " + response);

        System.out.println("\n[SQL 操作日志]");
        store.printSqlLog();
    }

    // ================================================================
    // 自定义存储实现
    // ================================================================

    /**
     * 内存存储实现 (开发测试用)
     */
    static class InMemoryChatMemoryStore implements ChatMemoryStore {

        private final Map<Object, List<ChatMessage>> storage = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            // ============================================
            // 【查询操作 - SELECT】
            // 实际实现: return cache.get(key)
            // ============================================
            System.out.println("  [内存] SELECT: 从 Map 中查询 memoryId=" + memoryId);
            return storage.getOrDefault(memoryId, new ArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            // ============================================
            // 【更新/插入操作 - UPDATE/INSERT】
            // 实际实现: cache.put(key, value)
            // ============================================
            System.out.println("  [内存] UPDATE: 存储 " + messages.size() + " 条消息到 memoryId=" + memoryId);
            storage.put(memoryId, new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            // ============================================
            // 【删除操作 - DELETE】
            // 实际实现: cache.remove(key)
            // ============================================
            System.out.println("  [内存] DELETE: 删除 memoryId=" + memoryId);
            storage.remove(memoryId);
        }

        public int getMessageCount(Object memoryId) {
            return storage.getOrDefault(memoryId, new ArrayList<>()).size();
        }
    }

    /**
     * Redis 存储实现 (模拟)
     *
     * 实际实现需要:
     * - RedisTemplate 或 Jedis/Lettuce 客户端
     * - 消息序列化/反序列化 (JSON)
     * - 可选: 设置过期时间 (TTL)
     */
    static class RedisChatMemoryStore implements ChatMemoryStore {

        // 模拟 Redis 存储
        private final Map<String, String> redisSimulator = new ConcurrentHashMap<>();
        private final List<String> operationLog = new ArrayList<>();

        private String buildKey(Object memoryId) {
            return "chat:memory:" + memoryId;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            String key = buildKey(memoryId);

            // ============================================
            // 【Redis 查询操作】
            // 实际实现:
            //   String json = redisTemplate.opsForValue().get(key);
            //   return deserialize(json);
            //
            // 或使用 List 结构:
            //   List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            // ============================================
            operationLog.add("GET " + key);
            System.out.println("  [Redis] GET " + key);

            String json = redisSimulator.get(key);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            String key = buildKey(memoryId);
            String json = ChatMessageSerializer.messagesToJson(messages);

            // ============================================
            // 【Redis 写入操作】
            // 实际实现:
            //   redisTemplate.opsForValue().set(key, json);
            //
            // 带过期时间:
            //   redisTemplate.opsForValue().set(key, json, 24, TimeUnit.HOURS);
            //
            // 使用 List 结构:
            //   redisTemplate.delete(key);
            //   redisTemplate.opsForList().rightPushAll(key, jsonList);
            //   redisTemplate.expire(key, 24, TimeUnit.HOURS);
            // ============================================
            operationLog.add("SET " + key + " (TTL: 24h)");
            System.out.println("  [Redis] SET " + key + " = <" + messages.size() + " messages>");

            redisSimulator.put(key, json);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            String key = buildKey(memoryId);

            // ============================================
            // 【Redis 删除操作】
            // 实际实现:
            //   redisTemplate.delete(key);
            // ============================================
            operationLog.add("DEL " + key);
            System.out.println("  [Redis] DEL " + key);

            redisSimulator.remove(key);
        }

        public void printOperationLog() {
            operationLog.forEach(op -> System.out.println("  - " + op));
        }
    }

    /**
     * 数据库存储实现 (模拟)
     *
     * 表结构示例:
     * CREATE TABLE chat_memory (
     *     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *     memory_id VARCHAR(255) NOT NULL,
     *     message_type VARCHAR(50) NOT NULL,  -- USER / AI / SYSTEM
     *     content TEXT NOT NULL,
     *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     *     INDEX idx_memory_id (memory_id)
     * );
     *
     * 或者简化版 (整体存储):
     * CREATE TABLE chat_memory (
     *     memory_id VARCHAR(255) PRIMARY KEY,
     *     messages_json TEXT NOT NULL,
     *     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     * );
     */
    static class DatabaseChatMemoryStore implements ChatMemoryStore {

        // 模拟数据库存储
        private final Map<Object, String> dbSimulator = new ConcurrentHashMap<>();
        private final List<String> sqlLog = new ArrayList<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            // ============================================
            // 【数据库查询操作 - SELECT】
            //
            // 方式1: 整体查询 (推荐简单场景)
            //   String sql = "SELECT messages_json FROM chat_memory WHERE memory_id = ?";
            //   String json = jdbcTemplate.queryForObject(sql, String.class, memoryId);
            //
            // 方式2: 逐条查询 (支持更灵活的操作)
            //   String sql = "SELECT message_type, content FROM chat_memory " +
            //                "WHERE memory_id = ? ORDER BY created_at ASC";
            //   List<ChatMessage> messages = jdbcTemplate.query(sql, rowMapper, memoryId);
            //
            // 方式3: JPA/MyBatis
            //   ChatMemoryEntity entity = chatMemoryRepository.findByMemoryId(memoryId);
            // ============================================
            String sql = "SELECT messages_json FROM chat_memory WHERE memory_id = '" + memoryId + "'";
            sqlLog.add(sql);
            System.out.println("  [DB] " + sql);

            String json = dbSimulator.get(memoryId);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            String json = ChatMessageSerializer.messagesToJson(messages);

            // ============================================
            // 【数据库更新/插入操作 - INSERT/UPDATE】
            //
            // 方式1: UPSERT (MySQL)
            //   String sql = "INSERT INTO chat_memory (memory_id, messages_json) " +
            //                "VALUES (?, ?) " +
            //                "ON DUPLICATE KEY UPDATE messages_json = VALUES(messages_json)";
            //   jdbcTemplate.update(sql, memoryId, json);
            //
            // 方式2: 先删后插 (逐条存储)
            //   jdbcTemplate.update("DELETE FROM chat_memory WHERE memory_id = ?", memoryId);
            //   for (ChatMessage msg : messages) {
            //       jdbcTemplate.update(
            //           "INSERT INTO chat_memory (memory_id, message_type, content) VALUES (?, ?, ?)",
            //           memoryId, msg.type(), msg.text()
            //       );
            //   }
            //
            // 方式3: JPA
            //   ChatMemoryEntity entity = new ChatMemoryEntity(memoryId, json);
            //   chatMemoryRepository.save(entity);
            // ============================================
            String sql = "INSERT INTO chat_memory (memory_id, messages_json) VALUES ('" + memoryId + "', '<json>') " +
                         "ON DUPLICATE KEY UPDATE messages_json = '<json>'";
            sqlLog.add(sql);
            System.out.println("  [DB] " + sql.replace("<json>", "<" + messages.size() + " messages>"));

            dbSimulator.put(memoryId, json);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            // ============================================
            // 【数据库删除操作 - DELETE】
            //
            // 实际实现:
            //   String sql = "DELETE FROM chat_memory WHERE memory_id = ?";
            //   jdbcTemplate.update(sql, memoryId);
            //
            // JPA:
            //   chatMemoryRepository.deleteByMemoryId(memoryId);
            // ============================================
            String sql = "DELETE FROM chat_memory WHERE memory_id = '" + memoryId + "'";
            sqlLog.add(sql);
            System.out.println("  [DB] " + sql);

            dbSimulator.remove(memoryId);
        }

        public void printSqlLog() {
            sqlLog.forEach(sql -> System.out.println("  - " + sql));
        }
    }
}
