package cn.gov.document.platform.writing.persistence;

import cn.gov.document.platform.writing.domain.DocumentTypeConfigVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentTypeConfigVersionMapper extends BaseMapper<DocumentTypeConfigVersionEntity> {}
