package com.dotlinea.soulecho.service.impl;

import com.aliyun.bailian20231229.Client;
import com.aliyun.bailian20231229.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.dotlinea.soulecho.constants.FileStatusEnum;
import com.dotlinea.soulecho.exception.BusinessException;
import com.dotlinea.soulecho.exception.ErrorCode;
import com.dotlinea.soulecho.exception.ResourceNotFoundException;
import com.dotlinea.soulecho.service.KnowledgeService;
import com.dotlinea.soulecho.entity.KnowledgeBase;
import com.dotlinea.soulecho.event.KnowledgeUploadEvent;
import com.dotlinea.soulecho.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

/**
 * 知识库服务实现类
 * <p>
 * 基于阿里云百炼托管知识库服务，使用官方SDK实现
 * 支持文件上传、解析和智能检索
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    @Value("${bailian.workspace.id}")
    private String workspaceId;

    @Value("${soul-echo.file.upload-path}")
    private String uploadPath;

    /**
     * 解析后的绝对路径，在应用启动时初始化
     */
    private Path resolvedUploadPath;

    private final Client bailianClient;
    private final OkHttpClient httpClient;
    private final KnowledgeBaseRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.dotlinea.soulecho.repository.CharacterRepository characterRepository;

    /**
     * 初始化上传路径
     * <p>
     * 将配置的相对路径转换为绝对路径，确保在任何环境下都能正常工作
     * </p>
     */
    @PostConstruct
    public void initUploadPath() {
        // 获取配置的路径
        String configuredPath = uploadPath;

        // 如果是相对路径，转换为绝对路径
        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            // 相对于当前工作目录（项目根目录）
            path = Paths.get(System.getProperty("user.dir"), configuredPath);
        }

        this.resolvedUploadPath = path.normalize();
        logger.info("知识库上传路径初始化完成 - 配置路径: {}, 解析后的绝对路径: {}",
                configuredPath, this.resolvedUploadPath);

        // 确保目录存在
        ensureUploadDirectoryExists();
    }

    /**
     * 确保上传目录存在
     */
    private void ensureUploadDirectoryExists() {
        try {
            if (!Files.exists(resolvedUploadPath)) {
                Files.createDirectories(resolvedUploadPath);
                logger.info("知识库上传目录已创建: {}", resolvedUploadPath);
            } else {
                logger.info("知识库上传目录已存在: {}", resolvedUploadPath);
            }

            // 验证目录是否可写
            if (!Files.isWritable(resolvedUploadPath)) {
                logger.error("知识库上传目录不可写: {}", resolvedUploadPath);
                throw new IllegalStateException("知识库上传目录不可写: " + resolvedUploadPath);
            }
        } catch (IOException e) {
            logger.error("创建知识库上传目录失败: {}", resolvedUploadPath, e);
            throw new IllegalStateException("创建知识库上传目录失败: " + resolvedUploadPath, e);
        }
    }

    @Override
    public List<String> search(String characterName, String query) {
        // 根据角色名称查找角色 - 保留降级逻辑
        try {
            if (characterName == null || characterName.trim().isEmpty()) {
                logger.warn("角色名称为空，无法检索知识库");
                return new ArrayList<>();
            }

            // 根据角色名称查找角色实体
            com.dotlinea.soulecho.entity.Character character = characterRepository.findByName(characterName);
            if (character == null) {
                logger.warn("未找到角色: {}，返回空结果", characterName);
                return new ArrayList<>();
            }

            // 使用查找到的角色 ID 进行知识库检索
            return searchByCharacterId(character.getId(), query);
        } catch (Exception e) {
            logger.error("检索知识库时发生异常，角色: {}, 查询: {}", characterName, query, e);
            return new ArrayList<>();
        }
    }

    /**
     * 上传文档到知识库（事件驱动模式）
     * <p>
     * 采用事件驱动架构，避免同步阻塞：
     * 1. 保存文件到本地临时目录
     * 2. 计算文件MD5哈希值
     * 3. 在数据库中创建状态为 "UPLOADING" 的记录
     * 4. 发布 KnowledgeUploadEvent 事件，触发异步上传流程
     * 5. 立即返回结果，不等待上传完成
     * </p>
     *
     * @param characterId 角色 ID
     * @param file        上传的文件
     * @return 上传结果信息，包含文件ID和状态
     */
    @Override
    public Map<String, Object> uploadDocument(Long characterId, MultipartFile file) {
        // === 步骤0: 校验角色是否存在 ===
        if (!characterRepository.existsById(characterId)) {
            logger.warn("角色不存在 - 角色ID: {}", characterId);
            throw new ResourceNotFoundException("角色不存在: " + characterId);
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_EMPTY);
        }

        var originalFileName = file.getOriginalFilename();
        var fileSize = file.getSize();

        try {
            logger.info("接收知识库文件上传请求 - 角色ID: {}, 文件名: {}, 大小: {} bytes",
                    characterId, originalFileName, fileSize);

            // === 步骤1: 保存文件到本地临时目录 ===
            // 使用在 @PostConstruct 中已经初始化好的绝对路径
            java.io.File uploadDir = resolvedUploadPath.toFile();

            logger.info("使用已初始化的上传路径: {}", uploadDir.getAbsolutePath());

            // 生成唯一文件名（UUID + 原始文件名）
            var uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
            java.io.File localFile = resolvedUploadPath.resolve(uniqueFileName).toFile();

            logger.debug("准备保存文件到: {}", localFile.getAbsolutePath());

            // 保存文件到本地
            try {
                file.transferTo(localFile);
                logger.info("文件已保存到本地: {}", localFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("文件保存失败 - 目标路径: {}, 错误: {}", localFile.getAbsolutePath(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.KNOWLEDGE_UPLOAD_FAILED,
                        "文件保存失败: " + e.getMessage() +
                        "。目标路径: " + localFile.getAbsolutePath() +
                        "。请检查磁盘空间和目录权限。");
            }

            // === 步骤2: 计算文件MD5哈希值 ===
            String fileMd5;
            try (var fileInputStream = new java.io.FileInputStream(localFile)) {
                fileMd5 = calculateMD5(fileInputStream);
            }
            logger.debug("文件MD5计算完成: {}", fileMd5);

            // === 步骤3: 在数据库中创建状态为 "UPLOADING" 的记录 ===
            var knowledgeBase = KnowledgeBase.builder()
                    .characterId(characterId)
                    .fileName(originalFileName)
                    .fileSize(fileSize)
                    .fileMd5(fileMd5)
                    .status(FileStatusEnum.UPLOADING.getCode())
                    .build();

            knowledgeBase = repository.save(knowledgeBase);
            logger.info("知识库记录已创建 - ID: {}, 状态: UPLOADING", knowledgeBase.getId());

            // === 步骤4: 发布 KnowledgeUploadEvent 事件 ===
            var uploadEvent = KnowledgeUploadEvent.builder()
                    .source(this)
                    .knowledgeBaseId(knowledgeBase.getId())
                    .characterId(characterId)
                    .localFilePath(localFile.getAbsolutePath())
                    .originalFileName(originalFileName)
                    .fileMd5(fileMd5)
                    .fileSize(fileSize)
                    .build();

            eventPublisher.publishEvent(uploadEvent);
            logger.info("KnowledgeUploadEvent 事件已发布 - ID: {}", knowledgeBase.getId());

            // === 步骤5: 立即返回结果 ===
            var result = new HashMap<String, Object>();
            result.put("id", knowledgeBase.getId());
            result.put("characterId", characterId);
            result.put("fileName", originalFileName);
            result.put("fileSize", fileSize);
            result.put("status", FileStatusEnum.UPLOADING.getCode());
            result.put("uploadTime", knowledgeBase.getGmtCreate());
            result.put("message", "文件上传任务已提交，正在异步处理中");

            logger.info("文件上传请求处理完成，异步任务已启动 - ID: {}", knowledgeBase.getId());
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("处理文件上传请求失败 - 文件名: {}", originalFileName, e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> searchByCharacterId(Long characterId, String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("查询文本为空");
            return new ArrayList<>();
        }

        try {
            logger.debug("检索知识库，角色ID: {}, 查询: {}", characterId, query);

            // 获取角色的专属知识库索引 ID
            com.dotlinea.soulecho.entity.Character character = characterRepository.findById(characterId).orElse(null);
            if (character == null) {
                logger.warn("角色不存在，ID: {}", characterId);
                return new ArrayList<>();
            }

            String knowledgeIndexId = character.getKnowledgeIndexId();
            if (knowledgeIndexId == null || knowledgeIndexId.trim().isEmpty()) {
                logger.warn("角色知识库索引ID为空，ID: {}", characterId);
                return new ArrayList<>();
            }

            // 构建检索请求
            RetrieveRequest retrieveRequest = new RetrieveRequest()
                    .setIndexId(knowledgeIndexId)
                    .setQuery(query)
                    .setDenseSimilarityTopK(5)
                    .setEnableRewrite(true);

            // 发起调用
            RetrieveResponse retrieveResponse = bailianClient.retrieveWithOptions(workspaceId, retrieveRequest, new HashMap<>(), new RuntimeOptions());

            // 校检响应基础结构
            if (retrieveResponse == null || retrieveResponse.getBody() == null || retrieveResponse.getBody().getData() == null || retrieveResponse.getBody().getData().getNodes() == null) {
                logger.warn("检索结果为空");
                return new ArrayList<>();
            }

            // 直接使用 SDK 提供对象，不转Map
            List<RetrieveResponseBody.RetrieveResponseBodyDataNodes> nodes = retrieveResponse.getBody().getData().getNodes();
            List<String> knowledgeChunks = new ArrayList<>();

            for (RetrieveResponseBody.RetrieveResponseBodyDataNodes node : nodes) {
                // 使用 getText() 方法直接获取内容
                if (node.getText() != null && !node.getText().isEmpty()) {
                    knowledgeChunks.add(node.getText());
                }
            }

            logger.info("检索到 {} 条知识片段", knowledgeChunks.size());
            return knowledgeChunks;

        } catch (Exception e) {
            logger.error("检索知识库时发生异常", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        logger.info("删除知识库文档，ID: {}", documentId);

        // 先从数据库中获取文档信息
        Optional<KnowledgeBase> knowledgeBaseOpt = repository.findById(documentId);
        if (knowledgeBaseOpt.isEmpty()) {
            logger.warn("文档不存在，ID: {}", documentId);
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND, "知识库文档不存在: ID[" + documentId + "]");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseOpt.get();

        try {
            // 获取角色的专属知识库索引 ID
            com.dotlinea.soulecho.entity.Character character = characterRepository.findById(knowledgeBase.getCharacterId()).orElse(null);
            if (character == null) {
                logger.warn("关联角色不存在，角色ID: {}", knowledgeBase.getCharacterId());
                throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND, "关联角色不存在");
            }

            String knowledgeIndexId = character.getKnowledgeIndexId();
            if (knowledgeIndexId == null || knowledgeIndexId.trim().isEmpty()) {
                logger.warn("角色知识库索引ID为空，角色ID: {}", knowledgeBase.getCharacterId());
                throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND, "角色知识库索引ID为空");
            }

            // 使用 SDK 删除文档
            DeleteIndexDocumentRequest deleteRequest = new DeleteIndexDocumentRequest()
                    .setIndexId(knowledgeIndexId)
                    .setDocumentIds(Collections.singletonList(knowledgeBase.getAliyunFileId()));

            DeleteIndexDocumentResponse deleteResponse = bailianClient.deleteIndexDocumentWithOptions(workspaceId, deleteRequest, new HashMap<>(), new RuntimeOptions());

            boolean success = (deleteResponse != null && deleteResponse.getBody() != null &&
                    deleteResponse.getBody().getSuccess() != null &&
                    deleteResponse.getBody().getSuccess());

            if (success) {
                // 如果百炼删除成功，从数据库中删除记录
                repository.deleteById(documentId);
                logger.info("文档删除成功，ID: {}", documentId);
            } else {
                logger.warn("百炼API删除失败，ID: {}", documentId);
                throw new BusinessException(ErrorCode.KNOWLEDGE_DELETE_FAILED, "云端文档删除失败");
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("删除知识库文档失败，ID: {}", documentId, e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_DELETE_FAILED, "文档删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listDocuments(Long characterId) {
        // 保留降级逻辑 - 查询失败时返回空列表
        try {
            logger.debug("获取角色 {} 的文档列表", characterId);

            List<KnowledgeBase> knowledgeBases = repository.findByCharacterIdOrderByGmtCreateDesc(characterId);
            List<Map<String, Object>> documents = new ArrayList<>();

            for (KnowledgeBase kb : knowledgeBases) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", kb.getId());
                doc.put("fileName", kb.getFileName());

                // 格式化文件大小显示
                if (kb.getFileSize() != null) {
                    long size = kb.getFileSize();
                    if (size >= 1024 * 1024) {
                        doc.put("fileSize", String.format("%.1fMB", size / (1024.0 * 1024.0)));
                    } else if (size >= 1024) {
                        doc.put("fileSize", String.format("%.1fKB", size / 1024.0));
                    } else {
                        doc.put("fileSize", size + "B");
                    }
                } else {
                    doc.put("fileSize", "未知");
                }

                // 状态映射：INDEXING -> INDEXING, ACTIVE -> ACTIVE, FAILED -> FAILED
                String status = kb.getStatus();
                if (FileStatusEnum.COMPLETED.getCode().equals(status)) {
                    status = FileStatusEnum.ACTIVE.getCode();
                }
                doc.put("status", status);
                doc.put("uploadTime", kb.getGmtCreate());
                documents.add(doc);
            }

            logger.info("获取到角色 {} 的 {} 个文档", characterId, documents.size());
            return documents;

        } catch (Exception e) {
            logger.error("获取文档列表失败，角色ID: {}", characterId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 计算文件MD5哈希值
     *
     * @param inputStream 文件输入流
     * @return MD5哈希值字符串
     */
    private String calculateMD5(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;

        while ((read = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * 上传文件到指定 URL
     *
     * @param uploadUrl   上传 URL
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @param headers     SDK 返回的请求头
     */
    private void uploadFileToUrl(String uploadUrl, InputStream inputStream, String fileName, Map<String, String> headers) throws IOException {
        // 1. 动态确定 Content-Type
        // 优先从 headers 中获取百炼指定的 Content-Type，如果没有则默认为 application/octet-stream
        String contentType = "application/octet-stream";
        if (headers != null && headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type");
        } else if (headers != null && headers.containsKey("content-type")) {
            contentType = headers.get("content-type");
        }

        final MediaType mediaType = MediaType.parse(contentType);

        RequestBody requestBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                // 使用动态获取的 Content-Type
                return mediaType;
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                okio.Source source = okio.Okio.source(inputStream);
                sink.writeAll(source);
                source.close();
            }

            @Override
            public long contentLength() {
                try {
                    return inputStream.available();
                } catch (IOException e) {
                    return -1;
                }
            }
        };

        Request.Builder requestBuilder = new Request.Builder()
                .url(uploadUrl)
                .put(requestBody);

        // 2. 添加所有 SDK 返回的 headers (包括 X-bailian-extra 等)
        // OkHttp 的 RequestBody 会自动处理 Content-Type，这里添加 Headers 主要是为了 X-bailian-extra
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                // Content-Length 和 Content-Type 由 RequestBody 管理，尽量不要重复添加以免冲突，但 X- 开头的必须加
                if (!"Content-Type".equalsIgnoreCase(header.getKey()) &&
                        !"Content-Length".equalsIgnoreCase(header.getKey())) {
                    requestBuilder.addHeader(header.getKey(), header.getValue());
                }
            }
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                // 记录详细的错误日志以便排查
                logger.error("OSS上传失败，URL: {}, Status: {}, Body: {}", uploadUrl, response.code(), errorBody);
                throw new BusinessException(ErrorCode.KNOWLEDGE_UPLOAD_FAILED, "文件上传失败，HTTP状态码: " + response.code());
            }
        }
    }
}