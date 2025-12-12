package com.dotlinea.soulecho.service.impl;

import com.aliyun.bailian20231229.Client;
import com.aliyun.bailian20231229.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.dotlinea.soulecho.service.KnowledgeService;
import com.dotlinea.soulecho.entity.KnowledgeBase;
import com.dotlinea.soulecho.event.KnowledgeUploadEvent;
import com.dotlinea.soulecho.repository.KnowledgeBaseRepository;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.accessKeySecret}")
    private String accessKeySecret;

    @Value("${bailian.workspace.id}")
    private String workspaceId;

    @Value("${bailian.knowledge.index.id}")
    private String indexId;

    @Value("${soul-echo.file.upload-path}")
    private String uploadPath;

    private Client bailianClient;
    private OkHttpClient httpClient;

    @Autowired
    private KnowledgeBaseRepository repository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        try {
            logger.info("初始化百炼知识库服务...");

            // 配置阿里云认证
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("bailian.cn-beijing.aliyuncs.com");

            // 创建百炼客户端
            bailianClient = new Client(config);

            // 初始化HTTP客户端（用于文件上传）
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            logger.info("百炼知识库服务初始化成功，Workspace: {}, Index: {}", workspaceId, indexId);
        } catch (Exception e) {
            logger.error("百炼知识库服务初始化失败", e);
            throw new RuntimeException("知识库服务初始化异常", e);
        }
    }

    @Override
    public List<String> search(String characterName, String query) {
        // 为了保持向后兼容性，使用searchByCharacterId的默认实现
        try {
            return searchByCharacterId(1L, query);
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
     * @param characterId 角色ID
     * @param file        上传的文件
     * @return 上传结果信息，包含文件ID和状态
     */
    @Override
    public Map<String, Object> uploadDocument(Long characterId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        var originalFileName = file.getOriginalFilename();
        var fileSize = file.getSize();

        try {
            logger.info("接收知识库文件上传请求 - 角色ID: {}, 文件名: {}, 大小: {} bytes",
                    characterId, originalFileName, fileSize);

            // === 步骤1: 保存文件到本地临时目录 ===
            var uploadDir = new java.io.File(uploadPath);
            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                throw new RuntimeException("无法创建上传目录: " + uploadPath);
            }

            // 生成唯一文件名（UUID + 原始文件名）
            var uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
            var localFilePath = uploadPath + java.io.File.separator + uniqueFileName;
            var localFile = new java.io.File(localFilePath);

            // 保存文件到本地
            file.transferTo(localFile);
            logger.debug("文件已保存到本地: {}", localFilePath);

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
                    .status("UPLOADING")
                    .build();

            knowledgeBase = repository.save(knowledgeBase);
            logger.info("知识库记录已创建 - ID: {}, 状态: UPLOADING", knowledgeBase.getId());

            // === 步骤4: 发布 KnowledgeUploadEvent 事件 ===
            var uploadEvent = KnowledgeUploadEvent.builder()
                    .source(this)
                    .knowledgeBaseId(knowledgeBase.getId())
                    .characterId(characterId)
                    .localFilePath(localFilePath)
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
            result.put("status", "UPLOADING");
            result.put("uploadTime", knowledgeBase.getCreatedAt());
            result.put("message", "文件上传任务已提交，正在异步处理中");

            logger.info("文件上传请求处理完成，异步任务已启动 - ID: {}", knowledgeBase.getId());
            return result;

        } catch (Exception e) {
            logger.error("处理文件上传请求失败 - 文件名: {}", originalFileName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
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

            // 1. 构建检索请求
            RetrieveRequest retrieveRequest = new RetrieveRequest()
                    .setIndexId(indexId)
                    .setQuery(query)
                    .setDenseSimilarityTopK(5)
                    .setEnableRewrite(true);

            // 2. 发起调用
            RetrieveResponse retrieveResponse = bailianClient.retrieveWithOptions(workspaceId, retrieveRequest, new HashMap<>(), new RuntimeOptions());

            // 3. 校检相应基础结构
            if (retrieveResponse == null || retrieveResponse.getBody() == null || retrieveResponse.getBody().getData() == null || retrieveResponse.getBody().getData().getNodes() == null) {
                logger.warn("检索结果为空");
                return new ArrayList<>();
            }

            // 4. 直接使用 SDK 提供对象，不转Map
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
    public boolean deleteDocument(Long documentId) {
        try {
            logger.info("删除知识库文档，ID: {}", documentId);

            // 先从数据库中获取文档信息
            Optional<KnowledgeBase> knowledgeBaseOpt = repository.findById(documentId);
            if (knowledgeBaseOpt.isEmpty()) {
                logger.warn("文档不存在，ID: {}", documentId);
                return false;
            }

            KnowledgeBase knowledgeBase = knowledgeBaseOpt.get();

            // 使用SDK删除文档
            DeleteIndexDocumentRequest deleteRequest = new DeleteIndexDocumentRequest()
                    .setIndexId(indexId)
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
            }

            return success;

        } catch (Exception e) {
            logger.error("删除知识库文档失败，ID: {}", documentId, e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> listDocuments(Long characterId) {
        try {
            logger.debug("获取角色 {} 的文档列表", characterId);

            List<KnowledgeBase> knowledgeBases = repository.findByCharacterIdOrderByCreatedAtDesc(characterId);
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
                if ("COMPLETED".equals(status)) {
                    status = "ACTIVE";
                }
                doc.put("status", status);
                doc.put("uploadTime", kb.getCreatedAt());
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
     * 上传文件到指定URL
     *
     * @param uploadUrl   上传URL
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @param headers     SDK返回的请求头
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
                throw new RuntimeException("文件上传失败，HTTP状态码: " + response.code() + ", 响应: " + errorBody);
            }
        }
    }
}
