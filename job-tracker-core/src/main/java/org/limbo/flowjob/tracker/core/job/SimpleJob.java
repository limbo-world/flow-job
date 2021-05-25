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

package org.limbo.flowjob.tracker.core.job;

import lombok.Getter;
import org.limbo.flowjob.tracker.core.executor.dispatcher.JobDispatchType;
import org.limbo.flowjob.tracker.core.job.context.JobContextRepository;
import org.limbo.flowjob.tracker.core.job.schedule.JobScheduleCalculator;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author Brozen
 * @since 2021-05-20
 */
public class SimpleJob extends AbstractJob implements Job {

    /**
     * 作业ID
     */
    @Getter
    private String id;

    /**
     * CPU内核需求数量
     */
    @Getter
    private float cpuRequirement;

    /**
     * 内存需求数量
     */
    @Getter
    private float ramRequirement;

    /**
     * 作业调度方式
     */
    @Getter
    private JobScheduleType scheduleType;

    /**
     * 作业分发方式
     */
    @Getter
    private JobDispatchType dispatchType;

    /**
     * 作业延迟时间
     * @see Job#getScheduleDelay()
     */
    @Getter
    private Duration scheduleDelay;

    /**
     * 作业调度间隔时间
     * @see Job#getScheduleInterval()
     */
    @Getter
    private Duration scheduleInterval;

    /**
     * 作业调度的CRON表达式
     */
    @Getter
    private String scheduleCron;

    /**
     * 作业创建时间
     */
    @Getter
    private LocalDateTime createdAt;


    public SimpleJob(String id, float cpuRequirement, float ramRequirement,
                     JobScheduleType scheduleType, JobDispatchType dispatchType,
                     Duration scheduleDelay, Duration scheduleInterval,
                     String scheduleCron, LocalDateTime createdAt,
                     JobScheduleCalculator triggerCalculator,
                     JobContextRepository jobContextRepository) {
        super(triggerCalculator, jobContextRepository);

        this.id = id;
        this.cpuRequirement = cpuRequirement;
        this.ramRequirement = ramRequirement;
        this.scheduleType = scheduleType;
        this.dispatchType = dispatchType;

        this.scheduleDelay = scheduleDelay;
        this.scheduleInterval = scheduleInterval;
        this.scheduleCron = scheduleCron;
        this.createdAt = createdAt;
    }

}