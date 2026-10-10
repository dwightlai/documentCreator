package cn.gov.document.platform.document.persistence;

import cn.gov.document.platform.document.domain.GenerationRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationRecordMapper extends BaseMapper<GenerationRecordEntity> {}
