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

package org.limbo.flowjob.broker.core.repository;

import org.limbo.flowjob.broker.api.constants.enums.TaskStatus;
import org.limbo.flowjob.broker.core.plan.job.context.Task;

import java.util.List;

/**
 * @author Brozen todo @B 状态流转
 * @since 2021-05-19
 */
public interface TaskRepository {

    /**
     * 持久化任务
     * @param task 任务
     */
    String add(Task task);

    /**
     * 任务下发成功，将任务状态从 {@link TaskStatus#DISPATCHING} 更新为 {@link TaskStatus#EXECUTING}
     * @param task 任务
     * @return 返回是否更新成功
     */
    boolean dispatched(Task task);

    /**
     * 任务下发失败，将任务状态从 {@link TaskStatus#DISPATCHING} 更新为 {@link TaskStatus#DISPATCH_FAILED}
     * @param task 任务
     * @return 返回是否更新成功
     */
    boolean dispatchFailed(Task task);

    /**
     * 任务执行成功，将任务状态从 {@link TaskStatus#EXECUTING} 更新为 {@link TaskStatus#SUCCEED}
     * @return 返回是否更新成功
     */
    boolean executeSucceed(Task task);

    /**
     * 任务执行失败，将任务状态从 {@link TaskStatus#EXECUTING} 更新为 {@link TaskStatus#FAILED}，并记录错误信息。
     * @return 返回是否更新成功
     */
    boolean executeFailed(Task task);

    /**
     * 根据状态统计数据
     */
    Long countByStatuses(String jobInstanceId, List<TaskStatus> statuses);

    /**
     * 获取作业执行实例
     */
    Task get(String taskId);

}