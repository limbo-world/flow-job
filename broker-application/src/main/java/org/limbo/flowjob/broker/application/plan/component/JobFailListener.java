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

package org.limbo.flowjob.broker.application.plan.component;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.limbo.flowjob.broker.api.constants.enums.JobStatus;
import org.limbo.flowjob.broker.application.plan.support.EventListener;
import org.limbo.flowjob.broker.core.events.Event;
import org.limbo.flowjob.broker.core.events.EventTopic;
import org.limbo.flowjob.broker.core.exceptions.JobExecuteException;
import org.limbo.flowjob.broker.core.plan.PlanInstance;
import org.limbo.flowjob.broker.core.plan.ScheduleEventTopic;
import org.limbo.flowjob.broker.core.plan.job.JobInstance;
import org.limbo.flowjob.broker.core.plan.job.context.TaskCreatorFactory;
import org.limbo.flowjob.broker.core.plan.job.handler.JobFailHandler;
import org.limbo.flowjob.broker.core.repository.PlanInstanceRepository;
import org.limbo.flowjob.broker.core.schedule.scheduler.Scheduler;
import org.limbo.flowjob.broker.dao.repositories.JobInstanceEntityRepo;
import org.limbo.flowjob.common.utils.TimeUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.transaction.Transactional;

/**
 * @author Devil
 * @since 2022/8/5
 */
@Slf4j
@Component
public class JobFailListener implements EventListener {

    @Setter(onMethod_ = @Inject)
    private JobInstanceEntityRepo jobInstanceEntityRepo;

    @Setter(onMethod_ = @Inject)
    private PlanInstanceRepository planInstanceRepository;

    @Setter(onMethod_ = @Inject)
    private Scheduler scheduler;

    @Setter(onMethod_ = @Inject)
    private TaskCreatorFactory taskCreatorFactory;

    @Override
    public EventTopic topic() {
        return ScheduleEventTopic.JOB_FAIL;
    }

    @Override
    @Transactional
    public void accept(Event event) {
        JobInstance jobInstance = (JobInstance) event.getSource();

        int num = jobInstanceEntityRepo.updateStatus(
                Long.valueOf(jobInstance.getJobInstanceId()),
                JobStatus.EXECUTING.status,
                JobStatus.FAILED.status
        );

        if (num != 1) {
            return;
        }

        // todo 这里获取到的 里面应该包含所有JobInstance
        PlanInstance planInstance = planInstanceRepository.get(jobInstance.getPlanInstanceId());

        if (planInstance.needRetryJob(jobInstance.getJobId())) {
            // 重试处理
            jobInstance = planInstance.getJobInfo(jobInstance.getJobId()).newInstance(
                    planInstance.getPlanInstanceId(),
                    taskCreatorFactory,
                    TimeUtil.currentLocalDateTime()); // todo 根据重试间隔决定触发时间
            scheduler.schedule(jobInstance);
        } else {
            // 执行失败处理
            JobFailHandler failHandler = jobInstance.getFailHandler();
            try {
                failHandler.handle();
            } catch (JobExecuteException e) {
                log.error("[JobFailHandler]execute error jobInstance:{}", jobInstance, e);
            }
            if (failHandler.terminate()) {
                planInstance.executeFailed();
            } else {
                planInstance.dispatchNext(jobInstance.getJobId());
            }
        }

    }

}