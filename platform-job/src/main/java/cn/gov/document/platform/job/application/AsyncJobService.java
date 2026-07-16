package cn.gov.document.platform.job.application;

import cn.gov.document.platform.common.context.RequestContext;
import cn.gov.document.platform.common.error.BusinessException;
import cn.gov.document.platform.job.api.AsyncJobDtos;
import cn.gov.document.platform.job.domain.AsyncJobEntity;
import cn.gov.document.platform.job.domain.AsyncJobEventEntity;
import cn.gov.document.platform.job.persistence.AsyncJobEventMapper;
import cn.gov.document.platform.job.persistence.AsyncJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AsyncJobService {
    private static final Logger log = LoggerFactory.getLogger(AsyncJobService.class);
    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final AsyncJobMapper jobMapper;
    private final AsyncJobEventMapper eventMapper;
    private final TaskExecutor jobExecutor;
    private final Map<String, AsyncJobHandler> handlers;
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, Object> eventLocks = new ConcurrentHashMap<>();

    public AsyncJobService(
            AsyncJobMapper jobMapper,
            AsyncJobEventMapper eventMapper,
            TaskExecutor jobExecutor,
            List<AsyncJobHandler> handlers) {
        this.jobMapper = jobMapper;
        this.eventMapper = eventMapper;
        this.jobExecutor = jobExecutor;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                AsyncJobHandler::jobType, Function.identity()));
    }

    public AsyncJobDtos.Response submit(AsyncJobDtos.SubmitRequest request) {
        if (!handlers.containsKey(request.jobType())) {
            throw BusinessException.unprocessable("不支持的异步任务类型: " + request.jobType());
        }
        AsyncJobEntity job = new AsyncJobEntity();
        job.setTenantId(RequestContext.tenantId());
        job.setJobType(request.jobType());
        job.setBusinessId(request.businessId());
        job.setPayload(request.payload());
        job.setStatus("QUEUED");
        job.setProgress(0);
        job.setRetryCount(0);
        job.setMaxRetries(request.maxRetries() == null ? 5 : request.maxRetries());
        job.setCancelRequested(false);
        job.setCreatedBy(RequestContext.userId());
        job.setCreatedAt(OffsetDateTime.now());
        job.setLockVersion(0);
        jobMapper.insert(job);
        appendEvent(job.getId(), "start", "{\"status\":\"QUEUED\",\"progress\":0}");
        dispatch(job.getTenantId(), job.getCreatedBy(), job.getId());
        return AsyncJobDtos.Response.from(job);
    }

    public AsyncJobDtos.Response get(String jobId) {
        return AsyncJobDtos.Response.from(require(jobId));
    }

    public List<AsyncJobDtos.Response> list(String status, int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        return jobMapper.selectList(new LambdaQueryWrapper<AsyncJobEntity>()
                        .eq(status != null && !status.isBlank(), AsyncJobEntity::getStatus, status)
                        .orderByDesc(AsyncJobEntity::getCreatedAt)
                        .last("LIMIT " + safeLimit))
                .stream().map(AsyncJobDtos.Response::from).toList();
    }

    public List<AsyncJobDtos.EventResponse> history(String jobId, int afterEventId) {
        require(jobId);
        return eventMapper.selectList(new LambdaQueryWrapper<AsyncJobEventEntity>()
                        .eq(AsyncJobEventEntity::getJobId, jobId)
                        .gt(AsyncJobEventEntity::getEventSequence, Math.max(0, afterEventId))
                        .orderByAsc(AsyncJobEventEntity::getEventSequence))
                .stream().map(AsyncJobDtos.EventResponse::from).toList();
    }

    public SseEmitter stream(String jobId, int afterEventId) {
        AsyncJobEntity job = require(jobId);
        String emitterKey = emitterKey(job.getTenantId(), jobId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(emitterKey, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> removeEmitter(emitterKey, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());

        try {
            for (AsyncJobDtos.EventResponse event : history(jobId, afterEventId)) {
                send(emitter, event);
            }
            if (terminal(job.getStatus())) {
                emitter.complete();
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public AsyncJobDtos.Response cancel(String jobId) {
        AsyncJobEntity job = require(jobId);
        if (terminal(job.getStatus())) {
            return AsyncJobDtos.Response.from(job);
        }
        job.setCancelRequested(true);
        if ("QUEUED".equals(job.getStatus()) || "RETRY_WAIT".equals(job.getStatus())) {
            job.setStatus("CANCELLED");
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMessage("用户取消");
            appendEvent(jobId, "error", "{\"status\":\"CANCELLED\",\"message\":\"用户取消\"}");
        }
        update(job);
        return AsyncJobDtos.Response.from(job);
    }

    public AsyncJobDtos.Response retry(String jobId) {
        AsyncJobEntity job = require(jobId);
        if (!List.of("FAILED", "DEAD_LETTER", "CANCELLED").contains(job.getStatus())) {
            throw BusinessException.conflict("只有失败、死信或已取消任务可以重试");
        }
        job.setStatus("QUEUED");
        job.setProgress(0);
        job.setErrorMessage(null);
        job.setNextRetryAt(null);
        job.setCancelRequested(false);
        job.setFinishedAt(null);
        update(job);
        appendEvent(jobId, "start", "{\"status\":\"QUEUED\",\"manualRetry\":true}");
        dispatch(job.getTenantId(), RequestContext.userId(), jobId);
        return AsyncJobDtos.Response.from(job);
    }

    @Scheduled(fixedDelayString = "${platform.jobs.recovery-delay-ms:5000}")
    public void recoverRunnableJobs() {
        for (AsyncJobEntity candidate : jobMapper.findRunnableForAllTenants()) {
            dispatch(candidate.getTenantId(), candidate.getCreatedBy(), candidate.getId());
        }
    }

    private void dispatch(String tenantId, String userId, String jobId) {
        jobExecutor.execute(() -> {
            RequestContext.set("job-" + jobId, tenantId, userId);
            try {
                execute(jobId);
            } finally {
                RequestContext.clear();
            }
        });
    }

    private void execute(String jobId) {
        int claimed = jobMapper.update(null, new LambdaUpdateWrapper<AsyncJobEntity>()
                .eq(AsyncJobEntity::getId, jobId)
                .in(AsyncJobEntity::getStatus, "QUEUED", "RETRY_WAIT")
                .set(AsyncJobEntity::getStatus, "RUNNING")
                .set(AsyncJobEntity::getStartedAt, OffsetDateTime.now())
                .set(AsyncJobEntity::getNextRetryAt, null)
                .setSql("lock_version = lock_version + 1"));
        if (claimed == 0) {
            return;
        }

        AsyncJobEntity job = require(jobId);
        appendEvent(jobId, "progress", "{\"status\":\"RUNNING\",\"progress\":1}");
        AsyncJobHandler handler = handlers.get(job.getJobType());
        try {
            String result = handler.execute(job.getPayload(), new Reporter(jobId));
            job = require(jobId);
            if (Boolean.TRUE.equals(job.getCancelRequested())) {
                throw new SystemHealthCheckJobHandler.JobCancelledException();
            }
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setResultSummary(result);
            job.setErrorMessage(null);
            job.setFinishedAt(OffsetDateTime.now());
            update(job);
            appendEvent(jobId, "completed", "{\"status\":\"COMPLETED\",\"progress\":100}");
        } catch (SystemHealthCheckJobHandler.JobCancelledException cancelled) {
            job = require(jobId);
            job.setStatus("CANCELLED");
            job.setErrorMessage("用户取消");
            job.setFinishedAt(OffsetDateTime.now());
            update(job);
            appendEvent(jobId, "error", "{\"status\":\"CANCELLED\",\"message\":\"用户取消\"}");
        } catch (Exception failure) {
            handleFailure(require(jobId), failure);
        }
    }

    private void handleFailure(AsyncJobEntity job, Exception failure) {
        int retry = job.getRetryCount() + 1;
        job.setRetryCount(retry);
        job.setErrorMessage(truncate(failure.getMessage(), 2000));
        if (retry <= job.getMaxRetries()) {
            long delaySeconds = Math.min(300, 1L << Math.min(8, retry));
            job.setStatus("RETRY_WAIT");
            job.setNextRetryAt(OffsetDateTime.now().plusSeconds(delaySeconds));
            update(job);
            appendEvent(job.getId(), "progress", "{\"status\":\"RETRY_WAIT\",\"retryCount\":" + retry + "}");
        } else {
            job.setStatus("DEAD_LETTER");
            job.setFinishedAt(OffsetDateTime.now());
            update(job);
            appendEvent(job.getId(), "error", "{\"status\":\"DEAD_LETTER\",\"retryCount\":" + retry + "}");
        }
        log.warn("Async job {} failed on attempt {}", job.getId(), retry, failure);
    }

    private void update(AsyncJobEntity job) {
        int updated = jobMapper.updateById(job);
        if (updated != 1) {
            throw BusinessException.conflict("异步任务状态已被并发修改");
        }
    }

    private AsyncJobEntity require(String jobId) {
        AsyncJobEntity entity = jobMapper.selectById(jobId);
        if (entity == null) {
            throw BusinessException.notFound("异步任务不存在");
        }
        return entity;
    }

    private void appendEvent(String jobId, String type, String data) {
        Object lock = eventLocks.computeIfAbsent(RequestContext.tenantId() + ":" + jobId, ignored -> new Object());
        synchronized (lock) {
            AsyncJobEventEntity previous = eventMapper.selectOne(new LambdaQueryWrapper<AsyncJobEventEntity>()
                    .eq(AsyncJobEventEntity::getJobId, jobId)
                    .orderByDesc(AsyncJobEventEntity::getEventSequence)
                    .last("LIMIT 1"));
            AsyncJobEventEntity event = new AsyncJobEventEntity();
            event.setTenantId(RequestContext.tenantId());
            event.setJobId(jobId);
            event.setEventSequence(previous == null ? 1 : previous.getEventSequence() + 1);
            event.setEventType(type);
            event.setEventData(data);
            event.setOccurredAt(OffsetDateTime.now());
            eventMapper.insert(event);
            publish(event);
        }
    }

    private void publish(AsyncJobEventEntity entity) {
        String key = emitterKey(entity.getTenantId(), entity.getJobId());
        List<SseEmitter> current = emitters.get(key);
        if (current == null) {
            return;
        }
        AsyncJobDtos.EventResponse event = AsyncJobDtos.EventResponse.from(entity);
        for (SseEmitter emitter : List.copyOf(current)) {
            try {
                send(emitter, event);
                if ("completed".equals(entity.getEventType()) || "error".equals(entity.getEventType())) {
                    emitter.complete();
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
        }
    }

    private void send(SseEmitter emitter, AsyncJobDtos.EventResponse event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Integer.toString(event.id()))
                .name(event.event())
                .data(event.data()));
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        emitters.computeIfPresent(key, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }

    private String emitterKey(String tenantId, String jobId) {
        return tenantId + ":" + jobId;
    }

    private boolean terminal(String status) {
        return List.of("COMPLETED", "FAILED", "DEAD_LETTER", "CANCELLED").contains(status);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "未知错误";
        }
        return value.substring(0, Math.min(max, value.length()));
    }

    private final class Reporter implements AsyncJobHandler.ProgressReporter {
        private final String jobId;

        private Reporter(String jobId) {
            this.jobId = jobId;
        }

        @Override
        public void progress(int percent, String message) {
            int safe = Math.max(1, Math.min(99, percent));
            AsyncJobEntity job = require(jobId);
            job.setProgress(safe);
            update(job);
            appendEvent(jobId, "progress", "{\"progress\":" + safe + ",\"message\":\""
                    + escape(message) + "\"}");
        }

        @Override
        public boolean cancellationRequested() {
            return Boolean.TRUE.equals(require(jobId).getCancelRequested());
        }

        private String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\r", "\\r").replace("\n", "\\n");
        }
    }
}
