package cn.gov.document.platform.review.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.document.application.PdfExporter;
import cn.gov.document.platform.document.application.DocxExporter;
import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import cn.gov.document.platform.review.api.ReviewReportDtos;
import cn.gov.document.platform.review.domain.ReviewIssueEntity;
import cn.gov.document.platform.review.domain.ReviewReportEntity;
import cn.gov.document.platform.review.domain.ReviewTaskEntity;
import cn.gov.document.platform.review.persistence.ReviewIssueMapper;
import cn.gov.document.platform.review.persistence.ReviewReportMapper;
import cn.gov.document.platform.review.persistence.ReviewTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;import java.util.ArrayList;import java.util.List;

@Service public class ReviewReportService{
    private final ReviewTaskMapper taskMapper;private final ReviewIssueMapper issueMapper;private final ReviewReportMapper reportMapper;private final PdfExporter pdfExporter;private final DocxExporter docxExporter;
    public ReviewReportService(ReviewTaskMapper taskMapper,ReviewIssueMapper issueMapper,ReviewReportMapper reportMapper,PdfExporter pdfExporter,DocxExporter docxExporter){this.taskMapper=taskMapper;this.issueMapper=issueMapper;this.reportMapper=reportMapper;this.pdfExporter=pdfExporter;this.docxExporter=docxExporter;}
    @Transactional public ReviewReportDtos.Response generate(String taskId){ReviewTaskEntity task=requireTask(taskId);List<ReviewIssueEntity> issues=issues(taskId);int version=Math.toIntExact(reportMapper.selectCount(new LambdaQueryWrapper<ReviewReportEntity>().eq(ReviewReportEntity::getReviewTaskId,taskId)))+1;ReviewReportEntity e=new ReviewReportEntity();e.setTenantId(RequestContext.tenantId());e.setReviewTaskId(taskId);e.setReportVersion(version);e.setTotalIssues(issues.size());e.setSevereCount(countRisk(issues,"严重"));e.setImportantCount(countRisk(issues,"重要"));e.setNormalCount(countRisk(issues,"一般"));e.setTipCount(countRisk(issues,"提示"));e.setAdoptedCount(countStatus(issues,"已采纳"));e.setIgnoredCount(countStatus(issues,"已忽略"));e.setPendingCount(countStatus(issues,"待处理"));e.setGeneratedAt(OffsetDateTime.now());e.setIsFinal(e.getPendingCount()==0);reportMapper.insert(e);return ReviewReportDtos.Response.from(e);}
    public List<ReviewReportDtos.Response> list(String taskId){requireTask(taskId);return reportMapper.selectList(new LambdaQueryWrapper<ReviewReportEntity>().eq(ReviewReportEntity::getReviewTaskId,taskId).orderByDesc(ReviewReportEntity::getReportVersion)).stream().map(ReviewReportDtos.Response::from).toList();}
    public ReviewReportDtos.Response get(String taskId,int version){return ReviewReportDtos.Response.from(requireReport(taskId,version));}
    public byte[] exportPdf(String taskId,int version){ReportContent content=content(taskId,version);return pdfExporter.export(content.document(),content.paragraphs());}
    public byte[] exportDocx(String taskId,int version){ReportContent content=content(taskId,version);return docxExporter.export(content.document(),content.paragraphs());}
    private ReportContent content(String taskId,int version){ReviewTaskEntity task=requireTask(taskId);ReviewReportEntity report=requireReport(taskId,version);DocumentEntity document=new DocumentEntity();document.setTitle("政务智能审校报告");document.setCurrentVersion(version);List<DocumentParagraphEntity> paragraphs=new ArrayList<>();paragraphs.add(paragraph("审校任务："+task.getId()+"。文种："+task.getDocumentTypeCode()+"。问题总数："+report.getTotalIssues()+"，严重 "+report.getSevereCount()+"，重要 "+report.getImportantCount()+"，一般 "+report.getNormalCount()+"，提示 "+report.getTipCount()+"。"));paragraphs.add(paragraph("处理结论：已采纳 "+report.getAdoptedCount()+"，已忽略 "+report.getIgnoredCount()+"，待处理 "+report.getPendingCount()+"。报告状态："+(Boolean.TRUE.equals(report.getIsFinal())?"最终版":"过程版")+"。"));for(ReviewIssueEntity issue:issues(taskId))paragraphs.add(paragraph(issue.getIssueNumber()+"［"+issue.getRiskLevel()+"／"+issue.getIssueCategory()+"］位置：第 "+issue.getPageNumber()+" 页第 "+issue.getParagraphNumber()+" 段字符 "+issue.getCharPositionStart()+"—"+issue.getCharPositionEnd()+"；原文“"+issue.getOriginalText()+"”，建议“"+issue.getSuggestedFix()+"”。处理状态："+issue.getProcessingStatus()+"。依据："+issue.getRuleBasis()));return new ReportContent(document,paragraphs);}
    private record ReportContent(DocumentEntity document,List<DocumentParagraphEntity> paragraphs){}
    private DocumentParagraphEntity paragraph(String text){DocumentParagraphEntity e=new DocumentParagraphEntity();e.setPlainText(text);return e;}
    private int countRisk(List<ReviewIssueEntity> values,String risk){return(int)values.stream().filter(i->risk.equals(i.getRiskLevel())).count();}private int countStatus(List<ReviewIssueEntity> values,String status){return(int)values.stream().filter(i->status.equals(i.getProcessingStatus())).count();}
    private List<ReviewIssueEntity> issues(String taskId){return issueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>().eq(ReviewIssueEntity::getReviewTaskId,taskId).orderByAsc(ReviewIssueEntity::getIssueNumber));}
    private ReviewTaskEntity requireTask(String id){ReviewTaskEntity e=taskMapper.selectById(id);if(e==null)throw BusinessException.notFound("审校任务不存在");return e;}private ReviewReportEntity requireReport(String taskId,int version){requireTask(taskId);ReviewReportEntity e=reportMapper.selectOne(new LambdaQueryWrapper<ReviewReportEntity>().eq(ReviewReportEntity::getReviewTaskId,taskId).eq(ReviewReportEntity::getReportVersion,version));if(e==null)throw BusinessException.notFound("审校报告不存在");return e;}
}
