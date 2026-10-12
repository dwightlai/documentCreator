package cn.gov.document.platform.knowledge.vector;

import cn.gov.document.platform.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Milvus RESTful API v2 适配。集合使用快速建表模式：主键 id（切片 ID）、vector 向量字段（COSINE），租户和文档 ID 作为动态字段，
 * 检索时以 {@code tenant_id == ... and document_id in [...]} 过滤，保证权限前置。
 */
public class MilvusVectorStore implements VectorStore {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final String endpoint;
    private final String collection;
    private final int dimensions;
    private final String token;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private volatile boolean collectionReady;

    public MilvusVectorStore(
            String endpoint, String collection, int dimensions, String token, ObjectMapper objectMapper) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.collection = collection;
        this.dimensions = dimensions;
        this.token = token;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String name() {
        return "milvus";
    }

    @Override
    public void upsert(String tenantId, List<Record> records) {
        if (records.isEmpty()) return;
        ensureCollection();
        List<Map<String, Object>> data = new ArrayList<>();
        for (Record record : records) {
            if (record.vector().length != dimensions)
                throw BusinessException.unprocessable(
                        "向量维度 " + record.vector().length + " 与 Milvus 集合维度 " + dimensions + " 不一致");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", safe(record.chunkId()));
            row.put("vector", record.vector());
            row.put("tenant_id", safe(tenantId));
            row.put("document_id", safe(record.documentId()));
            data.add(row);
        }
        call("/v2/vectordb/entities/upsert", Map.of("collectionName", collection, "data", data));
    }

    @Override
    public void deleteByDocument(String tenantId, String documentId) {
        ensureCollection();
        call(
                "/v2/vectordb/entities/delete",
                Map.of(
                        "collectionName",
                        collection,
                        "filter",
                        "tenant_id == \"" + safe(tenantId) + "\" and document_id == \"" + safe(documentId) + "\""));
    }

    @Override
    public List<Hit> search(String tenantId, float[] query, Collection<String> allowedDocumentIds, int topK) {
        if (allowedDocumentIds.isEmpty()) return List.of();
        ensureCollection();
        String documents =
                allowedDocumentIds.stream().map(id -> "\"" + safe(id) + "\"").collect(Collectors.joining(","));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", collection);
        body.put("data", List.of(query));
        body.put("annsField", "vector");
        body.put("limit", topK);
        body.put("filter", "tenant_id == \"" + safe(tenantId) + "\" and document_id in [" + documents + "]");
        body.put("outputFields", List.of("id"));
        JsonNode data = call("/v2/vectordb/entities/search", body).path("data");
        List<Hit> hits = new ArrayList<>();
        for (JsonNode item : data)
            hits.add(new Hit(item.path("id").asText(), item.path("distance").asDouble()));
        return hits;
    }

    private void ensureCollection() {
        if (collectionReady) return;
        synchronized (this) {
            if (collectionReady) return;
            boolean exists = call("/v2/vectordb/collections/has", Map.of("collectionName", collection))
                    .path("data")
                    .path("has")
                    .asBoolean(false);
            if (!exists) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("collectionName", collection);
                body.put("dimension", dimensions);
                body.put("metricType", "COSINE");
                body.put("primaryFieldName", "id");
                body.put("idType", "VarChar");
                body.put("vectorFieldName", "vector");
                body.put("params", Map.of("max_length", 64));
                body.put("enableDynamicField", true);
                call("/v2/vectordb/collections/create", body);
            }
            collectionReady = true;
        }
    }

    private JsonNode call(String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (StringUtils.hasText(token)) request.header("Authorization", "Bearer " + token);
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || json.path("code").asInt(0) != 0)
                throw BusinessException.unprocessable(
                        "Milvus 调用失败：" + json.path("message").asText(response.body()));
            return json;
        } catch (IOException e) {
            throw BusinessException.unprocessable("Milvus 调用失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.unprocessable("Milvus 调用被中断");
        }
    }

    /** 过滤表达式只拼接经过校验的标识符，防止表达式注入。 */
    private static String safe(String value) {
        if (value == null || !SAFE_ID.matcher(value).matches()) throw BusinessException.unprocessable("非法的向量库标识符");
        return value;
    }
}
