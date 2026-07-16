package cn.gov.document.platform.review.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("ai_review_issue")
public class ReviewIssueEntity extends BaseEntity {
    private String reviewTaskId; private String issueNumber; private String issueCategory; private String riskLevel;
    private Integer pageNumber; private Integer paragraphNumber; private String paragraphId; private Integer charPositionStart; private Integer charPositionEnd;
    private String originalText; private String suggestedFix; private String issueExplanation; private String ruleBasis;
    private String detectionMethod; private BigDecimal confidenceScore; private String processingStatus; private String processingNote;
    private String processedBy; private OffsetDateTime processedAt; private String mergedGroupId;
    public String getReviewTaskId(){return reviewTaskId;} public void setReviewTaskId(String v){reviewTaskId=v;}
    public String getIssueNumber(){return issueNumber;} public void setIssueNumber(String v){issueNumber=v;}
    public String getIssueCategory(){return issueCategory;} public void setIssueCategory(String v){issueCategory=v;}
    public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;}
    public Integer getPageNumber(){return pageNumber;} public void setPageNumber(Integer v){pageNumber=v;}
    public Integer getParagraphNumber(){return paragraphNumber;} public void setParagraphNumber(Integer v){paragraphNumber=v;}
    public String getParagraphId(){return paragraphId;} public void setParagraphId(String v){paragraphId=v;}
    public Integer getCharPositionStart(){return charPositionStart;} public void setCharPositionStart(Integer v){charPositionStart=v;}
    public Integer getCharPositionEnd(){return charPositionEnd;} public void setCharPositionEnd(Integer v){charPositionEnd=v;}
    public String getOriginalText(){return originalText;} public void setOriginalText(String v){originalText=v;}
    public String getSuggestedFix(){return suggestedFix;} public void setSuggestedFix(String v){suggestedFix=v;}
    public String getIssueExplanation(){return issueExplanation;} public void setIssueExplanation(String v){issueExplanation=v;}
    public String getRuleBasis(){return ruleBasis;} public void setRuleBasis(String v){ruleBasis=v;}
    public String getDetectionMethod(){return detectionMethod;} public void setDetectionMethod(String v){detectionMethod=v;}
    public BigDecimal getConfidenceScore(){return confidenceScore;} public void setConfidenceScore(BigDecimal v){confidenceScore=v;}
    public String getProcessingStatus(){return processingStatus;} public void setProcessingStatus(String v){processingStatus=v;}
    public String getProcessingNote(){return processingNote;} public void setProcessingNote(String v){processingNote=v;}
    public String getProcessedBy(){return processedBy;} public void setProcessedBy(String v){processedBy=v;}
    public OffsetDateTime getProcessedAt(){return processedAt;} public void setProcessedAt(OffsetDateTime v){processedAt=v;}
    public String getMergedGroupId(){return mergedGroupId;} public void setMergedGroupId(String v){mergedGroupId=v;}
}
