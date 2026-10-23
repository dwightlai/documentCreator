package cn.gov.document.platform.document.onlyoffice;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.document.api.DocumentTypesetDtos;
import cn.gov.document.platform.document.application.DocumentTypesetService;
import cn.gov.document.platform.document.domain.DocumentTypesetEntity;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 定稿稿在线编辑：为浏览器签发编辑器配置；文档服务器凭平台签发的短期令牌拉取文件、回调保存（这两个入口不经登录认证，
 * 由令牌携带租户、用户与定稿稿身份）。保存时只从配置的文档服务器地址下载，校验 DOCX 后追加为新的定稿稿。
 */
@Service
public class OnlyOfficeService {
    static final String FILE = "onlyoffice-file";
    static final String CALLBACK = "onlyoffice-callback";
    private static final Duration ACCESS_TTL = Duration.ofHours(24);
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    /** 2：编辑完成待保存；6：强制保存。其余状态（编辑中、无修改关闭、出错）不需要处理。 */
    private static final java.util.Set<Integer> SAVE_STATUSES = java.util.Set.of(2, 6);

    private final OnlyOfficeSettings settings;
    private final OnlyOfficeTokens tokens;
    private final DocumentTypesetService typesetService;
    private final HttpClient http;

    public OnlyOfficeService(OnlyOfficeSettings settings, OnlyOfficeTokens tokens, DocumentTypesetService typesetService) {
        this.settings = settings;
        this.tokens = tokens;
        this.typesetService = typesetService;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record Status(boolean enabled, String documentServerUrl) {}

    public record EditorConfig(String documentServerUrl, Map<String, Object> config) {}

    public Status status() {
        return new Status(settings.enabled(), settings.enabled() ? settings.publicUrl() : null);
    }

    public EditorConfig editorConfig(String taskId, String typesetId) {
        requireEnabled();
        DocumentTypesetEntity typeset = typesetService.require(taskId, typesetId);
        String user = RequestContext.userId();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", "docx");
        document.put("key", typeset.getId());
        document.put("title", "定稿稿-V" + typeset.getDocumentVersion() + ".docx");
        document.put("url", settings.callbackBaseUrl() + "/api/v1/onlyoffice/files/" + accessToken(FILE, taskId, typesetId));
        document.put("permissions", Map.of("edit", true, "download", true, "print", true, "comment", true, "review", true));
        Map<String, Object> editor = new LinkedHashMap<>();
        editor.put("mode", "edit");
        editor.put("lang", "zh-CN");
        editor.put("callbackUrl", settings.callbackBaseUrl() + "/api/v1/onlyoffice/callback/" + accessToken(CALLBACK, taskId, typesetId));
        editor.put("user", Map.of("id", user, "name", user));
        editor.put("customization", Map.of("forcesave", true, "autosave", true));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", "word");
        config.put("document", document);
        config.put("editorConfig", editor);
        config.put("token", tokens.sign(config));
        return new EditorConfig(settings.publicUrl(), config);
    }

    /** 文档服务器拉取定稿稿文件。 */
    public byte[] file(String token) {
        requireEnabled();
        Access access = access(token, FILE);
        return typesetService.file(typesetService.require(access.taskId(), access.typesetId()));
    }

    /**
     * 文档服务器保存回调：回调内容必须带有文档服务器的签名（请求体 token 字段或 Authorization 头），并且针对令牌中的定稿稿。
     *
     * @return 保存形成的新定稿稿；无需保存时为空
     */
    public DocumentTypesetDtos.TypesetResponse callback(String token, Map<String, Object> body, String authorization) {
        requireEnabled();
        Access access = access(token, CALLBACK);
        Map<String, Object> payload = signedPayload(body, authorization);
        if (!access.typesetId().equals(payload.get("key"))) throw BusinessException.unprocessable("回调文档与访问令牌不符");
        Object status = payload.get("status");
        if (!(status instanceof Number number) || !SAVE_STATUSES.contains(number.intValue())) return null;
        if (!(payload.get("url") instanceof String url)) throw BusinessException.unprocessable("保存回调缺少文件地址");
        byte[] docx = download(url);
        DocxSecurityValidator.validate("onlyoffice-edited.docx", docx, MAX_FILE_BYTES, "在线编辑文件");
        return typesetService.saveEdited(access.taskId(), access.typesetId(), docx);
    }

    private record Access(String taskId, String typesetId) {}

    private String accessToken(String purpose, String taskId, String typesetId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", purpose);
        claims.put("tenant", RequestContext.tenantId());
        claims.put("user", RequestContext.userId());
        claims.put("task", taskId);
        claims.put("typeset", typesetId);
        claims.put("exp", Instant.now().plus(ACCESS_TTL).getEpochSecond());
        return tokens.sign(claims);
    }

    /** 校验平台签发的访问令牌，并以令牌中的租户与用户作为本次请求的身份（这两个入口不经登录认证）。 */
    private Access access(String token, String purpose) {
        Map<String, Object> claims = tokens.verify(token);
        if (!purpose.equals(claims.get("purpose"))) throw BusinessException.unprocessable("ONLYOFFICE 访问令牌用途不符");
        if (!(claims.get("tenant") instanceof String tenant) || !(claims.get("task") instanceof String taskId)
                || !(claims.get("typeset") instanceof String typesetId))
            throw BusinessException.unprocessable("ONLYOFFICE 访问令牌缺少身份信息");
        RequestContext.set(RequestContext.requestId(), tenant, String.valueOf(claims.getOrDefault("user", "onlyoffice")));
        return new Access(taskId, typesetId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> signedPayload(Map<String, Object> body, String authorization) {
        if (body != null && body.get("token") instanceof String token) return tokens.verify(token);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            Object payload = tokens.verify(authorization.substring(7).strip()).get("payload");
            if (payload instanceof Map<?, ?> map) return (Map<String, Object>) map;
        }
        throw BusinessException.unprocessable("保存回调缺少文档服务器签名");
    }

    /** 只从配置的文档服务器下载；浏览器地址会改写为后端访问地址，防止回调把平台引向其他主机。 */
    private byte[] download(String url) {
        String target;
        if (url.startsWith(settings.internalUrl() + "/")) target = url;
        else if (url.startsWith(settings.publicUrl() + "/")) target = settings.internalUrl() + url.substring(settings.publicUrl().length());
        else throw BusinessException.unprocessable("保存文件地址不属于配置的文档服务器");
        try {
            HttpResponse<InputStream> response = http.send(
                    HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) throw BusinessException.unprocessable("下载在线编辑文件失败：HTTP " + response.statusCode());
                byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) throw BusinessException.unprocessable("在线编辑文件超过 20MB");
                return bytes;
            }
        } catch (IOException failure) {
            throw BusinessException.unprocessable("下载在线编辑文件失败");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw BusinessException.unprocessable("下载在线编辑文件被中断");
        }
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw BusinessException.conflict("未启用 ONLYOFFICE 在线编辑");
    }
}
