-- 为角色表添加知识库索引ID字段
-- 实现"一角色一 Index"架构，每个角色拥有独立的知识库索引
-- 使用存储过程实现幂等性检查：如果列不存在则添加

DELIMITER $$

CREATE PROCEDURE AddKnowledgeIndexIdColumn()
BEGIN
    -- 检查列是否已存在
    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'characters'
        AND COLUMN_NAME = 'knowledge_index_id'
    ) THEN
        -- 列不存在，执行添加
        ALTER TABLE characters
            ADD COLUMN knowledge_index_id VARCHAR(255) UNIQUE COMMENT '角色的专属知识库索引ID（阿里云百炼）';
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL AddKnowledgeIndexIdColumn();

-- 删除存储过程
DROP PROCEDURE IF EXISTS AddKnowledgeIndexIdColumn;
