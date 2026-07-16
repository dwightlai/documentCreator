package cn.gov.document.platform.job.persistence;

import cn.gov.document.platform.job.domain.IdempotencyRecordEntity;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM ai_idempotency_record WHERE expires_at < CURRENT_TIMESTAMP")
    int deleteExpiredForAllTenants();
}
