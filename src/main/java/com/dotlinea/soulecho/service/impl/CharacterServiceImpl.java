package com.dotlinea.soulecho.service.impl;

import com.dotlinea.soulecho.dto.CharacterDTO;
import com.dotlinea.soulecho.entity.Character;
import com.dotlinea.soulecho.repository.CharacterRepository;
import com.dotlinea.soulecho.service.CharacterService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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

    /**
     * 将实体转换为DTO
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
}