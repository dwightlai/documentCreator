package cn.gov.document.platform.document.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.storage.ObjectStorage;
import cn.gov.document.platform.document.api.DocumentTypesetDtos;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.domain.DocumentTypesetEntity;
import cn.gov.document.platform.document.persistence.DocumentTypesetMapper;
import cn.gov.document.platform.document.template.DocxFormatReviewService;
import cn.gov.document.platform.document.template.DocxTemplateEngine;
import cn.gov.document.platform.document.template.OutputTemplateService;
import cn.gov.document.platform.document.template.StandardOfficialTemplate;
import cn.gov.document.platform.writing.application.WritingTaskService;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * A2 一键套红：把当前正文按输出模板排成正式公文 DOCX（每段独立成段，保留加粗、列表、表格），同时执行 L4 格式检查，
 * 结果作为定稿稿保存。文种没有已发布的输出模板时使用内置标准版式。
 */
@Service
public class DocumentTypesetService {
    static final String GENERATED = "套红生成";
    static final String EDITED = "在线编辑";
    /** 可由用户填写的版头、版记要素（§6.3.1 统一占位符），标题取自文稿，正文取自段落。 */
    private static final Set<String> FIELDS = Set.of(
            "sender", "doc_number", "signer", "recipient", "closing", "attachment_desc", "date", "copy_to", "issue_unit", "issue_date");
    private static final int MAX_FIELD_LENGTH = 200;
    private static final Set<String> UPWARD = Set.of("请示", "报告", "议案");
    private static final DateTimeFormatter CHINESE_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DocumentService documentService;
    private final WritingTaskService taskService;
    private final OutputTemplateService templateService;
    private final DocxTemplateEngine engine;
    private final DocxFormatReviewService formatReview;
    private final ObjectStorage storage;
    private final DocumentTypesetMapper mapper;

    public DocumentTypesetService(
            DocumentService documentService,
            WritingTaskService taskService,
            OutputTemplateService templateService,
            DocxTemplateEngine engine,
            DocxFormatReviewService formatReview,
            ObjectStorage storage,
            DocumentTypesetMapper mapper) {
        this.documentService = documentService;
        this.taskService = taskService;
        this.templateService = templateService;
        this.engine = engine;
        this.formatReview = formatReview;
        this.storage = storage;
        this.mapper = mapper;
    }

    @Transactional
    public DocumentTypesetDtos.TypesetResponse create(String taskId, DocumentTypesetDtos.CreateRequest request) {
        WritingTaskEntity task = taskService.require(taskId);
        DocumentEntity document = documentService.documentOf(taskId);
        List<DocumentParagraphEntity> paragraphs = documentService.currentParagraphs(document);
        if (paragraphs.isEmpty()) throw BusinessException.unprocessable("正文尚未形成，不能套红排版");
        String templateId = templateFor(task, request.templateId());
        byte[] template = templateId == null ? StandardOfficialTemplate.build() : templateService.fileContent(templateId);
        String templateName = templateId == null ? StandardOfficialTemplate.NAME : templateService.get(templateId).templateName();
        String direction = templateId == null ? defaultDirection(task.getDocumentTypeCode()) : templateService.get(templateId).direction();

        Map<String, String> values = fieldValues(request.values());
        Map<String, String> rendering = new LinkedHashMap<>(values);
        rendering.put("title", document.getTitle());
        byte[] docx = engine.renderDocument(
                template,
                rendering,
                paragraphs.stream()
                        .map(p -> new DocxTemplateEngine.BodyBlock(p.getPlainText(), p.getRichContent()))
                        .toList());
        List<DocxFormatReviewService.Deviation> deviations = new ArrayList<>(
                formatReview.compareWith(templateId, template, docx).deviations());
        deviations.addAll(formatReview.inspectStructure(docx, direction));

        DocumentTypesetEntity entity = new DocumentTypesetEntity();
        entity.setDocumentVersion(document.getCurrentVersion());
        entity.setTemplateId(templateId);
        entity.setTemplateName(templateName);
        entity.setFieldValues(toJson(values));
        entity.setDeviations(toJson(deviations));
        entity.setDeviationCount(deviations.size());
        entity.setSource(GENERATED);
        store(document, entity, docx);
        return response(entity, document);
    }

    /** 最近一次定稿稿；尚未套红时为空。 */
    public DocumentTypesetDtos.TypesetResponse latest(String taskId) {
        DocumentEntity document = documentService.documentOf(taskId);
        DocumentTypesetEntity latest = mapper.selectOne(new LambdaQueryWrapper<DocumentTypesetEntity>()
                .eq(DocumentTypesetEntity::getDocumentId, document.getId())
                .orderByDesc(DocumentTypesetEntity::getCreatedAt)
                .last("LIMIT 1"));
        return latest == null ? null : response(latest, document);
    }

    public DocumentTypesetEntity require(String taskId, String typesetId) {
        DocumentEntity document = documentService.documentOf(taskId);
        DocumentTypesetEntity entity = mapper.selectOne(new LambdaQueryWrapper<DocumentTypesetEntity>()
                .eq(DocumentTypesetEntity::getId, typesetId)
                .eq(DocumentTypesetEntity::getDocumentId, document.getId()));
        if (entity == null) throw BusinessException.notFound("定稿稿不存在");
        return entity;
    }

