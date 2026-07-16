package cn.gov.document.platform.knowledge.application;

import cn.gov.document.platform.job.application.AsyncJobHandler;
import cn.gov.document.platform.job.application.SystemHealthCheckJobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeParseJobHandler implements AsyncJobHandler {
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public KnowledgeParseJobHandler(KnowledgeService knowledgeService, ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "KNOWLEDGE_PARSE";
    }

    @Override
    public String execute(String payload, ProgressReporter reporter) throws Exception {
        JsonNode command = objectMapper.readTree(payload);
        String sourceId = command.path("sourceId").asText();
        String documentId = command.path("documentId").asText();
        reporter.progress(10, "开始解析知识文档");
        if (reporter.cancellationRequested()) {
            throw new SystemHealthCheckJobHandler.JobCancelledException();
        }
        var result = knowledgeService.parse(sourceId, documentId);
        reporter.progress(90, "知识文档切分完成");
        return objectMapper.writeValueAsString(result);
    }
}
