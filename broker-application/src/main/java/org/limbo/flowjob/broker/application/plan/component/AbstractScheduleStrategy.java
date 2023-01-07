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
import org.apache.commons.collections4.CollectionUtils;
import org.limbo.flowjob.broker.core.dispatch.TaskDispatcher;
import org.limbo.flowjob.broker.core.domain.IDGenerator;
import org.limbo.flowjob.broker.core.domain.IDType;
import org.limbo.flowjob.broker.core.domain.job.JobInfo;
import org.limbo.flowjob.broker.core.domain.job.JobInstance;
import org.limbo.flowjob.broker.core.domain.plan.Plan;
import org.limbo.flowjob.broker.core.domain.plan.PlanInfo;
import org.limbo.flowjob.broker.core.domain.task.Task;
import org.limbo.flowjob.broker.core.domain.task.TaskFactory;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.MetaTaskScheduler;
import org.limbo.flowjob.broker.core.schedule.strategy.IScheduleStrategy;
import org.limbo.flowjob.broker.dao.converter.DomainConverter;
import org.limbo.flowjob.broker.dao.entity.JobInfoEntity;
import org.limbo.flowjob.broker.dao.entity.JobInstanceEntity;
import org.limbo.flowjob.broker.dao.entity.PlanInfoEntity;
import org.limbo.flowjob.broker.dao.entity.PlanInstanceEntity;
import org.limbo.flowjob.broker.dao.entity.TaskEntity;
import org.limbo.flowjob.broker.dao.repositories.JobInfoEntityRepo;
import org.limbo.flowjob.broker.dao.repositories.JobInstanceEntityRepo;
import org.limbo.flowjob.broker.dao.repositories.PlanInfoEntityRepo;
import org.limbo.flowjob.broker.dao.repositories.PlanInstanceEntityRepo;
import org.limbo.flowjob.broker.dao.repositories.TaskEntityRepo;
import org.limbo.flowjob.common.constants.JobStatus;
import org.limbo.flowjob.common.constants.JobType;
import org.limbo.flowjob.common.constants.MsgConstants;
import org.limbo.flowjob.common.constants.PlanStatus;
import org.limbo.flowjob.common.constants.PlanType;
import org.limbo.flowjob.common.constants.TaskStatus;
import org.limbo.flowjob.common.constants.TaskType;
import org.limbo.flowjob.common.constants.TriggerType;
import org.limbo.flowjob.common.utils.Verifies;
import org.limbo.flowjob.common.utils.json.JacksonUtils;
import org.limbo.flowjob.common.utils.time.TimeUtils;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 * @since 2023/1/4
 */
@Slf4j
public abstract class AbstractScheduleStrategy implements IScheduleStrategy {

    @Setter(onMethod_ = @Inject)
    protected TaskEntityRepo taskEntityRepo;

    @Setter(onMethod_ = @Inject)
    protected TaskFactory taskFactory;

    @Setter(onMethod_ = @Inject)
    protected MetaTaskScheduler metaTaskScheduler;

    @Setter(onMethod_ = @Inject)
    protected IDGenerator idGenerator;

    @Setter(onMethod_ = @Inject)
    protected PlanInstanceEntityRepo planInstanceEntityRepo;

    @Setter(onMethod_ = @Inject)
    protected DomainConverter domainConverter;

    @Setter(onMethod_ = @Inject)
    protected JobInstanceEntityRepo jobInstanceEntityRepo;

    @Setter(onMethod_ = @Inject)
    protected PlanInfoEntityRepo planInfoEntityRepo;

    @Setter(onMethod_ = @Inject)
    protected JobInfoEntityRepo jobInfoEntityRepo;

    @Setter(onMethod_ = @Inject)
    protected TaskDispatcher taskDispatcher;

