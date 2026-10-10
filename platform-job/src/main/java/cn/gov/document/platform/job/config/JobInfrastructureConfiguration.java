package cn.gov.document.platform.job.config;

import cn.gov.document.platform.job.application.IdempotencyService;
import cn.gov.document.platform.job.web.IdempotencyFilter;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class JobInfrastructureConfiguration {
    @Bean
    ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("platform-job-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    IdempotencyFilter idempotencyFilter(IdempotencyService service) {
        return new IdempotencyFilter(service);
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> disableAutomaticIdempotencyFilterRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
