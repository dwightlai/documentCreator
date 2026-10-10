package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.ReviewService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/review-tasks")
public class ReviewController {
    private final ReviewService service;
    private final cn.gov.document.platform.review.application.FalsePositiveService falsePositiveService;

    public ReviewController(
            ReviewService service,
            cn.gov.document.platform.review.application.FalsePositiveService falsePositiveService) {
        this.service = service;
        this.falsePositiveService = falsePositiveService;
    }

    @PostMapping
    ApiResponse<ReviewDtos.TaskResponse> create(@Valid @RequestBody ReviewDtos.CreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<ReviewDtos.TaskResponse> createFromDocument(
            @RequestParam String writingTaskId,
            @RequestParam(required = false) String reviewPlanId,
            @RequestParam String outputTemplateId,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return ApiResponse.success(service.createFromDocx(
                new ReviewDtos.CreateRequest(writingTaskId, reviewPlanId, null),
                outputTemplateId,
                file.getOriginalFilename(),
                file.getBytes()));
    }

    @GetMapping
    ApiResponse<List<ReviewDtos.TaskResponse>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{taskId}")
    ApiResponse<ReviewDtos.TaskResponse> get(@PathVariable String taskId) {
        return ApiResponse.success(service.get(taskId));
    }

    @DeleteMapping("/{taskId}")
    ApiResponse<Void> delete(@PathVariable String taskId) {
        service.delete(taskId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{taskId}/issues")
    ApiResponse<List<ReviewDtos.IssueResponse>> issues(
            @PathVariable String taskId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String processingStatus) {
        return ApiResponse.success(service.issues(taskId, category, riskLevel, processingStatus));
    }

    @GetMapping("/{taskId}/issues/{issueNumber}")
    ApiResponse<ReviewDtos.IssueResponse> issue(@PathVariable String taskId, @PathVariable String issueNumber) {
        return ApiResponse.success(service.issue(taskId, issueNumber));
    }

    @PatchMapping("/{taskId}/issues/{issueNumber}/accept")
    ApiResponse<ReviewDtos.IssueResponse> accept(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @RequestBody(required = false) ReviewDtos.ProcessRequest request) {
        return ApiResponse.success(service.accept(taskId, issueNumber, request));
    }

    @PatchMapping("/{taskId}/issues/{issueNumber}/ignore")
    ApiResponse<ReviewDtos.IssueResponse> ignore(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @RequestBody(required = false) ReviewDtos.ProcessRequest request) {
        return ApiResponse.success(service.ignore(taskId, issueNumber, request));
    }

    @PatchMapping("/{taskId}/issues/{issueNumber}/modify")
    ApiResponse<ReviewDtos.IssueResponse> modify(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @RequestBody(required = false) ReviewDtos.ProcessRequest request) {
        return ApiResponse.success(service.modify(taskId, issueNumber, request));
    }

    @PatchMapping("/{taskId}/issues/{issueNumber}/transfer")
    ApiResponse<ReviewDtos.IssueResponse> transfer(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @RequestBody(required = false) ReviewDtos.ProcessRequest request) {
        return ApiResponse.success(service.transfer(taskId, issueNumber, request));
    }

    @PostMapping("/{taskId}/issues/batch-ignore")
    ApiResponse<List<ReviewDtos.IssueResponse>> batchIgnore(
            @PathVariable String taskId, @Valid @RequestBody ReviewDtos.BatchRequest request) {
        return ApiResponse.success(service.batchIgnore(taskId, request));
    }

    @PostMapping("/{taskId}/issues/batch-accept")
    ApiResponse<List<ReviewDtos.IssueResponse>> batchAccept(
            @PathVariable String taskId, @Valid @RequestBody ReviewDtos.BatchRequest request) {
        return ApiResponse.success(service.batchAccept(taskId, request));
    }

    @PostMapping("/{taskId}/issues/{issueNumber}/add-to-dictionary")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('platform.admin')")
    ApiResponse<cn.gov.document.platform.rule.api.DictionaryDtos.TermResponse> addToDictionary(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @Valid @RequestBody ReviewDtos.AddToDictionaryRequest request) {
        return ApiResponse.success(service.addToDictionary(taskId, issueNumber, request));
    }

    @PostMapping("/{taskId}/issues/{issueNumber}/mark-false-positive")
    ApiResponse<FalsePositiveDtos.Response> falsePositive(
            @PathVariable String taskId,
            @PathVariable String issueNumber,
            @Valid @RequestBody FalsePositiveDtos.SubmitRequest request) {
        return ApiResponse.success(falsePositiveService.submit(taskId, issueNumber, request));
    }

    @PostMapping("/{taskId}/re-review")
    ApiResponse<ReviewDtos.TaskResponse> reReview(@PathVariable String taskId) {
        return ApiResponse.success(service.reReview(taskId));
    }

    @GetMapping("/{taskId}/statistics")
    ApiResponse<ReviewDtos.Statistics> statistics(@PathVariable String taskId) {
        return ApiResponse.success(service.statistics(taskId));
    }
}