    @Override
    @Transactional
    public void schedule(Plan plan) {
        String planId = plan.getPlanId();

        PlanInfoEntity planInfoEntity = planInfoEntityRepo.findById(plan.getVersion()).orElse(null);
        Verifies.notNull(planInfoEntity, "does not find " + planId + " plan's info by version--" + plan.getVersion() + "");

//        // 加锁 这个版本判断逻辑是为什么？？？
//        PlanEntity planEntity = planEntityRepo.selectForUpdate(planId);
//        if (!Objects.equals(plan.getVersion(), planEntity.getCurrentVersion())) {
//            log.info("{} version {} change to {}", plan.scheduleId(), plan.getVersion(), planEntity.getCurrentVersion());
//            return;
//        }

        // 判断并发情况下 是否已经有人提交调度任务 如有则无需处理 防止重复创建数据
        PlanInstanceEntity planInstanceEntity = planInstanceEntityRepo.findByPlanIdAndTriggerAtAndTriggerType(
                planId, plan.getTriggerAt(), TriggerType.SCHEDULE.type
        );
        if (planInstanceEntity != null) {
            return;
        }
        // 调度逻辑
        schedulePlanInfo(plan.getPlanInfo(), plan.getTriggerAt());
    }

    protected abstract void schedulePlanInfo(PlanInfo planInfo, LocalDateTime triggerAt);

    @Override
    @Transactional
    public void schedule(Task task) {
        if (task.getStatus() != TaskStatus.DISPATCHING) {
            return;
        }

        int num = taskEntityRepo.updateStatusDispatching(task.getTaskId());
        if (num < 1) {
            return; // 可能多个节点操作同个task
        }

        // 下面两个可能会被其他task更新 但是这是正常的
        planInstanceEntityRepo.executing(task.getPlanId(), TimeUtils.currentLocalDateTime());
        jobInstanceEntityRepo.updateStatusExecuting(task.getJobInstanceId());

        boolean dispatched = taskDispatcher.dispatch(task);
        if (dispatched) {
            // 下发成功
            taskEntityRepo.updateStatusExecuting(task.getTaskId(), task.getWorkerId(), TimeUtils.currentLocalDateTime());
        } else {
            // 下发失败
            handleTaskFail(task, MsgConstants.DISPATCH_FAIL, "");
        }
    }

    @Override
    @Transactional
    public void handleTaskSuccess(Task task, Map<String, Object> resultAttributes) {
        // todo 更新plan上下文
        int num = taskEntityRepo.updateStatusSuccess(task.getTaskId(), TimeUtils.currentLocalDateTime(), JacksonUtils.toJSONString(resultAttributes));

        if (num != 1) { // 已经被更新 无需重复处理
            return;
        }
        afterTaskStatusUpdateSuccess(task);
    }

    @Override
    @Transactional
    public void handleTaskFail(Task task, String errorMsg, String errorStackTrace) {
        int num = taskEntityRepo.updateStatusFail(task.getTaskId(), TimeUtils.currentLocalDateTime(), errorMsg, errorStackTrace);

        if (num != 1) {
            return; // 并发更新过了 正常来说前面job更新成功 这个不可能会进来
        }
        afterTaskStatusUpdateSuccess(task);
    }

    private void afterTaskStatusUpdateSuccess(Task task) {
        JobInstanceEntity jobInstanceEntity = jobInstanceEntityRepo.findById(task.getJobInstanceId()).orElse(null);
        JobInfoEntity jobInfoEntity = jobInfoEntityRepo.findByPlanInfoIdAndName(jobInstanceEntity.getPlanInfoId(), jobInstanceEntity.getJobId());
        JobInstance jobInstance = domainConverter.toJobInstance(jobInstanceEntity, jobInfoEntity);

        // 判断状态是不是已经更新 可能已经被其它线程处理 正常来说不可能的
        if (JobStatus.EXECUTING != jobInstance.getStatus()) {
            log.warn("task:{} update status success but jobInstance:{} is already changed", task.getTaskId(), task.getJobInstanceId());
            return;
        }
        // 检查task是否都已经完成
        List<TaskEntity> taskEntities = taskEntityRepo.findByJobInstanceIdAndType(task.getJobInstanceId(), task.getTaskType().type);
        for (TaskEntity taskE : taskEntities) {
            if (!TaskStatus.parse(taskE.getStatus()).isCompleted()) {
                return; // 如果还未完成 交由最后完成的task去做后续逻辑处理
            }
        }

        // 如果所有task都是执行成功 则处理成功
        // 如果所有task都是执行失败 则处理失败
        boolean success = taskEntities.stream().allMatch(entity -> TaskStatus.SUCCEED == TaskStatus.parse(entity.getStatus()));
        if (success) {
            JobType jobType = JobType.parse(jobInfoEntity.getType());
            switch (jobType) {
                case NORMAL:
                case BROADCAST:
                    handleJobSuccess(jobInstance);
                    break;
                case MAP:
                    handleMapJobSuccess(task, jobInstance);
                    break;
                case MAP_REDUCE:
                    handleMapReduceJobSuccess(task, jobInstance);
                    break;
                default:
                    throw new IllegalArgumentException(MsgConstants.UNKNOWN + " JobType in jobInstance:" + jobInstance.getJobInstanceId());
            }
        } else {
            handleJobFail(jobInstance);
        }

    }

