package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.OfflineEvidenceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/review/offline-evidence")
public class OfflineEvidenceController {
    private final OfflineEvidenceService service;

    public OfflineEvidenceController(OfflineEvidenceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<OfflineEvidenceService.PackInfo> info() {
        return ApiResponse.success(service.info());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<JsonNode> exportPack() {
        return ApiResponse.success(service.exportPack());
    }

    @GetMapping("/snapshots")
    public ApiResponse<List<OfflineEvidenceService.SourceSnapshot>> snapshots() {
        return ApiResponse.success(service.snapshots());
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<List<OfflineEvidenceService.PackInfo>> history() {
        return ApiResponse.success(service.history());
    }

    @PostMapping("/versions/{digest}/activate")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<OfflineEvidenceService.PackInfo> activate(@PathVariable String digest) {
        return ApiResponse.success(service.activate(digest));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('platform.admin')")
    public ApiResponse<OfflineEvidenceService.PackInfo> importPack(@RequestPart("file") MultipartFile file)
            throws IOException {
        return ApiResponse.success(service.importPack(file.getBytes()));
    }
}
