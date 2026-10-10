package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.ReviewPlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/review-plans")
public class ReviewPlanController {
    private final ReviewPlanService service;

    public ReviewPlanController(ReviewPlanService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<ReviewPlanDtos.Response>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    ApiResponse<ReviewPlanDtos.Response> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ReviewPlanDtos.Response> create(@Valid @RequestBody ReviewPlanDtos.Request request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<ReviewPlanDtos.Response> update(
            @PathVariable String id, @Valid @RequestBody ReviewPlanDtos.Request request) {
        return ApiResponse.success(service.update(id, request));
    }
}
