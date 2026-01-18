package com.dotlinea.soulecho.event;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 知识库上传事件
 * <p>
 * 当用户触发知识库文件上传时发布此事件,触发异步的文件上传和索引构建流程。
 * 采用事件驱动架构,避免同步阻塞,提升用户体验。
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
public class KnowledgeUploadEvent extends ApplicationEvent {

    /**
     * 知识库记录ID（数据库主键）
     */
    private final Long knowledgeBaseId;

    /**
     * 关联的角色 ID
     */
    private final Long characterId;

    /**
     * 本地临时文件路径
     */
    private final String localFilePath;

    /**
     * 原始文件名
     */
    private final String originalFileName;

    /**
     * 文件MD5哈希值
     */
    private final String fileMd5;

    /**
     * 文件大小（字节）
     */
    private final Long fileSize;

    /**
     * 构造函数
     *
     * @param source            事件源（通常是发布事件的Service）
     * @param knowledgeBaseId   知识库记录 ID
     * @param characterId       角色 ID
     * @param localFilePath     本地临时文件路径
     * @param originalFileName  原始文件名
     * @param fileMd5           文件MD5
     * @param fileSize          文件大小
     */
    @Builder
    public KnowledgeUploadEvent(Object source,
                                 Long knowledgeBaseId,
                                 Long characterId,
                                 String localFilePath,
                                 String originalFileName,
                                 String fileMd5,
                                 Long fileSize) {
        super(source);
        this.knowledgeBaseId = knowledgeBaseId;
        this.characterId = characterId;
        this.localFilePath = localFilePath;
        this.originalFileName = originalFileName;
        this.fileMd5 = fileMd5;
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return String.format("KnowledgeUploadEvent{id=%d, characterId=%d, fileName='%s', fileSize=%d}",
                knowledgeBaseId, characterId, originalFileName, fileSize);
    }
}
