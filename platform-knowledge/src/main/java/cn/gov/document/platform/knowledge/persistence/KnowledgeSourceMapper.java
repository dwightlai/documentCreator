package cn.gov.document.platform.knowledge.persistence;

import cn.gov.document.platform.knowledge.domain.KnowledgeSourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeSourceMapper extends BaseMapper<KnowledgeSourceEntity> {}
