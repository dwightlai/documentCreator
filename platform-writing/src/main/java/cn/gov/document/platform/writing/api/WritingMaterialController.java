package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.writing.application.WritingMaterialService;
import cn.gov.document.platform.writing.application.WritingMaterialFileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/materials")
public class WritingMaterialController {
    private final WritingMaterialService service;
    private final WritingMaterialFileService fileService;
    public WritingMaterialController(WritingMaterialService service, WritingMaterialFileService fileService) { this.service = service; this.fileService = fileService; }

    @PostMapping
    ApiResponse<WritingMaterialDtos.Response> create(@PathVariable String taskId,
                                                       @Valid @RequestBody WritingMaterialDtos.CreateRequest request) {
        return ApiResponse.success(service.create(taskId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<WritingMaterialDtos.Response> upload(@PathVariable String taskId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String authorityLevel, @RequestParam(required = false) String usageMode,
            @RequestParam(required = false) String citationPurpose, @RequestParam(required = false) String permissionStatus) {
        return ApiResponse.success(fileService.upload(taskId, file, authorityLevel, usageMode, citationPurpose, permissionStatus));
    }

    @GetMapping
    ApiResponse<List<WritingMaterialDtos.Response>> list(@PathVariable String taskId) {
        return ApiResponse.success(service.list(taskId));
    }

    @PutMapping("/{materialId}") ApiResponse<WritingMaterialDtos.Response> update(@PathVariable String taskId,@PathVariable String materialId,@Valid @RequestBody WritingMaterialDtos.UpdateRequest request){return ApiResponse.success(service.update(taskId,materialId,request));}
    @PostMapping("/snapshot") ApiResponse<WritingMaterialDtos.SnapshotResponse> createSnapshot(@PathVariable String taskId){return ApiResponse.success(service.createSnapshot(taskId));}
    @GetMapping("/snapshot/{version}") ApiResponse<WritingMaterialDtos.SnapshotResponse> snapshot(@PathVariable String taskId,@PathVariable int version){return ApiResponse.success(service.snapshot(taskId,version));}

    @DeleteMapping("/{materialId}")
    ResponseEntity<Void> delete(@PathVariable String taskId, @PathVariable String materialId) {
        service.delete(taskId, materialId);
        return ResponseEntity.noContent().build();
    }
}
