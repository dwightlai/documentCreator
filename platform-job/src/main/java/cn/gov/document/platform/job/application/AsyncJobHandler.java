package cn.gov.document.platform.job.application;

public interface AsyncJobHandler {
    String jobType();

    String execute(String payload, ProgressReporter reporter) throws Exception;

    interface ProgressReporter {
        void progress(int percent, String message);
        boolean cancellationRequested();
    }
}
