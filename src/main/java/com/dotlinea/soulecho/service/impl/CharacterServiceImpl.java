package com.dotlinea.soulecho.service.impl;

import com.dotlinea.soulecho.dto.CharacterDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.repository.CharacterRepository;
import com.dotlinea.soulecho.service.CharacterService;
import com.dotlinea.soulecho.service.RealtimeChatService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    private final CharacterRepository characterRepository;
    private final RealtimeChatService chatService;

    @Override
    @Transactional(readOnly = true)
    public List<CharacterDTO> findAll() {
        logger.debug("查询所有角色");
        try {
            return characterRepository.findAll()
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("查询所有角色失败", e);
            throw new RuntimeException("查询所有角色失败", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Character findById(Long id) {
        logger.debug("根据ID查找角色: {}", id);
        try {
            Optional<Character> character = characterRepository.findById(id);
            return character.orElse(null);
        } catch (Exception e) {
            logger.error("根据ID查找角色失败: {}", id, e);
            return null;
        }
    }

    @Override
    public Character save(Character character) {
        logger.debug("保存角色: {}", character.getName());
        try {
            return characterRepository.save(character);
        } catch (Exception e) {
            logger.error("保存角色失败: {}", character.getName(), e);
            throw new RuntimeException("保存角色失败", e);
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
            throw new RuntimeException("删除角色失败", e);
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
    public List<CharacterDTO> findAllPublic() {
        logger.debug("查询所有公开角色");
        try {
            return characterRepository.findByIsPublicTrue()
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("查询所有公开角色失败", e);
            throw new RuntimeException("查询所有公开角色失败", e);
        }
    }

    @Override
    public CharacterDTO updateCharacter(Long id, CharacterDTO characterDTO) {
        logger.debug("更新角色信息，ID: {}", id);
        try {
            Character existingCharacter = findById(id);
            if (existingCharacter == null) {
                throw new RuntimeException("未找到ID为 " + id + " 的角色");
            }

            // 更新角色信息
            if (characterDTO.getName() != null) {
                existingCharacter.setName(characterDTO.getName());
            }
            if (characterDTO.getPersonaPrompt() != null) {
                existingCharacter.setPersonaPrompt(characterDTO.getPersonaPrompt());
            }
            if (characterDTO.getAvatarUrl() != null) {
                existingCharacter.setAvatarUrl(characterDTO.getAvatarUrl());
            }
            if (characterDTO.getVoiceId() != null) {
                existingCharacter.setVoiceId(characterDTO.getVoiceId());
            }
            existingCharacter.setPublic(characterDTO.isPublic());

            Character updatedCharacter = save(existingCharacter);
            return convertToDTO(updatedCharacter);
        } catch (Exception e) {
            logger.error("更新角色失败，ID: {}", id, e);
            throw new RuntimeException("更新角色失败", e);
        }
    }

    @Override
    public ChatResponseDTO chat(ChatRequestDTO request) {
        logger.info("收到聊天请求: {}", request.getMessage());

        try {
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ChatResponseDTO.error("消息不能为空");
            }

            // 生成或使用现有会话ID
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = "chat_" + UUID.randomUUID();
            }

            // 查询角色
            if (request.getCharacterId() == null) {
                // characterId 为 null - 使用默认设定进行对话
                logger.debug("未提供角色ID，使用默认AI助手设定");

                String defaultPersonaPrompt = "你是一个友好、有帮助的AI助手，请用自然的方式与用户对话。";

                // 处理聊天
                String reply;
                try {
                    // 将对LLM的调用置于严密监控之下
                    reply = chatService.processTextChat(defaultPersonaPrompt, request.getMessage(), sessionId);
                } catch (Exception llmEx) {
                    // 如果LLM调用失败，立刻报告详细错误，而不是返回模糊信息
                    logger.error("调用LLM服务时发生错误, SessionID: {}", sessionId, llmEx);
                    return ChatResponseDTO.error("调用AI核心时出错: " + llmEx.getMessage());
                }

                if (reply != null && !reply.trim().isEmpty()) {
                    logger.info("聊天处理完成: {}", reply.length());
                    return ChatResponseDTO.success(reply, sessionId);
                } else {
                    return ChatResponseDTO.error("生成回复失败");
                }
            } else {
                // characterId 不为 null - 尝试查询角色
                Optional<Character> characterOptional = characterRepository.findById(request.getCharacterId());

                if (characterOptional.isPresent()) {
                    // 角色存在 - 执行正常的聊天逻辑
                    Character character = characterOptional.get();
                    String personaPrompt = character.getPersonaPrompt();

                    if (personaPrompt == null || personaPrompt.trim().isEmpty()) {
                        personaPrompt = "你是一个友好的AI助手，请用自然的方式与用户对话。";
                    }

                    logger.debug("使用角色 {} 的设定: {}", character.getName(), personaPrompt);

                    // 处理聊天
                    String reply;
                    try {
                        // 将对LLM的调用置于严密监控之下
                        reply = chatService.processTextChat(personaPrompt, request.getMessage(), sessionId);
                    } catch (Exception llmEx) {
                        // 如果LLM调用失败，立刻报告详细错误，而不是返回模糊信息
                        logger.error("调用LLM服务时发生错误, SessionID: {}, 角色: {}", sessionId, character.getName(), llmEx);
                        return ChatResponseDTO.error("调用AI核心时出错: " + llmEx.getMessage());
                    }

                    if (reply != null && !reply.trim().isEmpty()) {
                        logger.info("聊天处理完成: {}", reply.length());
                        return ChatResponseDTO.success(reply, sessionId);
                    } else {
                        return ChatResponseDTO.error("生成回复失败");
                    }
                } else {
                    // 角色不存在 - 返回引导性回复
                    logger.warn("未找到角色ID: {}，将引导用户。", request.getCharacterId());

                    String guideReply = "你好，你似乎想找一个我还不认识的角色。没关系，你可以先和我聊聊，或者在未来的版本中，我会学会如何创建新角色。现在，有什么可以帮你的吗？";

                    return ChatResponseDTO.success(guideReply, sessionId);
                }
            }

        } catch (Exception e) {
            logger.error("聊天处理失败", e);
            return ChatResponseDTO.error("服务器内部错误");
        }
    }

    /**
     * 将实体转换为DTO
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
}