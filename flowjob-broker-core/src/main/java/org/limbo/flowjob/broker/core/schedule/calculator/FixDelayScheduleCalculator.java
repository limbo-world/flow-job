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

package org.limbo.flowjob.broker.core.schedule.calculator;

import lombok.extern.slf4j.Slf4j;
import org.limbo.flowjob.api.constants.ScheduleType;
import org.limbo.flowjob.broker.core.schedule.Calculated;
import org.limbo.flowjob.broker.core.schedule.ScheduleCalculator;
import org.limbo.flowjob.broker.core.schedule.ScheduleOption;
import org.limbo.flowjob.common.utils.time.TimeUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 固定间隔作业调度时间计算器
 *
 * @author Brozen
 * @since 2021-05-21
 */
@Slf4j
public class FixDelayScheduleCalculator extends ScheduleCalculator {

    protected FixDelayScheduleCalculator() {
        super(ScheduleType.FIXED_DELAY);
    }

    /**
     * 通过此策略计算下一次触发调度的时间戳。如果不应该被触发，返回0或负数。
     *
     * @param calculated 待调度对象
     * @return 下次触发调度的时间戳，当返回非正数时，表示作业不会有触发时间。
     */
    @Override
    public Long doCalculate(Calculated calculated) {
        ScheduleOption scheduleOption = calculated.scheduleOption();
        LocalDateTime lastFeedbackAt = calculated.lastFeedbackAt();
        // 如果为空，表示此次上次任务还没反馈，等待反馈后重新调度
        if (lastFeedbackAt == null) {
            // 如果上次触发为空表示这是第一次
            if (calculated.lastTriggerAt() == null) {
                Instant nowInstant = TimeUtils.currentInstant();
                long startScheduleAt = calculateStartScheduleTimestamp(calculated.scheduleOption());
                return Math.max(startScheduleAt, nowInstant.getEpochSecond());
            } else {
                return ScheduleCalculator.NO_TRIGGER;
            }
        }

        Duration interval = scheduleOption.getScheduleInterval();
        if (interval == null) {
            log.error("cannot calculate next trigger timestamp of {} because interval is not assigned!", calculated);
            return ScheduleCalculator.NO_TRIGGER;
        }

        long now = TimeUtils.currentInstant().toEpochMilli();
        long scheduleAt = TimeUtils.toInstant(lastFeedbackAt).toEpochMilli() + interval.toMillis();
        return Math.max(scheduleAt, now);
    }

}
