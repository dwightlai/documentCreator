package cn.gov.document.platform.common.port;

public interface OutputTemplatePort {
    /**
     * Resolves an explicitly selected template or the tenant's first effective default for the document type.
     * Returns {@code null} only when no explicit template was requested and no effective default exists.
     */
    String resolveEffectiveTemplate(String requestedTemplateId, String documentTypeCode);
}
