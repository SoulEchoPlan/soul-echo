package com.dotlinea.soulecho.listener;

import com.aliyun.bailian20231229.Client;
import com.aliyun.bailian20231229.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.dotlinea.soulecho.constants.FileStatusEnum;
import com.dotlinea.soulecho.event.KnowledgeUploadEvent;
import com.dotlinea.soulecho.repository.CharacterRepository;
import com.dotlinea.soulecho.repository.KnowledgeBaseRepository;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 知识库上传事件监听器
 * <p>
 * 异步处理知识库文件上传和索引构建任务,避免阻塞主线程。
 * 监听 KnowledgeUploadEvent 事件,执行以下流程:
 * 1. 从本地临时目录读取文件
 * 2. 上传文件到阿里云百炼
 * 3. 提交索引构建任务
 * 4. 更新数据库状态
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Slf4j
@Component
public class KnowledgeUploadListener {

    @Value("${bailian.workspace.id}")
    private String workspaceId;

    @Autowired
    private Client bailianClient;

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private KnowledgeBaseRepository repository;

    @Autowired
    private CharacterRepository characterRepository;

    /**
     * 处理知识库上传事件
     * <p>
     * 此方法使用 @Async 注解,在独立的线程池中异步执行,避免阻塞主线程。
     * 使用 @Transactional 确保数据库操作的事务性。
     * </p>
     *
     * @param event 知识库上传事件
     */
    @Async("knowledgeUploadExecutor")
    @EventListener
    @Transactional
    public void handleKnowledgeUploadEvent(KnowledgeUploadEvent event) {
        log.info("开始处理知识库上传事件: {}", event);

        var knowledgeBaseId = event.getKnowledgeBaseId();
        var characterId = event.getCharacterId();
        var localFilePath = event.getLocalFilePath();
        var originalFileName = event.getOriginalFileName();
        var fileMd5 = event.getFileMd5();
        var fileSize = event.getFileSize();

        try {
            // 获取角色的专属知识库索引 ID
            var character = characterRepository.findById(characterId).orElse(null);
            if (character == null) {
                throw new RuntimeException("角色不存在，ID: " + characterId);
            }

            var knowledgeIndexId = character.getKnowledgeIndexId();
            if (knowledgeIndexId == null || knowledgeIndexId.trim().isEmpty()) {
                throw new RuntimeException("角色知识库索引ID为空，角色ID: " + characterId);
            }

            log.info("使用角色专属知识库索引 - 角色ID: {}, 索引ID: {}", characterId, knowledgeIndexId);

            // === 步骤1: 申请文件上传租约 ===
            log.debug("步骤1: 申请文件上传租约 - {}", originalFileName);

            var leaseRequest = new ApplyFileUploadLeaseRequest()
                    .setFileName(originalFileName)
                    .setMd5(fileMd5)
                    .setSizeInBytes(String.valueOf(fileSize));

            ApplyFileUploadLeaseResponse leaseResponse = bailianClient.applyFileUploadLeaseWithOptions(
                    "default", workspaceId, leaseRequest, new HashMap<>(), new RuntimeOptions());

            if (leaseResponse == null || leaseResponse.getBody() == null ||
                    leaseResponse.getBody().getData() == null ||
                    leaseResponse.getBody().getData().getParam() == null ||
                    leaseResponse.getBody().getData().getParam().getUrl() == null) {
                throw new RuntimeException("申请文件上传租约失败");
            }

            var uploadUrl = leaseResponse.getBody().getData().getParam().getUrl();
            var leaseId = leaseResponse.getBody().getData().getFileUploadLeaseId();

            // 解析上传头信息
            var uploadHeaders = extractUploadHeaders(leaseResponse.getBody().getData().getParam().getHeaders());

            log.info("获取文件上传租约成功 - LeaseId: {}", leaseId);

            // === 步骤2: 上传文件到百炼提供的URL ===
            log.debug("步骤2: 上传文件到百炼 - LeaseId: {}", leaseId);

            Path filePath = Paths.get(localFilePath);
            try (InputStream fileInputStream = new FileInputStream(filePath.toFile())) {
                uploadFileToBailian(uploadUrl, fileInputStream, originalFileName, uploadHeaders);
            }

            log.info("文件上传成功 - LeaseId: {}", leaseId);

            // === 步骤3: 通知百炼文件上传完成 ===
            log.debug("步骤3: 通知百炼文件上传完成");

            var addFileRequest = new AddFileRequest()
                    .setLeaseId(leaseId)
                    .setCategoryId("default")
                    .setParser("DASHSCOPE_DOCMIND");

            AddFileResponse addFileResponse = bailianClient.addFileWithOptions(
                    workspaceId, addFileRequest, new HashMap<>(), new RuntimeOptions());

            if (addFileResponse == null || addFileResponse.getBody() == null ||
                    addFileResponse.getBody().getData() == null ||
                    addFileResponse.getBody().getData().getFileId() == null) {
                throw new RuntimeException("文件注册失败");
            }

            var aliyunFileId = addFileResponse.getBody().getData().getFileId();
            log.info("文件注册成功 - FileId: {}", aliyunFileId);

            // === 步骤4: 提交索引构建任务 ===
            log.debug("步骤4: 提交索引构建任务 - FileId: {}", aliyunFileId);

            var indexRequest = new SubmitIndexAddDocumentsJobRequest()
                    .setIndexId(knowledgeIndexId)
                    .setSourceType("DATA_CENTER_FILE")
                    .setDocumentIds(Collections.singletonList(aliyunFileId));

            SubmitIndexAddDocumentsJobResponse indexResponse = bailianClient.submitIndexAddDocumentsJobWithOptions(
                    workspaceId, indexRequest, new HashMap<>(), new RuntimeOptions());

            if (indexResponse == null || indexResponse.getBody() == null ||
                    indexResponse.getBody().getData() == null ||
                    indexResponse.getBody().getData().getId() == null) {
                throw new RuntimeException("提交索引任务失败");
            }

            var jobId = indexResponse.getBody().getData().getId();
            log.info("索引任务提交成功 - JobId: {}", jobId);

            // === 步骤5: 更新数据库状态为 INDEXING ===
            updateKnowledgeBaseStatus(knowledgeBaseId, aliyunFileId, jobId, FileStatusEnum.INDEXING.getCode(), null);

            // === 步骤5.5: 轮询索引任务状态直到完成 ===
            pollIndexJobStatus(knowledgeBaseId, knowledgeIndexId, jobId);

            // === 步骤6: 清理本地临时文件 ===
            cleanupLocalFile(localFilePath);

            log.info("知识库上传处理完成 - ID: {}", knowledgeBaseId);

        } catch (Exception e) {
            log.error("知识库上传处理失败 - ID: {}", knowledgeBaseId, e);

            // 更新数据库状态为 FAILED,并记录错误信息
            updateKnowledgeBaseStatus(knowledgeBaseId, null, null, FileStatusEnum.FAILED.getCode(), e.getMessage());

            // 清理本地临时文件
            cleanupLocalFile(localFilePath);
        }
    }

