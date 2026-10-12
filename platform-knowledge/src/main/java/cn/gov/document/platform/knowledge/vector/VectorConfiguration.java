package cn.gov.document.platform.knowledge.vector;

import cn.gov.document.platform.knowledge.persistence.KnowledgeChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class VectorConfiguration {
    @Bean
    public VectorStore vectorStore(
            @Value("${platform.knowledge.vector-store:database}") String type,
            @Value("${platform.knowledge.milvus.endpoint:}") String milvusEndpoint,
            @Value("${platform.knowledge.milvus.collection:knowledge_chunks}") String collection,
            @Value("${platform.knowledge.milvus.dimensions:1024}") int dimensions,
            @Value("${platform.knowledge.milvus.token-env:}") String tokenEnvironmentVariable,
            KnowledgeChunkMapper chunkMapper,
            ObjectMapper objectMapper) {
        if ("milvus".equalsIgnoreCase(type)) {
            if (!StringUtils.hasText(milvusEndpoint))
                throw new IllegalStateException(
                        "platform.knowledge.vector-store=milvus 但未配置 platform.knowledge.milvus.endpoint");
            String token =
                    StringUtils.hasText(tokenEnvironmentVariable) ? System.getenv(tokenEnvironmentVariable) : null;
            return new MilvusVectorStore(milvusEndpoint, collection, dimensions, token, objectMapper);
        }
        return new DatabaseVectorStore(chunkMapper, objectMapper);
    }
}
