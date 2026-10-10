package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.review.api.FalsePositiveDtos;
import cn.gov.document.platform.review.domain.*;
import cn.gov.document.platform.review.persistence.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FalsePositiveService {
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewFalsePositiveMapper mapper;

    public FalsePositiveService(
            ReviewTaskMapper taskMapper, ReviewIssueMapper issueMapper, ReviewFalsePositiveMapper mapper) {
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.mapper = mapper;
    }

    @Transactional
    public FalsePositiveDtos.Response submit(
            String taskId, String issueNumber, FalsePositiveDtos.SubmitRequest request) {
        if (taskMapper.selectById(taskId) == null) throw BusinessException.notFound("审校任务不存在");
        ReviewIssueEntity issue = issueMapper.selectOne(new LambdaQueryWrapper<ReviewIssueEntity>()
                .eq(ReviewIssueEntity::getReviewTaskId, taskId)
                .eq(ReviewIssueEntity::getIssueNumber, issueNumber));
        if (issue == null) throw BusinessException.notFound("审校问题不存在");
        if (mapper.selectCount(new LambdaQueryWrapper<ReviewFalsePositiveEntity>()
                        .eq(ReviewFalsePositiveEntity::getIssueId, issue.getId())
                        .eq(ReviewFalsePositiveEntity::getReviewStatus, "待审核"))
                > 0) throw BusinessException.conflict("该问题已有待审核误报记录");
        ReviewFalsePositiveEntity e = new ReviewFalsePositiveEntity();
        e.setTenantId(RequestContext.tenantId());
        e.setIssueId(issue.getId());
        e.setOriginalText(issue.getOriginalText());
        e.setSuggestedFix(issue.getSuggestedFix());
        e.setReason(request.reason().trim());
        e.setSubmitterId(RequestContext.userId());
        e.setReviewStatus("待审核");
        mapper.insert(e);
        return FalsePositiveDtos.Response.from(e);
    }

    public List<FalsePositiveDtos.Response> list(String status) {
        return mapper
                .selectList(new LambdaQueryWrapper<ReviewFalsePositiveEntity>()
                        .eq(status != null && !status.isBlank(), ReviewFalsePositiveEntity::getReviewStatus, status)
                        .orderByDesc(ReviewFalsePositiveEntity::getCreatedAt))
                .stream()
                .map(FalsePositiveDtos.Response::from)
                .toList();
    }

    @Transactional
    public FalsePositiveDtos.Response review(String id, FalsePositiveDtos.ReviewRequest request) {
        if (!List.of("已确认", "已拒绝").contains(request.reviewStatus()))
            throw BusinessException.unprocessable("审核状态仅支持已确认或已拒绝");
        ReviewFalsePositiveEntity e = mapper.selectById(id);
        if (e == null) throw BusinessException.notFound("误报记录不存在");
        if (!"待审核".equals(e.getReviewStatus())) throw BusinessException.conflict("误报记录已审核");
        e.setReviewStatus(request.reviewStatus());
        e.setReviewedBy(RequestContext.userId());
        e.setOptimizationSuggestion(request.optimizationSuggestion());
        mapper.updateById(e);
        return FalsePositiveDtos.Response.from(e);
    }
}
