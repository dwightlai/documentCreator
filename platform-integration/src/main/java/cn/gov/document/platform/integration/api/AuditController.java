package cn.gov.document.platform.integration.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.integration.application.AuditService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<List<AuditDtos.Response>> list(
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(service.list(auditType, targetId, limit));
    }
}