    private void handleMapJobSuccess(Task task, JobInstance jobInstance) {
        switch (task.getTaskType()) {
            case SPLIT:
                saveAndScheduleTask(taskFactory.create(jobInstance, jobInstance.getTriggerAt(), TaskType.MAP));
                break;
            case MAP:
                handleJobSuccess(jobInstance);
                break;
            default:
                throw new IllegalArgumentException("Illegal TaskType in task:" + task.getTaskId());
        }
    }

    private void handleMapReduceJobSuccess(Task task, JobInstance jobInstance) {
        switch (task.getTaskType()) {
            case SPLIT:
                saveAndScheduleTask(taskFactory.create(jobInstance, jobInstance.getTriggerAt(), TaskType.MAP));
                break;
            case MAP:
                saveAndScheduleTask(taskFactory.create(jobInstance, jobInstance.getTriggerAt(), TaskType.REDUCE));
                break;
            case REDUCE:
                handleJobSuccess(jobInstance);
                break;
            default:
                throw new IllegalArgumentException("Illegal TaskType in task:" + task.getTaskId());
        }
    }

    public abstract void handleJobSuccess(JobInstance jobInstance);

    public abstract void handleJobFail(JobInstance jobInstance);

    /**
     * 生成新的计划调度记录
     *
     * @param triggerType 触发类型
     * @return 记录id
     */
    protected String savePlanInstanceEntity(String planId, String version, TriggerType triggerType, LocalDateTime triggerAt) {
        String planInstanceId = idGenerator.generateId(IDType.PLAN_INSTANCE);
        PlanInstanceEntity planInstanceEntity = new PlanInstanceEntity();
        planInstanceEntity.setPlanInstanceId(planInstanceId);
        planInstanceEntity.setPlanId(planId);
        planInstanceEntity.setPlanInfoId(version);
        planInstanceEntity.setStatus(PlanStatus.SCHEDULING.status);
        planInstanceEntity.setTriggerType(triggerType.type);
        planInstanceEntity.setTriggerAt(triggerAt);
        planInstanceEntityRepo.saveAndFlush(planInstanceEntity);
        return planInstanceId;
    }

    @Transactional
    public void saveAndScheduleTask(List<Task> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }

        List<TaskEntity> taskEntities = tasks.stream().map(domainConverter::toTaskEntity).collect(Collectors.toList());
        taskEntityRepo.saveAll(taskEntities);
        taskEntityRepo.flush();
        for (Task task : tasks) {
            try {
                metaTaskScheduler.schedule(task);
            } catch (Exception e) {
                // 调度失败 不要影响事务，事务提交后 由task的状态检查任务去修复task的执行情况
                log.error("task schedule fail! task={}", task, e);
            }
        }
    }

    protected JobInstance newJobInstance(String planId, String planVersion, PlanType planType, String planInstanceId, JobInfo jobInfo, LocalDateTime triggerAt) {
        JobInstance instance = new JobInstance();
        instance.setJobInstanceId(idGenerator.generateId(IDType.JOB_INSTANCE));
        instance.setPlanId(planId);
        instance.setPlanInstanceId(planInstanceId);
        instance.setPlanVersion(planVersion);
        instance.setPlanType(planType);
        instance.setJobId(jobInfo.getId());
        instance.setDispatchOption(jobInfo.getDispatchOption());
        instance.setExecutorName(jobInfo.getExecutorName());
        instance.setType(jobInfo.getType());
        instance.setTriggerType(jobInfo.getTriggerType());
        instance.setStatus(JobStatus.SCHEDULING);
        instance.setTriggerAt(triggerAt);
        instance.setAttributes(jobInfo.getAttributes());
        instance.setTerminateWithFail(jobInfo.isTerminateWithFail());
        return instance;
    }

}
