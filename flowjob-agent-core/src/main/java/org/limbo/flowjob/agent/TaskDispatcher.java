/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.limbo.flowjob.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.limbo.flowjob.agent.repository.JobRepository;
import org.limbo.flowjob.agent.rpc.AgentBrokerRpc;
import org.limbo.flowjob.agent.rpc.AgentWorkerRpc;
import org.limbo.flowjob.agent.worker.Worker;
import org.limbo.flowjob.agent.worker.WorkerSelectInvocation;
import org.limbo.flowjob.agent.worker.WorkerSelector;
import org.limbo.flowjob.agent.worker.WorkerSelectorFactory;
import org.limbo.flowjob.agent.worker.WorkerStatisticsRepository;
import org.limbo.flowjob.api.constants.TaskStatus;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Devil
 * @since 2023/8/8
 */
@Slf4j
public class TaskDispatcher {

    private final AgentBrokerRpc agentBrokerRpc;

    private final AgentWorkerRpc agentWorkerRpc;


    private final JobRepository jobRepository;

    private final WorkerSelectorFactory workerSelectorFactory;

    private final WorkerStatisticsRepository statisticsRepository;

    public TaskDispatcher(AgentBrokerRpc agentBrokerRpc, AgentWorkerRpc agentWorkerRpc, JobRepository jobRepository,
                          WorkerSelectorFactory workerSelectorFactory, WorkerStatisticsRepository statisticsRepository) {
        this.agentBrokerRpc = agentBrokerRpc;
        this.agentWorkerRpc = agentWorkerRpc;
        this.jobRepository = jobRepository;
        this.workerSelectorFactory = workerSelectorFactory;
        this.statisticsRepository = statisticsRepository;
    }

    /**
     * 将任务下发给worker。
     * task status -> EXECUTING or FAILED
     */
    public boolean dispatch(Task task) {
        if (log.isDebugEnabled()) {
            log.debug("start dispatch task={}", task);
        }

        if (task.getStatus() != TaskStatus.DISPATCHING) {
            log.error("Cannot startup context due to current status: {} {}", task.getStatus(), task.getTaskId());
            return false;
        }

        if (task.getWorker() != null) {
            return dispatchWithWorkerSelect(task);
        } else {
            return dispatchWithWorkerId(task);
        }
    }

    /**
     *  指定 worker 的任务
     */
    private boolean dispatchWithWorkerId(Task task) {
        Worker worker = task.getWorker();
        try {
            // 发送任务到worker，根据worker返回结果，更新状态
            boolean dispatched = agentWorkerRpc.dispatch(worker, task);
            if (dispatched) {
                onDispatchSucceed(task, worker);
                return true;
            } else {
                onDispatchToWorkerFailed(task, worker);
            }
        } catch (Exception e) {
            log.error("Task dispatch with error task={}", task, e);
        }

        // 下发失败
        onDispatchFailed(task);
        return false;
    }

    /**
     * 需要worker选择的任务
     */
    private boolean dispatchWithWorkerSelect(Task task) {
        List<Worker> allWorkers = agentBrokerRpc.availableWorkers(task.getJobId(), true, true, true);
        if (CollectionUtils.isEmpty(allWorkers)) {
            return false;
        }

        Job job = jobRepository.getById(task.getJobId());
        WorkerSelectInvocation invocation = new WorkerSelectInvocation(job.getAttributes());
        WorkerSelector workerSelector = workerSelectorFactory.newSelector(job.getLoadBalanceType());
        for (int i = 0; i < 3; i++) {
            try {
                Worker worker = workerSelector.select(invocation, allWorkers);
                if (worker == null) {
                    return false;
                }

                // 发送任务到worker，根据worker返回结果，更新状态
                boolean dispatched = agentWorkerRpc.dispatch(worker, task);
                if (dispatched) {
                    onDispatchSucceed(task, worker);
                    return true;
                } else {
                    onDispatchToWorkerFailed(task, worker);
                }

                allWorkers = allWorkers.stream().filter(w -> !Objects.equals(w.getId(), worker.getId())).collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Task dispatch with error task={}", task, e);
            }
        }

        // 下发失败
        onDispatchFailed(task);
        return false;
    }

    /**
     * 下发任务到 worker 成功时的流程
     */
    private void onDispatchSucceed(Task task, Worker worker) {

        task.setWorker(worker);

        // 记录统计数据
        statisticsRepository.recordDispatched(worker);

        if (log.isDebugEnabled()) {
            log.debug("Task dispatch success task={}", task.getTaskId());
        }
    }

    /**
     * 下发任务到 worker 失败时的流程
     */
    private void onDispatchToWorkerFailed(Task task, Worker worker) {
        if (log.isDebugEnabled()) {
            log.debug("Task dispatch failed: task={} worker={}", task.getTaskId(), worker.getId());
        }
    }

    /**
     * 下发任务流程整体失败时的处理逻辑，重试后仍失败会触发此逻辑。
     */
    private void onDispatchFailed(Task task) {
        task.setStatus(TaskStatus.FAILED);

        if (log.isDebugEnabled()) {
            log.debug("Task dispatch fail task={}", task.getTaskId());
        }
    }

}