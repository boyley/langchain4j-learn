# 企业级知识库服务

基于 Spring Boot + LangChain4j 的企业级知识库服务，支持多知识源、多向量存储、增量同步。

## 架构设计

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         企业知识库架构全景图                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  【知识源层】           【处理层】            【存储层】       【应用层】   │
│  ┌─────────┐          ┌─────────┐          ┌─────────┐     ┌─────────┐ │
│  │ MySQL   │          │         │          │  Redis  │     │  RAG    │ │
│  │ 知识表   │──┐       │ 知识处理 │          │ 向量库  │     │  检索   │ │
│  └─────────┘  │       │  管道   │          └─────────┘     └─────────┘ │
│  ┌─────────┐  │       │         │          ┌─────────┐     ┌─────────┐ │
│  │ 文件系统 │──┼──────→│ 1.加载   │─────────→│ Milvus │─────→│ 问答   │ │
│  │ PDF/DOC │  │       │ 2.分割   │          │ 向量库  │     │ 系统   │ │
│  └─────────┘  │       │ 3.向量化 │          └─────────┘     └─────────┘ │
│  ┌─────────┐  │       │ 4.存储   │          ┌─────────┐     ┌─────────┐ │
│  │ API     │──┘       │         │          │   PG    │     │  搜索   │ │
│  │Confluence│         │         │          │ pgvector│     │  推荐   │ │
│  └─────────┘          └─────────┘          └─────────┘     └─────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## 项目结构

```
knowledge-base-service/
├── src/main/java/com/example/knowledge/
│   ├── KnowledgeBaseApplication.java    # 启动类
│   │
│   ├── config/                          # 配置
│   │   └── EmbeddingModelConfig.java    # 嵌入模型配置
│   │
│   ├── entity/                          # 实体
│   │   ├── KnowledgeDocument.java       # 知识文档
│   │   └── SyncStatus.java              # 同步状态
│   │
│   ├── repository/                      # 数据访问
│   │   ├── KnowledgeDocumentRepository.java
│   │   └── SyncStatusRepository.java
│   │
│   ├── source/                          # 知识源 (接口+实现)
│   │   ├── KnowledgeSource.java         # 接口
│   │   └── impl/
│   │       ├── DatabaseKnowledgeSource.java    # 数据库实现
│   │       └── FileSystemKnowledgeSource.java  # 文件系统实现
│   │
│   ├── store/                           # 向量存储 (接口+实现)
│   │   ├── VectorStoreService.java      # 接口
│   │   └── impl/
│   │       ├── RedisVectorStoreService.java    # Redis实现
│   │       └── PgVectorStoreService.java       # pgvector实现
│   │
│   ├── pipeline/                        # 处理管道
│   │   └── KnowledgePipeline.java       # 知识处理管道
│   │
│   ├── service/                         # 业务服务
│   │   └── KnowledgeSearchService.java  # 知识检索服务
│   │
│   ├── scheduler/                       # 定时任务
│   │   └── SyncScheduler.java           # 同步调度器
│   │
│   └── controller/                      # REST API
│       ├── SearchController.java        # 搜索接口
│       └── SyncController.java          # 同步管理接口
│
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   └── schema.sql                       # 数据库初始化
│
├── docker-compose.yml                   # Docker 部署
├── Dockerfile                           # 镜像构建
└── pom.xml                              # Maven 依赖
```

## 快速开始

### 1. 环境准备

```bash
# 设置环境变量
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com/v1  # 或代理地址
```

### 2. Docker 一键启动

```bash
cd 11-knowledge-base/knowledge-base-service

# 启动所有服务 (MySQL + Redis + 应用)
docker-compose up -d

# 查看日志
docker-compose logs -f knowledge-service

# 停止服务
docker-compose down
```

### 3. 本地开发启动

```bash
# 启动 MySQL
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=knowledge_db \
  mysql:8.0

# 启动 Redis Stack
docker run -d --name redis-stack -p 6379:6379 redis/redis-stack:latest

# 初始化数据库
mysql -h localhost -u root -proot < src/main/resources/schema.sql

# 启动应用
mvn spring-boot:run
```

## API 接口

### 知识搜索

```bash
# 基础搜索
curl "http://localhost:8080/api/knowledge/search?q=如何请假"

# 带参数搜索
curl "http://localhost:8080/api/knowledge/search?q=报销&limit=5&minScore=0.5&category=财务制度"

# RAG 上下文检索
curl "http://localhost:8080/api/knowledge/context?q=代码规范&limit=3"
```

### 同步管理

```bash
# 查看知识源
curl "http://localhost:8080/api/knowledge/sync/sources"

# 手动触发增量同步
curl -X POST "http://localhost:8080/api/knowledge/sync/trigger?source=database&mode=incremental"

# 查看同步状态
curl "http://localhost:8080/api/knowledge/sync/status"

# 查看统计信息
curl "http://localhost:8080/api/knowledge/sync/stats"

# 健康检查
curl "http://localhost:8080/api/knowledge/sync/health"
```

## 配置说明

### 向量存储选择

```yaml
knowledge:
  vector-store:
    type: redis      # 可选: redis, pgvector, milvus, elasticsearch
```

| 存储类型 | 适用场景 | 数据规模 |
|---------|---------|---------|
| redis | 中小规模，快速部署 | < 100万 |
| pgvector | 已有 PostgreSQL 环境 | < 500万 |
| milvus | 大规模生产环境 | 亿级 |

### 知识源配置

```yaml
knowledge:
  source:
    database:
      enabled: true          # 数据库知识源
    file:
      enabled: true          # 文件系统知识源
      root-path: /data/docs  # 文档目录
```

### 同步配置

```yaml
knowledge:
  sync:
    incremental:
      enabled: true
      cron: "0 0 * * * *"    # 每小时增量同步
    full:
      enabled: false
      cron: "0 0 3 * * *"    # 每天凌晨全量同步
```

## 扩展开发

### 添加新的知识源

1. 实现 `KnowledgeSource` 接口
2. 添加 `@Component` 和 `@ConditionalOnProperty` 注解
3. 配置 `application.yml`

```java
@Component
@ConditionalOnProperty(name = "knowledge.source.notion.enabled", havingValue = "true")
public class NotionKnowledgeSource implements KnowledgeSource {
    // 实现接口方法...
}
```

### 添加新的向量存储

1. 实现 `VectorStoreService` 接口
2. 添加 `@Service` 和 `@ConditionalOnProperty` 注解

```java
@Service
@ConditionalOnProperty(name = "knowledge.vector-store.type", havingValue = "elasticsearch")
public class ElasticsearchVectorStoreService implements VectorStoreService {
    // 实现接口方法...
}
```

## 生产部署清单

- [ ] 配置环境变量 (API Key, 数据库密码等)
- [ ] 选择合适的向量存储
- [ ] 配置同步策略
- [ ] 设置监控告警
- [ ] 配置日志收集
- [ ] 设置备份策略
