package cn.gov.document.platform.knowledge.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.knowledge.api.KnowledgeDtos;
import cn.gov.document.platform.knowledge.domain.*;
import cn.gov.document.platform.knowledge.persistence.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024, CHUNK_SIZE = 500, OVERLAP = 50;
    private final KnowledgeSourceMapper sourceMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeFileStore fileStore;
    private final cn.gov.document.platform.common.ocr.OcrPort ocr;
    private final cn.gov.document.platform.knowledge.vector.EmbeddingClient embeddings;
    private final cn.gov.document.platform.knowledge.vector.VectorStore vectorStore;

    /** 混合检索 RRF 常数（Reciprocal Rank Fusion）。 */
    private static final int RRF_K = 60;

    @org.springframework.beans.factory.annotation.Value("${platform.knowledge.vector-min-score:0.5}")
    private double vectorMinScore = 0.5;

    public KnowledgeService(
            KnowledgeSourceMapper sourceMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeFileStore fileStore,
            cn.gov.document.platform.common.ocr.OcrPort ocr,
            cn.gov.document.platform.knowledge.vector.EmbeddingClient embeddings,
            cn.gov.document.platform.knowledge.vector.VectorStore vectorStore) {
        this.sourceMapper = sourceMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.fileStore = fileStore;
        this.ocr = ocr;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public KnowledgeDtos.SourceResponse createSource(KnowledgeDtos.CreateSourceRequest request) {
        if (sourceMapper.selectCount(new LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .eq(
                                KnowledgeSourceEntity::getSourceCode,
                                request.sourceCode().trim()))
                > 0) throw BusinessException.conflict("知识库代码已存在");
        if (request.storageType().contains("向量")
                && (!StringUtils.hasText(request.vectorModel()) || request.vectorDimensions() == null))
            throw BusinessException.unprocessable("向量或混合知识库必须配置向量模型和维度");
        KnowledgeSourceEntity e = new KnowledgeSourceEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setSourceCode(request.sourceCode().trim());
        e.setSourceName(request.sourceName().trim());
        e.setCategory(request.category());
        e.setStorageType(request.storageType());
        e.setVectorModel(request.vectorModel());
        e.setVectorDimensions(request.vectorDimensions());
        e.setPermissionScope(request.permissionScope());
        e.setEntryCount(0);
        e.setIsActive(true);
        sourceMapper.insert(e);
        return KnowledgeDtos.SourceResponse.from(e);
    }

    public List<KnowledgeDtos.SourceResponse> sources() {
        return sourceMapper
                .selectList(new LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .orderByAsc(KnowledgeSourceEntity::getSourceName))
                .stream()
                .map(KnowledgeDtos.SourceResponse::from)
                .toList();
    }

    public List<KnowledgeDtos.DocumentResponse> documents(String sourceId) {
        requireSource(sourceId);
        return documentMapper
                .selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getSourceId, sourceId)
                        .orderByDesc(KnowledgeDocumentEntity::getUpdatedAt))
                .stream()
                .map(KnowledgeDtos.DocumentResponse::from)
                .toList();
    }

    @Transactional
    public KnowledgeDtos.DocumentResponse upload(
            String sourceId,
            MultipartFile file,
            String title,
            String metadata,
            String authority,
            LocalDate effective,
            LocalDate expiry,
            String permission) {
        KnowledgeSourceEntity source = requireSource(sourceId);
        if (file == null || file.isEmpty()) throw BusinessException.unprocessable("请选择知识文档");
        if (file.getSize() > MAX_FILE_SIZE) throw BusinessException.unprocessable("知识文档不得超过 20MB");
        if (effective != null && expiry != null && expiry.isBefore(effective))
            throw BusinessException.unprocessable("失效日期不得早于生效日期");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.unprocessable("知识文档读取失败");
        }
        String hash = sha256(bytes);
        if (documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getSourceId, sourceId)
                        .eq(KnowledgeDocumentEntity::getOriginalFileHash, hash))
                > 0) throw BusinessException.conflict("同一知识库中已存在相同文件");
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        String extracted = extract(filename, bytes);
        if (!StringUtils.hasText(extracted) && filename.toLowerCase(Locale.ROOT).endsWith(".pdf") && ocr.enabled())
            extracted = ocr.recognize(filename, "application/pdf", bytes).orElse("");
        if (!StringUtils.hasText(extracted))
            throw BusinessException.unprocessable(ocr.enabled() ? "文档未识别出可检索文字，请检查扫描质量" : "文档未提取到可检索文本，扫描件请先完成 OCR");
        KnowledgeDocumentEntity e = new KnowledgeDocumentEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setTenantId(RequestContext.tenantId());
        e.setSourceId(sourceId);
        e.setDocumentTitle(StringUtils.hasText(title) ? title.trim() : filename);
        e.setOriginalFilePath(fileStore.write(sourceId, e.getId(), filename, bytes));
        e.setOriginalFileHash(hash);
        e.setParsedStatus("待解析");
        e.setMetadata(StringUtils.hasText(metadata) ? metadata : "{}");
        e.setAuthorityLevel(StringUtils.hasText(authority) ? authority : "一般资料");
        e.setEffectiveDate(effective);
        e.setExpiryDate(expiry);
        e.setPermissionLevel(StringUtils.hasText(permission) ? permission : "内部");
        e.setChunkCount(0);
        e.setDocumentVersion(1);
        e.setExtractedText(extracted);
        documentMapper.insert(e);
        source.setEntryCount(source.getEntryCount() + 1);
        source.setLastSyncTime(OffsetDateTime.now());
        sourceMapper.updateById(source);
        return KnowledgeDtos.DocumentResponse.from(e);
    }

    @Transactional
    public KnowledgeDtos.ParseResponse parse(String sourceId, String documentId) {
        requireSource(sourceId);
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        if ("已完成".equals(document.getParsedStatus()))
            return new KnowledgeDtos.ParseResponse(
                    documentId, "已完成", document.getChunkCount(), List.of("文档已解析，本次未重复切分"));
        KnowledgeSourceEntity source = requireSource(sourceId);
        if (vectorEnabled(source)) vectorStore.deleteByDocument(RequestContext.tenantId(), documentId);
        chunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkEntity>().eq(KnowledgeChunkEntity::getDocumentId, documentId));
        List<KnowledgeChunkEntity> created = new java.util.ArrayList<>();
        String text = document.getExtractedText()
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
        int order = 1, start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('\n', end));
                if (boundary > start + CHUNK_SIZE / 2) end = boundary + 1;
            }
            String value = text.substring(start, end).trim();
            if (!value.isEmpty()) {
                KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
                chunk.setTenantId(RequestContext.tenantId());
                chunk.setDocumentId(documentId);
                chunk.setChunkOrder(order++);
                chunk.setChunkText(value);
                chunk.setChunkTextHash(sha256(value.getBytes(StandardCharsets.UTF_8)));
                chunk.setKeywords("[]");
                chunk.setStartPosition(start);
                chunk.setEndPosition(end);
                chunkMapper.insert(chunk);
                created.add(chunk);
            }
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
        document.setChunkCount(order - 1);
        document.setParsedStatus("已完成");
        documentMapper.updateById(document);
        List<String> warnings = new java.util.ArrayList<>();
        if (vectorEnabled(source)) {
            try {
                index(source, documentId, created);
            } catch (BusinessException e) {
                // 向量化失败不影响全文检索，保留解析结果并提示管理员重新向量化
                warnings.add("向量化失败：" + e.getMessage() + "；已保留全文检索，可稍后重新向量化");
            }
        }
        return new KnowledgeDtos.ParseResponse(documentId, "已完成", document.getChunkCount(), warnings);
    }

    /** 对已解析文档重新生成向量（模型更换或向量化失败后使用）。 */
    @Transactional
    public KnowledgeDtos.ParseResponse vectorize(String sourceId, String documentId) {
        KnowledgeSourceEntity source = requireSource(sourceId);
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        if (!embeddings.enabled()) throw BusinessException.unprocessable("向量化服务尚未配置；当前仅启用权限前置的全文检索");
        if (!vectorEnabled(source)) throw BusinessException.unprocessable("该知识库存储类型不包含向量检索");
        if (!"已完成".equals(document.getParsedStatus())) throw BusinessException.unprocessable("请先完成文档解析");
        List<KnowledgeChunkEntity> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunkEntity::getChunkOrder));
        vectorStore.deleteByDocument(RequestContext.tenantId(), documentId);
        index(source, documentId, chunks);
        return new KnowledgeDtos.ParseResponse(documentId, "已完成", chunks.size(), List.of());
    }

    private void index(KnowledgeSourceEntity source, String documentId, List<KnowledgeChunkEntity> chunks) {
        if (chunks.isEmpty()) return;
        List<float[]> vectors = embeddings.embed(
                chunks.stream().map(KnowledgeChunkEntity::getChunkText).toList());
        Integer dimensions = source.getVectorDimensions();
        if (dimensions != null && vectors.get(0).length != dimensions)
            throw BusinessException.unprocessable("向量维度 " + vectors.get(0).length + " 与知识库配置的 " + dimensions + " 维不一致");
        List<cn.gov.document.platform.knowledge.vector.VectorStore.Record> records = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++)
            records.add(new cn.gov.document.platform.knowledge.vector.VectorStore.Record(
                    chunks.get(i).getId(), documentId, vectors.get(i)));
        vectorStore.upsert(RequestContext.tenantId(), records);
    }

    private boolean vectorEnabled(KnowledgeSourceEntity source) {
        String type = source.getStorageType() == null ? "" : source.getStorageType();
        return embeddings.enabled() && (type.contains("向量") || type.contains("混合"));
    }

    public List<KnowledgeDtos.SearchResponse> search(String query, String sourceId, int limit) {
        return search(query, sourceId, limit, null);
    }

    /**
     * 权限前置的知识检索（§26）。mode 为 keyword（全文）、vector（语义）或 hybrid（默认，向量服务可用时启用）：
     * 先按租户、权限级别和有效期筛出允许的文档，再分别做全文与向量检索，以 RRF 融合排序。
     */
    public List<KnowledgeDtos.SearchResponse> search(String query, String sourceId, int limit, String mode) {
        if (!StringUtils.hasText(query) || query.trim().length() < 2)
            throw BusinessException.unprocessable("检索词至少 2 个字符");
        int safeLimit = Math.min(50, Math.max(1, limit));
        LocalDate today = LocalDate.now();
        List<KnowledgeSourceEntity> sources = sourceMapper.selectList(new LambdaQueryWrapper<KnowledgeSourceEntity>()
                .eq(KnowledgeSourceEntity::getIsActive, true)
                .eq(StringUtils.hasText(sourceId), KnowledgeSourceEntity::getId, sourceId));
        Map<String, KnowledgeSourceEntity> sourceMap =
                sources.stream().collect(Collectors.toMap(KnowledgeSourceEntity::getId, s -> s));
        if (sourceMap.isEmpty()) return List.of();
        int clearance = clearance();
        List<KnowledgeDocumentEntity> allowed = documentMapper
                .selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .in(KnowledgeDocumentEntity::getSourceId, sourceMap.keySet())
                        .eq(KnowledgeDocumentEntity::getParsedStatus, "已完成"))
                .stream()
                .filter(d -> rank(d.getPermissionLevel()) <= clearance)
                .filter(d -> (d.getEffectiveDate() == null
                                || !d.getEffectiveDate().isAfter(today))
                        && (d.getExpiryDate() == null || !d.getExpiryDate().isBefore(today)))
                .toList();
        if (allowed.isEmpty()) return List.of();
        Map<String, KnowledgeDocumentEntity> docs =
                allowed.stream().collect(Collectors.toMap(KnowledgeDocumentEntity::getId, d -> d));
        String keyword = query.trim();
        String effectiveMode = mode == null ? "hybrid" : mode.toLowerCase(java.util.Locale.ROOT);
        if (!List.of("keyword", "vector", "hybrid").contains(effectiveMode))
            throw BusinessException.unprocessable("检索模式仅支持 keyword、vector 或 hybrid");
        if ("vector".equals(effectiveMode) && !embeddings.enabled())
            throw BusinessException.unprocessable("向量化服务尚未配置，无法进行语义检索");
        int candidates = safeLimit * 3;

        List<KnowledgeChunkEntity> keywordHits = List.of();
        if (!"vector".equals(effectiveMode)) {
            keywordHits = chunkMapper
                    .selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .in(KnowledgeChunkEntity::getDocumentId, docs.keySet())
                            .like(KnowledgeChunkEntity::getChunkText, keyword))
                    .stream()
                    .sorted(Comparator.comparingInt((KnowledgeChunkEntity c) -> occurrences(c.getChunkText(), keyword)
                                    + (docs.get(c.getDocumentId())
                                                    .getDocumentTitle()
                                                    .contains(keyword)
                                            ? 1
                                            : 0))
                            .reversed())
                    .limit(candidates)
                    .toList();
        }
        List<cn.gov.document.platform.knowledge.vector.VectorStore.Hit> vectorHits = List.of();
        if (!"keyword".equals(effectiveMode) && embeddings.enabled()) {
            // 只在向量化知识库的允许文档范围内做语义检索
            List<String> vectorDocuments = allowed.stream()
                    .filter(d -> vectorEnabled(sourceMap.get(d.getSourceId())))
                    .map(KnowledgeDocumentEntity::getId)
                    .toList();
            if (!vectorDocuments.isEmpty())
                vectorHits =
                        vectorStore
                                .search(
                                        RequestContext.tenantId(),
                                        embeddings.embed(keyword),
                                        vectorDocuments,
                                        candidates)
                                .stream()
                                // 低于相似度阈值的切片视为不相关，避免把无关材料带入写作问答
                                .filter(hit -> hit.score() >= vectorMinScore)
                                .toList();
        }

        Map<String, Double> fused = new java.util.LinkedHashMap<>();
        Map<String, String> matchType = new java.util.HashMap<>();
        for (int i = 0; i < keywordHits.size(); i++) {
            fused.merge(keywordHits.get(i).getId(), 1.0 / (RRF_K + i + 1), Double::sum);
            matchType.put(keywordHits.get(i).getId(), "关键词");
        }
        for (int i = 0; i < vectorHits.size(); i++) {
            String id = vectorHits.get(i).chunkId();
            fused.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            matchType.merge(id, "语义", (a, b) -> "混合");
        }
        if (fused.isEmpty()) return List.of();
        Map<String, KnowledgeChunkEntity> chunks = new java.util.HashMap<>();
        keywordHits.forEach(c -> chunks.put(c.getId(), c));
        List<String> missing =
                fused.keySet().stream().filter(id -> !chunks.containsKey(id)).toList();
        if (!missing.isEmpty())
            chunkMapper
                    .selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .in(KnowledgeChunkEntity::getId, missing)
                            .in(KnowledgeChunkEntity::getDocumentId, docs.keySet()))
                    .forEach(c -> chunks.put(c.getId(), c));
        double best = 2.0 / (RRF_K + 1);
        return fused.entrySet().stream()
                .filter(e -> chunks.containsKey(e.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(safeLimit)
                .map(e -> {
                    KnowledgeChunkEntity chunk = chunks.get(e.getKey());
                    KnowledgeDocumentEntity d = docs.get(chunk.getDocumentId());
                    KnowledgeSourceEntity s = sourceMap.get(d.getSourceId());
                    return new KnowledgeDtos.SearchResponse(
                            s.getId(),
                            s.getSourceName(),
                            d.getId(),
                            d.getDocumentTitle(),
                            chunk.getId(),
                            chunk.getChunkOrder(),
                            snippet(chunk.getChunkText(), keyword),
                            d.getAuthorityLevel(),
                            d.getPermissionLevel(),
                            Math.round(Math.min(1.0, e.getValue() / best) * 1000) / 1000.0,
                            d.getOriginalFileHash(),
                            matchType.get(e.getKey()));
                })
                .toList();
    }

    private String extract(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv"))
                return new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
            if (lower.endsWith(".docx")) {
                DocxSecurityValidator.validate(filename, bytes, 20L * 1024 * 1024, "知识文档");
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    return doc.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n"));
                }
            }
            if (lower.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(pdf);
                }
            }
            throw BusinessException.unprocessable("仅支持 TXT、Markdown、CSV、DOCX 和可提取文本的 PDF");
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException b) throw b;
            throw BusinessException.unprocessable("知识文档解析失败或文件已损坏");
        }
    }

    private int clearance() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getAuthorities().stream().anyMatch(v -> "ROLE_platform.admin".equals(v.getAuthority())))
            return 3;
        if (a != null && a.getAuthorities().stream().anyMatch(v -> "ROLE_platform.reviewer".equals(v.getAuthority())))
            return 2;
        return 1;
    }

    private int rank(String level) {
        return switch (level == null ? "内部" : level) {
            case "公开" -> 0;
            case "内部" -> 1;
            case "秘密" -> 2;
            case "机密" -> 3;
            default -> 3;
        };
    }

    private int occurrences(String text, String key) {
        int count = 0, from = 0;
        while ((from = text.indexOf(key, from)) >= 0) {
            count++;
            from += key.length();
        }
        return count;
    }

    private String snippet(String text, String key) {
        int at = Math.max(0, text.indexOf(key));
        int start = Math.max(0, at - 80), end = Math.min(text.length(), at + key.length() + 120);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private KnowledgeSourceEntity requireSource(String id) {
        KnowledgeSourceEntity e = sourceMapper.selectById(id);
        if (e == null) throw BusinessException.notFound("知识库不存在");
        return e;
    }

    private KnowledgeDocumentEntity requireDocument(String sourceId, String id) {
        KnowledgeDocumentEntity e = documentMapper.selectById(id);
        if (e == null || !sourceId.equals(e.getSourceId())) throw BusinessException.notFound("知识文档不存在");
        return e;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
