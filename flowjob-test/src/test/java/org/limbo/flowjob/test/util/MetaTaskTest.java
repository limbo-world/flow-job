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

package org.limbo.flowjob.test.util;

import com.cronutils.model.CronType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Test;
import org.limbo.flowjob.api.constants.ScheduleType;
import org.limbo.flowjob.api.constants.TriggerType;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.CronMetaTask;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.FixDelayMetaTask;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.FixRateMetaTask;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.MetaTaskScheduler;
import org.limbo.flowjob.broker.core.schedule.scheduler.meta.MetaTaskType;
import org.limbo.flowjob.common.thread.CommonThreadPool;
import org.limbo.flowjob.common.utils.time.TimeUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @author Devil
 * @since 2022/12/21
 */
@Slf4j
class MetaTaskTest {

    MetaTaskScheduler metaTaskScheduler = new MetaTaskScheduler(100L, TimeUnit.MILLISECONDS);

    @Test
    void testTime() {
        LocalDateTime startAt = LocalDateTime.of(2022, 12, 21, 13, 2, 58, 851);
        Duration delay = Duration.ZERO;
        long startScheduleAt = startAt.toInstant(TimeUtils.zoneOffset()).toEpochMilli();
        System.out.println(startScheduleAt);
        System.out.println(startAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        long l = startScheduleAt + delay.toMillis();
        System.out.println(l);
        System.out.println(DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT.format(l));
    }

    @Test
    void testFixDelay() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        metaTaskScheduler.schedule(new FixDelayMetaTask(Duration.ofMillis(5000), metaTaskScheduler) {

            @Override
            protected void executeTask() {
                log.info("execute {} start triggerAt:{} time:{}", scheduleId(), getLastTriggerAt(), TimeUtils.currentLocalDateTime());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("execute end {}", TimeUtils.currentLocalDateTime());
            }

            @Override
            public MetaTaskType getType() {
                return MetaTaskType.PLAN;
            }

            @Override
            public String getMetaId() {
                return "123";
            }
        });

        Thread.sleep(30000);
    }

    @Test
    void testFixRate() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        metaTaskScheduler.schedule(new FixRateMetaTask(Duration.ofMillis(5000), metaTaskScheduler) {

            @Override
            protected void executeTask() {
                log.info("execute {} start triggerAt:{} time:{}", scheduleId(), getLastTriggerAt(), TimeUtils.currentLocalDateTime());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("execute end {}", TimeUtils.currentLocalDateTime());
            }

            @Override
            public MetaTaskType getType() {
                return MetaTaskType.PLAN;
            }

            @Override
            public String getMetaId() {
                return "123";
            }
        });

        Thread.sleep(30000);
    }

    @Test
    void testCron() throws InterruptedException {
        log.info("start test {}", TimeUtils.currentLocalDateTime());
        metaTaskScheduler.schedule(new CronMetaTask("0/10 * * * * ? *", CronType.QUARTZ.name(), metaTaskScheduler) {
//        metaTaskScheduler.schedule(new CronMetaTask("0/60 * * * * ? *", CronType.QUARTZ.name(), metaTaskScheduler) {
//        metaTaskScheduler.schedule(new CronMetaTask("0-20/5 * * * * ? *", CronType.QUARTZ.name(), metaTaskScheduler) {

            @Override
            protected void executeTask() {
                log.info("execute {} start triggerAt:{} time:{}", scheduleId(), getLastTriggerAt(), TimeUtils.currentLocalDateTime());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("execute end {}", TimeUtils.currentLocalDateTime());
            }

            @Override
            public MetaTaskType getType() {
                return MetaTaskType.PLAN;
            }

            @Override
            public String getMetaId() {
                return "123";
            }
        });

        Thread.sleep(300000);
    }

}
