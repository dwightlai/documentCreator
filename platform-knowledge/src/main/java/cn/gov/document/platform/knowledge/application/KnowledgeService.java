package cn.gov.document.platform.knowledge.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.common.file.DocxSecurityValidator;
import cn.gov.document.platform.knowledge.api.KnowledgeDtos;
import cn.gov.document.platform.knowledge.domain.*;
import cn.gov.document.platform.knowledge.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
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
            JdbcTemplate jdbc,
            ObjectMapper json,
            KnowledgeFileStore fileStore,
            cn.gov.document.platform.common.ocr.OcrPort ocr,
            cn.gov.document.platform.knowledge.vector.EmbeddingClient embeddings,
            cn.gov.document.platform.knowledge.vector.VectorStore vectorStore) {
        this.sourceMapper = sourceMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.jdbc = jdbc;
        this.json = json;
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
        e.setCategory(category(request.category()));
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

    @Transactional
    public KnowledgeDtos.SourceResponse updateSource(String id, KnowledgeDtos.UpdateSourceRequest request) {
        KnowledgeSourceEntity source = requireSource(id);
        if (!source.getStorageType().equals(request.storageType())
                && documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getSourceId, id)) > 0)
            throw BusinessException.conflict("知识库已有文档，不能更改存储类型");
        if (request.storageType().contains("向量")
                && (!StringUtils.hasText(request.vectorModel()) || request.vectorDimensions() == null))
            throw BusinessException.unprocessable("向量或混合知识库必须配置向量模型和维度");
        source.setLockVersion(request.lockVersion());
        source.setSourceName(request.sourceName().trim());
        if (!source.getCategory().equals(category(request.category())))
            throw BusinessException.conflict("知识库分类在创建后固定，请使用单独的调整分类操作");
        source.setStorageType(request.storageType());
        source.setVectorModel(request.vectorModel());
        source.setVectorDimensions(request.vectorDimensions());
        source.setPermissionScope(request.permissionScope());
        if (sourceMapper.updateById(source) != 1) throw BusinessException.conflict("知识库已被其他用户修改");
        return KnowledgeDtos.SourceResponse.from(requireSource(id));
    }

    @Transactional
    public void deleteSource(String id) {
        requireSource(id);
        List<String> documentIds = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getSourceId, id))
                .stream().map(KnowledgeDocumentEntity::getId).toList();
        for (String documentId : documentIds) deleteDocument(id, documentId);
        sourceMapper.deleteById(id);
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
    public KnowledgeDtos.SourceResponse reclassifySource(String id, String category, int lockVersion) {
        KnowledgeSourceEntity source = requireSource(id);
        source.setLockVersion(lockVersion);
        source.setCategory(category(category));
        if (sourceMapper.updateById(source) != 1) throw BusinessException.conflict("知识库已被其他用户修改");
        return KnowledgeDtos.SourceResponse.from(requireSource(id));
    }

    @Transactional
    public KnowledgeDtos.DocumentResponse updateDocument(
            String sourceId, String documentId, KnowledgeDtos.UpdateDocumentRequest request) {
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        snapshotRevision(sourceId, document);
        if (request.effectiveDate() != null && request.expiryDate() != null
                && request.expiryDate().isBefore(request.effectiveDate()))
            throw BusinessException.unprocessable("失效日期不得早于生效日期");
        if (!List.of("公开", "内部", "秘密", "机密").contains(request.permissionLevel()))
            throw BusinessException.unprocessable("无效的数据权限");
        document.setLockVersion(request.lockVersion());
        document.setDocumentTitle(request.documentTitle().trim());
        document.setMetadata(StringUtils.hasText(request.metadata()) ? request.metadata() : "{}");
        document.setAuthorityLevel(request.authorityLevel().trim());
        document.setEffectiveDate(request.effectiveDate());
        document.setExpiryDate(request.expiryDate());
        document.setPermissionLevel(request.permissionLevel());
        if (request.usageType() != null) {
            if (!List.of("规范依据", "事实依据", "写法参考", "待分类").contains(request.usageType()))
                throw BusinessException.unprocessable("无效的写作用途");
            document.setUsageType(request.usageType());
        }
        if (request.completeness() != null) {
            if (!List.of("本地原文可用", "原文摘录", "来源索引").contains(request.completeness()))
                throw BusinessException.unprocessable("无效的原文完整性状态");
            if ("本地原文可用".equals(request.completeness())
                    && !StringUtils.hasText(document.getOriginalFilePath())
                    && !isPackagedOfflineOriginal(document))
                throw BusinessException.unprocessable("缺少本地原文文件，不能标记为本地原文可用");
            document.setCompleteness(request.completeness());
        }
        if (request.verificationStatus() != null) {
            if (!List.of("待整理", "待审核", "待核验", "已核验", "退回").contains(request.verificationStatus()))
                throw BusinessException.unprocessable("无效的核验状态");
            document.setVerificationStatus(request.verificationStatus());
            if ("已核验".equals(request.verificationStatus())) {
                document.setReviewedBy(RequestContext.userId());
                document.setReviewedAt(OffsetDateTime.now());
            } else { document.setReviewedBy(null); document.setReviewedAt(null); }
        }
        if (request.validityStatus() != null) {
            if (!List.of("待核验", "现行", "尚未施行", "已失效", "已废止", "被替代", "不适用").contains(request.validityStatus()))
                throw BusinessException.unprocessable("无效的效力状态");
            document.setValidityStatus(request.validityStatus());
        }
        if (request.publisher() != null) document.setPublisher(request.publisher());
        if (request.documentNumber() != null) document.setDocumentNumber(request.documentNumber());
        if (request.issuedDate() != null) document.setIssuedDate(request.issuedDate());
        if (request.applicableRegion() != null) document.setApplicableRegion(request.applicableRegion());
        if (request.applicableAudience() != null) document.setApplicableAudience(request.applicableAudience());
        if (request.applicableScope() != null) document.setApplicableScope(request.applicableScope());
        document.setDocumentVersion(document.getDocumentVersion() + 1);
        if (documentMapper.updateById(document) != 1) throw BusinessException.conflict("文档已被其他用户修改");
        return KnowledgeDtos.DocumentResponse.from(requireDocument(sourceId, documentId));
    }

    @Transactional
    public void deleteDocument(String sourceId, String documentId) {
        KnowledgeSourceEntity source = requireSource(sourceId);
        requireDocument(sourceId, documentId);
        Integer selections = jdbc.queryForObject("SELECT COUNT(*) FROM ai_writing_knowledge_selection WHERE tenant_id=? AND knowledge_document_id=? AND is_deleted=false", Integer.class, RequestContext.tenantId(), documentId);
        Integer citations = jdbc.queryForObject("SELECT COUNT(*) FROM ai_document_citation WHERE tenant_id=? AND knowledge_document_id=? AND is_deleted=false", Integer.class, RequestContext.tenantId(), documentId);
        if ((selections != null && selections > 0) || (citations != null && citations > 0))
            throw BusinessException.conflict("该原文已被写作任务选用或引用，请先移除选用；历史引用须保留原文");
        if (vectorEnabled(source)) vectorStore.deleteByDocument(RequestContext.tenantId(), documentId);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        source.setEntryCount(Math.toIntExact(documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getSourceId, sourceId))));
        source.setLastSyncTime(OffsetDateTime.now());
        sourceMapper.updateById(source);
    }

    public List<KnowledgeDtos.ChunkResponse> chunks(String sourceId, String documentId) {
        requireDocument(sourceId, documentId);
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunkEntity::getChunkOrder))
                .stream().map(KnowledgeDtos.ChunkResponse::from).toList();
    }

    public KnowledgeDtos.ReadResponse readDocument(String sourceId, String documentId) {
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        if (rank(document.getPermissionLevel()) > clearance())
            throw BusinessException.unprocessable("无权查看该知识文档");
        return new KnowledgeDtos.ReadResponse(KnowledgeDtos.DocumentResponse.from(document), chunks(sourceId, documentId));
    }

    public String sourceIdForDocument(String documentId) {
        KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) throw BusinessException.notFound("知识文档不存在");
        return document.getSourceId();
    }

    public boolean isCitationCurrent(String documentId, String chunkId, Integer version,
            String fileHash, String sourceExcerpt) {
        KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null || !"已核验".equals(document.getVerificationStatus())
                || "来源索引".equals(document.getCompleteness())
                || !Objects.equals(document.getDocumentVersion(), version)
                || !Objects.equals(document.getOriginalFileHash(), fileHash)
                || rank(document.getPermissionLevel()) > clearance()) return false;
        KnowledgeSourceEntity source = sourceMapper.selectById(document.getSourceId());
        if (source == null || !Boolean.TRUE.equals(source.getIsActive())) return false;
        if (List.of("已失效", "已废止", "被替代").contains(document.getValidityStatus())
                || "规范依据".equals(document.getUsageType())
                        && !"现行".equals(document.getValidityStatus())) return false;
        LocalDate today = LocalDate.now();
        if (document.getEffectiveDate() != null && document.getEffectiveDate().isAfter(today)
                || document.getExpiryDate() != null && document.getExpiryDate().isBefore(today)) return false;
        KnowledgeChunkEntity chunk = chunkMapper.selectById(chunkId);
        return chunk != null && documentId.equals(chunk.getDocumentId())
                && StringUtils.hasText(sourceExcerpt) && chunk.getChunkText().contains(sourceExcerpt);
    }

    public List<KnowledgeDtos.RevisionResponse> revisions(String sourceId, String documentId) {
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        if (rank(document.getPermissionLevel()) > clearance())
            throw BusinessException.unprocessable("无权查看该知识文档历史");
        return jdbc.query("SELECT document_version,snapshot_json,captured_by,captured_at FROM ai_knowledge_document_revision WHERE tenant_id=? AND document_id=? ORDER BY document_version DESC",
                (rs, row) -> new KnowledgeDtos.RevisionResponse(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class)), RequestContext.tenantId(), documentId);
    }

    private void snapshotRevision(String sourceId, KnowledgeDocumentEntity document) {
        try {
            String snapshot = json.writeValueAsString(Map.of(
                    "document", KnowledgeDtos.DocumentResponse.from(document),
                    "chunks", chunks(sourceId, document.getId())));
            jdbc.update("INSERT INTO ai_knowledge_document_revision (id,tenant_id,document_id,document_version,snapshot_json,captured_by,captured_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString().replace("-", ""), RequestContext.tenantId(), document.getId(),
                    document.getDocumentVersion(), snapshot, RequestContext.userId(), OffsetDateTime.now());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("知识文档历史版本保存失败", error);
        }
    }

    private boolean isPackagedOfflineOriginal(KnowledgeDocumentEntity document) {
        if (!StringUtils.hasText(document.getExtractedText())) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM public_source_pack_entry WHERE tenant_id=? AND document_id=? AND entry_code LIKE 'OFFLINE-%'",
                Integer.class, RequestContext.tenantId(), document.getId());
        return count != null && count > 0;
    }

    @Transactional
    public KnowledgeDtos.ChunkResponse updateChunk(
            String sourceId, String documentId, String chunkId, KnowledgeDtos.UpdateChunkRequest request) {
        KnowledgeSourceEntity source = requireSource(sourceId);
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        snapshotRevision(sourceId, document);
        if (!"已完成".equals(document.getParsedStatus())) throw BusinessException.conflict("文档尚未完成解析");
        KnowledgeChunkEntity chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || !documentId.equals(chunk.getDocumentId())) throw BusinessException.notFound("切片不存在");
        String text = request.chunkText().trim();
        chunk.setLockVersion(request.lockVersion());
        chunk.setChunkText(text);
        chunk.setChunkTextHash(sha256(text.getBytes(StandardCharsets.UTF_8)));
        if (chunkMapper.updateById(chunk) != 1) throw BusinessException.conflict("切片已被其他用户修改");
        document.setDocumentVersion(document.getDocumentVersion() + 1);
        document.setVerificationStatus("待核验");
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        documentMapper.updateById(document);
        if (vectorEnabled(source)) index(source, documentId, List.of(chunk));
        return KnowledgeDtos.ChunkResponse.from(chunkMapper.selectById(chunkId));
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
        e.setUsageType("待分类");
        e.setCompleteness("本地原文可用");
        e.setVerificationStatus("待核验");
        e.setValidityStatus("待核验");
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
                            matchType.get(e.getKey()), d.getUsageType(), d.getCompleteness(),
                            d.getVerificationStatus(), d.getValidityStatus(), d.getDocumentVersion());
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

    private String category(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 100)
            throw BusinessException.unprocessable("知识库分类必须填写，且不超过 100 字");
        return value.trim();
    }

    /** 在显式选用时核查权限、原文和效力；检索命中本身不构成依据。 */
    public WritingExcerpt requireWritingExcerpt(String sourceId, String documentId, String chunkId, String usageType) {
        return writingExcerpt(sourceId, documentId, chunkId, usageType, true);
    }

    /** 已选用内容的后台生成重核；异步任务保留用户 ID，但没有 HTTP SecurityContext。 */
    public WritingExcerpt requireWritingExcerptForGeneration(String sourceId, String documentId, String chunkId,
            String usageType) {
        return writingExcerpt(sourceId, documentId, chunkId, usageType, false);
    }

    private WritingExcerpt writingExcerpt(String sourceId, String documentId, String chunkId,
            String usageType, boolean checkClearance) {
        KnowledgeSourceEntity source = requireSource(sourceId);
        KnowledgeDocumentEntity document = requireDocument(sourceId, documentId);
        if (!Boolean.TRUE.equals(source.getIsActive()) || !"已完成".equals(document.getParsedStatus()))
            throw BusinessException.unprocessable("知识文档不可用或尚未解析");
        if (checkClearance && rank(document.getPermissionLevel()) > clearance())
            throw BusinessException.unprocessable("无权使用该知识文档");
        if (!"已核验".equals(document.getVerificationStatus()) || "来源索引".equals(document.getCompleteness()))
            throw BusinessException.unprocessable("请先核验本地原文，来源索引不能进入生成");
        LocalDate today = LocalDate.now();
        if (document.getEffectiveDate() != null && document.getEffectiveDate().isAfter(today)
                || document.getExpiryDate() != null && document.getExpiryDate().isBefore(today))
            throw BusinessException.unprocessable("知识文档不在有效日期内");
        if (!List.of("规范依据", "事实依据", "写法参考").contains(usageType)
                || !usageType.equals(document.getUsageType()))
            throw BusinessException.unprocessable("请先核对文档写作用途");
        if ("规范依据".equals(usageType) && !"现行".equals(document.getValidityStatus()))
            throw BusinessException.unprocessable("规范依据尚未核验为现行有效");
        KnowledgeChunkEntity chunk = chunkMapper.selectById(chunkId);
        if (chunk == null || !documentId.equals(chunk.getDocumentId())) throw BusinessException.notFound("原文切片不存在");
        return new WritingExcerpt(sourceId, source.getSourceName(), documentId, document.getDocumentTitle(),
                document.getDocumentVersion(), chunkId, chunk.getChunkText(), document.getOriginalFileHash(),
                usageType, document.getCompleteness(), document.getVerificationStatus(), document.getValidityStatus());
    }

    public record WritingExcerpt(String sourceId, String sourceName, String documentId, String documentTitle,
            int documentVersion, String chunkId, String text, String fileHash, String usageType,
            String completeness, String verificationStatus, String validityStatus) {}

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
