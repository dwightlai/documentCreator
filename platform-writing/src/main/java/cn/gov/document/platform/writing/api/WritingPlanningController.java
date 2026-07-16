package cn.gov.document.platform.writing.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.writing.application.WritingPlanningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/writing-tasks/{taskId}")
public class WritingPlanningController {
    private final WritingPlanningService service;
    public WritingPlanningController(WritingPlanningService service){this.service=service;}
    @PostMapping("/ideas") ApiResponse<WritingPlanningDtos.IdeaResponse> generateIdea(@PathVariable String taskId){return ApiResponse.success(service.generateIdea(taskId));}
    @GetMapping("/ideas/{version}") ApiResponse<WritingPlanningDtos.IdeaResponse> getIdea(@PathVariable String taskId,@PathVariable int version){return ApiResponse.success(service.getIdea(taskId,version));}
    @PutMapping("/ideas/{version}") ApiResponse<WritingPlanningDtos.IdeaResponse> updateIdea(@PathVariable String taskId,@PathVariable int version,@Valid @RequestBody WritingPlanningDtos.UpdateIdeaRequest request){return ApiResponse.success(service.updateIdea(taskId,version,request));}
    @PostMapping("/title-candidates") ApiResponse<List<WritingPlanningDtos.TitleCandidate>> titles(@PathVariable String taskId){return ApiResponse.success(service.titleCandidates(taskId));}
}
