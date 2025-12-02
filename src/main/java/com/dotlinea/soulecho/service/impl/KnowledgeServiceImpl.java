package com.dotlinea.soulecho.service.impl;

import org.json.JSONObject;
import com.dotlinea.soulecho.service.KnowledgeService;
import com.dotlinea.soulecho.entity.KnowledgeBase;
import com.dotlinea.soulecho.repository.KnowledgeBaseRepository;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * 基于阿里云百炼托管知识库服务，使用DashScope SDK实现
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

    @Value("${bailian.workspace.id}")
    private String workspaceId;

    @Value("${bailian.knowledge.index.id}")
    private String indexId;

    @Value("${bailian.api.key}")
    private String apiKey;

    private OkHttpClient httpClient;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    // 百炼API端点
    private static final String BAILIAN_API_BASE = "https://bailian.cn-beijing.aliyuncs.com";
    private static final String BAILIAN_API_VERSION = "2023-12-29";

    @PostConstruct
    public void init() {
        try {
            logger.info("初始化百炼知识库服务...");

            // 初始化HTTP客户端
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

    /**
     * 向知识库中添加文本文档
     *
     * @param characterName 角色名称，用于标识文档所属的角色
     * @param text 文档内容文本，不能为空或空白字符串
     */
    @Override
    public void addDocument(String characterName, String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("尝试添加空文档到知识库");
            return;
        }

        try {
            logger.info("开始为角色 {} 添加文本文档到知识库", characterName);

            // 构造调用百炼API的请求参数
            Map<String, Object> request = new HashMap<>();
            request.put("IndexId", indexId);
            request.put("Content", text);
            request.put("CharacterName", characterName);
            request.put("DocumentName", characterName + "_" + System.currentTimeMillis());

            // 调用百炼的文本文档添加API接口
            Map<String, Object> response = callBailianAPI("AddTextDocument", request);

            // 处理API响应结果
            if (response != null && response.containsKey("DocumentId")) {
                logger.info("成功为角色 {} 添加文本文档到知识库，DocumentId: {}",
                    characterName, response.get("DocumentId"));
            } else {
                logger.error("为角色 {} 添加文本文档失败，响应异常", characterName);
            }

        } catch (Exception e) {
            logger.error("为角色 {} 添加文本文档时发生异常", characterName, e);
            throw new RuntimeException("添加文本文档失败: " + e.getMessage(), e);
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

    @Override
    public Map<String, Object> uploadDocument(Long characterId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        String aliyunFileId = null;
        String jobId = null;

        try {
            logger.info("开始上传文件到百炼知识库，角色ID: {}, 文件名: {}", characterId, file.getOriginalFilename());

            // 第一步：申请文件上传租约并上传文件，获取 aliyunFileId
            try {
                // 1. 申请文件上传租约
                String md5Hash = calculateMD5(file.getInputStream());
                long fileSize = file.getSize();

                Map<String, Object> leaseRequest = new HashMap<>();
                leaseRequest.put("FileName", file.getOriginalFilename());
                leaseRequest.put("FileMd5", md5Hash);
                leaseRequest.put("FileSize", fileSize);

                Map<String, Object> leaseResponse = callBailianAPI("ApplyFileUploadLease", leaseRequest);

                if (leaseResponse == null || !leaseResponse.containsKey("UploadUrl")) {
                    throw new RuntimeException("申请文件上传租约失败");
                }

                String uploadUrl = (String) leaseResponse.get("UploadUrl");
                String fileId = (String) leaseResponse.get("FileId");

                // 2. 上传文件到提供的URL
                uploadFileToUrl(uploadUrl, file.getInputStream(), file.getOriginalFilename());

                // 3. 通知百炼文件已上传完成
                Map<String, Object> addFileRequest = new HashMap<>();
                addFileRequest.put("FileId", fileId);

                Map<String, Object> addFileResponse = callBailianAPI("AddFile", addFileRequest);

                if (addFileResponse == null || !addFileResponse.containsKey("AliyunFileId")) {
                    throw new RuntimeException("文件添加通知失败");
                }

                aliyunFileId = (String) addFileResponse.get("AliyunFileId");

                // 4. 提交索引任务，将文件添加到知识库索引
                Map<String, Object> indexRequest = new HashMap<>();
                indexRequest.put("IndexId", indexId);
                List<String> fileIds = Arrays.asList(aliyunFileId);
                indexRequest.put("FileIds", fileIds);

                Map<String, Object> indexResponse = callBailianAPI("SubmitIndexAddDocumentsJob", indexRequest);

                if (indexResponse == null || !indexResponse.containsKey("JobId")) {
                    throw new RuntimeException("提交索引任务失败");
                }

                jobId = (String) indexResponse.get("JobId");

                logger.info("文件上传成功，AliyunFileId: {}, JobId: {}", aliyunFileId, jobId);

            } catch (Exception e) {
                logger.error("文件上传到百炼失败", e);
                throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
            }

            // 第二步：构建完整的 KnowledgeBase 实体（包含已获取的 aliyunFileId）
            KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                    .characterId(characterId)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .fileMd5(calculateMD5(file.getInputStream()))
                    .aliyunFileId(aliyunFileId)
                    .jobId(jobId)
                    // 索引中
                    .status("INDEXING")
                    .build();

            // 第三步：一次性保存到数据库
            knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);

            // 构造返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("id", knowledgeBase.getId());
            result.put("aliyunFileId", aliyunFileId);
            result.put("characterId", characterId);
            result.put("fileName", file.getOriginalFilename());
            result.put("status", "INDEXING"); // 索引中
            result.put("uploadTime", knowledgeBase.getCreatedAt());

            logger.info("知识库记录保存成功，ID: {}, 状态: INDEXING", knowledgeBase.getId());
            return result;

        } catch (Exception e) {
            logger.error("上传文件到百炼知识库失败", e);
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

            Map<String, Object> retrieveRequest = new HashMap<>();
            retrieveRequest.put("IndexId", indexId);
            retrieveRequest.put("Query", query);
            retrieveRequest.put("TopK", 5);

            Map<String, Object> retrieveResponse = callBailianAPI("Retrieve", retrieveRequest);

            if (retrieveResponse == null || !retrieveResponse.containsKey("Results")) {
                logger.warn("检索结果为空");
                return new ArrayList<>();
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) retrieveResponse.get("Results");
            List<String> knowledgeChunks = new ArrayList<>();

            if (results != null) {
                for (Map<String, Object> result : results) {
                    String text = (String) result.get("Text");
                    if (text != null && !text.isEmpty()) {
                        knowledgeChunks.add(text);
                    }
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
            Optional<KnowledgeBase> knowledgeBaseOpt = knowledgeBaseRepository.findById(documentId);
            if (knowledgeBaseOpt.isEmpty()) {
                logger.warn("文档不存在，ID: {}", documentId);
                return false;
            }

            KnowledgeBase knowledgeBase = knowledgeBaseOpt.get();

            // 调用百炼API删除文档
            Map<String, Object> deleteRequest = new HashMap<>();
            deleteRequest.put("IndexId", indexId);
            deleteRequest.put("DocumentId", knowledgeBase.getAliyunFileId());

            Map<String, Object> deleteResponse = callBailianAPI("DeleteIndexDocument", deleteRequest);

            boolean success = (deleteResponse != null && deleteResponse.containsKey("Success") &&
                              Boolean.TRUE.equals(deleteResponse.get("Success")));

            if (success) {
                // 如果百炼删除成功，从数据库中删除记录
                knowledgeBaseRepository.deleteById(documentId);
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

            List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findByCharacterIdOrderByCreatedAtDesc(characterId);
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
                    status = "ACTIVE"; // 文档期望ACTIVE状态
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
     * 调用百炼API
     *
     * @param action API动作名称
     * @param params 请求参数
     * @return 响应结果
     */
    private Map<String, Object> callBailianAPI(String action, Map<String, Object> params) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("Action", action);
            request.put("Version", BAILIAN_API_VERSION);
            request.put("WorkspaceId", workspaceId);
            request.putAll(params);

            // 构建请求体
            JSONObject jsonRequest = new JSONObject(request);
            String jsonBody = jsonRequest.toString();

            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));

            Request httpRequest = new Request.Builder()
                    .url(BAILIAN_API_BASE)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-DashScope-SSE", "disable")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new RuntimeException("API调用失败，HTTP状态码: " + response.code() + ", 响应: " + errorBody);
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                return jsonResponse.toMap();
            }

        } catch (Exception e) {
            logger.error("调用百炼API失败，Action: {}", action, e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
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
     * @param uploadUrl     上传URL
     * @param inputStream   文件输入流
     * @param fileName      文件名
     */
    private void uploadFileToUrl(String uploadUrl, InputStream inputStream, String fileName) throws IOException {
        RequestBody requestBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                okio.Source source = okio.Okio.source(inputStream);
                sink.writeAll(source);
                source.close();
            }

            @Override
            public long contentLength() {
                return -1; // Unknown length
            }
        };

        Request request = new Request.Builder()
                .url(uploadUrl)
                .addHeader("X-Bailian-Extra", fileName)
                .put(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("文件上传失败，HTTP状态码: " + response.code() + ", 响应: " + errorBody);
            }
        }
    }
}
