package cn.gov.document.platform.job.persistence;

import cn.gov.document.platform.job.domain.AsyncJobEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AsyncJobEventMapper extends BaseMapper<AsyncJobEventEntity> {}
