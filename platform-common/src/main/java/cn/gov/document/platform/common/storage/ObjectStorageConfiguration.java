package cn.gov.document.platform.common.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ObjectStorageConfiguration {
    @Bean
    public ObjectStorage objectStorage(
            @Value("${platform.storage.type:local}") String type,
            @Value("${platform.storage.file-root:./data/files}") String fileRoot,
            @Value("${platform.storage.minio.endpoint:}") String endpoint,
            @Value("${platform.storage.minio.access-key:}") String accessKey,
            @Value("${platform.storage.minio.secret-key:}") String secretKey,
            @Value("${platform.storage.minio.bucket:document-platform}") String bucket) {
        if ("minio".equalsIgnoreCase(type)) {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            return new MinioObjectStorage(client, bucket);
        }
        return new LocalObjectStorage(Path.of(fileRoot));
    }
}
