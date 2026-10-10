package cn.gov.document.platform.review.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.review.application.ReviewReportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/review-tasks/{taskId}/reports")
public class ReviewReportController {
    private final ReviewReportService service;

    public ReviewReportController(ReviewReportService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<ReviewReportDtos.Response> generate(@PathVariable String taskId) {
        return ApiResponse.success(service.generate(taskId));
    }

    @GetMapping
    ApiResponse<List<ReviewReportDtos.Response>> list(@PathVariable String taskId) {
        return ApiResponse.success(service.list(taskId));
    }

    @GetMapping("/{version}")
    ApiResponse<ReviewReportDtos.Response> get(@PathVariable String taskId, @PathVariable int version) {
        return ApiResponse.success(service.get(taskId, version));
    }

    @PostMapping("/{version}/export")
    ResponseEntity<byte[]> export(
            @PathVariable String taskId, @PathVariable int version, @RequestParam(defaultValue = "PDF") String format) {
        boolean docx = "DOCX".equalsIgnoreCase(format);
        if (!docx && !"PDF".equalsIgnoreCase(format))
            throw cn.gov.document.platform.common.error.BusinessException.unprocessable("审校报告仅支持 PDF 或 DOCX");
        byte[] body = docx ? service.exportDocx(taskId, version) : service.exportPdf(taskId, version);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                docx
                        ? MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("review-report-v" + version + (docx ? ".docx" : ".pdf"), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
