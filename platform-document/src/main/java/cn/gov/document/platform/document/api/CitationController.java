package cn.gov.document.platform.document.api;
import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.application.CitationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}/document/citations")
public class CitationController {
    private final CitationService service; public CitationController(CitationService service){this.service=service;}
    @PostMapping ApiResponse<CitationDtos.Response> create(@PathVariable String taskId,@Valid @RequestBody CitationDtos.CreateRequest request){return ApiResponse.success(service.create(taskId,request));}
    @GetMapping ApiResponse<List<CitationDtos.Response>> list(@PathVariable String taskId){return ApiResponse.success(service.list(taskId));}
    @DeleteMapping("/{citationId}") ApiResponse<Void> delete(@PathVariable String taskId,@PathVariable String citationId){service.delete(taskId,citationId);return ApiResponse.success(null);}
}
