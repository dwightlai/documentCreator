package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.common.storage.ObjectStorage;
import cn.gov.document.platform.document.api.DocumentDtos.UpdateParagraphRequest;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.document.template.DocxFormatReviewService;
import cn.gov.document.platform.document.template.OutputTemplateDtos;
import cn.gov.document.platform.document.template.OutputTemplateService;
import cn.gov.document.platform.review.api.ReviewDtos;
import cn.gov.document.platform.review.domain.ReviewIssueActionEntity;
import cn.gov.document.platform.review.domain.ReviewIssueEntity;
import cn.gov.document.platform.review.domain.ReviewTaskEntity;
import cn.gov.document.platform.review.persistence.ReviewIssueActionMapper;
import cn.gov.document.platform.review.persistence.ReviewIssueMapper;
import cn.gov.document.platform.review.persistence.ReviewTaskMapper;
import cn.gov.document.platform.rule.api.DictionaryDtos;
import cn.gov.document.platform.rule.application.DictionaryService;
import cn.gov.document.platform.writing.application.WritingTaskService;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReviewService {
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewIssueActionMapper actionMapper;
    private final WritingTaskService writingTaskService;
    private final DocumentService documentService;
    private final BuiltInReviewEngine engine;
    private final ReviewPlanService planService;
    private final DictionaryService dictionaryService;
    private final DocxFormatReviewService formatReviewService;
    private final OutputTemplateService outputTemplateService;
    private final ObjectStorage objectStorage;

    public ReviewService(
            ReviewTaskMapper taskMapper,
            ReviewIssueMapper issueMapper,
            ReviewIssueActionMapper actionMapper,
            WritingTaskService writingTaskService,
            DocumentService documentService,
            BuiltInReviewEngine engine,
            ReviewPlanService planService,
            DictionaryService dictionaryService,
            DocxFormatReviewService formatReviewService,
            OutputTemplateService outputTemplateService,
            ObjectStorage objectStorage) {
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.actionMapper = actionMapper;
        this.writingTaskService = writingTaskService;
        this.documentService = documentService;
        this.engine = engine;
        this.planService = planService;
        this.dictionaryService = dictionaryService;
        this.formatReviewService = formatReviewService;
        this.outputTemplateService = outputTemplateService;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public ReviewDtos.TaskResponse createFromDocx(
            ReviewDtos.CreateRequest request, String outputTemplateId, String originalFileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw BusinessException.unprocessable("待审 DOCX 文件不能为空");
        }
        if (content.length > 50L * 1024 * 1024) {
            throw BusinessException.unprocessable("待审 DOCX 文件不得超过 50MB");
        }
        if (originalFileName == null
                || !originalFileName.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")) {
            throw BusinessException.unprocessable("格式审校仅支持 DOCX 文件");
        }
        DocxSecurityValidator.validate(originalFileName, content, 50L * 1024 * 1024, "待审文稿");
        WritingTaskEntity writingTask = writingTaskService.require(request.writingTaskId());
        OutputTemplateDtos.Response template = outputTemplateService.get(outputTemplateId);
        if (!"生效".equals(template.lifecycleStatus())) {
            throw BusinessException.unprocessable("格式审校必须选择已生效输出模板");
        }
        if (!writingTask.getDocumentTypeCode().equals(template.documentTypeCode())) {
            throw BusinessException.unprocessable("输出模板与写作任务文种不一致");
        }

        String planId =
                StringUtils.hasText(request.reviewPlanId()) ? request.reviewPlanId() : planService.defaultPlanId();
        ReviewPlanService.RuntimePlan runtimePlan = planService.runtime(planId);
        List<DocumentParagraphEntity> importedParagraphs = extractParagraphs(content);
        // 先完成格式解析，确保无效/恶意 DOCX 不会留下审校任务记录。
        DocxFormatReviewService.Result formatResult =
                runtimePlan.levels().contains("L4") ? formatReviewService.compare(outputTemplateId, content) : null;

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTenantId(RequestContext.tenantId());
        task.setSourceSystem("internal");
        task.setDocumentTypeCode(writingTask.getDocumentTypeCode());
        task.setReviewPlanId(planId);
        task.setWritingTaskId(writingTask.getId());
        task.setOutputTemplateId(outputTemplateId);
        task.setStatus("审校处理中");
        task.setInitiatorId(RequestContext.userId());
        taskMapper.insert(task);

        String key = RequestContext.tenantId() + "/review-input/" + task.getId() + ".docx";
        String hash = sha256(content);
        objectStorage.put(key, content, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        task.setDocumentFileId(key);
        task.setDocumentFileHash(hash);
        int number = 1;
        for (BuiltInReviewEngine.Candidate candidate : engine.inspect(
                writingTask.getDocumentTypeCode(),
                importedParagraphs.isEmpty() ? null : importedParagraphs.get(0).getPlainText(),
                importedParagraphs,
                new LinkedHashSet<>(runtimePlan.levels()),
                runtimePlan.riskThreshold())) {
            insertCandidate(task.getId(), number++, candidate);
        }
        if (formatResult != null) {
            for (DocxFormatReviewService.Deviation deviation : formatResult.deviations()) {
                ReviewIssueEntity issue = new ReviewIssueEntity();
                issue.setTenantId(RequestContext.tenantId());
                issue.setReviewTaskId(task.getId());
                issue.setIssueNumber("ISS-%04d".formatted(number++));
                issue.setIssueCategory("格式版式");
                issue.setRiskLevel(deviation.riskLevel());
                issue.setPageNumber(1);
                issue.setParagraphNumber(1);
                issue.setCharPositionStart(0);
                issue.setCharPositionEnd(Math.max(
                        0,
                        deviation.actualValue() == null
                                ? 0
                                : deviation.actualValue().length()));
                issue.setOriginalText(deviation.actualValue());
                issue.setSuggestedFix(null);
                issue.setIssueExplanation(deviation.description());
                issue.setRuleBasis(deviation.dimensionCode() + ": " + deviation.expectedValue());
                issue.setDetectionMethod("格式解析-L4");
                issue.setConfidenceScore(new java.math.BigDecimal("1.00"));
                issue.setProcessingStatus("待处理");
                issueMapper.insert(issue);
            }
        }
        task.setStatus("审校完成");
        taskMapper.updateById(task);
        return get(task.getId());
    }

    @Transactional
    public ReviewDtos.TaskResponse create(ReviewDtos.CreateRequest request) {
        WritingTaskEntity writingTask = writingTaskService.require(request.writingTaskId());
        DocumentService.ExportData data = documentService.exportData(request.writingTaskId());
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTenantId(RequestContext.tenantId());
        task.setSourceSystem("internal");
        task.setDocumentId(data.document().getId());
        task.setDocumentTypeCode(writingTask.getDocumentTypeCode());
        String planId = request.reviewPlanId();
        if (planService != null) {
            if (StringUtils.hasText(planId)) planService.verify(planId);
            else planId = planService.defaultPlanId();
        }
        task.setReviewPlanId(planId);
        task.setWritingTaskId(writingTask.getId());
        task.setStatus("审校处理中");
        task.setInitiatorId(RequestContext.userId());
        task.setCallbackUrl(request.callbackUrl());
        taskMapper.insert(task);
        int number = 1;
        ReviewPlanService.RuntimePlan runtimePlan = planService.runtime(planId);
        for (BuiltInReviewEngine.Candidate candidate : engine.inspect(
                writingTask.getDocumentTypeCode(),
                data.document().getTitle(),
                data.paragraphs(),
                new LinkedHashSet<>(runtimePlan.levels()),
                runtimePlan.riskThreshold())) {
            insertCandidate(task.getId(), number++, candidate);
        }
        task.setStatus("审校完成");
        taskMapper.updateById(task);
        return get(task.getId());
    }

    public ReviewDtos.TaskResponse get(String taskId) {
        ReviewTaskEntity task = requireTask(taskId);
        return ReviewDtos.TaskResponse.from(task, findIssues(taskId, null, null, null));
    }

    @Transactional
    public ReviewDtos.TaskResponse annotateExternal(
            String taskId, String sourceSystem, String businessId, String documentFileId, String callbackUrl) {
        ReviewTaskEntity task = requireTask(taskId);
        task.setSourceSystem(sourceSystem);
        task.setBusinessId(businessId);
        task.setDocumentFileId(documentFileId);
        task.setCallbackUrl(callbackUrl);
        taskMapper.updateById(task);
        return get(taskId);
    }

    public List<ReviewDtos.TaskResponse> list() {
        return taskMapper
                .selectList(new LambdaQueryWrapper<ReviewTaskEntity>().orderByDesc(ReviewTaskEntity::getCreatedAt))
                .stream()
                .map(task -> ReviewDtos.TaskResponse.from(task, findIssues(task.getId(), null, null, null)))
                .toList();
    }

    public List<ReviewDtos.IssueResponse> issues(String taskId, String category, String risk, String status) {
        requireTask(taskId);
        return findIssues(taskId, category, risk, status).stream()
                .map(ReviewDtos.IssueResponse::from)
                .toList();
    }

    public ReviewDtos.IssueResponse issue(String taskId, String issueNumber) {
        return ReviewDtos.IssueResponse.from(requireIssue(taskId, issueNumber));
    }

    @Transactional
    public ReviewDtos.IssueResponse ignore(String taskId, String issueNumber, ReviewDtos.ProcessRequest request) {
        ReviewIssueEntity issue = requirePendingIssue(taskId, issueNumber);
        if ("严重".equals(issue.getRiskLevel()) && (request == null || !StringUtils.hasText(request.note())))
            throw BusinessException.unprocessable("严重问题不得无说明忽略");
        issue.setProcessingStatus("已忽略");
        issue.setProcessingNote(request == null ? null : request.note());
        issue.setProcessedBy(RequestContext.userId());
        issue.setProcessedAt(OffsetDateTime.now());
        issueMapper.updateById(issue);
        recordAction(issue, "忽略", issue.getProcessingNote());
        return ReviewDtos.IssueResponse.from(issueMapper.selectById(issue.getId()));
    }

    @Transactional
    public ReviewDtos.IssueResponse modify(String taskId, String issueNumber, ReviewDtos.ProcessRequest request) {
        ReviewIssueEntity issue = requirePendingIssue(taskId, issueNumber);
        issue.setProcessingStatus("已修改");
        issue.setProcessingNote(request == null ? null : request.note());
        issue.setProcessedBy(RequestContext.userId());
        issue.setProcessedAt(OffsetDateTime.now());
        issueMapper.updateById(issue);
        recordAction(issue, "修改", issue.getProcessingNote());
        return ReviewDtos.IssueResponse.from(issue);
    }

    @Transactional
    public ReviewDtos.IssueResponse transfer(String taskId, String issueNumber, ReviewDtos.ProcessRequest request) {
        ReviewIssueEntity issue = requirePendingIssue(taskId, issueNumber);
        issue.setProcessingStatus("转人工");
        issue.setProcessingNote(request == null ? null : request.note());
        issue.setProcessedBy(RequestContext.userId());
        issue.setProcessedAt(OffsetDateTime.now());
        issueMapper.updateById(issue);
        recordAction(issue, "转人工", issue.getProcessingNote());
        return ReviewDtos.IssueResponse.from(issue);
    }

    @Transactional
    public List<ReviewDtos.IssueResponse> batchIgnore(String taskId, ReviewDtos.BatchRequest request) {
        requireTask(taskId);
        List<ReviewDtos.IssueResponse> result = new java.util.ArrayList<>();
        for (String number : request.issueNumbers()) {
            ReviewIssueEntity issue = requirePendingIssue(taskId, number);
            if (!List.of("提示", "一般").contains(issue.getRiskLevel()))
                throw BusinessException.unprocessable("批量忽略仅允许提示或一般问题");
            issue.setProcessingStatus("已忽略");
            issue.setProcessingNote(request.note());
            issue.setProcessedBy(RequestContext.userId());
            issue.setProcessedAt(OffsetDateTime.now());
            issueMapper.updateById(issue);
            recordAction(issue, "批量忽略", request.note());
            result.add(ReviewDtos.IssueResponse.from(issue));
        }
        return result;
    }

    @Transactional
    public List<ReviewDtos.IssueResponse> batchAccept(String taskId, ReviewDtos.BatchRequest request) {
        requireTask(taskId);
        List<ReviewIssueEntity> ordered = request.issueNumbers().stream()
                .map(number -> requirePendingIssue(taskId, number))
                .sorted(java.util.Comparator.comparing(ReviewIssueEntity::getParagraphId)
                        .thenComparing(ReviewIssueEntity::getCharPositionStart, java.util.Comparator.reverseOrder()))
                .toList();
        List<ReviewDtos.IssueResponse> result = new java.util.ArrayList<>();
        for (ReviewIssueEntity issue : ordered) {
            result.add(accept(taskId, issue.getIssueNumber(), new ReviewDtos.ProcessRequest(request.note())));
        }
        return result;
    }

    @Transactional
    public DictionaryDtos.TermResponse addToDictionary(
            String taskId, String issueNumber, ReviewDtos.AddToDictionaryRequest request) {
        ReviewIssueEntity issue = requireIssue(taskId, issueNumber);
        DictionaryDtos.TermResponse term = dictionaryService.createTerm(
                request.dictionaryId(),
                new DictionaryDtos.CreateTermRequest(
                        issue.getOriginalText(),
                        issue.getSuggestedFix(),
                        List.of(),
                        issue.getIssueCategory(),
                        issue.getRiskLevel(),
                        request.sourceReference(),
                        null,
                        null,
                        request.usageNote()));
        recordAction(issue, "加入词库", "dictionaryId=" + request.dictionaryId() + "; termId=" + term.id());
        return term;
    }

    @Transactional
    public ReviewDtos.TaskResponse reReview(String taskId) {
        ReviewTaskEntity previous = requireTask(taskId);
        ReviewDtos.TaskResponse created = create(new ReviewDtos.CreateRequest(
                previous.getWritingTaskId(), previous.getReviewPlanId(), previous.getCallbackUrl()));
        if ("OA".equalsIgnoreCase(previous.getSourceSystem())) {
            return annotateExternal(
                    created.id(),
                    "OA",
                    previous.getBusinessId(),
                    previous.getDocumentFileId(),
                    previous.getCallbackUrl());
        }
        return created;
    }

    @Transactional
    public ReviewDtos.IssueResponse accept(String taskId, String issueNumber, ReviewDtos.ProcessRequest request) {
        ReviewTaskEntity task = requireTask(taskId);
        ReviewIssueEntity issue = requirePendingIssue(taskId, issueNumber);
        if (!StringUtils.hasText(issue.getSuggestedFix())) {
            throw BusinessException.unprocessable("该问题需要人工修改，不能直接自动采纳");
        }
        if (task.getDocumentId() == null || issue.getParagraphId() == null) {
            throw BusinessException.unprocessable("导入文稿问题需在原 DOCX 中人工修改后重新审校");
        }
        DocumentParagraphEntity paragraph = documentService.exportData(task.getWritingTaskId()).paragraphs().stream()
                .filter(item -> item.getId().equals(issue.getParagraphId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("文稿已产生新版本，请重新发起审校后再采纳"));
        String text = paragraph.getPlainText();
        int start = issue.getCharPositionStart();
        int end = issue.getCharPositionEnd();
        if (start < 0 || end > text.length() || !text.substring(start, end).equals(issue.getOriginalText())) {
            throw BusinessException.conflict("问题定位内容已变化，请重新审校");
        }
        String changed = text.substring(0, start) + issue.getSuggestedFix() + text.substring(end);
        documentService.updateParagraph(
                task.getWritingTaskId(),
                paragraph.getId(),
                new UpdateParagraphRequest(changed, paragraph.getLockVersion(), "采纳审校问题 " + issueNumber));
        issue.setProcessingStatus("已采纳");
        issue.setProcessingNote(request == null ? null : request.note());
        issue.setProcessedBy(RequestContext.userId());
        issue.setProcessedAt(OffsetDateTime.now());
        issueMapper.updateById(issue);
        recordAction(issue, "采纳", issue.getSuggestedFix());
        return ReviewDtos.IssueResponse.from(issueMapper.selectById(issue.getId()));
    }

    public ReviewDtos.Statistics statistics(String taskId) {
        List<ReviewIssueEntity> values = findIssues(taskId, null, null, null);
        return new ReviewDtos.Statistics(
                values.size(),
                count(values, ReviewIssueEntity::getRiskLevel),
                count(values, ReviewIssueEntity::getIssueCategory),
                count(values, ReviewIssueEntity::getProcessingStatus));
    }

    @Transactional
    public void delete(String taskId) {
        requireTask(taskId);
        taskMapper.deleteById(taskId);
    }

    private Map<String, Long> count(List<ReviewIssueEntity> values, Function<ReviewIssueEntity, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
    }

    private void recordAction(ReviewIssueEntity issue, String type, String detail) {
        ReviewIssueActionEntity action = new ReviewIssueActionEntity();
        action.setTenantId(RequestContext.tenantId());
        action.setIssueId(issue.getId());
        action.setActionType(type);
        action.setActionDetail(detail);
        action.setOperatorId(RequestContext.userId());
        actionMapper.insert(action);
    }

    private ReviewTaskEntity requireTask(String id) {
        ReviewTaskEntity task = taskMapper.selectById(id);
        if (task == null) throw BusinessException.notFound("审校任务不存在");
        return task;
    }

    private ReviewIssueEntity requireIssue(String taskId, String number) {
        requireTask(taskId);
        ReviewIssueEntity issue = issueMapper.selectOne(new LambdaQueryWrapper<ReviewIssueEntity>()
                .eq(ReviewIssueEntity::getReviewTaskId, taskId)
                .eq(ReviewIssueEntity::getIssueNumber, number));
        if (issue == null) throw BusinessException.notFound("审校问题不存在");
        return issue;
    }

    private ReviewIssueEntity requirePendingIssue(String taskId, String number) {
        ReviewIssueEntity issue = requireIssue(taskId, number);
        if (!"待处理".equals(issue.getProcessingStatus())) throw BusinessException.conflict("该问题已处理");
        return issue;
    }

    private List<ReviewIssueEntity> findIssues(String taskId, String category, String risk, String status) {
        return issueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
                .eq(ReviewIssueEntity::getReviewTaskId, taskId)
                .eq(StringUtils.hasText(category), ReviewIssueEntity::getIssueCategory, category)
                .eq(StringUtils.hasText(risk), ReviewIssueEntity::getRiskLevel, risk)
                .eq(StringUtils.hasText(status), ReviewIssueEntity::getProcessingStatus, status)
                .orderByAsc(ReviewIssueEntity::getIssueNumber));
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }

    private void insertCandidate(String taskId, int number, BuiltInReviewEngine.Candidate candidate) {
        ReviewIssueEntity issue = new ReviewIssueEntity();
        issue.setTenantId(RequestContext.tenantId());
        issue.setReviewTaskId(taskId);
        issue.setIssueNumber("ISS-%04d".formatted(number));
        issue.setIssueCategory(candidate.category());
        issue.setRiskLevel(candidate.risk());
        issue.setParagraphId(candidate.paragraphId());
        issue.setParagraphNumber(candidate.paragraphNumber());
        issue.setPageNumber(Math.max(1, (candidate.paragraphNumber() - 1) / 8 + 1));
        issue.setCharPositionStart(candidate.start());
        issue.setCharPositionEnd(candidate.end());
        issue.setOriginalText(candidate.original());
        issue.setSuggestedFix(candidate.replacement());
        issue.setIssueExplanation(candidate.explanation());
        issue.setRuleBasis(candidate.basis());
        issue.setDetectionMethod("规则-" + candidate.level());
        issue.setConfidenceScore(candidate.confidence());
        issue.setProcessingStatus("待处理");
        issueMapper.insert(issue);
    }

    private List<DocumentParagraphEntity> extractParagraphs(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> parts = new java.util.ArrayList<>();
            document.getParagraphs().stream()
                    .map(value -> value.getText().trim())
                    .filter(value -> !value.isBlank())
                    .forEach(parts::add);
            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    String text = row.getTableCells().stream()
                            .map(cell -> cell.getText())
                            .collect(Collectors.joining("\t"));
                    if (!text.isBlank()) {
                        parts.add(text);
                    }
                }
            }
            if (parts.isEmpty()) {
                return List.of();
            }
            List<DocumentParagraphEntity> imported = new java.util.ArrayList<>();
            int order = 1;
            for (String part : parts) {
                DocumentParagraphEntity paragraph = new DocumentParagraphEntity();
                paragraph.setParagraphOrder(order++);
                paragraph.setPlainText(part);
                imported.add(paragraph);
            }
            return imported;
        } catch (IOException invalidDocument) {
            throw BusinessException.unprocessable("待审文稿不是有效 DOCX 文件");
        }
    }
}
