package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.application.AsyncJobService;
import cn.gov.document.platform.review.api.SubmissionGateDtos;
import cn.gov.document.platform.review.application.SubmissionGateService;
import cn.gov.document.platform.writing.api.WritingMaterialDtos;
import cn.gov.document.platform.writing.api.WritingTaskDtos;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import cn.gov.document.platform.writing.application.WritingTaskService;
import cn.gov.document.platform.writing.domain.WritingTaskEntity;
import cn.gov.document.platform.writing.persistence.WritingTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** §31.1 从 OA 发起写作，以及经提交门禁（§31.3）后将文稿回传 OA。 */
@Service
public class OaWritingService {
    static final String SOURCE_SYSTEM = "OA";

    private final WritingTaskService writingTaskService;
    private final WritingTaskMapper writingTaskMapper;
    private final WritingMaterialService materialService;
    private final DocumentService documentService;
    private final SubmissionGateService gateService;
    private final DocumentSubmissionMapper submissionMapper;
    private final AsyncJobService jobService;
    private final OaCallbackSecurity callbackSecurity;
    private final ObjectMapper objectMapper;

    public OaWritingService(
            WritingTaskService writingTaskService,
            WritingTaskMapper writingTaskMapper,
            WritingMaterialService materialService,
            DocumentService documentService,
            SubmissionGateService gateService,
            DocumentSubmissionMapper submissionMapper,
            AsyncJobService jobService,
            OaCallbackSecurity callbackSecurity,
            ObjectMapper objectMapper) {
        this.writingTaskService = writingTaskService;
        this.writingTaskMapper = writingTaskMapper;
        this.materialService = materialService;
        this.documentService = documentService;
        this.gateService = gateService;
        this.submissionMapper = submissionMapper;
        this.jobService = jobService;
        this.callbackSecurity = callbackSecurity;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OaIntegrationDtos.CreateWritingResponse createWriting(OaIntegrationDtos.CreateWritingRequest request) {
        if (StringUtils.hasText(request.callbackUrl())) callbackSecurity.validateCallbackUrl(request.callbackUrl());
        WritingTaskEntity existing = writingTaskMapper.selectOne(new LambdaQueryWrapper<WritingTaskEntity>()
                .eq(WritingTaskEntity::getSourceSystem, SOURCE_SYSTEM)
                .eq(WritingTaskEntity::getBusinessId, request.businessId())
                .last("LIMIT 1"));
        if (existing != null) throw BusinessException.conflict("该 OA 业务单据已发起写作任务：" + existing.getId());
        WritingTaskDtos.Response created = writingTaskService.create(new WritingTaskDtos.CreateRequest(
                request.taskName(),
                request.documentType(),
                StringUtils.hasText(request.documentCategory()) ? request.documentCategory() : "法定公文",
                request.writingTopic(),
                request.writingPurpose(),
                null,
                null,
                request.primaryRecipient(),
                null,
                null,
                null,
                null,
                false,
                StringUtils.hasText(request.securityLevel()) ? request.securityLevel() : "内部",
                null));
        WritingTaskDtos.Response linked = writingTaskService.linkExternal(
                created.id(), SOURCE_SYSTEM, request.businessId(), request.draftingDepartment(), request.callbackUrl());
        int materials = 0;
        if (request.materials() != null) {
            for (OaIntegrationDtos.Material material : request.materials()) {
                materialService.create(
                        created.id(),
                        new WritingMaterialDtos.CreateRequest(
                                material.materialName(),
                                "OA关联",
                                request.businessId(),
                                StringUtils.hasText(material.authorityLevel()) ? material.authorityLevel() : "工作材料",
                                "全文参考",
                                "背景资料",
                                null,
                                null,
                                "允许",
                                material.content(),
                                true,
                                null,
                                null));
                materials++;
            }
        }
        return new OaIntegrationDtos.CreateWritingResponse(linked, materials, "/api/v1/writing-tasks/" + created.id());
    }

    /**
     * 通过提交门禁后记录提交版本、保护该版本不被保留策略清理，并异步回传 OA。回传内容只含标识、状态和地址，不含正文（§31.2）。
     */
    @Transactional
    public OaIntegrationDtos.SubmitResponse submit(String writingTaskId, OaIntegrationDtos.SubmitRequest request)
            throws JsonProcessingException {
        SubmissionGateDtos.CheckResult gate = gateService.check(writingTaskId);
        if (!gate.passed()) {
            String failed = gate.rules().stream()
                    .filter(rule -> !rule.passed())
                    .map(SubmissionGateDtos.Rule::message)
                    .collect(Collectors.joining("；"));
            throw BusinessException.unprocessable("未通过提交门禁：" + failed);
        }
        WritingTaskEntity task = writingTaskService.require(writingTaskId);
        String callbackUrl = request != null && StringUtils.hasText(request.callbackUrl())
                ? request.callbackUrl()
                : task.getCallbackUrl();
        if (StringUtils.hasText(callbackUrl)) callbackSecurity.validateCallbackUrl(callbackUrl);
        int version = gate.documentVersion();
        documentService.protectVersion(writingTaskId, version, true);
        DocumentService.AiDisclosure disclosure = documentService.aiDisclosure(writingTaskId);

        DocumentSubmissionEntity submission = new DocumentSubmissionEntity();
        submission.setTenantId(RequestContext.tenantId());
        submission.setWritingTaskId(writingTaskId);
        submission.setDocumentId(task.getCurrentDocumentId());
        submission.setDocumentVersion(version);
        submission.setReviewTaskId(gate.reviewTaskId());
        submission.setTargetSystem(SOURCE_SYSTEM);
        submission.setBusinessId(task.getBusinessId());
        submission.setGateResult(objectMapper.writeValueAsString(gate));
        submission.setSubmittedBy(RequestContext.userId());
        submission.setSubmittedAt(OffsetDateTime.now());
        submissionMapper.insert(submission);

        String callbackJobId = null;
        if (StringUtils.hasText(callbackUrl)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceSystem", SOURCE_SYSTEM);
            body.put("event", "DOCUMENT_SUBMITTED");
            body.put("businessId", task.getBusinessId());
            body.put("writingTaskId", writingTaskId);
            body.put("submissionId", submission.getId());
            body.put("documentVersion", version);
            body.put("reviewTaskId", gate.reviewTaskId());
            body.put("reviewStatus", "提交门禁已通过");
            body.put(
                    "reportUrl",
                    gate.reviewTaskId() == null ? null : "/api/v1/review-tasks/" + gate.reviewTaskId() + "/reports");
            body.put("documentUrl", "/api/v1/writing-tasks/" + writingTaskId + "/document/versions/" + version);
            body.put("aiGenerated", disclosure.aiGeneratedRatio().signum() > 0);
            body.put("aiGeneratedRatio", disclosure.aiGeneratedRatio());
            String payload = objectMapper.writeValueAsString(Map.of("callbackUrl", callbackUrl, "body", body));
            AsyncJobDtos.Response job =
                    jobService.submit(new AsyncJobDtos.SubmitRequest("OA_CALLBACK", submission.getId(), payload, 5));
            callbackJobId = job.id();
            submission.setCallbackJobId(callbackJobId);
            submissionMapper.updateById(submission);
        }
        return new OaIntegrationDtos.SubmitResponse(
                submission.getId(), writingTaskId, version, task.getBusinessId(), callbackJobId, gate);
    }
}
