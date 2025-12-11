package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.dto.CharacterRequestDTO;
import com.dotlinea.soulecho.dto.CharacterResponseDTO;
import com.dotlinea.soulecho.dto.ChatRequestDTO;
import com.dotlinea.soulecho.dto.ChatResponseDTO;
import com.dotlinea.soulecho.service.CharacterService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<List<CharacterResponseDTO>> getAllCharacters() {
        logger.debug("获取所有角色列表");
        List<CharacterResponseDTO> characters = characterService.findAll();
        return ResponseEntity.ok(characters);
    }

    /**
     * 根据ID获取角色
     * @param id 角色ID
     * @return 角色信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterResponseDTO> getCharacterById(@PathVariable Long id) {
        logger.debug("获取角色信息，ID: {}", id);
        CharacterResponseDTO character = characterService.findCharacterById(id);
        return ResponseEntity.ok(character);
    }

    /**
     * 创建角色
     * @param requestDTO 角色请求数据
     * @return 创建的角色信息
     */
    @PostMapping
    public ResponseEntity<CharacterResponseDTO> createCharacter(@Valid @RequestBody CharacterRequestDTO requestDTO) {
        logger.info("创建角色: {}", requestDTO.name());
        CharacterResponseDTO response = characterService.createCharacter(requestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * 更新角色信息
     * @param id 角色ID
     * @param requestDTO 更新的角色数据
     * @return 更新后的角色信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<CharacterResponseDTO> updateCharacter(@PathVariable Long id, @Valid @RequestBody CharacterRequestDTO requestDTO) {
        logger.info("更新角色信息，ID: {}", id);
        CharacterResponseDTO response = characterService.updateCharacter(id, requestDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除角色
     * @param id 角色ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable Long id) {
        logger.info("删除角色，ID: {}", id);
        boolean deleted = characterService.deleteById(id);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}