package org.limbo.flowjob.tracker.core.job.handler;

/**
 * @author Devil
 * @since 2021/8/24
 */
public interface JobFailHandler {
    /**
     * 失败处理
     */
    void handle();

    /**
     * 是否终止计划
     */
    boolean terminate();

}
