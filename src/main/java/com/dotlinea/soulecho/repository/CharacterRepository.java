package com.dotlinea.soulecho.repository;

import com.dotlinea.soulecho.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色资源库
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    /**
     * 根据名称查找角色
     * @param name 角色名
     * @return 角色实体
     */
    Character findByName(String name);

    /**
     * 查找所有公开角色
     * @return 公开角色列表
     */
    List<Character> findByIsPublicTrue();

    /**
     * 根据名称判断角色是否存在
     * @param name 角色名
     * @return 存在则返回true，否则返回false
     */
    boolean existsByName(String name);
}