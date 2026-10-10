package cn.gov.document.platform.knowledge.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.storage.ObjectStorage;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeFileStore {
    private final ObjectStorage storage;

    public KnowledgeFileStore(ObjectStorage storage) {
        this.storage = storage;
    }

    public String write(String sourceId, String documentId, String originalName, byte[] content) {
        validateSegment(RequestContext.tenantId());
        validateSegment(sourceId);
        validateSegment(documentId);
        String safeName = safeName(originalName);
        String key = RequestContext.tenantId() + "/knowledge/" + sourceId + "/" + documentId + "/" + safeName;
        storage.put(key, content, contentType(safeName));
        return key;
    }

    public byte[] read(String storagePath) {
        return storage.read(storagePath);
    }

    private String contentType(String name) {
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".txt") || name.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    private String safeName(String originalName) {
        String value = originalName == null ? "document.bin" : originalName;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank() || value.equals(".") || value.equals("..")) {
            return "document.bin";
        }
        return value.substring(0, Math.min(180, value.length())).toLowerCase(Locale.ROOT);
    }

    private void validateSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法知识文件存储路径段");
        }
    }
}
