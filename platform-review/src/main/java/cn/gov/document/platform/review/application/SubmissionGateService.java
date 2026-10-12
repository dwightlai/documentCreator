package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.api.DocumentDtos;
import cn.gov.document.platform.document.application.DocumentService;
import cn.gov.document.platform.review.api.SubmissionGateDtos;
import cn.gov.document.platform.review.domain.ReviewIssueEntity;
import cn.gov.document.platform.review.domain.ReviewReportEntity;
import cn.gov.document.platform.review.domain.ReviewTaskEntity;
import cn.gov.document.platform.review.domain.SubmissionGateConfigEntity;
import cn.gov.document.platform.review.persistence.ReviewIssueMapper;
import cn.gov.document.platform.review.persistence.ReviewReportMapper;
import cn.gov.document.platform.review.persistence.ReviewTaskMapper;
import cn.gov.document.platform.review.persistence.SubmissionGateConfigMapper;
import cn.gov.document.platform.writing.application.WritingTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提交门禁（§31.3）：文稿提交 OA 前，按单位配置检查审校是否完成、是否针对最新文稿、严重与重要问题是否处理、报告是否生成。
 *
 * <p>“忽略严重问题必须填写原因”在问题处置时即强制执行；“涉密文稿禁止调用外部模型”由模型网关统一拒绝，不在此重复检查。
 */
@Service
public class SubmissionGateService {
    private static final String REVIEW_ACCEPT_PREFIX = "采纳审校问题";

    private final SubmissionGateConfigMapper configMapper;
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewReportMapper reportMapper;
    private final WritingTaskService writingTaskService;
    private final DocumentService documentService;

    public SubmissionGateService(
            SubmissionGateConfigMapper configMapper,
            ReviewTaskMapper taskMapper,
            ReviewIssueMapper issueMapper,
            ReviewReportMapper reportMapper,
            WritingTaskService writingTaskService,
            DocumentService documentService) {
        this.configMapper = configMapper;
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.reportMapper = reportMapper;
        this.writingTaskService = writingTaskService;
        this.documentService = documentService;
    }

    public SubmissionGateDtos.Config config() {
        return SubmissionGateDtos.Config.from(configEntity());
    }

    @Transactional
    public SubmissionGateDtos.Config updateConfig(SubmissionGateDtos.Config request) {
        SubmissionGateConfigEntity entity = configEntity();
        boolean created = entity == null;
        if (created) {
            entity = new SubmissionGateConfigEntity();
            entity.setTenantId(RequestContext.tenantId());
        }
        entity.setRequireReview(request.requireReview());
        entity.setRequireLatestReview(request.requireLatestReview());
        entity.setBlockOnSevere(request.blockOnSevere());
        entity.setRequireImportantProcessed(request.requireImportantProcessed());
        entity.setRequireReport(request.requireReport());
        String role = request.requiredSubmitRole() == null
                ? null
                : request.requiredSubmitRole().trim();
        if (role != null && !role.isEmpty() && !role.matches("[A-Za-z][A-Za-z0-9._-]{1,48}"))
            throw BusinessException.unprocessable("提交角色名称不合法");
        entity.setRequiredSubmitRole(role == null || role.isEmpty() ? null : role);
        if (created) configMapper.insert(entity);
        else if (configMapper.updateById(entity) != 1) throw BusinessException.conflict("门禁配置已被其他用户修改，请刷新后重试");
        return SubmissionGateDtos.Config.from(entity);
    }

