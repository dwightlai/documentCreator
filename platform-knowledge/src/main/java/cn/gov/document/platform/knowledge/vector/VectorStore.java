package cn.gov.document.platform.knowledge.vector;

import java.util.Collection;
import java.util.List;

/**
 * 切片向量存储。检索时调用方必须先按租户、权限、有效期过滤出允许的文档，再把文档 ID 传入，向量库只在该范围内检索（权限前置过滤）。
 */
public interface VectorStore {
    String name();

    void upsert(String tenantId, List<Record> records);

    void deleteByDocument(String tenantId, String documentId);

    List<Hit> search(String tenantId, float[] query, Collection<String> allowedDocumentIds, int topK);

    record Record(String chunkId, String documentId, float[] vector) {}

    record Hit(String chunkId, double score) {}
}
