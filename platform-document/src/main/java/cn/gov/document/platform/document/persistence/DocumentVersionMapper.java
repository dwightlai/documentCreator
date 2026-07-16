package cn.gov.document.platform.document.persistence;

import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersionEntity> { }