    public SubmissionGateDtos.CheckResult check(String writingTaskId) {
        writingTaskService.require(writingTaskId);
        SubmissionGateDtos.Config config = config();
        List<SubmissionGateDtos.Rule> rules = new ArrayList<>();
        if (config.requiredSubmitRole() != null) {
            boolean hasRole = currentUserHasRole(config.requiredSubmitRole());
            rules.add(new SubmissionGateDtos.Rule(
                    "SUBMIT_ROLE",
                    "由指定角色确认提交",
                    hasRole,
                    hasRole
                            ? "当前用户具备 " + config.requiredSubmitRole() + " 角色"
                            : "须由具备 " + config.requiredSubmitRole() + " 角色的人员提交"));
        }
        DocumentDtos.DocumentResponse document;
        try {
            document = documentService.get(writingTaskId);
        } catch (BusinessException missing) {
            rules.add(new SubmissionGateDtos.Rule("DOCUMENT", "已形成文稿", false, "文稿尚未生成，不能提交"));
            return new SubmissionGateDtos.CheckResult(writingTaskId, null, null, false, rules);
        }
        rules.add(new SubmissionGateDtos.Rule("DOCUMENT", "已形成文稿", true, "当前文稿版本 V" + document.currentVersion()));

        ReviewTaskEntity review = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
                .eq(ReviewTaskEntity::getWritingTaskId, writingTaskId)
                .isNotNull(ReviewTaskEntity::getDocumentId)
                .orderByDesc(ReviewTaskEntity::getCreatedAt)
                .last("LIMIT 1"));
        boolean needsReview = config.requireReview()
                || config.requireLatestReview()
                || config.blockOnSevere()
                || config.requireImportantProcessed()
                || config.requireReport();
        if (needsReview) {
            rules.add(new SubmissionGateDtos.Rule(
                    "REVIEW", "已完成智能审校", review != null, review != null ? "最近一次审校：" + review.getId() : "尚未发起智能审校"));
        }
        if (review != null) {
            if (config.requireLatestReview()) rules.add(latestReviewRule(writingTaskId, review, document));
            if (config.blockOnSevere()) {
                long severe = countIssues(review.getId(), "严重", List.of("待处理", "转人工"));
                rules.add(new SubmissionGateDtos.Rule(
                        "NO_SEVERE",
                        "无未处理的严重问题",
                        severe == 0,
                        severe == 0 ? "严重问题均已处理" : "仍有 " + severe + " 个严重问题未处理或转人工未办结"));
            }
            if (config.requireImportantProcessed()) {
                long important = countIssues(review.getId(), "重要", List.of("待处理"));
                rules.add(new SubmissionGateDtos.Rule(
                        "IMPORTANT_PROCESSED",
                        "重要问题全部处理",
                        important == 0,
                        important == 0 ? "重要问题均已处理" : "仍有 " + important + " 个重要问题待处理"));
            }
            if (config.requireReport()) {
                boolean hasReport = reportMapper.selectCount(new LambdaQueryWrapper<ReviewReportEntity>()
                                .eq(ReviewReportEntity::getReviewTaskId, review.getId()))
                        > 0;
                rules.add(new SubmissionGateDtos.Rule(
                        "REPORT", "已生成审校报告", hasReport, hasReport ? "审校报告已生成" : "请先生成审校报告"));
            }
        }
        boolean passed = rules.stream().allMatch(SubmissionGateDtos.Rule::passed);
        return new SubmissionGateDtos.CheckResult(
                writingTaskId, document.currentVersion(), review == null ? null : review.getId(), passed, rules);
    }

    /** 审校后只允许存在“采纳审校问题”产生的版本；其他修改须重新审校。 */
    private SubmissionGateDtos.Rule latestReviewRule(
            String writingTaskId, ReviewTaskEntity review, DocumentDtos.DocumentResponse document) {
        Integer reviewed = review.getDocumentVersion();
        if (reviewed == null) {
            return new SubmissionGateDtos.Rule("LATEST_REVIEW", "审校对应最新文稿", false, "该审校记录未记录文稿版本，请重新审校");
        }
        List<Integer> unreviewed = documentService.versions(writingTaskId).stream()
                .filter(v -> v.versionNumber() > reviewed)
                .filter(v ->
                        v.changeDescription() == null || !v.changeDescription().startsWith(REVIEW_ACCEPT_PREFIX))
                .map(DocumentDtos.VersionResponse::versionNumber)
                .sorted()
                .toList();
        boolean passed = unreviewed.isEmpty();
        return new SubmissionGateDtos.Rule(
                "LATEST_REVIEW",
                "审校对应最新文稿",
                passed,
                passed
                        ? "审校基于 V" + reviewed + "，此后仅有采纳审校建议产生的修改"
                        : "审校基于 V" + reviewed + "，之后的 V" + unreviewed.get(0) + " 等版本有其他修改，请重新审校");
    }

    private static boolean currentUserHasRole(String role) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private long countIssues(String reviewTaskId, String risk, List<String> statuses) {
        return issueMapper.selectCount(new LambdaQueryWrapper<ReviewIssueEntity>()
                .eq(ReviewIssueEntity::getReviewTaskId, reviewTaskId)
                .eq(ReviewIssueEntity::getRiskLevel, risk)
                .in(ReviewIssueEntity::getProcessingStatus, statuses));
    }

    private SubmissionGateConfigEntity configEntity() {
        return configMapper.selectOne(new LambdaQueryWrapper<SubmissionGateConfigEntity>().last("LIMIT 1"));
    }
}
