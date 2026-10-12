package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.VersionRetentionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运维接口：立即对当前租户执行一次版本保留策略（正常情况下由每日定时任务执行）。 */
@RestController
@RequestMapping("/api/v1/document-version-retention")
public class VersionRetentionController {
    private final VersionRetentionService service;

    public VersionRetentionController(VersionRetentionService service) {
        this.service = service;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('platform.admin')")
    @PostMapping("/run")
    ApiResponse<VersionRetentionService.Result> run() {
        return ApiResponse.success(service.runForCurrentTenant());
    }
}
