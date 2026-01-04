package com.dotlinea.soulecho.entity;

import com.dotlinea.soulecho.constants.FileStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Objects;

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
@Getter
@Setter
@ToString
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
     * <p>
     * 允许为 null，因为业务流程是"先入库占位(UPLOADING)" -> "异步上传获取ID"
     * 在入库时还没有阿里云文件ID，等待异步上传完成后更新
     * </p>
     */
    @Column(name = "aliyun_file_id", unique = true, nullable = true)
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
    private String status = FileStatusEnum.UPLOADING.getCode();

    /**
     * 索引任务ID（如果有）
     */
    @Column(name = "job_id")
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KnowledgeBase that = (KnowledgeBase) o;
        return id != null && Objects.equals(id, that.id);
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