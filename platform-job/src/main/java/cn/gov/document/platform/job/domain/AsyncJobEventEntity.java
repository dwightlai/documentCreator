package cn.gov.document.platform.job.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("ai_async_job_event")
public class AsyncJobEventEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String jobId;
    private Integer eventSequence;
    private String eventType;
    private String eventData;
    private OffsetDateTime occurredAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public String getJobId() { return jobId; }
    public void setJobId(String value) { jobId = value; }
    public Integer getEventSequence() { return eventSequence; }
    public void setEventSequence(Integer value) { eventSequence = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getEventData() { return eventData; }
    public void setEventData(String value) { eventData = value; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime value) { occurredAt = value; }
}
