package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.storage.ObjectStorage;
import org.springframework.stereotype.Component;

@Component
public class TemplateFileStore {
    private final ObjectStorage storage;

    public TemplateFileStore(ObjectStorage storage) {
        this.storage = storage;
    }

    public String write(String templateId, int version, byte[] content) {
        validateSegment(RequestContext.tenantId());
        validateSegment(templateId);
        String key = RequestContext.tenantId() + "/output-templates/" + templateId + "/v" + version + ".docx";
        storage.put(key, content, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return key;
    }

    public byte[] read(String storagePath) {
        return storage.read(storagePath);
    }

    private void validateSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法模板存储路径段");
        }
    }
}
