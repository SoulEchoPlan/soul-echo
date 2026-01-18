-- V1: 初始化数据库表结构
-- 包含 characters 表和 knowledge_base 表

-- 1. 创建 characters 表
CREATE TABLE IF NOT EXISTS characters (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                                          name VARCHAR(255) NOT NULL UNIQUE COMMENT '角色名称',
                                          description TEXT COMMENT '角色描述',
                                          persona_prompt TEXT NOT NULL COMMENT '角色人设提示词',
                                          avatar_url VARCHAR(1024) COMMENT '头像URL',
                                          voice_id VARCHAR(255) COMMENT '语音ID',
                                          is_public BOOLEAN DEFAULT TRUE COMMENT '是否公开',
    -- 注意：这里不包含 knowledge_index_id，因为它会在 V2 中添加
                                          gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI角色表';

-- 2. 创建 knowledge_base 表
CREATE TABLE IF NOT EXISTS knowledge_base (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                                              character_id BIGINT NOT NULL COMMENT '关联的角色ID',
                                              aliyun_file_id VARCHAR(255) UNIQUE COMMENT '阿里云百炼返回的文件ID',
                                              file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
                                              file_size BIGINT COMMENT '文件大小',
                                              file_md5 VARCHAR(32) COMMENT '文件MD5值',
                                              status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '状态',
                                              job_id VARCHAR(255) COMMENT '任务ID',
                                              error_message TEXT COMMENT '错误信息',
                                              gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                              INDEX idx_character_id (character_id),
                                              INDEX idx_status (status),
                                              INDEX idx_gmt_create (gmt_create)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文件表';