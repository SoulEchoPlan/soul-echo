-- 知识库文件表
-- 用于记录上传到阿里云百炼知识库的文件信息

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    character_id BIGINT NOT NULL COMMENT '关联的角色ID',
    aliyun_file_id VARCHAR(255) UNIQUE COMMENT '阿里云百炼返回的文件ID（允许为NULL，上传完成后回填）',
    file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_md5 VARCHAR(32) COMMENT '文件MD5值',
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '文件状态：UPLOADING（上传中）、INDEXING（索引中）、COMPLETED（完成）、FAILED（失败）',
    job_id VARCHAR(255) COMMENT '索引任务ID（如果有）',
    error_message TEXT COMMENT '错误信息（如果上传或索引失败）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_character_id (character_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文件表';