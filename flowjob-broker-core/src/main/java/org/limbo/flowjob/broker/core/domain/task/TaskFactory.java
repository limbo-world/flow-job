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

package org.limbo.flowjob.broker.core.domain.task;

import org.apache.commons.collections4.CollectionUtils;
import org.limbo.flowjob.api.constants.TaskStatus;
import org.limbo.flowjob.api.constants.TaskType;
import org.limbo.flowjob.broker.core.domain.IDGenerator;
import org.limbo.flowjob.broker.core.domain.IDType;
import org.limbo.flowjob.broker.core.domain.job.JobInfo;
import org.limbo.flowjob.broker.core.domain.job.JobInstance;
import org.limbo.flowjob.broker.core.worker.Worker;
import org.limbo.flowjob.broker.core.worker.WorkerRepository;
import org.limbo.flowjob.common.utils.attribute.Attributes;
import org.limbo.flowjob.common.utils.time.TimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author Devil
 * @since 2022/8/17
 */
public class TaskFactory {

    /**
     * 策略类型和策略生成器直接的映射
     */
    private final Map<TaskType, TaskCreator> creators;

    private final WorkerRepository workerRepository;

    private final TaskManager taskManager;

    private final IDGenerator idGenerator;

    public TaskFactory(WorkerRepository workerRepository, TaskManager taskManager, IDGenerator idGenerator) {
        this.workerRepository = workerRepository;
        this.taskManager = taskManager;
        this.idGenerator = idGenerator;

        creators = new EnumMap<>(TaskType.class);

        creators.put(TaskType.STANDALONE, new NormalTaskCreator());
        creators.put(TaskType.BROADCAST, new BroadcastTaskCreator());
        creators.put(TaskType.MAP, new MapTaskCreator());
        creators.put(TaskType.REDUCE, new ReduceTaskCreator());
        creators.put(TaskType.SHARDING, new ShardingTaskCreator());
    }

    public List<Task> create(JobInstance instance, TaskType taskType) {
        TaskCreator creator = creators.get(taskType);
        if (creator == null) {
            return Collections.emptyList();
        }
        return creator.tasks(instance);
    }

    /**
     * Task 创建策略接口，在这里对 Task 进行多种代理（装饰），实现下发重试策略。
     */
    abstract class TaskCreator {

        public abstract TaskType getType();

        public abstract List<Task> tasks(JobInstance instance);

        protected Task initTask(TaskType type, JobInstance instance, String workerId, LocalDateTime triggerAt) {
            JobInfo jobInfo = instance.getJobInfo();
            Task task = new Task();
            task.setTaskId(idGenerator.generateId(IDType.TASK));
            task.setJobId(jobInfo.getId());
            task.setType(type);
            task.setPlanVersion(instance.getPlanVersion());
            task.setPlanId(instance.getPlanId());
            task.setPlanInstanceId(instance.getPlanInstanceId());
            task.setJobInstanceId(instance.getJobInstanceId());
            task.setStatus(TaskStatus.SCHEDULING);
            task.setDispatchOption(jobInfo.getDispatchOption());
            task.setExecutorName(jobInfo.getExecutorName());
            task.setContext(instance.getContext());
            task.setJobAttributes(instance.getJobAttributes());
            task.setWorkerId(workerId);
            task.setTriggerAt(triggerAt);
            return task;
        }

    }

    /**
     * 普通任务创建策略
     */
    public class NormalTaskCreator extends TaskCreator {

        @Override
        public List<Task> tasks(JobInstance instance) {
            Task task = initTask(TaskType.STANDALONE, instance, null, instance.getTriggerAt());
            return Collections.singletonList(task);
        }

        /**
         * 此策略仅适用于 {@link TaskType#STANDALONE} 类型的任务
         */
        @Override
        public TaskType getType() {
            return TaskType.STANDALONE;
        }
    }

    /**
     * 广播任务创建策略
     */
    public class BroadcastTaskCreator extends TaskCreator {

        @Override
        public List<Task> tasks(JobInstance instance) {
            List<Worker> workers = workerRepository.listAvailableWorkers();
            if (CollectionUtils.isEmpty(workers)) {
                return Collections.emptyList();
            }
            List<Task> tasks = new ArrayList<>();
            for (Worker worker : workers) {
                Task task = initTask(TaskType.BROADCAST, instance, worker.getId(), instance.getTriggerAt());
                tasks.add(task);
            }
            return tasks;
        }

        /**
         * 此策略仅适用于 {@link TaskType#BROADCAST} 类型的任务
         */
        @Override
        public TaskType getType() {
            return TaskType.BROADCAST;
        }

    }

    /**
     * 分片任务创建策略
     */
    public class ShardingTaskCreator extends TaskCreator {

        @Override
        public List<Task> tasks(JobInstance instance) {
            Task task = initTask(TaskType.SHARDING, instance, null, instance.getTriggerAt());
            return Collections.singletonList(task);
        }

        /**
         * 此策略仅适用于 {@link TaskType#SHARDING} 类型的任务
         */
        @Override
        public TaskType getType() {
            return TaskType.SHARDING;
        }

    }


    /**
     * Map任务创建策略
     */
    public class MapTaskCreator extends TaskCreator {

        @Override
        public List<Task> tasks(JobInstance instance) {
            TaskResult taskResult = taskManager.getTaskResults(instance.getJobInstanceId(), TaskType.SHARDING).get(0);
            List<Task> tasks = new ArrayList<>();
            for (Map<String, Object> attribute : taskResult.getSubTaskAttributes()) {
                Task task = initTask(TaskType.MAP, instance, null, TimeUtils.currentLocalDateTime());
                task.setMapAttributes(new Attributes(attribute));
                tasks.add(task);
            }
            return tasks;
        }

        /**
         * 此策略仅适用于 {@link TaskType#MAP} 类型的任务
         */
        @Override
        public TaskType getType() {
            return TaskType.MAP;
        }

    }

    /**
     * Reduce任务创建策略
     */
    public class ReduceTaskCreator extends TaskCreator {

        @Override
        public List<Task> tasks(JobInstance instance) {
            List<TaskResult> taskResults = taskManager.getTaskResults(instance.getJobInstanceId(), TaskType.MAP);
            List<Attributes> reduceAttributes = new ArrayList<>();
            for (TaskResult taskResult : taskResults) {
                reduceAttributes.add(new Attributes(taskResult.getResultAttributes()));
            }
            Task task = initTask(TaskType.REDUCE, instance, null, TimeUtils.currentLocalDateTime());
            task.setReduceAttributes(reduceAttributes);
            return Collections.singletonList(task);
        }

        /**
         * 此策略仅适用于 {@link TaskType#REDUCE} 类型的任务
         */
        @Override
        public TaskType getType() {
            return TaskType.REDUCE;
        }
    }

}
