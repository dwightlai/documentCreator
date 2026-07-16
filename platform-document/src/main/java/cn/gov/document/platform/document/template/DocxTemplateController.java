package cn.gov.document.platform.document.template;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/template-engine")
public class DocxTemplateController {
    private final DocxTemplateEngine engine;
    private final ObjectMapper objectMapper;

    public DocxTemplateController(DocxTemplateEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('platform.admin')")
    ApiResponse<DocxTemplateEngine.Inspection> inspect(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(engine.inspect(file.getOriginalFilename(), file.getBytes()));
    }

    @PostMapping(value = "/render-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('platform.admin')")
    ResponseEntity<byte[]> renderPreview(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") String data) throws IOException {
        RenderData renderData = readRenderData(data);
        byte[] body = engine.render(file.getBytes(), renderData.values(), renderData.sections());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("template-preview.docx", StandardCharsets.UTF_8).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private RenderData readRenderData(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if (root == null || !root.isObject()) {
                throw BusinessException.unprocessable("模板预览 data 必须是 JSON 对象");
            }
            Map<String, String> values = new LinkedHashMap<>();
            JsonNode valueNode = root.path("values");
            if (valueNode.isObject()) {
                valueNode.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText("")));
            }
            List<String> sections = new ArrayList<>();
            JsonNode sectionNode = root.path("sections");
            if (sectionNode.isArray()) {
                sectionNode.forEach(value -> sections.add(value.asText("")));
            }
            return new RenderData(Map.copyOf(values), List.copyOf(sections));
        } catch (JsonProcessingException invalidJson) {
            throw BusinessException.unprocessable("模板预览 data 不是有效 JSON");
        }
    }

    private record RenderData(Map<String, String> values, List<String> sections) { }
}
