package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.dto.ApiResponse;
import com.dotlinea.soulecho.service.KnowledgeService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库控制器
 * <p>
 * 专门负责知识库相关的操作，包括文档上传、搜索、删除等
 * 遵循职责单一原则，将知识库操作从角色管理中分离
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@RestController
@RequestMapping("/api/knowledge")
@AllArgsConstructor
public class KnowledgeController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    /**
     * 上传知识库文档
     * <p>
     * 支持多种文件格式上传到百炼知识库
     * </p>
     *
     * @param characterId 角色ID
     * @param file        上传的文件
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadKnowledgeDocument(
            @RequestParam("characterId") Long characterId,
            @RequestParam("file") MultipartFile file) {

        logger.info("上传知识库文档，角色ID: {}, 文件名: {}", characterId, file.getOriginalFilename());

        Map<String, Object> result = knowledgeService.uploadDocument(characterId, file);
        return ApiResponse.success(result);
    }

    /**
     * 搜索知识库内容
     * <p>
     * 基于角色ID在知识库中进行智能检索
     * </p>
     *
     * @param characterId 角色ID
     * @param query       查询文本
     * @return 检索结果
     */
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchKnowledge(
            @RequestParam("characterId") Long characterId,
            @RequestParam("query") String query) {

        logger.debug("搜索知识库，角色ID: {}, 查询: {}", characterId, query);

        List<String> results = knowledgeService.searchByCharacterId(characterId, query);

        Map<String, Object> response = new HashMap<>();
        response.put("characterId", characterId);
        response.put("query", query);
        response.put("results", results);
        response.put("count", results.size());

        return ApiResponse.success(response);
    }

    /**
     * 获取某个角色的文档列表
     * <p>
     * 查询指定角色上传了哪些文件
     * </p>
     *
     * @param characterId 角色ID
     * @return 文档列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listDocuments(@RequestParam("characterId") Long characterId) {
        logger.debug("获取角色 {} 的文档列表", characterId);

        List<Map<String, Object>> documents = knowledgeService.listDocuments(characterId);
        return ApiResponse.success(documents);
    }

    /**
     * 删除知识库文档
     * <p>
     * 根据文档ID删除知识库中的文件
     * </p>
     *
     * @param id 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteKnowledgeDocument(@PathVariable Long id) {
        logger.info("删除知识库文档，ID: {}", id);

        knowledgeService.deleteDocument(id);
        return ApiResponse.success();
    }

    /**
     * 调试用知识检索接口
     * <p>
     * 供开发者在APIFox中调试RAG效果，前端不需要展示此功能
     * </p>
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping("/debug/search")
    public ApiResponse<Map<String, Object>> debugSearch(@RequestBody Map<String, Object> request) {
        Long characterId = Long.valueOf(request.get("characterId").toString());
        String query = request.get("query").toString();

        logger.debug("调试搜索知识库，角色ID: {}, 查询: {}", characterId, query);

        List<String> results = knowledgeService.searchByCharacterId(characterId, query);

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> matches = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> match = new HashMap<>();
            match.put("content", results.get(i));
            // 模拟相关性得分
            match.put("score", 1.0 - (i * 0.1));
            matches.add(match);
        }

        response.put("matches", matches);

        return ApiResponse.success(response);
    }
}