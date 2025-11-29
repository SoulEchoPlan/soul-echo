package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.service.KnowledgeService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@CrossOrigin(origins = "*")
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
    public ResponseEntity<Map<String, Object>> uploadKnowledgeDocument(
            @RequestParam("characterId") Long characterId,
            @RequestParam("file") MultipartFile file) {
        try {
            logger.info("上传知识库文档，角色ID: {}, 文件名: {}", characterId, file.getOriginalFilename());

            Map<String, Object> result = knowledgeService.uploadDocument(characterId, file);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("上传知识库文档失败，角色ID: {}, 文件名: {}", characterId, file.getOriginalFilename(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "文档上传失败");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", "ERROR");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
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
    public ResponseEntity<Map<String, Object>> searchKnowledge(
            @RequestParam("characterId") Long characterId,
            @RequestParam("query") String query) {
        try {
            logger.debug("搜索知识库，角色ID: {}, 查询: {}", characterId, query);

            List<String> results = knowledgeService.searchByCharacterId(characterId, query);

            Map<String, Object> response = new HashMap<>();
            response.put("characterId", characterId);
            response.put("query", query);
            response.put("results", results);
            response.put("count", results.size());
            response.put("status", "SUCCESS");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("搜索知识库失败，角色ID: {}, 查询: {}", characterId, query, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "搜索失败");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", "ERROR");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
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
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(@RequestParam("characterId") Long characterId) {
        try {
            logger.debug("获取角色 {} 的文档列表", characterId);

            List<Map<String, Object>> documents = knowledgeService.listDocuments(characterId);

            Map<String, Object> response = new HashMap<>();
            response.put("characterId", characterId);
            response.put("documents", documents);
            response.put("count", documents.size());
            response.put("status", "SUCCESS");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取文档列表失败，角色ID: {}", characterId, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取文档列表失败");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", "ERROR");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 删除知识库文档
     * <p>
     * 根据文档ID删除知识库中的文件
     * </p>
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteKnowledgeDocument(@PathVariable Long documentId) {
        try {
            logger.info("删除知识库文档，ID: {}", documentId);

            boolean success = knowledgeService.deleteDocument(documentId);

            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("success", success);
            response.put("status", success ? "SUCCESS" : "FAILED");
            response.put("message", success ? "文档删除成功" : "文档删除失败");

            return success ? ResponseEntity.ok(response) : ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            logger.error("删除知识库文档失败，ID: {}", documentId, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "删除失败");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("status", "ERROR");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}