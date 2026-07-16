package cn.gov.document.platform.integration.oa;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.application.AsyncJobService;
import cn.gov.document.platform.review.api.ReviewDtos;
import cn.gov.document.platform.review.application.ReviewService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/oa")
public class OaIntegrationController {
    private final ReviewService reviewService;
    private final AsyncJobService jobService;
    private final OaCallbackSecurity callbackSecurity;
    private final ObjectMapper objectMapper;

    public OaIntegrationController(
            ReviewService reviewService,
            AsyncJobService jobService,
            OaCallbackSecurity callbackSecurity,
            ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.jobService = jobService;
        this.callbackSecurity = callbackSecurity;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/review-tasks")
    ResponseEntity<ApiResponse<OaIntegrationDtos.CreateReviewResponse>> createReview(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OaIntegrationDtos.CreateReviewRequest request) throws JsonProcessingException {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw BusinessException.unprocessable("OA 发起审校必须提供 Idempotency-Key");
        }
        if (StringUtils.hasText(request.callbackUrl())) {
            callbackSecurity.validateCallbackUrl(request.callbackUrl());
        }
        ReviewDtos.TaskResponse created = reviewService.create(
                new ReviewDtos.CreateRequest(request.writingTaskId(), request.reviewPlanId(), request.callbackUrl()));
        ReviewDtos.TaskResponse external = reviewService.annotateExternal(
                created.id(), "OA", request.businessId(), request.documentFileId(), request.callbackUrl());

        String statusUrl = "/api/v1/review-tasks/" + external.id();
        String callbackJobId = null;
        if (StringUtils.hasText(request.callbackUrl())) {
            Map<String, Object> callbackBody = new LinkedHashMap<>();
            callbackBody.put("sourceSystem", "OA");
            callbackBody.put("businessId", request.businessId());
            callbackBody.put("reviewTaskId", external.id());
            callbackBody.put("status", external.status());
            callbackBody.put("statusUrl", statusUrl);
            String payload = objectMapper.writeValueAsString(
                    Map.of("callbackUrl", request.callbackUrl(), "body", callbackBody));
            AsyncJobDtos.Response callbackJob = jobService.submit(
                    new AsyncJobDtos.SubmitRequest("OA_CALLBACK", external.id(), payload, 5));
            callbackJobId = callbackJob.id();
        }
        var response = new OaIntegrationDtos.CreateReviewResponse(external, callbackJobId, statusUrl);
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(ApiResponse.accepted(response));
    }
}
