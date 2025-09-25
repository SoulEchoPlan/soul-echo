package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.dto.CharacterDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.service.CharacterService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色控制器
 * <p>
 * 提供角色CRUD操作，以及聊天功能
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@RestController
@RequestMapping("/api/characters")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class CharacterController {

    private static final Logger logger = LoggerFactory.getLogger(CharacterController.class);

    private final CharacterService characterService;

    /**
     * 文本聊天接口
     * <p>
     * 使用LLM进行文本对话
     * </p>
     *
     * @param request 聊天请求
     * @return 聊天回复
     */
    @PostMapping("/chat")
    public ChatResponseDTO chat(@Valid @RequestBody ChatRequestDTO request) {
        return characterService.chat(request);
    }

    /**
     * 获取所有角色列表
     * @return 角色列表
     */
    @GetMapping
    public ResponseEntity<List<CharacterDTO>> getAllCharacters() {
        logger.debug("获取所有角色列表");
        try {
            List<CharacterDTO> characters = characterService.findAll();
            return ResponseEntity.ok(characters);
        } catch (Exception e) {
            logger.error("获取角色列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据ID获取角色
     * @param id 角色ID
     * @return 角色信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterDTO> getCharacterById(@PathVariable Long id) {
        logger.debug("获取角色信息，ID: {}", id);
        try {
            Character character = characterService.findById(id);
            if (character != null) {
                CharacterDTO dto = convertToDTO(character);
                return ResponseEntity.ok(dto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("获取角色信息失败，ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建角色
     * @param characterDTO 角色数据
     * @return 创建的角色信息
     */
    @PostMapping
    public ResponseEntity<CharacterDTO> createCharacter(@Valid @RequestBody CharacterDTO characterDTO) {
        logger.info("创建角色: {}", characterDTO.getName());
        try {
            Character character = convertToEntity(characterDTO);
            Character savedCharacter = characterService.save(character);
            CharacterDTO responseDTO = convertToDTO(savedCharacter);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            logger.error("创建角色失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新角色信息
     * @param id 角色ID
     * @param characterDTO 更新的角色数据
     * @return 更新后的角色信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<CharacterDTO> updateCharacter(@PathVariable Long id, @Valid @RequestBody CharacterDTO characterDTO) {
        logger.info("更新角色信息，ID: {}", id);
        try {
            CharacterDTO updatedCharacter = characterService.updateCharacter(id, characterDTO);
            return ResponseEntity.ok(updatedCharacter);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("未找到")) {
                return ResponseEntity.notFound().build();
            }
            logger.error("更新角色失败，ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            logger.error("更新角色失败，ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除角色
     * @param id 角色ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable Long id) {
        logger.info("删除角色，ID: {}", id);
        try {
            boolean deleted = characterService.deleteById(id);
            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("删除角色失败，ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 实体转换为DTO
     */
    private CharacterDTO convertToDTO(Character character) {
        CharacterDTO dto = new CharacterDTO();
        dto.setId(character.getId());
        dto.setName(character.getName());
        dto.setPersonaPrompt(character.getPersonaPrompt());
        dto.setAvatarUrl(character.getAvatarUrl());
        dto.setVoiceId(character.getVoiceId());
        dto.setPublic(character.isPublic());
        return dto;
    }

    /**
     * DTO转换为实体
     */
    private Character convertToEntity(CharacterDTO dto) {
        Character character = new Character();
        character.setName(dto.getName());
        character.setPersonaPrompt(dto.getPersonaPrompt());
        character.setAvatarUrl(dto.getAvatarUrl());
        character.setVoiceId(dto.getVoiceId());
        character.setPublic(dto.isPublic());
        return character;
    }

    /**
     * 从DTO更新实体
     */
    private void updateCharacterFromDTO(Character character, CharacterDTO dto) {
        if (dto.getName() != null) {
            character.setName(dto.getName());
        }
        if (dto.getPersonaPrompt() != null) {
            character.setPersonaPrompt(dto.getPersonaPrompt());
        }
        if (dto.getAvatarUrl() != null) {
            character.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getVoiceId() != null) {
            character.setVoiceId(dto.getVoiceId());
        }
        character.setPublic(dto.isPublic());
    }

}