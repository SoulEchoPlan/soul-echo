package com.dotlinea.soulecho.repository;

import com.dotlinea.soulecho.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库文件数据访问接口
 * <p>
 * 提供对knowledge_base表的数据访问操作
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /**
     * 根据角色 ID 查找所有文件
     *
     * @param characterId 角色 ID
     * @return 文件列表
     */
    List<KnowledgeBase> findByCharacterIdOrderByGmtCreateDesc(Long characterId);

    /**
     * 根据阿里云文件 ID 查找文件
     *
     * @param aliyunFileId 阿里云文件 ID
     * @return 文件信息
     */
    Optional<KnowledgeBase> findByAliyunFileId(String aliyunFileId);

    /**
     * 根据角色 ID 和状态查找文件
     *
     * @param characterId 角色 ID
     * @param status 文件状态
     * @return 文件列表
     */
    List<KnowledgeBase> findByCharacterIdAndStatusOrderByGmtCreateDesc(Long characterId, String status);

    /**
     * 统计某个角色的文件数量
     *
     * @param characterId 角色 ID
     * @return 文件数量
     */
    @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.characterId = :characterId")
    long countByCharacterId(@Param("characterId") Long characterId);

    /**
     * 根据角色 ID 和状态统计文件数量
     *
     * @param characterId 角色 ID
     * @param status 文件状态
     * @return 文件数量
     */
    @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.characterId = :characterId AND kb.status = :status")
    long countByCharacterIdAndStatus(@Param("characterId") Long characterId, @Param("status") String status);

    /**
     * 检查阿里云文件 ID 是否存在
     *
     * @param aliyunFileId 阿里云文件 ID
     * @return 是否存在
     */
    boolean existsByAliyunFileId(String aliyunFileId);

    /**
     * 根据角色 ID 查找最新的N个文件
     *
     * @param characterId 角色 ID
     * @param limit 限制数量
     * @return 文件列表
     */
    @Query(value = "SELECT * FROM knowledge_base WHERE character_id = :characterId ORDER BY gmt_create DESC LIMIT :limit", nativeQuery = true)
    List<KnowledgeBase> findTopNByCharacterIdOrderByGmtCreateDesc(@Param("characterId") Long characterId, @Param("limit") int limit);
}