package cn.gov.document.platform.publicdata;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.context.RequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/public-source-pack")
public class PublicSourcePackController {
    private final PublicSourcePackService service;

    public PublicSourcePackController(PublicSourcePackService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PublicSourcePackService.PackInfo> info() {
        return ApiResponse.success(service.info(RequestContext.tenantId()));
    }

    @PostMapping("/install")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<PublicSourcePackService.InstallResult> install() {
        return ApiResponse.success(service.install(RequestContext.tenantId(), RequestContext.userId()));
    }
}
