package cn.gov.document.platform.writing.domain;

import cn.gov.document.platform.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_document_type_config_version")
public class DocumentTypeConfigVersionEntity extends BaseEntity {
    private String configId; private Integer versionNumber; private String fullConfig; private String changeDescription;
    private String testResults; private String approvedBy; private OffsetDateTime approvedAt;
    public String getConfigId(){return configId;} public void setConfigId(String v){configId=v;}
    public Integer getVersionNumber(){return versionNumber;} public void setVersionNumber(Integer v){versionNumber=v;}
    public String getFullConfig(){return fullConfig;} public void setFullConfig(String v){fullConfig=v;}
    public String getChangeDescription(){return changeDescription;} public void setChangeDescription(String v){changeDescription=v;}
    public String getTestResults(){return testResults;} public void setTestResults(String v){testResults=v;}
    public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;}
    public OffsetDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(OffsetDateTime v){approvedAt=v;}
}
