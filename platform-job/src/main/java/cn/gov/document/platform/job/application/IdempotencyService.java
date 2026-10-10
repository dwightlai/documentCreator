package cn.gov.document.platform.job.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.job.domain.IdempotencyRecordEntity;
import cn.gov.document.platform.job.persistence.IdempotencyRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private static final int RETENTION_HOURS = 24;
    private final IdempotencyRecordMapper mapper;

    public IdempotencyService(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    public Reservation reserve(String endpoint, String key, String requestHash) {
        IdempotencyRecordEntity existing = find(endpoint, key);
        if (existing != null) {
            return existing(existing, requestHash);
        }

        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setTenantId(RequestContext.tenantId());
        entity.setUserId(RequestContext.userId());
        entity.setEndpoint(endpoint);
        entity.setIdempotencyKey(key);
        entity.setRequestHash(requestHash);
        entity.setStatus("PROCESSING");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setExpiresAt(OffsetDateTime.now().plusHours(RETENTION_HOURS));
        try {
            mapper.insert(entity);
            return new Reservation(Decision.PROCEED, entity.getId(), null, null, null);
        } catch (DuplicateKeyException concurrentInsert) {
            return existing(find(endpoint, key), requestHash);
        }
    }

    public void complete(String id, int status, String contentType, byte[] responseBody) {
        IdempotencyRecordEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new IllegalStateException("幂等记录不存在");
        }
        entity.setStatus("COMPLETED");
        entity.setHttpStatus(status);
        entity.setContentType(contentType);
        entity.setResponseBody(Base64.getEncoder().encodeToString(responseBody));
        entity.setCompletedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }

    public void abandon(String id) {
        mapper.deleteById(id);
    }

    @Scheduled(cron = "0 17 * * * *")
    public void cleanExpired() {
        mapper.deleteExpiredForAllTenants();
    }

    private IdempotencyRecordEntity find(String endpoint, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<IdempotencyRecordEntity>()
                .eq(IdempotencyRecordEntity::getUserId, RequestContext.userId())
                .eq(IdempotencyRecordEntity::getEndpoint, endpoint)
                .eq(IdempotencyRecordEntity::getIdempotencyKey, key));
    }

    private Reservation existing(IdempotencyRecordEntity entity, String requestHash) {
        if (entity == null) {
            return new Reservation(Decision.IN_PROGRESS, null, null, null, null);
        }
        if (!entity.getRequestHash().equals(requestHash)) {
            return new Reservation(Decision.HASH_CONFLICT, entity.getId(), null, null, null);
        }
        if (!"COMPLETED".equals(entity.getStatus())) {
            return new Reservation(Decision.IN_PROGRESS, entity.getId(), null, null, null);
        }
        byte[] body = entity.getResponseBody() == null
                ? new byte[0]
                : Base64.getDecoder().decode(entity.getResponseBody());
        return new Reservation(Decision.REPLAY, entity.getId(), entity.getHttpStatus(), entity.getContentType(), body);
    }

    public enum Decision {
        PROCEED,
        REPLAY,
        IN_PROGRESS,
        HASH_CONFLICT
    }

    public record Reservation(
            Decision decision, String recordId, Integer httpStatus, String contentType, byte[] responseBody) {}
}
