package cn.gov.document.platform.generation;

import cn.gov.document.platform.common.api.ApiResponse;
import cn.gov.document.platform.document.api.DocumentDtos;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.application.AsyncJobHandler;
import cn.gov.document.platform.job.application.AsyncJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
public class DocumentGenerationJob implements AsyncJobHandler {
    private final DocumentService documents;
    private final ObjectMapper mapper;
    private final org.springframework.transaction.support.TransactionTemplate progressTransaction;
    public DocumentGenerationJob(DocumentService documents, ObjectMapper mapper, org.springframework.transaction.PlatformTransactionManager transactions) {
        this.documents = documents; this.mapper = mapper;
        progressTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        progressTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public String jobType() { return "DOCUMENT_GENERATION"; }
    public record Command(String taskId, String nodeId, String instruction, boolean confirmed) {}
    public String execute(String payload, ProgressReporter reporter) throws Exception {
        Command command = mapper.readValue(payload, Command.class);
        reporter.progress(5, command.nodeId() == null ? "开始按提纲重建正文" : "正在重新生成本节");
        var result = command.nodeId() == null
                ? documents.rebuild(command.taskId(), command.confirmed(), (percent, message) ->
                    progressTransaction.executeWithoutResult(status -> reporter.progress(percent, message)))
                : documents.generateSection(command.taskId(), new DocumentDtos.GenerateSectionRequest(command.nodeId(), command.instruction()));
        reporter.progress(100, "正文已保存为 V" + result.currentVersion());
        return mapper.writeValueAsString(result);
    }

    @RestController
    @RequestMapping("/api/v1/writing-tasks/{taskId}/document/generation-jobs")
    public static class Controller {
        private final AsyncJobService jobs;
        private final ObjectMapper mapper;
        private final cn.gov.document.platform.writing.application.WritingTaskService tasks;
        public Controller(AsyncJobService jobs, ObjectMapper mapper, cn.gov.document.platform.writing.application.WritingTaskService tasks) { this.jobs = jobs; this.mapper = mapper; this.tasks = tasks; }
        @PostMapping
        public ApiResponse<AsyncJobDtos.Response> submit(@PathVariable String taskId, @RequestBody Command request) throws Exception {
            tasks.require(taskId);
            return ApiResponse.success(jobs.submit(new AsyncJobDtos.SubmitRequest("DOCUMENT_GENERATION", taskId,
                    mapper.writeValueAsString(new Command(taskId, request.nodeId(), request.instruction(), request.confirmed())), 0)));
        }
    }
}
