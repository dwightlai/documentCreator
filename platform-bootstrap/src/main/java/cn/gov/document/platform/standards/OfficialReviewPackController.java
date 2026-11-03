package cn.gov.document.platform.standards;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.context.RequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/standards/review-pack")
public class OfficialReviewPackController {
    private final OfficialReviewPackService service;

    public OfficialReviewPackController(OfficialReviewPackService service) { this.service = service; }

    @GetMapping
    public ApiResponse<OfficialReviewPackService.PackInfo> info() {
        return ApiResponse.success(service.info(RequestContext.tenantId()));
    }

    @PostMapping("/install")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<OfficialReviewPackService.InstallResult> install() {
        return ApiResponse.success(service.install(RequestContext.tenantId(), RequestContext.userId()));
    }
}
