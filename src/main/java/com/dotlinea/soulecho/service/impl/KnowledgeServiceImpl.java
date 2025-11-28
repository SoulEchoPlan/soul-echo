package com.dotlinea.soulecho.service.impl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.dotlinea.soulecho.service.KnowledgeService;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 知识库服务实现类
 * <p>
 * 基于阿里云OpenSearch向量检索服务和DashScope文本向量化模型
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    @Value("${vector.api.key}")
    private String apiKey;

    @Value("${vector.opensearch.endpoint}")
    private String openSearchEndpoint;

    @Value("${vector.opensearch.instance.id}")
    private String instanceId;

    @Value("${vector.opensearch.app.name:soul-echo}")
    private String appName;

    @Value("${vector.embedding.model:text-embedding-v2}")
    private String embeddingModel;

    @Value("${vector.search.top.k:5}")
    private int topK;

    private OkHttpClient httpClient;
    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        try {
            logger.info("初始化知识库服务...");

            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            textEmbedding = new TextEmbedding();

            logger.info("知识库服务初始化成功，使用向量模型: {}", embeddingModel);
        } catch (Exception e) {
            logger.error("知识库服务初始化失败", e);
            throw new RuntimeException("知识库服务初始化异常", e);
        }
    }

    @Override
    public void addDocument(String characterName, String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("尝试添加空文档到知识库");
            return;
        }

        try {
            logger.debug("为角色 {} 添加文档到知识库: {}", characterName, text.substring(0, Math.min(50, text.length())));

            List<Double> vector = embedText(text);

            JSONObject document = new JSONObject();
            document.put("id", System.currentTimeMillis() + "_" + characterName);
            document.put("character_name", characterName);
            document.put("text", text);
            document.put("vector", new JSONArray(vector));

            String url = String.format("%s/v3/openapi/apps/%s/actions/pushing", openSearchEndpoint, appName);

            JSONArray documents = new JSONArray();
            documents.put(document);

            JSONObject requestBody = new JSONObject();
            requestBody.put("items", documents);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "DASHSCOPE " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("添加文档到知识库失败，HTTP状态码: {}, 响应: {}",
                            response.code(), response.body() != null ? response.body().string() : "");
                } else {
                    logger.info("成功添加文档到知识库，角色: {}", characterName);
                }
            }

        } catch (Exception e) {
            logger.error("添加文档到知识库时发生异常", e);
        }
    }

    @Override
    public List<String> search(String characterName, String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("查询文本为空");
            return new ArrayList<>();
        }

        try {
            logger.debug("为角色 {} 检索知识库，查询: {}", characterName, query);

            List<Double> queryVector = embedText(query);

            String url = String.format("%s/v3/openapi/apps/%s/actions/query", openSearchEndpoint, appName);

            JSONObject vectorQuery = new JSONObject();
            vectorQuery.put("vector", new JSONArray(queryVector));
            vectorQuery.put("top_k", topK);

            JSONObject filter = new JSONObject();
            filter.put("character_name", characterName);

            JSONObject requestBody = new JSONObject();
            requestBody.put("query", vectorQuery);
            requestBody.put("filter", filter);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "DASHSCOPE " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            String responseBody;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("检索知识库失败，HTTP状态码: {}, 响应: {}",
                            response.code(), response.body() != null ? response.body().string() : "");
                    return new ArrayList<>();
                }

                if (response.body() != null) {
                    responseBody = response.body().string();
                }
            }
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray results = jsonResponse.optJSONArray("results");

            List<String> knowledgeChunks = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    String text = result.optString("text");
                    if (text != null && !text.isEmpty()) {
                        knowledgeChunks.add(text);
                    }
                }
            }

            logger.info("为角色 {} 检索到 {} 条知识片段", characterName, knowledgeChunks.size());
            return knowledgeChunks;

        } catch (Exception e) {
            logger.error("检索知识库时发生异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将文本转换为向量
     *
     * @param text 文本内容
     * @return 向量表示
     */
    private List<Double> embedText(String text) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(embeddingModel)
                    .texts(Collections.singletonList(text))
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);

            if (result.getOutput() != null &&
                    result.getOutput().getEmbeddings() != null &&
                    !result.getOutput().getEmbeddings().isEmpty()) {

                return result.getOutput().getEmbeddings().get(0).getEmbedding();
            }

            logger.error("文本向量化返回空结果");
            return new ArrayList<>();

        } catch (Exception e) {
            logger.error("文本向量化失败", e);
            return new ArrayList<>();
        }
    }
}
