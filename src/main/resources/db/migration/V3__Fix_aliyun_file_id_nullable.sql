-- 修复知识库表结构：允许 aliyun_file_id 为 NULL
-- 业务流程：先入库占位(UPLOADING) -> 异步上传获取ID并更新

-- 修改 aliyun_file_id 字段，允许为 NULL
ALTER TABLE knowledge_base MODIFY COLUMN aliyun_file_id VARCHAR(255) NULL COMMENT '阿里云百炼返回的文件ID（允许为NULL，上传完成后回填）';
