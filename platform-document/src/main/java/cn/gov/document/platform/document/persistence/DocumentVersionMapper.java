package cn.gov.document.platform.document.persistence;

import cn.gov.document.platform.document.domain.DocumentVersionEntity;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersionEntity> {
    /** 版本保留任务按租户逐个执行，此处是唯一跨租户的查询。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM ai_document_version WHERE is_deleted = FALSE")
    List<String> findTenantIds();

    /** 版本段落被审校问题、批注或写作问答引用的数量；被引用的版本属于审校前后等关键版本，永久保留。 */
    @Select("SELECT"
            + " (SELECT COUNT(*) FROM ai_review_issue i JOIN ai_document_paragraph p ON i.paragraph_id = p.id"
            + "   WHERE p.document_id = #{documentId} AND p.version_number = #{version})"
            + " + (SELECT COUNT(*) FROM ai_document_comment c JOIN ai_document_paragraph p ON c.paragraph_id = p.id"
            + "   WHERE p.document_id = #{documentId} AND p.version_number = #{version})"
            + " + (SELECT COUNT(*) FROM ai_writing_qa q JOIN ai_document_paragraph p ON q.inserted_paragraph_id = p.id"
            + "   WHERE p.document_id = #{documentId} AND p.version_number = #{version})")
    long countReferences(@Param("documentId") String documentId, @Param("version") int version);

    /** 物理删除指定版本的段落引用（§14.4 要求清理正文内容，逻辑删除不满足要求）。 */
    @Delete("DELETE FROM ai_document_citation WHERE paragraph_id IN ("
            + "SELECT id FROM ai_document_paragraph WHERE document_id = #{documentId} AND version_number = #{version})")
    int purgeCitations(@Param("documentId") String documentId, @Param("version") int version);

    @Delete("DELETE FROM ai_document_paragraph WHERE document_id = #{documentId} AND version_number = #{version}")
    int purgeParagraphs(@Param("documentId") String documentId, @Param("version") int version);
}
