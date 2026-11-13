package cn.gov.document.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能公文写作与政务智能审校平台 API")
                        .version("v1")
                        .description("V1.6 详细设计对应的 REST 与流式接口契约。第三方集成请查看「开放 API」分组。")
                        .license(new License().name("Proprietary")));
    }

    @Bean
    GroupedOpenApi openIntegrationApi() {
        return GroupedOpenApi.builder()
                .group("open-api")
                .displayName("开放 API（第三方集成）")
                .pathsToMatch("/api/v1/integrations/oa/**")
                .addOpenApiCustomizer(OpenApiConfiguration::customizeOpenApi)
                .build();
    }

    @Bean
    GroupedOpenApi platformApiGroup() {
        return GroupedOpenApi.builder()
                .group("platform")
                .displayName("平台 API")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/v1/integrations/oa/**")
                .build();
    }

    private static void customizeOpenApi(OpenAPI openApi) {
        openApi.info(new Info().title("公文开放 API").version("v1").description(OVERVIEW));
        Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
        openApi.components(components);
        components.addSecuritySchemes(
                "AppCode",
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Open-App-Code")
                        .description(DOCS.get("X-Open-App-Code")));
        components.addSecuritySchemes(
                "ApiKey",
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Open-Api-Key")
                        .description(DOCS.get("X-Open-Api-Key")));
        openApi.setServers(List.of(new Server().url("/").description("当前环境")));
        openApi.setSecurity(List.of(new SecurityRequirement().addList("AppCode").addList("ApiKey")));
        openApi.setTags(List.of(
                new Tag().name("写作").description("发起写作、查询进度，读取已提交文稿和审校报告"),
                new Tag().name("审校").description("提交正文或 DOCX 审校，查询结果并导出报告")));
        if (openApi.getPaths() == null) return;
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean writing = path.contains("/writing-tasks")
                    || path.endsWith("/documents/summary")
                    || path.endsWith("/documents/polish")
                    || path.endsWith("/documents/expand");
            operation.setTags(List.of(writing ? "写作" : "审校"));
            String summary = summary(method, path);
            if (summary != null) operation.setSummary(summary);
            String detail = detail(path);
            if (detail != null) operation.setDescription(detail);
            explainParameters(operation);
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                operation.getRequestBody().getContent().values().forEach(OpenApiConfiguration::explainBody);
            }
            if (requiresIdempotency(method, path)) explainIdempotency(operation);
        }));
        explainSchemas(openApi);
    }

    private static final Map<String, String> DOCS = docs();

    private static final String OVERVIEW =
            """
            供 OA 等第三方业务系统调用。摘要、润色、扩写和正文审校直接提交正文，平台不保存文稿。文种按标题识别。审校固定为内容安全审校。

            每个请求都要带这两个请求头：
            - `X-Open-App-Code`：%s
            - `X-Open-Api-Key`：%s

            以下请求头可选，只写入审计，不参与鉴权，也不对应平台用户：
            - `X-Open-User-Id`：%s
            - `X-Open-Unit-Id`：%s
            - `X-Open-Department-Id`：%s
            - `X-Open-Role-Ids`：%s

            `Idempotency-Key`：%s

            `businessId`：%s

            `callbackUrl`：%s

            `outputTemplateId`：%s

            `documentType`：%s

            `format`：%s
            """
                    .formatted(
                            DOCS.get("X-Open-App-Code"),
                            DOCS.get("X-Open-Api-Key"),
                            DOCS.get("X-Open-User-Id"),
                            DOCS.get("X-Open-Unit-Id"),
                            DOCS.get("X-Open-Department-Id"),
                            DOCS.get("X-Open-Role-Ids"),
                            DOCS.get("Idempotency-Key"),
                            DOCS.get("businessId"),
                            DOCS.get("callbackUrl"),
                            DOCS.get("outputTemplateId"),
                            DOCS.get("documentType"),
                            DOCS.get("format"));

    private static Map<String, String> docs() {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("X-Open-App-Code", "第三方应用编码，与「第三方」中的应用编码一致。");
        docs.put("X-Open-Api-Key", "该应用生成的密钥，只在生成时显示一次。未加入白名单不能调用；白名单选择「全部开放能力」后可调用本租户全部开放接口。");
        docs.put("X-Open-User-Id", "调用方系统中的用户 ID。");
        docs.put("X-Open-Unit-Id", "调用方单位 ID。");
        docs.put("X-Open-Department-Id", "调用方部门 ID。");
        docs.put("X-Open-Role-Ids", "调用方角色 ID，多个用英文逗号分隔。");
        docs.put(
                "Idempotency-Key",
                "只在创建任务时必填：发起写作、审校提交正文、上传 DOCX 审校、审校平台已有写作任务。调用方每次新建任务自己生成一个键，长度 8–128，只能包含字母、数字、点、下划线、冒号和连字符。超时或失败后用同一个键、同一份请求重试，平台返回第一次的结果，不会再建一条任务。同一个键配上不同的请求内容返回 409。摘要、润色、扩写和所有查询不用带。");
        docs.put(
                "businessId",
                "调用方自己的业务单据编号，最长 100。平台不靠它查找文稿。发起写作，以及之后查询写作进度、读取已提交文稿时必填，且要与创建时一致。上传 DOCX 审校、按任务 ID 查询审校时可选；传了才能按这个编号回查，不传则用返回的任务 ID 查询。");
        docs.put("callbackUrl", "可选。任务完成后平台向该地址 POST 通知。必须是管理员白名单内的 HTTPS 地址，不能带用户信息或片段。不传则不回调，由调用方自己查询。");
        docs.put("outputTemplateId", "可选。本平台输出模板 ID，仅上传 DOCX 审校时用于对照版式。不传则不对照。模板文种须与标题识别出的文种一致。");
        docs.put("documentType", "只在发起写作时必填。这时还没有成稿标题，不能自动识别。取值为通知、请示、报告、函、通报、批复、决定、纪要。审校不用传。");
        docs.put("format", "导出报告的文件格式，PDF 或 DOCX，默认 PDF。");
        docs.put("documentFileId", "可选。调用方自己的文件编号，只随审校任务保存，平台不会按它下载文件。");
        docs.put("text", "待处理正文，按换行分段，最长 20000 字。平台不保存这份文稿。");
        docs.put("file", "待审 DOCX 文件。不能包含宏、嵌入对象或外部链接。");
        docs.put("writingTaskId", "本平台写作任务 ID。仅当审校的是已在本平台创建的写作任务时必填。调用方自己的文稿请改用审校提交正文或上传 DOCX。");
        docs.put("taskId", "创建写作或审校时返回的任务 ID。写作任务和审校任务各自独立。");
        docs.put("reviewId", "该写作任务关联的审校任务 ID。");
        docs.put("version", "文稿或报告的版本号，从 1 开始。");
        docs.put("securityLevel", "可选。密级，不传按「内部」。");
        docs.put("documentCategory", "可选。文稿类别，不传则不填。");
        docs.put("authorityLevel", "可选。材料的权威级别，不传按「工作材料」。");
        docs.put("materials", "可选。写作引用的材料，直接传文本，不接受外部文件地址。");
        return docs;
    }

    private static boolean requiresIdempotency(PathItem.HttpMethod method, String path) {
        return method == PathItem.HttpMethod.POST
                && (path.endsWith("/writing-tasks")
                        || path.endsWith("/documents/review")
                        || path.endsWith("/review-tasks")
                        || path.endsWith("/review-tasks/upload"));
    }

    private static void explainIdempotency(Operation operation) {
        if (operation.getParameters() == null) operation.setParameters(new ArrayList<>());
        Parameter header = operation.getParameters().stream()
                .filter(parameter ->
                        "Idempotency-Key".equals(parameter.getName()) && "header".equals(parameter.getIn()))
                .findFirst()
                .orElseGet(() -> {
                    Parameter created = new Parameter().name("Idempotency-Key").in("header");
                    operation.getParameters().add(created);
                    return created;
                });
        header.required(true)
                .description(DOCS.get("Idempotency-Key"))
                .schema(new StringSchema().minLength(8).maxLength(128).pattern("[A-Za-z0-9._:-]{8,128}"));
    }

    private static void explainParameters(Operation operation) {
        if (operation.getParameters() == null) return;
        for (Parameter parameter : operation.getParameters()) {
            String doc = DOCS.get(parameter.getName());
            if (doc != null) parameter.setDescription(doc);
        }
    }

    private static void explainBody(MediaType media) {
        describe(media.getSchema());
    }

    private static void explainSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
        openApi.getComponents().getSchemas().values().forEach(OpenApiConfiguration::describe);
    }

    private static void describe(Schema<?> schema) {
        if (schema == null) return;
        describe(schema.getItems());
        if (schema.getProperties() == null) return;
        schema.getProperties().forEach((name, property) -> {
            String doc = DOCS.get(name);
            if (doc != null) property.setDescription(doc);
            describe(property);
        });
    }

    private static String summary(PathItem.HttpMethod method, String path) {
        if (path.endsWith("/review-tasks/upload")) return "审校已有 DOCX";
        if (path.contains("/writing-tasks/") && path.endsWith("/export")) return "导出写作任务审校报告";
        if (path.contains("/reviews/") && path.endsWith("/reports")) return "读取审校报告列表";
        if (path.contains("/document/versions/")) return "读取已提交文稿";
        if (path.contains("/review-tasks/") && path.endsWith("/export")) return "导出审校报告";
        if (path.endsWith("/writing-tasks") && method == PathItem.HttpMethod.POST) return "发起写作";
        if (path.endsWith("/writing-tasks") && method == PathItem.HttpMethod.GET) return "按单据编号查询写作";
        if (path.matches(".*/writing-tasks/\\{[^}]+}") && method == PathItem.HttpMethod.GET) return "查询写作进度";
        if (path.endsWith("/documents/summary")) return "提取正文摘要";
        if (path.endsWith("/documents/polish")) return "按段润色";
        if (path.endsWith("/documents/expand")) return "按段扩写";
        if (path.endsWith("/documents/review")) return "审校提交正文";
        if (path.endsWith("/review-tasks") && method == PathItem.HttpMethod.POST) return "审校平台写作任务";
        if (path.endsWith("/review-tasks") && method == PathItem.HttpMethod.GET) return "按单据编号查询审校";
        if (path.matches(".*/review-tasks/\\{[^}]+}")) return "查询审校结果";
        return null;
    }

    private static String detail(String path) {
        if (path.endsWith("/documents/summary")) return "OA 在请求体提交正文 text。文种按标题识别，不必传入。平台返回摘要和要点，不保存文稿，不需要 businessId。";
        if (path.endsWith("/documents/polish")) return "OA 在请求体提交正文 text。平台按段返回润色稿，只改表述，不改事实、数字和政策口径，不保存文稿。";
        if (path.endsWith("/documents/expand")) return "OA 在请求体提交正文 text。平台按段返回扩写稿，不另起章节，不保存文稿。";
        if (path.endsWith("/documents/review"))
            return "OA 在请求体提交 text。文种按标题识别，审校固定为内容安全审校。不需要本地写作任务，也不需要 businessId。平台返回问题列表和报告。版式差异请改用上传 DOCX。";
        if (path.endsWith("/review-tasks/upload"))
            return "上传第三方 DOCX。文种按标题识别，不必传 documentTypeCode。审校固定为内容安全审校。businessId 可选，只用于 OA 回查。";
        if (path.endsWith("/review-tasks"))
            return "POST 只审校本平台已有写作任务，需要 writingTaskId。第三方文稿请用审校提交正文或上传 DOCX。GET 仅在创建时传了 businessId 时按该编号回查。";
        return null;
    }
}
