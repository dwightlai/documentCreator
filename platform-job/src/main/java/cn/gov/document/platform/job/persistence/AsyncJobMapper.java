package cn.gov.document.platform.job.persistence;

import cn.gov.document.platform.job.domain.AsyncJobEntity;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AsyncJobMapper extends BaseMapper<AsyncJobEntity> {
    @InterceptorIgnore(tenantLine = "true")
    @Select(
            """
            SELECT * FROM ai_async_job
            WHERE (status = 'QUEUED' OR (status = 'RETRY_WAIT' AND next_retry_at <= CURRENT_TIMESTAMP))
            ORDER BY created_at
            LIMIT 50
            """)
    List<AsyncJobEntity> findRunnableForAllTenants();
}
