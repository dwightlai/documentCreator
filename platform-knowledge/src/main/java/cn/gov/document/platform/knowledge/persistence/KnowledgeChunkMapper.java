package cn.gov.document.platform.knowledge.persistence;

import cn.gov.document.platform.knowledge.domain.KnowledgeChunkEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {}
