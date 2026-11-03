package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.DocumentDtos;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.application.AsyncJobService;
import cn.gov.document.platform.review.api.ReviewDtos;
import cn.gov.document.platform.review.application.ReviewService;
import cn.gov.document.platform.review.application.ReviewReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/integrations/oa")
public class OaIntegrationController {
    private final ReviewService reviewService;
    private final ReviewReportService reportService;
    private final DocumentService documentService;
    private final AsyncJobService jobService;
    private final OaCallbackSecurity callbackSecurity;
    private final ObjectMapper objectMapper;
    private final OaWritingService writingService;

    public OaIntegrationController(
            ReviewService reviewService,
            ReviewReportService reportService,
            DocumentService documentService,
            AsyncJobService jobService,
            OaCallbackSecurity callbackSecurity,
            ObjectMapper objectMapper,
            OaWritingService writingService) {
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.documentService = documentService;
        this.jobService = jobService;
        this.callbackSecurity = callbackSecurity;
        this.objectMapper = objectMapper;
        this.writingService = writingService;
    }

    /** §31.1 从 OA 发起写作：建立写作任务、关联业务单据并导入关联材料。 */
    @PostMapping("/writing-tasks")
    ResponseEntity<ApiResponse<OaIntegrationDtos.CreateWritingResponse>> createWriting(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OaIntegrationDtos.CreateWritingRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw BusinessException.unprocessable("OA 发起写作必须提供 Idempotency-Key");
        }
        OaIntegrationDtos.CreateWritingResponse response = writingService.createWriting(request);
        return ResponseEntity.created(URI.create(response.statusUrl())).body(ApiResponse.success(response));
    }

    @GetMapping("/writing-tasks/{taskId}")
    ApiResponse<OaIntegrationDtos.WritingStatus> writingStatus(
            @PathVariable String taskId, @RequestParam String businessId) {
        return ApiResponse.success(writingService.status(taskId, businessId));
    }

    @GetMapping("/writing-tasks")
    ApiResponse<OaIntegrationDtos.WritingStatus> writingStatusByBusinessId(@RequestParam String businessId) {
        return ApiResponse.success(writingService.findStatus(businessId));
    }

    @GetMapping("/writing-tasks/{taskId}/document/versions/{version}")
    ApiResponse<DocumentDtos.DocumentResponse> submittedDocument(
            @PathVariable String taskId, @PathVariable int version, @RequestParam String businessId) {
        if (!writingService.isSubmittedVersion(taskId, businessId, version))
            throw BusinessException.notFound("该版本尚未提交 OA");
        return ApiResponse.success(documentService.version(taskId, version));
    }

    @PostMapping("/review-tasks")
    @Transactional
    ResponseEntity<ApiResponse<OaIntegrationDtos.CreateReviewResponse>> createReview(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OaIntegrationDtos.CreateReviewRequest request)
            throws JsonProcessingException {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw BusinessException.unprocessable("OA 发起审校必须提供 Idempotency-Key");
        }
        if (StringUtils.hasText(request.callbackUrl())) {
            callbackSecurity.validateCallbackUrl(request.callbackUrl());
        }
        rejectDuplicateReview(request.businessId());
        ReviewDtos.TaskResponse created = reviewService.create(
                new ReviewDtos.CreateRequest(request.writingTaskId(), request.reviewPlanId(), request.callbackUrl()));
        ReviewDtos.TaskResponse external = reviewService.annotateExternal(
                created.id(), "OA", request.businessId(), request.documentFileId(), request.callbackUrl());
        reportService.generate(external.id());

        var response = reviewResponse(external, request.callbackUrl());
        return ResponseEntity.accepted().location(URI.create(response.statusUrl())).body(ApiResponse.accepted(response));
    }

    @PostMapping(value = "/review-tasks/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    ResponseEntity<ApiResponse<OaIntegrationDtos.CreateReviewResponse>> uploadReview(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam String businessId,
            @RequestParam String documentTypeCode,
            @RequestParam(required = false) String reviewPlanId,
            @RequestParam(required = false) String outputTemplateId,
            @RequestParam(required = false) String callbackUrl,
            @RequestPart("file") MultipartFile file) throws IOException {
        requireIdempotencyKey(idempotencyKey);
        if (!StringUtils.hasText(businessId) || businessId.length() > 100)
            throw BusinessException.unprocessable("businessId 不能为空且不能超过 100 字符");
        if (StringUtils.hasText(callbackUrl)) callbackSecurity.validateCallbackUrl(callbackUrl);
        rejectDuplicateReview(businessId);
        ReviewDtos.TaskResponse created = reviewService.createStandaloneDocx(
                documentTypeCode, reviewPlanId, outputTemplateId, file.getOriginalFilename(), file.getBytes());
        ReviewDtos.TaskResponse external = reviewService.annotateExternal(
                created.id(), "OA", businessId, null, callbackUrl);
        reportService.generate(external.id());
        OaIntegrationDtos.CreateReviewResponse response = reviewResponse(external, callbackUrl);
        return ResponseEntity.accepted().location(URI.create(response.statusUrl())).body(ApiResponse.accepted(response));
    }

    @GetMapping("/review-tasks/{taskId}")
    ApiResponse<OaIntegrationDtos.ReviewResult> reviewResult(
            @PathVariable String taskId, @RequestParam String businessId) {
        ReviewDtos.TaskResponse task = reviewService.get(taskId);
        if (!"OA".equals(task.sourceSystem()) || !businessId.equals(task.businessId()))
            throw BusinessException.notFound("OA 审校任务不存在");
        return ApiResponse.success(new OaIntegrationDtos.ReviewResult(
                task, reviewService.issues(taskId, null, null, null), reportService.list(taskId)));
    }

    @GetMapping("/review-tasks")
    ApiResponse<OaIntegrationDtos.ReviewResult> reviewResultByBusinessId(@RequestParam String businessId) {
        ReviewDtos.TaskResponse task = reviewService.findExternal("OA", businessId);
        if (task == null) throw BusinessException.notFound("OA 审校任务不存在");
        return reviewResult(task.id(), businessId);
    }

    @GetMapping("/review-tasks/{taskId}/reports/{version}/export")
    ResponseEntity<byte[]> exportReviewReport(
            @PathVariable String taskId, @PathVariable int version,
            @RequestParam String businessId, @RequestParam(defaultValue = "PDF") String format) {
        ReviewDtos.TaskResponse task = reviewService.get(taskId);
        if (!"OA".equals(task.sourceSystem()) || !businessId.equals(task.businessId()))
            throw BusinessException.notFound("OA 审校任务不存在");
        return reportFile(taskId, version, format);
    }

    @GetMapping("/writing-tasks/{taskId}/reviews/{reviewId}/reports")
    ApiResponse<java.util.List<cn.gov.document.platform.review.api.ReviewReportDtos.Response>> writingReviewReports(
            @PathVariable String taskId, @PathVariable String reviewId, @RequestParam String businessId) {
        requireWritingReview(taskId, reviewId, businessId);
        return ApiResponse.success(reportService.list(reviewId));
    }

    @GetMapping("/writing-tasks/{taskId}/reviews/{reviewId}/reports/{version}/export")
    ResponseEntity<byte[]> exportWritingReviewReport(
            @PathVariable String taskId, @PathVariable String reviewId, @PathVariable int version,
            @RequestParam String businessId, @RequestParam(defaultValue = "PDF") String format) {
        requireWritingReview(taskId, reviewId, businessId);
        return reportFile(reviewId, version, format);
    }

    private void requireWritingReview(String taskId, String reviewId, String businessId) {
        writingService.status(taskId, businessId);
        if (!taskId.equals(reviewService.get(reviewId).writingTaskId()))
            throw BusinessException.notFound("OA 写作任务的审校报告不存在");
    }

    private ResponseEntity<byte[]> reportFile(String taskId, int version, String format) {
        boolean docx = "DOCX".equalsIgnoreCase(format);
        if (!docx && !"PDF".equalsIgnoreCase(format))
            throw BusinessException.unprocessable("报告仅支持 PDF 或 DOCX");
        byte[] content = docx ? reportService.exportDocx(taskId, version) : reportService.exportPdf(taskId, version);
        String mediaType = docx
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header("Content-Disposition", "attachment; filename=review-report-v" + version + (docx ? ".docx" : ".pdf"))
                .body(content);
    }

    private void requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) throw BusinessException.unprocessable("OA 请求必须提供 Idempotency-Key");
    }

    private void rejectDuplicateReview(String businessId) {
        ReviewDtos.TaskResponse existing = reviewService.findExternal("OA", businessId);
        if (existing != null) throw BusinessException.conflict("该 OA 业务单据已有审校任务：" + existing.id());
    }

    private OaIntegrationDtos.CreateReviewResponse reviewResponse(ReviewDtos.TaskResponse task, String callbackUrl)
            throws JsonProcessingException {
        String statusUrl = "/api/v1/integrations/oa/review-tasks/" + task.id();
        String callbackJobId = null;
        if (StringUtils.hasText(callbackUrl)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event", "REVIEW_COMPLETED");
            body.put("sourceSystem", "OA");
            body.put("businessId", task.businessId());
            body.put("reviewTaskId", task.id());
            body.put("status", task.status());
            body.put("totalIssues", task.totalIssues());
            body.put("pendingIssues", task.pendingIssues());
            body.put("statusUrl", statusUrl + "?businessId=" + java.net.URLEncoder.encode(task.businessId(), java.nio.charset.StandardCharsets.UTF_8));
            String payload = objectMapper.writeValueAsString(Map.of("callbackUrl", callbackUrl, "body", body));
            AsyncJobDtos.Response job = jobService.submit(
                    new AsyncJobDtos.SubmitRequest("OA_CALLBACK", task.id(), payload, 5));
            callbackJobId = job.id();
        }
        return new OaIntegrationDtos.CreateReviewResponse(task, callbackJobId, statusUrl);
    }
}
