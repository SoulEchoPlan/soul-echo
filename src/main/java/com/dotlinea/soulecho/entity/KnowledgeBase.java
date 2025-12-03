package com.dotlinea.soulecho.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 知识库文件实体类
 * <p>
 * 记录上传到知识库的文件信息，包括阿里云文件ID和原始文件名
 * 用于跟踪和管理角色上传的知识库文档
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Entity
@Table(name = "knowledge_base")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的角色ID
     */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /**
     * 阿里云百炼返回的文件ID
     */
    @Column(name = "aliyun_file_id", nullable = false, unique = true, length = 255)
    private String aliyunFileId;

    /**
     * 原始文件名
     */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 文件MD5值
     */
    @Column(name = "file_md5", length = 32)
    private String fileMd5;

    /**
     * 文件状态：UPLOADING（上传中）、INDEXING（索引中）、COMPLETED（完成）、FAILED（失败）
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "UPLOADING";

    /**
     * 索引任务ID（如果有）
     */
    @Column(name = "job_id", length = 255)
    private String jobId;

    /**
     * 错误信息（如果上传或索引失败）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * JPA生命周期回调，自动更新时间
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}