    public byte[] file(DocumentTypesetEntity entity) {
        return storage.read(entity.getFilePath());
    }

    /** 在线编辑保存的新文件：沿用原定稿稿的模板与要素，重新执行结构检查，追加为新的定稿稿。 */
    @Transactional
    public DocumentTypesetDtos.TypesetResponse saveEdited(String taskId, String parentId, byte[] docx) {
        DocumentTypesetEntity parent = require(taskId, parentId);
        DocumentEntity document = documentService.documentOf(taskId);
        WritingTaskEntity task = taskService.require(taskId);
        String direction = parent.getTemplateId() == null
                ? defaultDirection(task.getDocumentTypeCode())
                : templateService.get(parent.getTemplateId()).direction();
        byte[] template = parent.getTemplateId() == null
                ? StandardOfficialTemplate.build()
                : templateService.fileContent(parent.getTemplateId());
        List<DocxFormatReviewService.Deviation> deviations = new ArrayList<>(
                formatReview.compareWith(parent.getTemplateId(), template, docx).deviations());
        deviations.addAll(formatReview.inspectStructure(docx, direction));
        DocumentTypesetEntity entity = new DocumentTypesetEntity();
        entity.setDocumentVersion(parent.getDocumentVersion());
        entity.setTemplateId(parent.getTemplateId());
        entity.setTemplateName(parent.getTemplateName());
        entity.setFieldValues(parent.getFieldValues());
        entity.setDeviations(toJson(deviations));
        entity.setDeviationCount(deviations.size());
        entity.setSource(EDITED);
        entity.setParentId(parent.getId());
        store(document, entity, docx);
        return response(entity, document);
    }

    private void store(DocumentEntity document, DocumentTypesetEntity entity, byte[] docx) {
        String key = RequestContext.tenantId() + "/typesets/" + document.getId() + "/" + UUID.randomUUID().toString().replace("-", "") + ".docx";
        storage.put(key, docx, DOCX);
        entity.setTenantId(RequestContext.tenantId());
        entity.setDocumentId(document.getId());
        entity.setFilePath(key);
        entity.setFileHash(sha256(docx));
        entity.setFileSize(docx.length);
        mapper.insert(entity);
    }

    /** 指定模板必须已发布且与文种匹配；未指定时依次用写作任务的模板（仍生效时）、文种默认模板，都没有时返回空（内置标准版式）。 */
    private String templateFor(WritingTaskEntity task, String requested) {
        String type = task.getDocumentTypeCode();
        if (StringUtils.hasText(requested)) return templateService.resolveEffectiveTemplate(requested, type);
        if (StringUtils.hasText(task.getOutputTemplateId())) {
            try {
                return templateService.resolveEffectiveTemplate(task.getOutputTemplateId(), type);
            } catch (BusinessException withdrawn) {
                // 任务创建后模板被停用或替换：改用文种当前的默认模板
            }
        }
        return templateService.resolveEffectiveTemplate(null, type);
    }

    private static String defaultDirection(String documentType) {
        if (UPWARD.contains(documentType)) return "上行";
        return "函".equals(documentType) ? "平行" : "下行";
    }

    /** 只接受统一占位符清单中的要素；成文日期、印发日期未填写时取当天。 */
    private static Map<String, String> fieldValues(Map<String, String> input) {
        Map<String, String> values = new LinkedHashMap<>();
        if (input != null) {
            for (var entry : input.entrySet()) {
                if (!FIELDS.contains(entry.getKey())) throw BusinessException.unprocessable("不支持的版式要素：" + entry.getKey());
                String value = entry.getValue() == null ? "" : entry.getValue().strip();
                if (value.length() > MAX_FIELD_LENGTH)
                    throw BusinessException.unprocessable("版式要素过长：" + entry.getKey());
                if (!value.isEmpty()) values.put(entry.getKey(), value);
            }
        }
        String today = LocalDate.now().format(CHINESE_DATE);
        values.putIfAbsent("date", today);
        values.putIfAbsent("issue_date", today);
        return values;
    }

    private DocumentTypesetDtos.TypesetResponse response(DocumentTypesetEntity entity, DocumentEntity document) {
        return new DocumentTypesetDtos.TypesetResponse(
                entity.getId(),
                entity.getDocumentVersion(),
                !entity.getDocumentVersion().equals(document.getCurrentVersion()),
                entity.getTemplateId(),
                entity.getTemplateName(),
                fromJson(entity.getFieldValues(), new TypeReference<Map<String, String>>() {}, Map.of()),
                entity.getDeviationCount(),
                fromJson(entity.getDeviations(), new TypeReference<List<DocxFormatReviewService.Deviation>>() {}, List.of()),
                entity.getSource(),
                entity.getFileSize(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static <T> T fromJson(String value, TypeReference<T> type, T empty) {
        if (value == null || value.isBlank()) return empty;
        try {
            return JSON.readValue(value, type);
        } catch (JsonProcessingException corrupted) {
            throw new IllegalStateException("定稿稿记录格式不正确", corrupted);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
