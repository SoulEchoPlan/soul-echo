package com.dotlinea.soulecho.service;

import com.dotlinea.soulecho.dto.CharacterRequestDTO;
import com.dotlinea.soulecho.dto.CharacterResponseDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.exception.BusinessException;
import com.dotlinea.soulecho.exception.ResourceNotFoundException;

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
     * @return 角色响应DTO列表
     */
    List<CharacterResponseDTO> findAll();

    /**
     * 根据ID查找角色
     * @param id 角色ID
     * @return 角色响应DTO
     */
    CharacterResponseDTO findCharacterById(Long id);

    /**
     * 创建角色
     * @param requestDTO 角色请求DTO
     * @return 创建后的角色响应DTO
     */
    CharacterResponseDTO createCharacter(CharacterRequestDTO requestDTO);

    /**
     * 根据ID删除角色
     *
     * @param id 角色ID
     * @throws ResourceNotFoundException 如果角色不存在
     */
    void deleteById(Long id);

    /**
     * 根据名称查找角色
     *
     * @param name 角色名称
     * @return 角色实体
     * @throws BusinessException 如果找不到角色
     */
    Character findByName(String name);

    /**
     * 查询所有公开角色
     * @return 公开角色响应DTO列表
     */
    List<CharacterResponseDTO> findAllPublic();

    /**
     * 更新角色信息
     * @param id 角色ID
     * @param requestDTO 角色请求DTO
     * @return 更新后的角色响应DTO
     */
    CharacterResponseDTO updateCharacter(Long id, CharacterRequestDTO requestDTO);

    /**
     * 处理聊天请求
     * @param request 聊天请求DTO
     * @return 聊天响应DTO
     */
    ChatResponseDTO chat(ChatRequestDTO request);
}