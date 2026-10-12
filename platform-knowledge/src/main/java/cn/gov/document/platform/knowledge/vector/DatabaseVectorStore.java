package cn.gov.document.platform.knowledge.vector;

import cn.gov.document.platform.knowledge.domain.KnowledgeChunkEntity;
import cn.gov.document.platform.knowledge.persistence.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 以逻辑 CLOB 在切片表中保存向量、在应用内计算余弦相似度。适用于开发环境和中小规模知识库；单次检索最多比较
 * {@value #MAX_CANDIDATES} 个切片，更大规模应切换为 Milvus。
 */
public class DatabaseVectorStore implements VectorStore {
    static final int MAX_CANDIDATES = 20_000;

    private final KnowledgeChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    public DatabaseVectorStore(KnowledgeChunkMapper chunkMapper, ObjectMapper objectMapper) {
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public void upsert(String tenantId, List<Record> records) {
        for (Record record : records) {
            chunkMapper.update(
                    null,
                    new LambdaUpdateWrapper<KnowledgeChunkEntity>()
                            .eq(KnowledgeChunkEntity::getId, record.chunkId())
                            .set(KnowledgeChunkEntity::getEmbedding, write(record.vector()))
                            .set(KnowledgeChunkEntity::getVectorId, "db:" + record.chunkId()));
        }
    }

    @Override
    public void deleteByDocument(String tenantId, String documentId) {
        chunkMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                        .set(KnowledgeChunkEntity::getEmbedding, null)
                        .set(KnowledgeChunkEntity::getVectorId, null));
    }

    @Override
    public List<Hit> search(String tenantId, float[] query, Collection<String> allowedDocumentIds, int topK) {
        if (allowedDocumentIds.isEmpty()) return List.of();
        double queryNorm = norm(query);
        if (queryNorm == 0) return List.of();
        return chunkMapper
                .selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .select(KnowledgeChunkEntity::getId, KnowledgeChunkEntity::getEmbedding)
                        .in(KnowledgeChunkEntity::getDocumentId, allowedDocumentIds)
                        .isNotNull(KnowledgeChunkEntity::getEmbedding)
                        .last("LIMIT " + MAX_CANDIDATES))
                .stream()
                .map(chunk -> new Hit(chunk.getId(), cosine(query, queryNorm, read(chunk.getEmbedding()))))
                .filter(hit -> !Double.isNaN(hit.score()))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(topK)
                .toList();
    }

    static double cosine(float[] query, double queryNorm, float[] candidate) {
        if (candidate.length != query.length) return Double.NaN;
        double dot = 0;
        for (int i = 0; i < query.length; i++) dot += query[i] * candidate[i];
        double candidateNorm = norm(candidate);
        return candidateNorm == 0 ? Double.NaN : dot / (queryNorm * candidateNorm);
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float v : vector) sum += v * v;
        return Math.sqrt(sum);
    }

    private String write(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private float[] read(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            return new float[0];
        }
    }
}
