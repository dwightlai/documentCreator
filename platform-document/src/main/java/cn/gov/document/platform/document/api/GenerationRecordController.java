package cn.gov.document.platform.document.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.DocumentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/generation-records")
public class GenerationRecordController {
    private final DocumentService service;

    public GenerationRecordController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<DocumentDtos.GenerationRecordResponse>> list(@PathVariable String taskId) {
        return ApiResponse.success(service.generationRecords(taskId));
    }
}
