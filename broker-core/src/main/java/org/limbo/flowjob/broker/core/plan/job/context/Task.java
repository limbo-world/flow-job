/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limbo.flowjob.broker.core.plan.job.context;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;
import org.limbo.flowjob.broker.api.clent.dto.TaskReceiveDTO;
import org.limbo.flowjob.broker.api.constants.enums.JobScheduleStatus;
import org.limbo.flowjob.broker.api.constants.enums.TaskStatus;
import org.limbo.flowjob.broker.api.constants.enums.TaskType;
import org.limbo.flowjob.broker.core.cluster.WorkerManager;
import org.limbo.flowjob.broker.core.dispatcher.WorkerSelector;
import org.limbo.flowjob.broker.core.exceptions.JobDispatchException;
import org.limbo.flowjob.broker.core.plan.PlanInstance;
import org.limbo.flowjob.broker.core.plan.job.DispatchOption;
import org.limbo.flowjob.broker.core.plan.job.ExecutorOption;
import org.limbo.flowjob.broker.core.plan.job.Job;
import org.limbo.flowjob.broker.core.plan.job.JobDAG;
import org.limbo.flowjob.broker.core.repositories.TaskRepository;
import org.limbo.flowjob.broker.core.worker.Worker;

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 作业执行上下文
 *
 * @author Brozen
 * @since 2021-05-14
 */
@Getter
@Setter
@ToString
public class Task implements Serializable {
    private static final long serialVersionUID = -9164373359695671417L;

    protected String taskId;

    protected String planId;

    protected String planInstanceId;

    protected String jobId;

    protected String jobInstanceId;

    /**
     * 状态
     */
    protected TaskStatus status;

    /**
     * 此分发执行此作业上下文的worker
     */
    protected String workerId;

    /**
     * sharding normal
     */
    protected TaskType type;

    /**
     * 作业属性，不可变。作业属性可用于分片作业、MapReduce作业、DAG工作流进行传参
     */
    protected Attributes attributes;

    /**
     * 执行失败时的异常信息
     */
    protected String errorMsg;

    /**
     * 执行失败时的异常堆栈
     */
    protected String errorStackTrace;

    /**
     * 开始时间
     */
    protected Instant startAt;

    /**
     * 结束时间
     */
    protected Instant endAt;

    // -------- 非 po 属性

    /**
     * 作业分发配置参数
     */
    protected DispatchOption dispatchOption;

    /**
     * 作业执行器配置参数
     */
    protected ExecutorOption executorOption;

    // --------------------- 需注入
    @ToString.Exclude
    @Setter(onMethod_ = @Inject)
    protected transient TaskRepository taskRepo;

    @ToString.Exclude
    @Setter(onMethod_ = @Inject)
    protected transient WorkerManager workerManager;

    /**
     * 将此任务下发给worker。
     *
     * @param workerSelector 会将此上下文分发去执行的worker
     * @return 任务下发是否成功
     * @throws JobDispatchException 状态检测失败时，即此上下文的状态不是INIT或FAILED时抛出异常。
     */
    public boolean dispatch(WorkerSelector workerSelector) throws JobDispatchException {
        // 检测状态
        TaskStatus status = this.getStatus();
        if (status != TaskStatus.DISPATCHING) {
            throw new JobDispatchException(jobId, taskId, "Cannot startup context due to current status: " + status);
        }

        Worker worker = workerSelector.select(this, workerManager.availableWorkers());
        if (worker == null) {
            return false;
        }

        // 下发任务
        return doDispatch(worker);
    }


    /**
     * 执行任务下发
     */
    protected boolean doDispatch(Worker worker) {
        try {

            // 发送任务到worker，根据worker返回结果，更新状态
            TaskReceiveDTO result = worker.sendTask(this);
            if (result != null && result.getAccepted()) {

                this.accepted(worker);
                return true;

            } else {

                this.refused(worker);
                return false;

            }

        } catch (Exception e) {
            // 失败时更新上下文状态，冒泡异常
            // todo 如果是下发失败网络问题等，应该需要重试
//            setState(TaskScheduleStatus.FAILED);
            throw new JobDispatchException(jobId, worker.getWorkerId(),
                    "Context startup failed due to send job to worker error!", e);
        }
    }


