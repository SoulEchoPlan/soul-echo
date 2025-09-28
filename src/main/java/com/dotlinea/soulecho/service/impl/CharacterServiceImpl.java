package com.dotlinea.soulecho.service.impl;

import com.dotlinea.soulecho.dto.CharacterRequestDTO;
import com.dotlinea.soulecho.dto.CharacterResponseDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.exception.CharacterServiceException;
import com.dotlinea.soulecho.exception.ResourceNotFoundException;
import com.dotlinea.soulecho.repository.CharacterRepository;
import com.dotlinea.soulecho.service.CharacterService;
import com.dotlinea.soulecho.service.RealtimeChatService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Service
@AllArgsConstructor
@Transactional
public class CharacterServiceImpl implements CharacterService {

    private static final Logger logger = LoggerFactory.getLogger(CharacterServiceImpl.class);
    private static final String DEFAULT_PERSONA_PROMPT = "你是一个友好、有帮助的AI助手，请用自然的方式与用户对话。";
    private static final String FALLBACK_PERSONA_PROMPT = "你是一个友好的AI助手，请用自然的方式与用户对话。";
    private static final String CHAT_SESSION_PREFIX = "chat_";
    private static final String CHARACTER_NOT_FOUND_GUIDE = "你好，你似乎想找一个我还不认识的角色。没关系，你可以先和我聊聊，或者在未来的版本中，我会学会如何创建新角色。现在，有什么可以帮你的吗？";

    private final CharacterRepository characterRepository;
    private final RealtimeChatService chatService;

