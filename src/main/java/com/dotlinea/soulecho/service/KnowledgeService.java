package com.dotlinea.soulecho.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务接口
 * <p>
 * 提供知识库的增删查改功能，支持文件上传和向量化检索
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface KnowledgeService {

    /**
     * 从知识库中检索相关文档
     * <p>
     * 根据用户查询，检索与角色相关的知识片段
     * </p>
     *
     * @param characterName 角色名称
     * @param query         查询文本
     * @return 相关知识片段列表
     */
    List<String> search(String characterName, String query);

    /**
     * 上传文档到知识库
     * <p>
     * 支持多种文件格式，将文件内容与角色关联并存入知识库
     * </p>
     *
     * @param characterId 角色ID
     * @param file        上传的文件
     * @return 上传结果信息，包含文件ID和状态
     */
    Map<String, Object> uploadDocument(Long characterId, MultipartFile file);

    /**
     * 在知识库中搜索相关内容
     * <p>
     * 基于向量相似度进行智能检索
     * </p>
     *
     * @param characterId 角色ID
     * @param query       查询文本
     * @return 相关知识片段列表
     */
    List<String> searchByCharacterId(Long characterId, String query);

    /**
     * 删除知识库文档
     * <p>
     * 根据文档ID删除知识库中的文件
     * </p>
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    boolean deleteDocument(Long documentId);

    /**
     * 获取某个角色的文档列表
     * <p>
     * 查询指定角色上传了哪些文件
     * </p>
     *
     * @param characterId 角色ID
     * @return 文档信息列表
     */
    List<Map<String, Object>> listDocuments(Long characterId);
}
