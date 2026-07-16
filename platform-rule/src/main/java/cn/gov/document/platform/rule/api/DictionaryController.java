package cn.gov.document.platform.rule.api;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.rule.application.DictionaryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {
    private final DictionaryService service;public DictionaryController(DictionaryService service){this.service=service;}
    @GetMapping ApiResponse<List<DictionaryDtos.DictionaryResponse>> list(@RequestParam(required=false)String dictType){return ApiResponse.success(service.dictionaries(dictType));}
    @PostMapping @PreAuthorize("hasRole('platform.admin')") ApiResponse<DictionaryDtos.DictionaryResponse> create(@Valid @RequestBody DictionaryDtos.CreateDictionaryRequest request){return ApiResponse.success(service.createDictionary(request));}
    @GetMapping("/{dictId}") ApiResponse<DictionaryDtos.DictionaryResponse> get(@PathVariable String dictId){return ApiResponse.success(service.dictionary(dictId));}
    @GetMapping("/{dictId}/terms") ApiResponse<List<DictionaryDtos.TermResponse>> terms(@PathVariable String dictId,@RequestParam(required=false)String keyword,@RequestParam(required=false)String category){return ApiResponse.success(service.terms(dictId,keyword,category));}
    @PostMapping("/{dictId}/terms") @PreAuthorize("hasRole('platform.admin')") ApiResponse<DictionaryDtos.TermResponse> createTerm(@PathVariable String dictId,@Valid @RequestBody DictionaryDtos.CreateTermRequest request){return ApiResponse.success(service.createTerm(dictId,request));}
    @PutMapping("/{dictId}/terms/{termId}") @PreAuthorize("hasRole('platform.admin')") ApiResponse<DictionaryDtos.TermResponse> updateTerm(@PathVariable String dictId,@PathVariable String termId,@Valid @RequestBody DictionaryDtos.UpdateTermRequest request){return ApiResponse.success(service.updateTerm(dictId,termId,request));}
    @DeleteMapping("/{dictId}/terms/{termId}") @PreAuthorize("hasRole('platform.admin')") ApiResponse<Void> deleteTerm(@PathVariable String dictId,@PathVariable String termId){service.deleteTerm(dictId,termId);return ApiResponse.success(null);}
    @PostMapping(value="/{dictId}/import",consumes="multipart/form-data") @PreAuthorize("hasRole('platform.admin')") ApiResponse<DictionaryDtos.ImportResponse> importCsv(@PathVariable String dictId,@RequestPart("file") MultipartFile file){return ApiResponse.success(service.importCsv(dictId,file));}
}
