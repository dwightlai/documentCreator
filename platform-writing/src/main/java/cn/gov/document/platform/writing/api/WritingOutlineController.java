package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.writing.application.WritingOutlineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/outlines")
public class WritingOutlineController {
    private final WritingOutlineService service;

    public WritingOutlineController(WritingOutlineService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<WritingOutlineDtos.Response> generate(@PathVariable String taskId) {
        return ApiResponse.success(service.generate(taskId));
    }

    @GetMapping("/{version}")
    ApiResponse<WritingOutlineDtos.Response> get(@PathVariable String taskId, @PathVariable int version) {
        return ApiResponse.success(service.get(taskId, version));
    }

    @GetMapping("/current")
    ApiResponse<WritingOutlineDtos.Response> current(@PathVariable String taskId) {
        return ApiResponse.success(service.current(taskId));
    }

    @PostMapping("/{version}/structure-check")
    ApiResponse<WritingOutlineDtos.Response> check(@PathVariable String taskId, @PathVariable int version) {
        return ApiResponse.success(service.structureCheck(taskId, version));
    }

    @PostMapping("/{version}/nodes")
    ApiResponse<WritingOutlineDtos.Response> addNode(
            @PathVariable String taskId,
            @PathVariable int version,
            @Valid @RequestBody WritingOutlineDtos.NodeRequest request) {
        return ApiResponse.success(service.addNode(taskId, version, request));
    }

    @PutMapping("/{version}/nodes/{nodeId}")
    ApiResponse<WritingOutlineDtos.Response> updateNode(
            @PathVariable String taskId,
            @PathVariable int version,
            @PathVariable String nodeId,
            @Valid @RequestBody WritingOutlineDtos.NodeRequest request) {
        return ApiResponse.success(service.updateNode(taskId, version, nodeId, request));
    }

    @DeleteMapping("/{version}/nodes/{nodeId}")
    ApiResponse<WritingOutlineDtos.Response> deleteNode(
            @PathVariable String taskId, @PathVariable int version, @PathVariable String nodeId) {
        return ApiResponse.success(service.deleteNode(taskId, version, nodeId));
    }

    @PostMapping("/{version}/nodes/{nodeId}/regenerate")
    ApiResponse<WritingOutlineDtos.Response> regenerateNode(
            @PathVariable String taskId, @PathVariable int version, @PathVariable String nodeId) {
        return ApiResponse.success(service.regenerateNode(taskId, version, nodeId));
    }

    @PostMapping("/{version}/confirm")
    ApiResponse<WritingOutlineDtos.Response> confirm(@PathVariable String taskId, @PathVariable int version) {
        return ApiResponse.success(service.confirm(taskId, version));
    }
}
