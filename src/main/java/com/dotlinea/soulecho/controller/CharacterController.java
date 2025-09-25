package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.dto.CharacterDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.service.CharacterService;
import com.dotlinea.soulecho.service.RealtimeChatService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final RealtimeChatService chatService;

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
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        logger.info("收到文本聊天请求: {}", request.getMessage());

        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "消息不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 获取角色设定
            String personaPrompt = null;
            if (request.getCharacterId() != null) {
                try {
                    Character character = characterService.findById(request.getCharacterId());
                    if (character != null) {
                        personaPrompt = character.getPersonaPrompt();
                        logger.debug("使用角色 {} 的设定: {}", character.getName(), personaPrompt);
                    }
                } catch (Exception e) {
                    logger.warn("获取角色信息失败，将使用默认设定", e);
                }
            }

            // 如果没有找到角色设定，使用默认设定
            if (personaPrompt == null || personaPrompt.trim().isEmpty()) {
                personaPrompt = "你是一个友好的AI助手，请用自然的方式与用户对话。";
            }

            // 生成或使用现有会话ID
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = "chat_" + UUID.randomUUID();
            }

            // 处理聊天
            String reply = chatService.processTextChat(personaPrompt, request.getMessage(), sessionId);

            if (reply != null && !reply.trim().isEmpty()) {
                response.put("success", true);
                response.put("reply", reply);
                response.put("sessionId", sessionId);
                logger.info("文本聊天处理完成: {}", reply.length());
            } else {
                response.put("success", false);
                response.put("error", "生成回复失败");
            }

        } catch (Exception e) {
            logger.error("文本聊天处理失败", e);
            response.put("success", false);
            response.put("error", "服务器内部错误");
        }

        return ResponseEntity.ok(response);
    }

    // TODO: 实现完整的角色 CRUD API

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
    public ResponseEntity<CharacterDTO> createCharacter(@RequestBody CharacterDTO characterDTO) {
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
    public ResponseEntity<CharacterDTO> updateCharacter(@PathVariable Long id, @RequestBody CharacterDTO characterDTO) {
        logger.info("更新角色信息，ID: {}", id);
        try {
            Character existingCharacter = characterService.findById(id);
            if (existingCharacter == null) {
                return ResponseEntity.notFound().build();
            }

            // 更新角色信息
            updateCharacterFromDTO(existingCharacter, characterDTO);
            Character updatedCharacter = characterService.save(existingCharacter);
            CharacterDTO responseDTO = convertToDTO(updatedCharacter);
            return ResponseEntity.ok(responseDTO);
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
        dto.setDescription(character.getDescription());
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
        character.setDescription(dto.getDescription());
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
        if (dto.getDescription() != null) {
            character.setDescription(dto.getDescription());
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

    /**
     * 聊天请求DTO
     */
    @Getter
    public static class ChatRequest {
        private String message;
        private Long characterId;
        private String sessionId;
    }
}