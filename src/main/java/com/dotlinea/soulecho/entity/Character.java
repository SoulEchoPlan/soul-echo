package com.dotlinea.soulecho.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * AI角色实体类
 * 存储AI角色的基本信息，包括角色描述、人设提示、头像和语音配置等
 */
@Data
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
     * 角色头像URL
     */
    @Column(length = 1024)
    private String avatarUrl;

    /**
     * 语音合成使用的声音ID
     */
    private String voiceId;

    /**
     * 是否为公开角色，默认为true
     */
    @Column(name = "is_public", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isPublic = true;

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
}