    /**
     * 从 SDK 返回的 Headers 对象中提取上传头信息
     *
     * @param headersObj SDK 返回的 Headers 对象
     * @return 上传头信息 Map
     */
    private Map<String, String> extractUploadHeaders(Object headersObj) {
        var uploadHeaders = new HashMap<String, String>();
        if (headersObj instanceof Map<?, ?> rawHeaders) {
            for (Map.Entry<?, ?> entry : rawHeaders.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    uploadHeaders.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }
        return uploadHeaders;
    }

    /**
     * 上传文件到百炼提供的 URL
     *
     * @param uploadUrl   上传 URL
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @param headers     上传头信息
     * @throws IOException 上传失败
     */
    private void uploadFileToBailian(String uploadUrl, InputStream inputStream,
                                      String fileName, Map<String, String> headers) throws IOException {
        // 确定Content-Type
        var contentType = "application/octet-stream";
        if (headers != null) {
            contentType = headers.getOrDefault("Content-Type",
                    headers.getOrDefault("content-type", contentType));
        }

        var mediaType = MediaType.parse(contentType);

        // 构建请求体
        RequestBody requestBody = new RequestBody() {
            @Override
            public MediaType contentType() {
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

        // 构建请求
        Request.Builder requestBuilder = new Request.Builder()
                .url(uploadUrl)
                .put(requestBody);

        // 添加自定义头信息(排除Content-Type和Content-Length,由RequestBody管理)
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (!"Content-Type".equalsIgnoreCase(key) && !"Content-Length".equalsIgnoreCase(key)) {
                    requestBuilder.addHeader(key, value);
                }
            });
        }

        Request request = requestBuilder.build();

        // 执行上传
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                var errorBody = response.body() != null ? response.body().string() : "";
                log.error("文件上传失败 - URL: {}, Status: {}, Body: {}", uploadUrl, response.code(), errorBody);
                throw new IOException("文件上传失败,HTTP状态码: " + response.code() + ",响应: " + errorBody);
            }
        }
    }

    /**
     * 更新知识库状态
     *
     * @param knowledgeBaseId 知识库 ID
     * @param aliyunFileId    阿里云文件 ID
     * @param jobId           索引任务 ID
     * @param status          状态
     * @param errorMessage    错误信息
     */
    private void updateKnowledgeBaseStatus(Long knowledgeBaseId, String aliyunFileId,
                                           String jobId, String status, String errorMessage) {
        try {
            var knowledgeBase = repository.findById(knowledgeBaseId)
                    .orElseThrow(() -> new RuntimeException("知识库记录不存在: " + knowledgeBaseId));

            if (aliyunFileId != null) {
                knowledgeBase.setAliyunFileId(aliyunFileId);
            }
            if (jobId != null) {
                knowledgeBase.setJobId(jobId);
            }
            knowledgeBase.setStatus(status);
            knowledgeBase.setErrorMessage(errorMessage);

            repository.save(knowledgeBase);

            log.debug("更新知识库状态成功 - ID: {}, Status: {}", knowledgeBaseId, status);
        } catch (Exception e) {
            log.error("更新知识库状态失败 - ID: {}", knowledgeBaseId, e);
        }
    }

    /**
     * 轮询索引任务状态直到完成
     * <p>
     * 主动查询阿里云索引任务状态，并根据最终状态更新数据库记录。
     * 包含5分钟超时机制，避免长时间阻塞。
     * </p>
     *
     * @param knowledgeBaseId   知识库 ID
     * @param knowledgeIndexId  知识库索引 ID
     * @param jobId             索引任务 ID
     */
    private void pollIndexJobStatus(Long knowledgeBaseId, String knowledgeIndexId, String jobId) {
        log.info("开始轮询索引任务状态 - JobId: {}, IndexId: {}", jobId, knowledgeIndexId);

        // 5分钟超时
        final long TIMEOUT_MS = 5 * 60 * 1000L;
        // 2秒轮询间隔
        final long POLL_INTERVAL_MS = 2000L;    
        long startTime = System.currentTimeMillis();

        while (true) {
            // 检查超时
            if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                log.warn("索引任务轮询超时 - JobId: {}, 超时时间: {}分钟", jobId, TIMEOUT_MS / 60000);
                updateKnowledgeBaseStatus(knowledgeBaseId, null, null,
                        FileStatusEnum.FAILED.getCode(), "索引任务超时（5分钟）");
                break;
            }

            try {
                // 查询索引任务状态
                var statusRequest = new GetIndexJobStatusRequest()
                        .setIndexId(knowledgeIndexId)
                        .setJobId(jobId);

                GetIndexJobStatusResponse statusResponse = bailianClient.getIndexJobStatusWithOptions(
                        workspaceId, statusRequest, new HashMap<>(), new RuntimeOptions());

                if (statusResponse == null || statusResponse.getBody() == null ||
                        statusResponse.getBody().getData() == null) {
                    log.warn("查询索引任务状态返回空结果 - JobId: {}", jobId);
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }

                var status = statusResponse.getBody().getData().getStatus();
                log.debug("索引任务状态 - JobId: {}, Status: {}", jobId, status);

                // 情况A: 成功完成
                if ("COMPLETED".equalsIgnoreCase(status) || "FINISH".equalsIgnoreCase(status)) {
                    log.info("索引任务成功完成 - JobId: {}", jobId);

                    // 更新数据库状态为 COMPLETED
                    var knowledgeBase = repository.findById(knowledgeBaseId).orElse(null);
                    if (knowledgeBase != null) {
                        knowledgeBase.setStatus(FileStatusEnum.COMPLETED.getCode());
                        knowledgeBase.setErrorMessage(null);
                        repository.save(knowledgeBase);
                        log.info("知识库索引完成并更新状态 - ID: {}, IndexId: {}", knowledgeBaseId, knowledgeIndexId);
                    }
                    break;
                }

                // 情况B: 失败
                if ("FAILED".equalsIgnoreCase(status) || "INSERT_ERROR".equalsIgnoreCase(status)) {
                    log.error("索引任务失败 - JobId: {}, Status: {}", jobId, status);

                    // 获取错误信息
                    var errorMsg = "索引任务失败，状态: " + status;
                    var message = statusResponse.getBody().getMessage();
                    if (message != null && !message.trim().isEmpty()) {
                        errorMsg = message;
                    }

                    updateKnowledgeBaseStatus(knowledgeBaseId, null, null,
                            FileStatusEnum.FAILED.getCode(), errorMsg);
                    break;
                }

                // 情况C: 进行中 (RUNNING, PENDING等)
                log.debug("索引任务进行中 - JobId: {}, Status: {}", jobId, status);
                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                log.warn("索引任务轮询被中断 - JobId: {}", jobId, e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("查询索引任务状态异常 - JobId: {}", jobId, e);
                // 网络波动时继续轮询，不立即退出
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("索引任务状态轮询结束 - JobId: {}", jobId);
    }

    /**
     * 清理本地临时文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        try {
            Path filePath = Paths.get(localFilePath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("清理临时文件成功: {}", localFilePath);
            }
        } catch (Exception e) {
            log.warn("清理临时文件失败: {}", localFilePath, e);
        }
    }
}