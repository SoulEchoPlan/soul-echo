/*
 * Soul Echo 初始化数据库结构
 * 包含 Characters (角色表) 和 Knowledge_Base (知识库表)
 * 已整合所有历史补丁，一步到位。
 */

-- 1. 创建角色表 Characters
-- 对应实体: src/main/java/com/dotlinea/soulecho/entity/Character.java
CREATE TABLE IF NOT EXISTS characters (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 核心信息
                                          name VARCHAR(255) NOT NULL UNIQUE COMMENT '角色名称，必须唯一',
    description LONGTEXT COMMENT '角色描述信息',
    persona_prompt LONGTEXT NOT NULL COMMENT '角色人设提示词',
    avatar_url VARCHAR(1024) COMMENT '角色头像URL',

    -- 语音与配置
    voice_id VARCHAR(255) COMMENT '语音合成使用的声音ID',
    is_public BOOLEAN DEFAULT TRUE COMMENT '是否为公开角色',

    -- 知识库关联 (原 V2 补丁内容)
    knowledge_index_id VARCHAR(255) UNIQUE COMMENT '角色的专属知识库索引ID（阿里云百炼）',

    -- 审计字段 (对应 Entity 类的 gmt_create 和 gmt_modified)
    gmt_create DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    gmt_modified DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI角色表';


-- 2. 创建知识库文件表 Knowledge_Base
-- 对应实体: src/main/java/com/dotlinea/soulecho/entity/KnowledgeBase.java
CREATE TABLE IF NOT EXISTS knowledge_base (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- 关联
                                              character_id BIGINT NOT NULL COMMENT '关联的角色ID',

    -- 文件信息
                                              aliyun_file_id VARCHAR(255) COMMENT '阿里云百炼返回的文件ID（允许为NULL，原 V3 补丁修复内容）',
    file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_md5 VARCHAR(32) COMMENT '文件MD5值',

    -- 状态与任务
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '文件状态：UPLOADING, INDEXING, COMPLETED, FAILED',
    job_id VARCHAR(255) COMMENT '索引任务ID',
    error_message TEXT COMMENT '错误信息',

    -- 审计字段 (对应 Entity 类的 gmt_create 和 gmt_modified)
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引优化
    INDEX idx_character_id (character_id),
    INDEX idx_status (status),
    INDEX idx_gmt_create (gmt_create)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文件表';