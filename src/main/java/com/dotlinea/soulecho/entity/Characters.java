package com.dotlinea.soulecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName characters
 */
@TableName(value ="characters")
@Data
public class Characters {
    /**
     * 角色id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色简介
     */
    private String description;

    /**
     * 角色人设提示词
     */
    private String personaprompt;

    /**
     * 角色头像URL
     */
    private String avatarurl;

    /**
     * 声音
     */
    private String voiceid;

    /**
     * 是否为公开角色，默认为true
     */
    private Integer isPublic;

    /**
     * 创建时间
     */
    private Date createdat;

    /**
     * 更新时间
     */
    private Date updatedat;

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
        Characters other = (Characters) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getName() == null ? other.getName() == null : this.getName().equals(other.getName()))
            && (this.getDescription() == null ? other.getDescription() == null : this.getDescription().equals(other.getDescription()))
            && (this.getPersonaprompt() == null ? other.getPersonaprompt() == null : this.getPersonaprompt().equals(other.getPersonaprompt()))
            && (this.getAvatarurl() == null ? other.getAvatarurl() == null : this.getAvatarurl().equals(other.getAvatarurl()))
            && (this.getVoiceid() == null ? other.getVoiceid() == null : this.getVoiceid().equals(other.getVoiceid()))
            && (this.getIsPublic() == null ? other.getIsPublic() == null : this.getIsPublic().equals(other.getIsPublic()))
            && (this.getCreatedat() == null ? other.getCreatedat() == null : this.getCreatedat().equals(other.getCreatedat()))
            && (this.getUpdatedat() == null ? other.getUpdatedat() == null : this.getUpdatedat().equals(other.getUpdatedat()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getDescription() == null) ? 0 : getDescription().hashCode());
        result = prime * result + ((getPersonaprompt() == null) ? 0 : getPersonaprompt().hashCode());
        result = prime * result + ((getAvatarurl() == null) ? 0 : getAvatarurl().hashCode());
        result = prime * result + ((getVoiceid() == null) ? 0 : getVoiceid().hashCode());
        result = prime * result + ((getIsPublic() == null) ? 0 : getIsPublic().hashCode());
        result = prime * result + ((getCreatedat() == null) ? 0 : getCreatedat().hashCode());
        result = prime * result + ((getUpdatedat() == null) ? 0 : getUpdatedat().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", name=").append(name);
        sb.append(", description=").append(description);
        sb.append(", personaprompt=").append(personaprompt);
        sb.append(", avatarurl=").append(avatarurl);
        sb.append(", voiceid=").append(voiceid);
        sb.append(", isPublic=").append(isPublic);
        sb.append(", createdat=").append(createdat);
        sb.append(", updatedat=").append(updatedat);
        sb.append("]");
        return sb.toString();
    }
}