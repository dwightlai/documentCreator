package cn.gov.document.platform.rule.persistence;

import cn.gov.document.platform.rule.domain.ReviewRuleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewRuleMapper extends BaseMapper<ReviewRuleEntity> {}
