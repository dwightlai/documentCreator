package cn.gov.document.platform.review.persistence;

import cn.gov.document.platform.review.domain.ReviewReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewReportMapper extends BaseMapper<ReviewReportEntity> {}
