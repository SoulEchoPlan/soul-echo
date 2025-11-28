package com.dotlinea.soulecho.service;

import java.util.List;

/**
 * 知识库服务接口
 * <p>
 * 提供向量数据库的增删查改功能，支持文本向量化和相似度检索
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface KnowledgeService {

    /**
     * 添加文档到知识库
     * <p>
     * 将文本资料与特定角色关联，并存入向量数据库
     * </p>
     *
     * @param characterName 角色名称
     * @param text          文本内容
     */
    void addDocument(String characterName, String text);

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
}
