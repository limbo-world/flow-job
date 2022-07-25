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

package org.limbo.flowjob.broker.application.plan.config;

import org.limbo.flowjob.broker.core.cluster.WorkerManager;
import org.limbo.flowjob.broker.core.cluster.WorkerManagerImpl;
import org.limbo.flowjob.broker.core.dispatcher.WorkerSelectorFactory;
import org.limbo.flowjob.broker.core.dispatcher.strategies.RoundRobinWorkerSelector;
import org.limbo.flowjob.broker.core.plan.job.context.TaskCreateStrategyFactory;
import org.limbo.flowjob.broker.core.schedule.calculator.SimpleScheduleCalculatorFactory;
import org.limbo.flowjob.broker.core.schedule.scheduler.HashedWheelTimerScheduler;
import org.limbo.flowjob.broker.core.schedule.scheduler.Scheduler;
import org.limbo.flowjob.broker.core.worker.WorkerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Brozen
 * @since 2021-06-01
 */
@ComponentScan({
        "org.limbo.flowjob.tracker.infrastructure.plan",
        "org.limbo.flowjob.tracker.infrastructure.job",
        "org.limbo.flowjob.tracker.infrastructure.worker",
})
@EnableConfigurationProperties({BrokerProperties.class})
public class BrokerConfiguration {

    @Value("${server.port}")
    private int port;

    @Autowired
    private BrokerProperties brokerProperties;

//    @Bean
//    @ConditionalOnMissingBean(TrackerNode.class)
//    public TrackerNode trackerNode(WorkerManager workerManager,
//                                   JobTrackerFactory jobTrackerFactory) {
//
//        TrackerModes mode = TrackerModes.parse(trackerProperties.getMode());
//        switch (mode) {
//            case SINGLE:
//                return new SingleTrackerNode(trackerProperties.getHost(), port, jobTrackerFactory, workerManager);
//
//            case ELECTION: {
//                // raft 选举参数
//                ElectionNodeOptions electionNodeOptions = new ElectionNodeOptions();
//                electionNodeOptions.setDataPath(trackerProperties.getDataPath());
//                electionNodeOptions.setGroupId(StringUtils.isBlank(trackerProperties.getGroupId()) ? "flowjob" :
//                        trackerProperties.getDataPath());
//                electionNodeOptions.setServerAddress(trackerProperties.getServerAddress());
//                electionNodeOptions.setServerAddressList(trackerProperties.getServerAddressList());
//
//                return new ElectionTrackerNode(port, electionNodeOptions, jobTrackerFactory, workerManager);
//            }
//
//            case CLUSTER:
//                throw new UnsupportedOperationException("cluster mode is not supported now.");
//
//            default:
//                throw new IllegalArgumentException("flowjob.tracker.mode only can be null or election/cluster");
//
//
//        }
//
//    }
//
//    /**
//     * 主从模式下，tracker 节点工厂
//     */
//    @Bean
//    public JobTrackerFactory jobTrackerFactory(Scheduler scheduler) {
//        return new JobTrackerFactory(scheduler);
//    }


    /**
     * 任务调度器
     */
    @Bean
    public Scheduler scheduler() {
        return new HashedWheelTimerScheduler();
    }


    /**
     * worker 管理，持久化等
     */
    @Bean
    public WorkerManager workerManager(WorkerRepository workerRepository) {
        return new WorkerManagerImpl(workerRepository);
    }

    /**
     * 调度时间计算器
     */
    @Bean
    public SimpleScheduleCalculatorFactory scheduleCalculatorFactory() {
        return new SimpleScheduleCalculatorFactory();
    }


    /**
     * 作业分发器工厂
     */
    @Bean
    public WorkerSelectorFactory workerSelectorFactory() {
        return new WorkerSelectorFactory();
    }


    /**
     * Worker负载均衡：轮询
     */
    @Bean
    public RoundRobinWorkerSelector roundRobinWorkerSelector() {
        return new RoundRobinWorkerSelector();
    }


    /**
     * 任务生成策略工厂
     */
    @Bean
    public TaskCreateStrategyFactory taskCreateStrategyFactory() {
        return new TaskCreateStrategyFactory();
    }

}