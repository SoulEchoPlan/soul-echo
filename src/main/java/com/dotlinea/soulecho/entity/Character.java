package com.dotlinea.soulecho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI角色实体类
 * 存储AI角色的基本信息，包括角色描述、人设提示、头像和语音配置等
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "characters")
public class Character {

    /**
     * 角色唯一标识
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色名称，必须唯一
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * 角色描述信息
     */
    @Lob
    private String description;

    /**
     * 角色人设提示词，用于AI对话生成
     */
    @Lob
    @Column(nullable = false)
    private String personaPrompt;

    /**
     * 角色头像 URL
     */
    @Column(length = 1024)
    private String avatarUrl;

    /**
     * 语音合成使用的声音 ID
     */
    private String voiceId;

    /**
     * 是否为公开角色，默认为true
     */
    @Column(name = "is_public", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isPublic = true;

    /**
     * 角色专属知识库索引ID（阿里云百炼）
     */
    @Column(name = "knowledge_index_id", unique = true, length = 255)
    private String knowledgeIndexId;

    /**
     * 创建时间，自动生成
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间，自动维护
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * 重写 equals 方法，仅比较 id
     * <p>
     * 符合 JPA 最佳实践：
     * 1. 瞬态对象（id 为 null）视为不相等
     * 2. 持久化对象仅根据 id 判断相等性
     * 3. 处理 Hibernate 代理类型不同的情况
     * </p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Character character = (Character) o;
        return id != null && Objects.equals(id, character.id);
    }

    /**
     * 重写 hashCode 方法，仅基于 id
     * <p>
     * 注意：为了保证集合操作的一致性，瞬态对象使用固定的 hashCode
     * </p>
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}