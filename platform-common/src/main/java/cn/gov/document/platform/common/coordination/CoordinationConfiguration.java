package cn.gov.document.platform.common.coordination;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CoordinationConfiguration {
    @Bean
    public CoordinationStore coordinationStore(
            @Value("${platform.coordination.type:memory}") String type, ObjectProvider<StringRedisTemplate> redis) {
        if ("redis".equalsIgnoreCase(type)) {
            StringRedisTemplate template = redis.getIfAvailable();
            if (template == null) throw new IllegalStateException("platform.coordination.type=redis 但未配置 Redis 连接");
            return new RedisCoordinationStore(template);
        }
        return new InMemoryCoordinationStore();
    }
}
