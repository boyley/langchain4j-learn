-- ============================================================
-- 知识库服务数据库初始化脚本 (企业级版本)
-- ============================================================

-- 创建数据库 (如果不存在)
CREATE DATABASE IF NOT EXISTS knowledge_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE knowledge_db;

-- ============================================================
-- 知识文档表 (元数据) - 轻量级，不含大内容
-- ============================================================
-- 设计说明:
-- 1. 只存储元数据，content 移到 knowledge_content 表
-- 2. 批量查询时不会加载大内容，避免 OOM
-- 3. summary 字段存储摘要用于预览
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(128) NOT NULL UNIQUE COMMENT '文档唯一标识',
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    summary VARCHAR(500) COMMENT '文档摘要 (前500字符)',
    category VARCHAR(64) COMMENT '分类',
    version VARCHAR(32) DEFAULT '1.0' COMMENT '版本号',
    source VARCHAR(32) COMMENT '来源: database/file/api',

    -- 内容元信息 (不存储实际内容)
    content_size BIGINT COMMENT '内容大小(字节)',
    content_hash VARCHAR(64) COMMENT '内容哈希(MD5)',
    content_location VARCHAR(32) DEFAULT 'database' COMMENT '内容存储位置: database/oss',
    content_url VARCHAR(500) COMMENT '对象存储URL (如果存在OSS)',

    -- 状态字段
    status INT DEFAULT 1 COMMENT '状态: 1有效 0已删除',
    vector_status INT DEFAULT 0 COMMENT '向量化状态: 0未处理 1已完成 2失败',
    vector_time DATETIME COMMENT '向量化时间',
    segment_count INT COMMENT '向量化片段数量',

    -- 时间字段
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX idx_doc_id (doc_id),
    INDEX idx_category (category),
    INDEX idx_source (source),
    INDEX idx_update_time (update_time),
    INDEX idx_vector_status (vector_status),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档元数据表';

-- ============================================================
-- 知识内容表 (分离大内容)
-- ============================================================
-- 设计说明:
-- 1. 与 knowledge_documents 通过 doc_id 关联
-- 2. 使用 LONGTEXT 支持最大 4GB 内容
-- 3. 查询元数据时不会加载此表
-- 4. 处理文档时按需查询
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(128) NOT NULL UNIQUE COMMENT '关联文档ID',
    content LONGTEXT NOT NULL COMMENT '完整文档内容',
    content_size BIGINT COMMENT '内容大小(字节)',
    content_hash VARCHAR(64) COMMENT '内容哈希(用于变更检测)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_content_doc_id (doc_id),
    CONSTRAINT fk_content_doc_id FOREIGN KEY (doc_id) REFERENCES knowledge_documents(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档内容表';

-- ============================================================
-- 同步状态表
-- ============================================================
CREATE TABLE IF NOT EXISTS sync_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_name VARCHAR(64) NOT NULL UNIQUE COMMENT '知识源名称',
    last_sync_time DATETIME COMMENT '上次同步时间',
    last_sync_count INT DEFAULT 0 COMMENT '上次同步文档数',
    total_sync_count BIGINT DEFAULT 0 COMMENT '累计同步文档数',
    last_sync_status VARCHAR(32) COMMENT '同步状态: SUCCESS/FAILED/RUNNING',
    last_error_message VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步状态表';

-- ============================================================
-- 插入示例数据 (元数据 + 内容分开插入)
-- ============================================================

-- 插入元数据
INSERT INTO knowledge_documents (doc_id, title, summary, category, source, content_size) VALUES
('db:doc-001', '员工休假管理办法', '第一条 年假规定：工作满1年的员工，享有5天带薪年假；工作满3年的员工，享有10天带薪年假...', 'HR政策', 'database', 280),
('db:doc-002', '费用报销制度', '第一条 报销范围：差旅费、办公用品费、业务招待费。第二条 报销流程：填写报销单...', '财务制度', 'database', 220),
('db:doc-003', '新员工入职指南', '第一条 入职准备材料：身份证原件及复印件、学历证书原件、离职证明、银行卡信息...', 'HR政策', 'database', 230),
('db:doc-004', '代码开发规范', '第一条 命名规范：类名使用大驼峰(UserService)、方法名使用小驼峰(getUserById)...', '技术规范', 'database', 200),
('db:doc-005', 'API接口规范', '第一条 URL设计：使用RESTful风格、资源名使用复数(/api/users)、版本放在URL中...', '技术规范', 'database', 280)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;

-- 插入内容 (分离存储)
INSERT INTO knowledge_content (doc_id, content, content_size) VALUES
('db:doc-001', '第一条 年假规定\n1. 工作满1年的员工，享有5天带薪年假\n2. 工作满3年的员工，享有10天带薪年假\n3. 年假可分次使用，最小单位为半天\n\n第二条 请假流程\n1. 提前3天在OA系统提交申请\n2. 直属领导审批后生效\n3. 紧急情况可电话请假，事后补单', 280),
('db:doc-002', '第一条 报销范围\n1. 差旅费：交通、住宿、餐饮\n2. 办公用品费\n3. 业务招待费\n\n第二条 报销流程\n1. 填写报销单，附上发票原件\n2. 部门领导审批\n3. 财务审核后付款\n\n第三条 报销时限\n费用发生后30天内完成报销', 220),
('db:doc-003', '第一条 入职准备材料\n1. 身份证原件及复印件\n2. 学历证书原件\n3. 离职证明\n4. 银行卡信息\n\n第二条 入职流程\n1. HR办理入职手续\n2. IT开通账号权限\n3. 部门领导介绍团队\n4. 导师分配和培训', 230),
('db:doc-004', '第一条 命名规范\n1. 类名使用大驼峰: UserService\n2. 方法名使用小驼峰: getUserById\n3. 常量使用全大写下划线: MAX_RETRY_COUNT\n\n第二条 代码格式\n1. 使用4空格缩进\n2. 单行不超过120字符\n3. 方法不超过50行', 200),
('db:doc-005', '第一条 URL设计\n1. 使用 RESTful 风格\n2. 资源名使用复数: /api/users\n3. 版本放在URL中: /api/v1/users\n\n第二条 响应格式\n{\n  "code": 0,\n  "message": "success",\n  "data": {}\n}\n\n第三条 错误码规范\n0: 成功\n-1: 通用错误\n40x: 客户端错误\n50x: 服务端错误', 280)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP, content = VALUES(content);

-- 打印完成信息
SELECT '数据库初始化完成!' AS message;
SELECT COUNT(*) AS document_count FROM knowledge_documents;
SELECT COUNT(*) AS content_count FROM knowledge_content;
