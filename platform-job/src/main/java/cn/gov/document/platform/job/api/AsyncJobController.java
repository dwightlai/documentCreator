package cn.gov.document.platform.job.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.job.application.AsyncJobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/jobs")
public class AsyncJobController {
    private final AsyncJobService service;

    public AsyncJobController(AsyncJobService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ApiResponse<AsyncJobDtos.Response>> submit(@Valid @RequestBody AsyncJobDtos.SubmitRequest request) {
        AsyncJobDtos.Response job = service.submit(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + job.id()))
                .body(ApiResponse.success(job));
    }

    @GetMapping
    ApiResponse<List<AsyncJobDtos.Response>> list(
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(service.list(status, limit));
    }

    @GetMapping("/{jobId}")
    ApiResponse<AsyncJobDtos.Response> get(@PathVariable String jobId) {
        return ApiResponse.success(service.get(jobId));
    }

    @GetMapping("/{jobId}/events/history")
    ApiResponse<List<AsyncJobDtos.EventResponse>> history(
            @PathVariable String jobId, @RequestParam(defaultValue = "0") int afterEventId) {
        return ApiResponse.success(service.history(jobId, afterEventId));
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(
            @PathVariable String jobId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        int after = 0;
        if (lastEventId != null && lastEventId.matches("\\d+")) {
            after = Integer.parseInt(lastEventId);
        }
        return service.stream(jobId, after);
    }

    @DeleteMapping("/{jobId}")
    ApiResponse<AsyncJobDtos.Response> cancel(@PathVariable String jobId) {
        return ApiResponse.success(service.cancel(jobId));
    }

    @PostMapping("/{jobId}/retry")
    ApiResponse<AsyncJobDtos.Response> retry(@PathVariable String jobId) {
        return ApiResponse.success(service.retry(jobId));
    }
}
