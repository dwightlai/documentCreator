package cn.gov.document.platform.document.onlyoffice;

import cn.gov.document.platform.common.api.ApiResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnlyOfficeController {
    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeController.class);
    private final OnlyOfficeService service;

    public OnlyOfficeController(OnlyOfficeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/onlyoffice/status")
    ApiResponse<OnlyOfficeService.Status> status() {
        return ApiResponse.success(service.status());
    }

    @GetMapping("/api/v1/writing-tasks/{taskId}/document/typesets/{typesetId}/editor-config")
    ApiResponse<OnlyOfficeService.EditorConfig> editorConfig(@PathVariable String taskId, @PathVariable String typesetId) {
        return ApiResponse.success(service.editorConfig(taskId, typesetId));
    }

    /** 文档服务器拉取文件：不经登录认证，凭路径中的平台访问令牌。 */
    @GetMapping("/api/v1/onlyoffice/files/{token}")
    ResponseEntity<byte[]> file(@PathVariable String token) {
        byte[] body = service.file(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(body.length)
                .body(body);
    }

    /** 文档服务器保存回调：按 ONLYOFFICE 约定返回 {"error":0}，处理失败返回 {"error":1}，由文档服务器提示保存失败。 */
    @PostMapping("/api/v1/onlyoffice/callback/{token}")
    Map<String, Integer> callback(
            @PathVariable String token,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        try {
            service.callback(token, body, authorization);
            return Map.of("error", 0);
        } catch (RuntimeException failure) {
            log.warn("ONLYOFFICE 保存回调处理失败: {}", failure.getMessage());
            return Map.of("error", 1);
        }
    }
}
