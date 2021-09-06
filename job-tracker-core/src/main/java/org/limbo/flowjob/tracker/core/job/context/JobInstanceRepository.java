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

package org.limbo.flowjob.tracker.core.job.context;

import org.limbo.flowjob.tracker.commons.constants.enums.JobScheduleStatus;

import java.util.List;

/**
 * @author Brozen
 * @since 2021-05-19
 */
public interface JobInstanceRepository {

    Long createId();

    /**
     * 持久化作业实例
     * @param instance 作业执行实例
     */
    void add(JobInstance instance);

    /**
     * 更新作业实例
     * @param instance 作业执行实例
     */
    void updateInstance(Task instance);

    void end(JobInstance instance);

    /**
     * 将实例的状态由旧值更新为新值，如果当前状态不为旧值不会更新
     * @param planId
     * @param planInstanceId
     * @param jobId
     * @param oldState
     * @param newState
     */
    void compareAndSwapInstanceState(String planId, Long planInstanceId, String jobId, JobScheduleStatus oldState, JobScheduleStatus newState);

    /**
     * 获取作业执行实例
     * @param planId 作业ID
     * @param planInstanceId 实例ID
     * @param jobId 作业ID
     * @return 作业实例
     */
    Task getInstance(String planId, Long planInstanceId, String jobId);

    /**
     * 批量获取作业执行实例
     * @param planId 作业ID
     * @param planInstanceId 实例ID
     * @param jobIds 作业ID
     * @return 作业实例
     */
    List<Task> getInstances(String planId, Long planInstanceId, List<String> jobIds);

    /**
     * 获取最近一次作业执行时的实例
     * @param jobId 作业ID
     * @return 最近一次作业执行时的实例
     */
    Task getLatestInstance(String jobId);
}