    /**
     * worker确认接收此作业上下文，表示开始执行作业
     *
     * @param worker 确认接收此上下文的worker
     * @throws JobDispatchException 接受上下文的worker和上下文记录的worker不同时，抛出异常。
     */
    public void accepted(Worker worker) throws JobDispatchException {
        // 不为此状态 无需更新
        if (this.getStatus() != TaskStatus.DISPATCHING) {
            return;
        }

        // 更新状态
        setStatus(TaskStatus.EXECUTING);
        setWorkerId(worker.getWorkerId());
        taskRepo.dispatched(this);
    }


    /**
     * worker拒绝接收此任务，表示任务下发失败
     *
     * @param worker 拒绝接收的worker
     * @throws JobDispatchException 拒绝任务的worker和任务记录的worker不同时，抛出异常。
     */
    public void refused(Worker worker) throws JobDispatchException {
        // 不为此状态 无需更新
        if (this.getStatus() != TaskStatus.DISPATCHING) {
            return;
        }

        // 更新状态
        setStatus(TaskStatus.DISPATCH_FAILED);
        setWorkerId(worker.getWorkerId());
        taskRepo.dispatchFailed(this);
    }


    /**
     * 任务执行成功，worker反馈任务执行完成后，才会调用此方法。
     *
     * @throws JobDispatchException 任务状态不是{@link JobScheduleStatus#EXECUTING}时抛出异常。
     * @param planInstance 执行计划实例
     * @param jobInstance 作业实例
     */
    public void succeed(PlanInstance planInstance, JobInstance jobInstance) throws JobDispatchException {
        // 当前状态无需变更
        if (this.getStatus().isCompleted()) {
            return;
        }

        // 更新任务状态，更新失败说明已经处理过，CAS保证幂等
        setStatus(TaskStatus.SUCCEED);
        if (!taskRepo.executeSucceed(this)) {
            return;
        }

        // 更新作业状态，更新失败说明处理过
        if (!jobInstance.succeed()) {
            return;
        }

        // 触发后续任务下发
        dispatchNextTask(planInstance);
    }


    /**
     * 下发后续任务
     * @param planInstance 计划实例
     */
    protected void dispatchNextTask(PlanInstance planInstance) {

        // 从作业 DAG 中读取后续的作业节点
        JobDAG dag = planInstance.getDag();
        List<Job> subJobs = dag.getSubJobs(this.jobId);

        if (CollectionUtils.isEmpty(subJobs)) {

            // 后续作业不存在，需检测是否 Plan 执行完成
            if (planInstance.isAllJobFinished()) {
                planInstance.executeSucceed();
            }

        } else {

            // 后续作业存在，则检测是否可触发，并继续下发作业
            for (Job subJob : subJobs) {
                if (planInstance.isJobTriggerable(subJob)) {
                    JobInstance subJobInstance = subJob.newInstance(planInstance);
                    subJobInstance.dispatch();
                }
            }

        }
    }


    /**
     * 任务执行失败，worker反馈任务失败时，执行此方法。
     * @param planInstance 执行计划实例
     * @param jobInstance 作业实例
     * @param errorMsg 执行失败的异常信息
     * @param errorStackTrace 执行失败的异常堆栈
     */
    public void failed(PlanInstance planInstance, JobInstance jobInstance, String errorMsg, String errorStackTrace) {
        // 当前状态无需变更
        if (getStatus().isCompleted()) {
            return;
        }

        // 更新任务状态，更新失败说明已经处理过，CAS保证幂等
        setStatus(TaskStatus.FAILED);
        setErrorMsg(errorMsg);
        setErrorStackTrace(errorStackTrace);
        taskRepo.executeFailed(this);
    }

}
