package com.dotlinea.soulecho.service;

import com.dotlinea.soulecho.dto.CharacterDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.entity.Character;

import java.util.List;

/**
 * 角色服务接口
 * <p>
 * 定义角色相关的CRUD操作
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface CharacterService {

    /**
     * 查询所有角色
     * @return 角色DTO列表
     */
    List<CharacterDTO> findAll();

    /**
     * 根据ID查找角色
     * @param id 角色ID
     * @return 角色实体, 如果找不到则返回null
     */
    Character findById(Long id);

    /**
     * 保存角色
     * @param character 角色实体
     * @return 保存后的角色实体
     */
    Character save(Character character);

    /**
     * 根据ID删除角色
     * @param id 角色ID
     * @return 删除成功则返回true, 否则返回false
     */
    boolean deleteById(Long id);

    /**
     * 根据名称查找角色
     * @param name 角色名称
     * @return 角色实体, 如果找不到则返回null
     */
    Character findByName(String name);

    /**
     * 查询所有公开角色
     * @return 公开角色DTO列表
     */
    List<CharacterDTO> findAllPublic();

    /**
     * 更新角色信息
     * @param id 角色ID
     * @param characterDTO 角色DTO
     * @return 更新后的角色DTO
     */
    CharacterDTO updateCharacter(Long id, CharacterDTO characterDTO);

    /**
     * 处理聊天请求
     * @param request 聊天请求DTO
     * @return 聊天响应DTO
     */
    ChatResponseDTO chat(ChatRequestDTO request);
}