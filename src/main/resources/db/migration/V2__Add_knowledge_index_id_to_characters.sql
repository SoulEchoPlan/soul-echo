-- 为角色表添加知识库索引ID字段
-- 实现"一角色一 Index"架构，每个角色拥有独立的知识库索引

ALTER TABLE characters
    ADD COLUMN knowledge_index_id VARCHAR(255) UNIQUE COMMENT '角色的专属知识库索引ID（阿里云百炼）';

-- 为现有角色创建默认索引（可选，如果需要迁移数据）
-- 注意：这里只是占位，实际需要调用阿里云 API 创建索引后更新
-- UPDATE characters SET knowledge_index_id = CONCAT('default_index_', id) WHERE knowledge_index_id IS NULL;
