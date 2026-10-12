package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.coordination.ExclusiveGuard;
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
    private final ExclusiveGuard guard;
    private final cn.gov.document.platform.review.application.deep.ModelLogicReviewer modelLogicReviewer;
    private final cn.gov.document.platform.writing.application.DocumentTypeConfigService documentTypeConfigService;

    private static final java.time.Duration REVIEW_RUN_TTL = java.time.Duration.ofMinutes(10);
    private static final java.time.Duration ISSUE_TTL = java.time.Duration.ofMinutes(2);

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
            ObjectStorage objectStorage,
            ExclusiveGuard guard,
            cn.gov.document.platform.review.application.deep.ModelLogicReviewer modelLogicReviewer,
            cn.gov.document.platform.writing.application.DocumentTypeConfigService documentTypeConfigService) {
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
        this.guard = guard;
        this.modelLogicReviewer = modelLogicReviewer;
        this.documentTypeConfigService = documentTypeConfigService;
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
        List<DocxFormatReviewService.Deviation> formatDeviations = new java.util.ArrayList<>();
        if (formatResult != null) {
            formatDeviations.addAll(formatResult.deviations());
            formatDeviations.addAll(
                    formatReviewService.inspectStructure(content, direction(writingTask.getDocumentTypeCode())));
        }

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
        int number = insertInspection(
                task.getId(),
                writingTask.getDocumentTypeCode(),
                importedParagraphs.isEmpty() ? null : importedParagraphs.get(0).getPlainText(),
                importedParagraphs,
                runtimePlan,
                writingTask.getSecurityLevel());
        if (formatResult != null) {
            for (DocxFormatReviewService.Deviation deviation : formatDeviations) {
                if (!atOrAboveThreshold(deviation.riskLevel(), runtimePlan.riskThreshold())) continue;
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
        // 同一文稿同一时刻只允许一个审校任务处于运行中（§34B.2）
        guard.holdUntilTransactionEnd(
                "review:document:" + data.document().getId(), REVIEW_RUN_TTL, "该文稿正在审校中，请等待当前审校完成后再试");
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTenantId(RequestContext.tenantId());
        task.setSourceSystem("internal");
        task.setDocumentId(data.document().getId());
        task.setDocumentVersion(data.document().getCurrentVersion());
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
        ReviewPlanService.RuntimePlan runtimePlan = planService.runtime(planId);
        insertInspection(
                task.getId(),
                writingTask.getDocumentTypeCode(),
                data.document().getTitle(),
                data.paragraphs(),
                runtimePlan,
                writingTask.getSecurityLevel());
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
            lockIssue(taskId, number);
            ReviewIssueEntity issue = requireIssue(taskId, number);
            // 批量操作期间已被他人处理的问题直接跳过，前端按返回数量提示（§34B.2）
            if (!"待处理".equals(issue.getProcessingStatus())) continue;
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
                .map(number -> requireIssue(taskId, number))
                .filter(issue -> "待处理".equals(issue.getProcessingStatus()))
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
        if (AutoFixPolicy.isManualOnly(issue.getDetectionMethod())) {
            throw BusinessException.unprocessable("事实数据、逻辑和内容安全类问题禁止自动修改，请人工修改后标记“修改完成”");
        }
        if (!StringUtils.hasText(issue.getSuggestedFix())) {
            throw BusinessException.unprocessable("该问题需要人工修改，不能直接自动采纳");
        }
        if (task.getDocumentId() == null || issue.getParagraphId() == null) {
            throw BusinessException.unprocessable("导入文稿问题需在原 DOCX 中人工修改后重新审校");
        }
        // 同一段落的多个问题依次采纳时，每次采纳都会产生新版本；按位置定位当前版本段落，再以原文比对确认定位未失效
        DocumentParagraphEntity paragraph =
                documentService.currentParagraphAt(task.getWritingTaskId(), issue.getParagraphId());
        String text = paragraph.getPlainText();
        int start = issue.getCharPositionStart();
        int end = issue.getCharPositionEnd();
        if (start < 0 || end > text.length() || !text.substring(start, end).equals(issue.getOriginalText())) {
            // 同段落前面的问题已被采纳导致偏移变化：原文在段落中唯一出现时仍可安全定位
            int first = text.indexOf(issue.getOriginalText());
            if (first < 0 || text.indexOf(issue.getOriginalText(), first + 1) >= 0) {
                throw BusinessException.conflict("问题定位内容已变化，请重新审校");
            }
            start = first;
            end = first + issue.getOriginalText().length();
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
        lockIssue(taskId, number);
        ReviewIssueEntity issue = requireIssue(taskId, number);
        if (!"待处理".equals(issue.getProcessingStatus())) throw BusinessException.conflict("该问题已处理");
        return issue;
    }

    /** 单条问题的处置互斥，避免两人同时采纳或忽略同一问题（§34B.2）。 */
    private void lockIssue(String taskId, String number) {
        guard.holdUntilTransactionEnd("review:" + taskId + ":issue:" + number, ISSUE_TTL, "该问题正在被其他用户处理，请刷新后查看");
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

    /**
     * 执行规则审校与（方案包含 L6 时）模型辅助逻辑审校，为可合并的同类问题分配合并组后入库，返回下一个问题序号。
     */
    private int insertInspection(
            String taskId,
            String documentTypeCode,
            String title,
            List<DocumentParagraphEntity> paragraphs,
            ReviewPlanService.RuntimePlan plan,
            String securityLevel) {
        List<BuiltInReviewEngine.Candidate> candidates = engine.inspect(
                documentTypeCode,
                direction(documentTypeCode),
                title,
                paragraphs,
                new LinkedHashSet<>(plan.levels()),
                plan.riskThreshold());
        java.util.Map<String, String> groups = mergeGroups(candidates);
        int number = 1;
        for (BuiltInReviewEngine.Candidate candidate : candidates) {
            insertCandidate(taskId, number++, candidate, "规则-" + candidate.level(), groups.get(mergeKey(candidate)));
        }
        boolean includesHints = !StringUtils.hasText(plan.riskThreshold()) || "提示".equals(plan.riskThreshold());
        if (modelLogicReviewer != null && plan.levels().contains("L6") && includesHints) {
            for (var finding : modelLogicReviewer.review(documentTypeCode, paragraphs, securityLevel, taskId)) {
                insertCandidate(
                        taskId,
                        number++,
                        new BuiltInReviewEngine.Candidate(
                                finding.level(),
                                finding.paragraph().getId(),
                                finding.paragraph().getParagraphOrder(),
                                finding.category(),
                                finding.risk(),
                                finding.start(),
                                finding.end(),
                                finding.original(),
                                null,
                                finding.explanation(),
                                finding.basis(),
                                finding.confidence()),
                        "模型-L6",
                        null);
            }
        }
        return number;
    }

    private static boolean atOrAboveThreshold(String risk, String threshold) {
        List<String> order = List.of("提示", "一般", "重要", "严重");
        return order.indexOf(risk) >= Math.max(0, order.indexOf(threshold));
    }

    /** 行文方向以租户生效的文种配置为准，未配置时使用平台默认。 */
    private String direction(String documentTypeCode) {
        if (documentTypeConfigService != null) {
            String configured = documentTypeConfigService
                    .effective(documentTypeCode)
                    .map(
                            cn.gov.document.platform.writing.application.DocumentTypeConfigService.RuntimeConfig
                                    ::direction)
                    .orElse(null);
            if (StringUtils.hasText(configured)) return configured;
        }
        return L3StructureInspector.DEFAULT_DIRECTIONS.get(documentTypeCode);
    }

    /**
     * 错误合并（§20）：同一层级、同一原文且有相同修改建议的非严重问题出现两次及以上时归入同一合并组，便于一次确认后批量处理。
     * 严重问题和无修改建议的事实、逻辑、内容安全问题不合并。
     */
    private static java.util.Map<String, String> mergeGroups(List<BuiltInReviewEngine.Candidate> candidates) {
        java.util.Map<String, Long> counts = candidates.stream()
                .map(ReviewService::mergeKey)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(key -> key, java.util.stream.Collectors.counting()));
        java.util.Map<String, String> groups = new java.util.HashMap<>();
        counts.forEach((key, count) -> {
            if (count > 1)
                groups.put(key, java.util.UUID.randomUUID().toString().replace("-", ""));
        });
        return groups;
    }

    private static String mergeKey(BuiltInReviewEngine.Candidate candidate) {
        if (!StringUtils.hasText(candidate.replacement()) || "严重".equals(candidate.risk())) return null;
        return candidate.level() + "|" + candidate.category() + "|" + candidate.original() + "|"
                + candidate.replacement();
    }

    private void insertCandidate(
            String taskId,
            int number,
            BuiltInReviewEngine.Candidate candidate,
            String detectionMethod,
            String groupId) {
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
        issue.setDetectionMethod(detectionMethod);
        issue.setMergedGroupId(groupId);
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