    @Override
    @Transactional(readOnly = true)
    public List<CharacterResponseDTO> findAll() {
        logger.debug("查询所有角色");
        try {
            return characterRepository.findAll()
                    .stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("查询所有角色失败", e);
            throw new CharacterServiceException("查询所有角色失败", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CharacterResponseDTO findCharacterById(Long id) {
        logger.debug("根据ID查找角色: {}", id);
        Character character = characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("未找到ID为 " + id + " 的角色"));
        return convertToResponseDTO(character);
    }

    @Override
    public CharacterResponseDTO createCharacter(CharacterRequestDTO requestDTO) {
        logger.debug("创建角色: {}", requestDTO.name());
        try {
            Character character = convertToEntity(requestDTO);
            Character savedCharacter = characterRepository.save(character);
            return convertToResponseDTO(savedCharacter);
        } catch (Exception e) {
            logger.error("创建角色失败: {}", requestDTO.name(), e);
            throw new CharacterServiceException("创建角色失败: 角色名称[" + requestDTO.name() + "]", e);
        }
    }

    @Override
    public boolean deleteById(Long id) {
        logger.debug("删除角色: {}", id);
        try {
            if (characterRepository.existsById(id)) {
                characterRepository.deleteById(id);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("删除角色失败: {}", id, e);
            throw new CharacterServiceException("删除角色失败: ID[" + id + "]", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Character findByName(String name) {
        logger.debug("根据名称查找角色: {}", name);
        try {
            return characterRepository.findByName(name);
        } catch (Exception e) {
            logger.error("根据名称查找角色失败: {}", name, e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterResponseDTO> findAllPublic() {
        logger.debug("查询所有公开角色");
        try {
            return characterRepository.findByIsPublicTrue()
                    .stream()
                    .map(this::convertToResponseDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("查询所有公开角色失败", e);
            throw new CharacterServiceException("查询所有公开角色失败", e);
        }
    }

    @Override
    public CharacterResponseDTO updateCharacter(Long id, CharacterRequestDTO requestDTO) {
        logger.debug("更新角色信息，ID: {}", id);

        Character existingCharacter = characterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("未找到ID为 " + id + " 的角色"));

        // 更新角色信息
        if (requestDTO.name() != null) {
            existingCharacter.setName(requestDTO.name());
        }
        if (requestDTO.personaPrompt() != null) {
            existingCharacter.setPersonaPrompt(requestDTO.personaPrompt());
        }
        if (requestDTO.avatarUrl() != null) {
            existingCharacter.setAvatarUrl(requestDTO.avatarUrl());
        }
        if (requestDTO.voiceId() != null) {
            existingCharacter.setVoiceId(requestDTO.voiceId());
        }
        if (requestDTO.isPublic() != null) {
            existingCharacter.setPublic(requestDTO.isPublic());
        }

        Character updatedCharacter = characterRepository.save(existingCharacter);
        return convertToResponseDTO(updatedCharacter);
    }

    @Override
    public ChatResponseDTO chat(ChatRequestDTO request) {
        logger.info("收到聊天请求: {}", request.getMessage());

        try {
            validateChatRequest(request);

            String sessionId = generateOrGetSessionId(request.getSessionId());

            if (request.getCharacterId() == null) {
                return handleDefaultChat(request.getMessage(), sessionId);
            } else {
                return handleCharacterChat(request.getCharacterId(), request.getMessage(), sessionId);
            }

        } catch (Exception e) {
            logger.error("聊天处理失败", e);
            return ChatResponseDTO.error("服务器内部错误");
        }
    }

    /**
     * 验证聊天请求参数
     */
    private void validateChatRequest(ChatRequestDTO request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
    }

    /**
     * 生成或获取会话ID
     */
    private String generateOrGetSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return CHAT_SESSION_PREFIX + UUID.randomUUID();
        }
        return sessionId;
    }

    /**
     * 处理默认聊天（无角色ID）
     */
    private ChatResponseDTO handleDefaultChat(String message, String sessionId) {
        logger.debug("未提供角色ID，使用默认AI助手设定");

        String reply = processLlmChat(DEFAULT_PERSONA_PROMPT, message, sessionId, null);
        return buildChatResponse(reply, sessionId);
    }

    /**
     * 处理角色聊天
     */
    private ChatResponseDTO handleCharacterChat(Long characterId, String message, String sessionId) {
        Optional<Character> characterOptional = characterRepository.findById(characterId);

        if (characterOptional.isPresent()) {
            Character character = characterOptional.get();
            String personaPrompt = getValidPersonaPrompt(character.getPersonaPrompt());

            logger.debug("使用角色 {} 的设定: {}", character.getName(), personaPrompt);

            String reply = processLlmChat(personaPrompt, message, sessionId, character.getName());
            return buildChatResponse(reply, sessionId);
        } else {
            logger.warn("未找到角色ID: {}，将引导用户。", characterId);
            return ChatResponseDTO.success(CHARACTER_NOT_FOUND_GUIDE, sessionId);
        }
    }

    /**
     * 获取有效的角色设定提示词
     */
    private String getValidPersonaPrompt(String personaPrompt) {
        if (personaPrompt == null || personaPrompt.trim().isEmpty()) {
            return FALLBACK_PERSONA_PROMPT;
        }
        return personaPrompt;
    }

    /**
     * 调用LLM服务处理聊天
     */
    private String processLlmChat(String personaPrompt, String message, String sessionId, String characterName) {
        try {
            return chatService.processTextChat(personaPrompt, message, sessionId);
        } catch (Exception llmEx) {
            String errorContext = characterName != null
                ? String.format("SessionID: %s, 角色: %s", sessionId, characterName)
                : String.format("SessionID: %s", sessionId);

            logger.error("调用LLM服务时发生错误, {}", errorContext, llmEx);
            throw new ChatServiceException("调用AI核心时出错: " + llmEx.getMessage(), llmEx);
        }
    }

    /**
     * 构建聊天响应
     */
    private ChatResponseDTO buildChatResponse(String reply, String sessionId) {
        if (reply != null && !reply.trim().isEmpty()) {
            logger.info("聊天处理完成: {}", reply.length());
            return ChatResponseDTO.success(reply, sessionId);
        } else {
            return ChatResponseDTO.error("生成回复失败");
        }
    }

    /**
     * 聊天服务异常
     */
    private static class ChatServiceException extends RuntimeException {
        public ChatServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 将请求DTO转换为实体
     */
    private Character convertToEntity(CharacterRequestDTO requestDTO) {
        Character character = new Character();
        character.setName(requestDTO.name());
        character.setPersonaPrompt(requestDTO.personaPrompt());
        character.setAvatarUrl(requestDTO.avatarUrl());
        character.setVoiceId(requestDTO.voiceId());
        character.setPublic(Objects.requireNonNullElse(requestDTO.isPublic(), true));
        return character;
    }

    /**
     * 将实体转换为响应DTO
     */
    private CharacterResponseDTO convertToResponseDTO(Character character) {
        return new CharacterResponseDTO(
                character.getId(),
                character.getName(),
                character.getPersonaPrompt(),
                character.getAvatarUrl(),
                character.getVoiceId(),
                character.isPublic(),
                character.getCreatedAt(),
                character.getUpdatedAt()
        );
    }
}