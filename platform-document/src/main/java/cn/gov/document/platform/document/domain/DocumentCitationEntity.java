package cn.gov.document.platform.document.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_document_citation")
public class DocumentCitationEntity extends BaseEntity {
    private String documentId; private String paragraphId; private String materialId; private String citationType;
    private String citedFragment; private Integer positionStart; private Integer positionEnd; private String displayMode;
    public String getDocumentId(){return documentId;} public void setDocumentId(String v){documentId=v;}
    public String getParagraphId(){return paragraphId;} public void setParagraphId(String v){paragraphId=v;}
    public String getMaterialId(){return materialId;} public void setMaterialId(String v){materialId=v;}
    public String getCitationType(){return citationType;} public void setCitationType(String v){citationType=v;}
    public String getCitedFragment(){return citedFragment;} public void setCitedFragment(String v){citedFragment=v;}
    public Integer getPositionStart(){return positionStart;} public void setPositionStart(Integer v){positionStart=v;}
    public Integer getPositionEnd(){return positionEnd;} public void setPositionEnd(Integer v){positionEnd=v;}
    public String getDisplayMode(){return displayMode;} public void setDisplayMode(String v){displayMode=v;}
}
