package com.dotlinea.soulecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 聊天记录表
 * @TableName Chat_history
 */
@TableName(value ="Chat_history")
@Data
public class ChatHistory {
    /**
     * 主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 会话ID，用于关联同一会话的消息
     */
    private Long memoryId;

    /**
     * 消息角色
     */
    @TableField(value = "`Character_type`")
    private String characterType;

    /**
     * 消息内容
     */
    @TableField(value = "`message`")
    private String message;

    /**
     * 消息创建时间，默认为当前时间戳
     */
    private Date createdAt;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        ChatHistory other = (ChatHistory) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getMemoryId() == null ? other.getMemoryId() == null : this.getMemoryId().equals(other.getMemoryId()))
            && (this.getCharacterType() == null ? other.getCharacterType() == null : this.getCharacterType().equals(other.getCharacterType()))
            && (this.getMessage() == null ? other.getMessage() == null : this.getMessage().equals(other.getMessage()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getMemoryId() == null) ? 0 : getMemoryId().hashCode());
        result = prime * result + ((getCharacterType() == null) ? 0 : getCharacterType().hashCode());
        result = prime * result + ((getMessage() == null) ? 0 : getMessage().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", memoryId=").append(memoryId);
        sb.append(", character=").append(characterType);
        sb.append(", message=").append(message);
        sb.append(", createdAt=").append(createdAt);
        sb.append("]");
        return sb.toString();
    }
}