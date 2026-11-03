package cn.gov.document.platform.rule.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.rule.application.RuleDataExchangeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RuleDataExchangeController {
    private final RuleDataExchangeService service;

    public RuleDataExchangeController(RuleDataExchangeService service) {
        this.service = service;
    }

    @GetMapping("/rules/export")
    public ApiResponse<RuleDataExchangeDtos.RuleBundle> exportRules() {
        return ApiResponse.success(service.exportRules());
    }

    @PostMapping("/rules/import")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<RuleDataExchangeDtos.ImportResult> importRules(
            @RequestBody RuleDataExchangeDtos.RuleBundle bundle) {
        return ApiResponse.success(service.importRules(bundle));
    }

    @GetMapping("/dictionaries/export")
    public ApiResponse<RuleDataExchangeDtos.DictionaryBundle> exportDictionaries() {
        return ApiResponse.success(service.exportDictionaries());
    }

    @PostMapping("/dictionaries/import")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<RuleDataExchangeDtos.ImportResult> importDictionaries(
            @RequestBody RuleDataExchangeDtos.DictionaryBundle bundle) {
        return ApiResponse.success(service.importDictionaries(bundle));
    }
}
