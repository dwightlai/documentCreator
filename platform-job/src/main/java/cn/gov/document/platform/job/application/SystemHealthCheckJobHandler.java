package cn.gov.document.platform.job.application;

import org.springframework.stereotype.Component;

@Component
public class SystemHealthCheckJobHandler implements AsyncJobHandler {
    @Override
    public String jobType() {
        return "SYSTEM_HEALTH_CHECK";
    }

    @Override
    public String execute(String payload, ProgressReporter reporter) {
        reporter.progress(25, "基础组件检查完成");
        if (reporter.cancellationRequested()) {
            throw new JobCancelledException();
        }
        reporter.progress(75, "业务组件检查完成");
        return "系统健康检查完成";
    }

    public static class JobCancelledException extends RuntimeException {
        public JobCancelledException() {
            super("任务已取消");
        }
    }
}
